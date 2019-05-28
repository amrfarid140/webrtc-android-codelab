package me.amryousef

import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.gson.gson
import io.ktor.http.cio.websocket.*
import io.ktor.routing.routing
import io.ktor.websocket.WebSocketServerSession
import io.ktor.websocket.WebSockets
import io.ktor.websocket.webSocket
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import java.time.Duration
import java.util.*

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

    val connections = Collections.synchronizedMap(mutableMapOf<String, WebSocketServerSession>())

    routing {
        webSocket(path = "/connect") {
            val id = UUID.randomUUID().toString()
            connections[id] = this
            println("Connected clients = ${connections.size}")
            try {
                for (data in incoming) {
                    if (data is Frame.Text) {
                        val clients = connections.filter { it.key != id }
                        val text = data.readText()
                        clients.forEach {
                            println("Sending to:${it.key}")
                            println("Sending $text")
                            it.value.send(text)
                        }
                    }
                }
            } finally {
                connections.remove(id)
            }
        }
    }
}

