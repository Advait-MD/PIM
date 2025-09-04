package com.ak.pim.websocket

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import okhttp3.*
import okio.ByteString

class WebSocketClient(private val listener: ConnectionListener): WebSocketListener() {

    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private val gson = Gson()

    data class ChatMessageData (
        val message: String,
        val isSentByUser: Boolean,
        val timestamp: Long = System.currentTimeMillis()
    )

    interface ConnectionListener {
        fun onConnected()
        fun onDisconnected()
        fun onMessageReceived(text: String)
    }

    fun connect() {
        val request = Request.Builder()
            .url("ws://192.168.31.188/ws") // 10.0.2.2 = localhost for Android emulator
            .build()

        webSocket = client.newWebSocket(request, this)
    }

    fun sendMessage(message: String) {
        webSocket?.send(message)
    }
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WebSocket", "Connected to server")
                listener.onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("WebSocket", "Received: $text")
                try {
                    val messageData = gson.fromJson(text, ChatMessageData::class.java)
                    listener.onMessageReceived(messageData.message) // Extract message field
                } catch (e: JsonSyntaxException) {
                    Log.e("WebSocket", "Invalid JSON: ${e.message}")
                    listener.onMessageReceived("Error: Invalid message format")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WebSocket", "Closing: $code / $reason")
                webSocket.close(1000, null)
                listener.onDisconnected()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.d("WebSocket", "Error: ${t.message}")
                listener.onDisconnected()
            }

    }




