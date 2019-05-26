package me.amryousef

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.gson.gson
import io.ktor.http.cio.websocket.CloseReason
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.close
import io.ktor.http.cio.websocket.readText
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.websocket.WebSockets
import io.ktor.websocket.webSocket
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import java.io.FileInputStream
import java.time.Duration

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

@ExperimentalCoroutinesApi
@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    install(DefaultHeaders) {
        header("X-Engine", "Ktor") // will send this header with each response
    }

    install(CallLogging)

    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    install(ContentNegotiation) {
        gson {
        }
    }

    val doorOutData = ConflatedBroadcastChannel<String>()
    val clientOutData = ConflatedBroadcastChannel<String>()
    val serviceAccount = FileInputStream("service_key.json")
    val firebaseOptions = FirebaseOptions.Builder()
        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
        .build()
    FirebaseApp.initializeApp(firebaseOptions)

    routing {

        post(path = "/door/ring") {
            FirebaseMessaging
                .getInstance()
                .send(
                    Message.builder()
                        .setNotification(
                            Notification(
                                "Someone is at the door",
                                "Check the camera stream"
                            )
                        )
                        .build()
                )
        }

        webSocket(path = "/door/out") {
            println("Door out connected")
            try {
                for (data in doorOutData.openSubscription()) {
                    println("Door frame sent $data")
                    send(Frame.Text(data))
                }
            } finally {
                close(CloseReason(CloseReason.Codes.SERVICE_RESTART, "Restart Connection"))
            }
        }

        webSocket("/door/in") {
            println("Door in connected")
            try {
                for (data in incoming) {
                    if (data is Frame.Text) {
                        println("Door frame received $data")
                        clientOutData.send(data.readText())
                    }
                }
            } finally {
                close(CloseReason(CloseReason.Codes.SERVICE_RESTART, "Restart Connection"))
            }
        }

        webSocket("/client/out") {
            println("Client out connected")
            try {
                for (data in clientOutData.openSubscription()) {
                    println("Client frame sent $data")
                    send(Frame.Text(data))
                }
            } finally {
                close(CloseReason(CloseReason.Codes.SERVICE_RESTART, "Restart Connection"))
            }
        }

        webSocket("/client/in") {
            println("Client in connected")
            try {
                for (data in incoming) {
                    if (data is Frame.Text) {
                        println("Client frame received $data")
                        doorOutData.send(data.readText())
                    }
                }
            } finally {
                close(CloseReason(CloseReason.Codes.SERVICE_RESTART, "Restart Connection"))
            }
        }
    }
}

