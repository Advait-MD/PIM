package com.ak.pim.websocket

import com.ak.pim.R
import com.google.gson.Gson
import com.ak.pim.model.PimDataRequest
import com.ak.pim.model.PimResponse
import com.ak.pim.model.PromptRequest
import okhttp3.*
import android.content.Context
import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WebSocketClient {
    private val TAG = "WebSocketClient"
    private val gson = Gson()
    private var webSocket: WebSocket? = null
    private val responseChannel = Channel<PimResponse>(Channel.CONFLATED)

    // Flag to track when connection is ready
    private var connectionReady = CompletableDeferred<Boolean>()

    fun connect(context: Context) {
        //this is to just build a network or establish a network (GET, POST and Websocket)
        val client = OkHttpClient.Builder().build()

        //this is used to send request (request is mainly the data that is transfered from client ot server) over the established network and here the endponit is defined wher the request has to be sent
        val request = Request.Builder().url(context.getString(R.string.websocket_url)).build()

        //"actual opening of the websocket connections", it makes the objects by which we will send data and receive data from the server (objects=paths, for data)
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                if (!connectionReady.isCompleted) {
                    connectionReady.complete(true) // mark ready
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("WebSocketClient", "Raw message: $text")
                try {
                    val response = Gson().fromJson(text, PimResponse::class.java)
                    Log.d("WebSocketClient", "Parsed PimResponse: $response")
                    responseChannel.trySend(response)
                } catch (e: Exception) {
                    Log.e("WebSocketClient", "Failed to parse PimResponse: ${e.message}", e)
                    responseChannel.close(e)
                }
            }


            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                if (!connectionReady.isCompleted) {
                    connectionReady.completeExceptionally(t)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $reason")
            }
        })
    }

    //this function can be pause without blocking the main thread, PimResponse? function either returns PimResponse or null
    suspend fun sendPrompt(prompt: String): PimResponse? {
        //background I/O thread pool
        return withContext(Dispatchers.IO) {
            try {
                // Wait until connection is established
                connectionReady.await()

                val request = PromptRequest(userPrompt = prompt)
                val json = gson.toJson(request)
                Log.d(TAG, "Sending prompt: $json")

                if (webSocket?.send(json) == true) {
                    responseChannel.receive()
                } else {
                    Log.e(TAG, "WebSocket send failed")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error waiting for connection: ${e.message}")
                null
            }
        }
    }

    //it will send data to the server in this format of json, {service : name, data : [map]}
    suspend fun sendData(service: String, data: List<Map<String, Any>>) {
        withContext(Dispatchers.IO) {
            try {
                connectionReady.await()
                val request = PimDataRequest(service = service, data = data)
                val json = gson.toJson(request)
                Log.d(TAG, "Sending data: $json")
                if (webSocket?.send(json) != true) {
                    Log.e(TAG, "WebSocket send failed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error waiting for connection: ${e.message}")
            }
        }
    }

    fun disconnect() {
        webSocket?.close(1000, "Client disconnect")
        if (!connectionReady.isCompleted) {
            connectionReady.complete(false)
        }
        responseChannel.close()
    }
}

data class PimResponse(
    val service: String,
    val parameters: Parameters,
    val data: List<Map<String, Any>>? = null
)

data class Parameters(
    val fields: List<String>,
    val filter: String,
    val selectionArgs: List<String>,
    val sort: String,
    val limit: Int
)


//webSocket?.close(1000, "Client disconnect")
//Means: “If webSocket exists, call close. If it’s null, do nothing.”
//
//Prevents crashes if the socket was never opened.
//?. tell if its not null then do .the_thing_to_do(____,____) and if is null then do nothing.
