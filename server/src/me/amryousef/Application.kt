package me.amryousef

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
import io.ktor.routing.routing
import io.ktor.websocket.WebSockets
import io.ktor.websocket.webSocket
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
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
    val outData = ConflatedBroadcastChannel<String>()

    routing {

        webSocket(path = "/in") {
            try {
                for (data in incoming) {
                    if (data is Frame.Text) {
                        outData.send(data.readText())
                    }
                }
            } finally {
                close(CloseReason(CloseReason.Codes.SERVICE_RESTART, "Restart Connection"))
            }
        }

        webSocket(path = "/out") {
            try {
                for (data in outData.openSubscription()) {
                    send(Frame.Text(data))
                }
            } finally {
                close(CloseReason(CloseReason.Codes.SERVICE_RESTART, "Restart Connection"))
            }
        }
    }
}

