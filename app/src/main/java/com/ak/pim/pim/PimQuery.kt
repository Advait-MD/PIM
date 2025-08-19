package com.ak.pim.pim

import android.content.ContentResolver
import android.database.Cursor
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.util.Log
import com.ak.pim.model.Parameters

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

private const val TAG = "PimQuery"

fun queryPimService(
    contentResolver: ContentResolver,
    service: String,
    parameters: Parameters,
    userPrompt: String
): List<Map<String, Any>> {
    return when (service.lowercase(Locale.US)) {
        "calendar" -> queryGeneric(
            contentResolver,
            CalendarContract.Events.CONTENT_URI,
            parameters,
            userPrompt,
            fieldMap = mapOf(
                "title" to CalendarContract.Events.TITLE,
                "start_date" to CalendarContract.Events.DTSTART,
                "end_date" to CalendarContract.Events.DTEND,
                "description" to CalendarContract.Events.DESCRIPTION,
                "location" to CalendarContract.Events.EVENT_LOCATION
            )
        )

        "contacts" -> {
            // Basic contact handling (phone or email)
            val wantsPhone = parameters.fields.any { it.equals("phone_number", true) }
            val wantsEmail = parameters.fields.any { it.equals("email", true) }

            val (uri, fieldMap) = when {
                wantsPhone && !wantsEmail -> {
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI to mapOf(
                        "display_name" to ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        "phone_number" to ContactsContract.CommonDataKinds.Phone.NUMBER
                    )
                }
                wantsEmail && !wantsPhone -> {
                    ContactsContract.CommonDataKinds.Email.CONTENT_URI to mapOf(
                        "display_name" to ContactsContract.CommonDataKinds.Email.DISPLAY_NAME,
                        "email" to ContactsContract.CommonDataKinds.Email.ADDRESS
                    )
                }
                else -> {
                    // fallback: phone table
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI to mapOf(
                        "display_name" to ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        "phone_number" to ContactsContract.CommonDataKinds.Phone.NUMBER,
                        "email" to ContactsContract.CommonDataKinds.Email.ADDRESS
                    )
                }
            }

            queryGeneric(contentResolver, uri, parameters, userPrompt, fieldMap)
        }

        "alarm", "reminder" -> queryGeneric(
            contentResolver,
            CalendarContract.Reminders.CONTENT_URI,
            parameters,
            userPrompt,
            fieldMap = mapOf(
                "event_id" to CalendarContract.Reminders.EVENT_ID,
                "minutes" to CalendarContract.Reminders.MINUTES,
                "method" to CalendarContract.Reminders.METHOD
            )
        )

        else -> emptyList()
    }
}

/**
 * Generic query runner for PIM services
 * - uses parameters.selectionArgs if present (preferred)
 * - otherwise falls back to parseFilter(...) generating args heuristically
 */
private fun queryGeneric(
    contentResolver: ContentResolver,
    uri: android.net.Uri,
    parameters: Parameters,
    userPrompt: String,
    fieldMap: Map<String, String>
): List<Map<String, Any>> {
    val results = mutableListOf<Map<String, Any>>()

    // projection: map abstract field -> real column name
    val projection = parameters.fields.map { field ->
        fieldMap[field] ?: throw IllegalArgumentException("Unknown field: $field")
    }.toTypedArray()

    // Decide selection & args: backend selectionArgs (preferred) or local parser fallback
    // Decide selection & args: backend selectionArgs (preferred) or local parser fallback
    var selection: String = ""               // default empty string
    var selectionArgs: Array<String>? = null // default null

    if (!parameters.selectionArgs.isNullOrEmpty()) {
        // Use backend’s filter
        selection = parameters.filter.replaceFieldNames(fieldMap)
        selectionArgs = parameters.selectionArgs.map { resolveSelectionArg(it) }.toTypedArray()
    } else {
        val pair = parseFilter(filter = parameters.filter, userPrompt, fieldMap)
        selection = pair.first
        selectionArgs = pair.second
    }


    // Convert sort (abstract names) to real column names
    val sortOrder = parameters.sort.replaceFieldNames(fieldMap)

    Log.d(TAG, "Querying URI=$uri projection=${projection.contentToString()} selection=\"$selection\" args=${selectionArgs?.contentToString()} sort=\"$sortOrder\" limit=${parameters.limit}")

    val cursor: Cursor? = contentResolver.query(
        uri,
        projection,
        selection,
        selectionArgs,
        sortOrder
    )

    cursor?.use {
        var count = 0
        while (it.moveToNext() && count < parameters.limit) {
            val row = mutableMapOf<String, Any>()
            parameters.fields.forEach { field ->
                val column = fieldMap[field] ?: return@forEach
                val index = it.getColumnIndex(column)
                if (index >= 0) {
                    row[field] = when (it.getType(index)) {
                        Cursor.FIELD_TYPE_STRING -> it.getString(index) ?: ""
                        Cursor.FIELD_TYPE_INTEGER -> it.getLong(index)
                        Cursor.FIELD_TYPE_FLOAT -> it.getDouble(index)
                        Cursor.FIELD_TYPE_NULL -> ""
                        else -> it.getString(index) ?: ""
                    }
                } else {
                    // column not found — put empty string to keep shape stable
                    row[field] = ""
                }
            }
            results.add(row)
            count++
        }
    }

    return results
}

/**
 * Convert backend selectionArg tokens into actual values used by ContentResolver:
 * - if arg is an ISO timestamp (ends with Z) -> convert to epoch millis
 * - if arg starts/contains % -> treat as LIKE pattern (pass as-is)
 * - known tokens: START_OF_NEXT_WEEK, END_OF_NEXT_WEEK
 * - fallback: return arg unchanged
 */
private fun resolveSelectionArg(arg: String): String {
    val s = arg.trim()
    // LIKE pattern or contains % => leave as-is
    if (s.contains("%")) return s

    // ISO timestamp like 2024-03-04T00:00:00Z
    val isoRegex = Regex("\\d{4}-\\d{2}-\\d{2}T.*Z")
    if (isoRegex.matches(s)) {
        return s.toTimestamp().toString()
    }

    when (s.uppercase(Locale.US)) {
        "START_OF_NEXT_WEEK" -> {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            // move to start of next week (Monday 00:00) — use ISO week assumptions
            cal.add(Calendar.WEEK_OF_YEAR, 1)
            // set to first day of week (Mon) - ensure Monday
            cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis.toString()
        }
        "END_OF_NEXT_WEEK" -> {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            cal.add(Calendar.WEEK_OF_YEAR, 1)
            cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            val end = cal.timeInMillis + 7L * 24 * 60 * 60 * 1000 - 1
            return end.toString()
        }
        else -> return s
    }
}

/**
 * The existing parseFilter fallback: creates selection and args heuristically from a filter template
 * (keeps your earlier behavior for compatibility when backend doesn't send selectionArgs)
 */
private fun parseFilter(
    filter: String,
    userPrompt: String,
    fieldMap: Map<String, String>
): Pair<String, Array<String>> {
    if (filter.isBlank()) return "" to emptyArray()

    val parts = filter.split(" AND ").map { it.trim() }.filter { it.isNotEmpty() }

    // Build selection with real column names
    val selection = parts.joinToString(" AND ") { it.replaceFieldNames(fieldMap) }

    val args = mutableListOf<String>()
    parts.forEach { raw ->
        val part = raw.replaceFieldNames(fieldMap).lowercase(Locale.US)

        when {
            part.contains(" like ?") && (part.contains("title") || part.contains("display_name")) -> {
                val quoted = Regex("\"([^\"]+)\"").find(userPrompt)?.groupValues?.get(1)
                val token = quoted ?: userPrompt.split(" ").firstOrNull { it.length > 2 } ?: ""
                args.add("%$token%")
            }

            (part.contains("dtstart") || part.contains("start_date")) && part.contains(">= ?") -> {
                args.add(System.currentTimeMillis().toString())
            }

            (part.contains("dtend") || part.contains("end_date")) && part.contains("<= ?") -> {
                val oneWeekLater = System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000
                args.add(oneWeekLater.toString())
            }

            part.contains("display_name like ?") -> {
                val keyword = userPrompt.split(" ").find { it.length > 2 }?.let { "%$it%" } ?: "%"
                args.add(keyword)
            }
        }
    }
    return selection to args.toTypedArray()
}

/**
 * Helpers
 */
private fun String.replaceFieldNames(fieldMap: Map<String, String>): String {
    var updated = this
    fieldMap.forEach { (k, v) ->
        updated = updated.replace(k, v, ignoreCase = true)
    }
    return updated
}

private fun String.toTimestamp(): Long {
    val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return formatter.parse(this)?.time ?: 0
}
