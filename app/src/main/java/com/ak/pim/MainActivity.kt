package com.ak.pim

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.ak.pim.pim.queryPimService
import com.ak.pim.websocket.WebSocketClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {


    private val TAG = "MainActivity"
    private val webSocketClient = WebSocketClient(this)

    // Register permission launcher
    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                fetchAndProcessPrompt("date of next sunday")
            } else {
                Log.e(TAG, "Permissions denied by user")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // Ensure activity_main.xml exists

        requestPermissionsIfNeeded()
        webSocketClient.connect(this) // Pass context for WebSocketClient if needed
    }

    private fun requestPermissionsIfNeeded() {
        val permissions = arrayOf(
            android.Manifest.permission.READ_CALENDAR,
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isNotEmpty()) {
            requestPermissionsLauncher.launch(notGranted.toTypedArray())
        } else {
            fetchAndProcessPrompt("date of next sunday")
        }
    }

    private fun fetchAndProcessPrompt(prompt: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Sending prompt from app: $prompt")
                val response = webSocketClient.sendPrompt(prompt)

                if (response != null) {
                    Log.d(TAG, "Received parameters: $response")

                    Log.d(TAG, "About to query service=${response.service} with parameters=${response.parameters}")


                    val extractedData = queryPimService(
                        contentResolver,
                        response.service,
                        response.parameters,
                        prompt
                    )

                    if (extractedData.isNotEmpty()) {
                        webSocketClient.sendData(response.service, extractedData)
                        Log.d(TAG, "Sent data: $extractedData")
                    } else {
                        Log.d(TAG, "No data extracted for prompt: $prompt")
                    }
                } else {
                    Log.e(TAG, "No response from WebSocket")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in fetchAndProcessPrompt: ${e.message}", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocketClient.disconnect()
    }
}
