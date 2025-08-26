package com.ak.pim.model

data class ChatMessage(
    val text: String,
    val isSentByUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
