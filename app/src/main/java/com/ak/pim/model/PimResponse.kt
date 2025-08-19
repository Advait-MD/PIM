package com.ak.pim.model

data class PimResponse(
    val service: String,
    val parameters: Parameters,
    val data: List<Map<String, Any>>? = null
)
