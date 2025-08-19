package com.ak.pim.model

import com.google.gson.annotations.SerializedName

// Response from backend (parameters for querying)


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
