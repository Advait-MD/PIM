package com.ak.pim.websocket

import android.util.Log
import okhttp3.*
import okio.ByteString

class WebSocketClient(private val listener: ConnectionListener) {

    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null

    interface ConnectionListener {
        fun onConnected()
        fun onDisconnected()
    }

    fun connect() {
        val request = Request.Builder()
            .url("wss://vesper-vny3.onrender.com/ws") // 10.0.2.2 = localhost for Android emulator
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WebSocket", "Connected to server")
                listener.onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("WebSocket", "Received: $text")
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
        })
    }

    fun sendMessage(message: String) {
        webSocket?.send(message)
    }
    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
    }
}
