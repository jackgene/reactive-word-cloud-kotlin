package com.jackleow.wordcloud.plugins

import com.jackleow.wordcloud.flow.wordFountsFlow
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Duration

fun Application.configureSockets() {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(45)
        timeout = Duration.ofSeconds(45)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    routing {
        webSocket("/ws") {
            wordFountsFlow.buffer().collectLatest {
                outgoing.send(Frame.Text(Json.encodeToString(it)))
            }
        }
    }
}
