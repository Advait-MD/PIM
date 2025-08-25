package com.ak.pim

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.ak.pim.R
import com.ak.pim.websocket.WebSocketClient

class MainActivity : AppCompatActivity(), WebSocketClient.ConnectionListener {

    private lateinit var statusText: TextView
    private lateinit var inputMessage: EditText
    private lateinit var sendButton: Button
    private lateinit var wsClient: WebSocketClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        inputMessage = findViewById(R.id.inputMessage)
        sendButton = findViewById(R.id.sendButton)

        wsClient = WebSocketClient(this)
        wsClient.connect()

        // Handle send button click
        sendButton.setOnClickListener {
            val text = inputMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                try {
                    // ✅ Send message to server
                    wsClient.sendMessage(text)
                    inputMessage.setText("")
                } catch (_: Exception) {
                    // Ignore if not connected
                }
            }
        }

        // Handle keyboard "send" action
        inputMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendButton.performClick()
                true
            } else false
        }
    }

    override fun onConnected() {
        runOnUiThread { statusText.text = "Connected" }
    }

    override fun onDisconnected() {
        runOnUiThread { statusText.text = "Not Connected" }
    }

    override fun onDestroy() {
        super.onDestroy()
        wsClient.disconnect()
    }
}
