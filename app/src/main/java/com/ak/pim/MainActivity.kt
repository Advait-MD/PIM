package com.ak.pim

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.ak.pim.R
import com.ak.pim.websocket.WebSocketClient

class MainActivity : AppCompatActivity(), WebSocketClient.ConnectionListener {

    private lateinit var statusText: TextView
    private lateinit var wsClient: WebSocketClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)

        wsClient = WebSocketClient(this)
        wsClient.connect()
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
