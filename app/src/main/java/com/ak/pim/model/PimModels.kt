package com.ak.pim.model

import com.google.gson.annotations.SerializedName

// Response from backend (parameters for querying)
data class PimResponse(
    @SerializedName("service") val service: String,
    @SerializedName("parameters") val parameters: Parameters,
    @SerializedName("data") val data: List<Map<String, Any>>? = null

)

data class Parameters(
    @SerializedName("fields") val fields: List<String>,
    @SerializedName("filter") val filter: String,
    @SerializedName("sort") val sort: String,
    @SerializedName("limit") val limit: Int
)

// Request to backend (user prompt)
data class PromptRequest(
    @SerializedName("type") val type: String = "prompt",
    @SerializedName("user_prompt") val userPrompt: String
)

// Request to send extracted data to backend
data class PimDataRequest(
    @SerializedName("type") val type: String = "data",
    @SerializedName("service") val service: String,
    @SerializedName("data") val data: List<Map<String, Any>>
)
