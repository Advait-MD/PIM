package com.ak.pim.model

data class Parameters(
    val fields: List<String>,
    val filter: String,
    val selectionArgs: List<String>, // stays as List<String> for JSON parsing
    val sort: String,
    val limit: Int
) {
    fun selectionArgsArray(): Array<String> = selectionArgs.toTypedArray()
}
