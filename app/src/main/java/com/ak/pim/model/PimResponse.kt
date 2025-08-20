package com.ak.pim.model

data class PimResponse(
    val service: String,
    val parameters: Parameters,              // no more "?"
    var data: List<Map<String, Any>>? = null // keep data nullable (backend might send [])
)
