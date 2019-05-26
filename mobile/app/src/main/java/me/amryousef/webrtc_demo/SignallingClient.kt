package me.amryousef.webrtc_demo

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.json.GsonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.websocket.WebSockets
import io.ktor.client.features.websocket.ws
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.readText
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

@ExperimentalCoroutinesApi
@KtorExperimentalAPI
class SignallingClient(
    private val listener: SignallingClientListener
) : CoroutineScope {

    companion object {
        private const val HOST_ADDRESS = ""
        private const val PATH_IN = "path_in"
        private const val PATH_OUT = "path_out"
    }

    private val job = Job()

    private val gson = Gson()

    override val coroutineContext = Dispatchers.IO + job

    private val client = HttpClient(CIO) {
        install(WebSockets)
        install(JsonFeature) {
            serializer = GsonSerializer()
        }
    }

    private val sendChannel = ConflatedBroadcastChannel<String>()

    init {
        connectIn()
        connectOut()
    }

    private fun connectIn() = launch {
        client.ws(host = HOST_ADDRESS, port = 8080, path = PATH_IN) {
            listener.onConnectionEstablished()
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val jsonObject = gson.fromJson(frame.readText(), JsonObject::class.java)
                    if (jsonObject.has("serverUrl")) {
                        listener.onIceCandidateReceived(gson.fromJson(jsonObject, IceCandidate::class.java))
                    } else if (jsonObject.has("type") && jsonObject.get("type").asString == "OFFER") {
                        listener.onOfferReceived(gson.fromJson(jsonObject, SessionDescription::class.java))
                    } else if (jsonObject.has("type") && jsonObject.get("type").asString == "ANSWER") {
                        listener.onAnswerReceived(gson.fromJson(jsonObject, SessionDescription::class.java))
                    }
                }
            }
        }
    }

    private fun connectOut() = launch {
        client.ws(host = HOST_ADDRESS, port = 8080, path = PATH_OUT) {
            for (data in sendChannel.openSubscription()) {
                send(Frame.Text(data))
                flush()
            }
        }
    }

    fun send(dataObject: Any) = launch {
        sendChannel.send(gson.toJson(dataObject))
    }

    fun destroy() {
        client.close()
        job.complete()
    }
}