package com.ak.pim

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ak.pim.model.ChatMessage
import com.ak.pim.ui.MessageAdapter
import com.ak.pim.websocket.WebSocketClient

class MainActivity : AppCompatActivity(), WebSocketClient.ConnectionListener {

    private lateinit var statusText: TextView
    private lateinit var inputMessage: EditText
    private lateinit var sendButton: Button
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: MessageAdapter
    private val messages = mutableListOf<ChatMessage>()

    private lateinit var wsClient: WebSocketClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        inputMessage = findViewById(R.id.inputMessage)
        sendButton = findViewById(R.id.sendButton)
        recycler = findViewById(R.id.recyclerMessages)

        adapter = MessageAdapter(messages)
        recycler.adapter = adapter
        recycler.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }

        wsClient = WebSocketClient(this)
        wsClient.connect()

        sendButton.setOnClickListener { sendCurrentInput() }

        inputMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendCurrentInput()
                true
            } else false
        }
    }

    private fun sendCurrentInput() {
        val text = inputMessage.text.toString().trim()
        if (text.isNotEmpty()) {
            // show in UI immediately
            val msg = ChatMessage(text = text, isSentByUser = true)
            adapter.addMessage(msg)
            recycler.scrollToPosition(messages.size - 1)

            // send to backend
            try {
                wsClient.sendMessage(text)
            } catch (_: Exception) {
            }

            inputMessage.setText("")
        }
    }

    // --- WebSocketClient.ConnectionListener ---
    override fun onConnected() {
        runOnUiThread { statusText.text = "Connected" }
    }

    override fun onDisconnected() {
        runOnUiThread { statusText.text = "Not Connected" }
    }

    override fun onMessageReceived(text: String) {
        runOnUiThread {
            val msg = ChatMessage(text = text, isSentByUser = false)
            adapter.addMessage(msg)
            recycler.scrollToPosition(messages.size - 1)
        }
    }

}
