package com.ak.pim.pim

import android.content.ContentResolver
import android.database.Cursor
import android.provider.CalendarContract
import android.provider.ContactsContract
import com.ak.pim.model.Parameters
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

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
            // choose URI based on requested fields
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
                    // simplest fallback: phone table (you can extend to merge phone+email later)
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI to mapOf(
                        "display_name" to ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        "phone_number" to ContactsContract.CommonDataKinds.Phone.NUMBER
                    )
                }
            }

            queryGeneric(contentResolver, uri, parameters, userPrompt, fieldMap)
        }


        //"contacts" -> queryGeneric(
          //  contentResolver,
            //ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            //parameters,
            //userPrompt,
            //fieldMap = mapOf(
              //  "name" to ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
              //  "email" to ContactsContract.CommonDataKinds.Email.ADDRESS,
              //  "phone" to ContactsContract.CommonDataKinds.Phone.NUMBER
           // )
       // )

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
 */
private fun queryGeneric(
    contentResolver: ContentResolver,
    uri: android.net.Uri,
    parameters: Parameters,
    userPrompt: String,
    fieldMap: Map<String, String>
): List<Map<String, Any>> {
    val results = mutableListOf<Map<String, Any>>()

    val projection = parameters.fields.map { field ->
        fieldMap[field] ?: throw IllegalArgumentException("Unknown field: $field")
    }.toTypedArray()

    val (selection, selectionArgs) = parseFilter(parameters.filter, userPrompt, fieldMap)
    val sortOrder = parameters.sort.replaceFieldNames(fieldMap)

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
                        else -> it.getString(index) ?: ""
                    }
                }
            }
            results.add(row)
            count++
        }
    }
    return results
}

/**
 * Converts backend filter string + userPrompt into selection + args
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
        // Normalize the part to real column names so matching works regardless of abstract vs real
        val part = raw.replaceFieldNames(fieldMap).lowercase(Locale.US)

        when {
            // title/display name LIKE ?
            part.contains(" like ?") && (part.contains("title") || part.contains("display_name")) -> {
                val quoted = Regex("\"([^\"]+)\"").find(userPrompt)?.groupValues?.get(1)
                val token = quoted ?: userPrompt.split(" ").firstOrNull { it.length > 2 } ?: ""
                args.add("%$token%")
            }

            // start >= ?
            (part.contains("dtstart") || part.contains("start_date")) && part.contains(">= ?") -> {
                args.add(System.currentTimeMillis().toString())
            }

            // end <= ?
            (part.contains("dtend") || part.contains("end_date")) && part.contains("<= ?") -> {
                val oneWeekLater = System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000
                args.add(oneWeekLater.toString())
            }
        }
    }
    return selection to args.toTypedArray()
}

//private fun parseFilter(
  //  filter: String,
    //userPrompt: String,
    //fieldMap: Map<String, String>
//): Pair<String, Array<String>> {
  //  val parts = filter.split(" AND ")
    //val selection = parts.joinToString(" AND ") { expr ->
      //  expr.replaceFieldNames(fieldMap)
    //}
    //val args = mutableListOf<String>()

    //for (part in parts) {
      //  when {
        //    part.contains("LIKE ?") -> {
          //      val keyword = when {
            //        userPrompt.contains("holiday", true) -> "%holiday%"
              //      userPrompt.contains("meeting", true) -> "%meeting%"
                //    else -> "%${userPrompt.split(" ").firstOrNull() ?: ""}%"
                //}
                //args.add(keyword)
     //       }

       //     part.contains(">= ?") && part.contains("DTSTART") -> {
         //       args.add(System.currentTimeMillis().toString()) // now
           // }

            //part.contains("<= ?") && part.contains("DTSTART") -> {
              //  val oneWeekLater = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000
                //args.add(oneWeekLater.toString())
            //}

           // part.contains("DISPLAY_NAME LIKE ?") -> {
             //   val keyword = userPrompt.split(" ").find { it.length > 2 }?.let { "%$it%" } ?: "%"
               // args.add(keyword)
            //}
        //}
    //}
    //return selection to args.toTypedArray()
//}

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
