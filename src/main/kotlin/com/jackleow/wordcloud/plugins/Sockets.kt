@file:Suppress("TYPEALIAS_EXPANSION_DEPRECATION")

package com.jackleow.wordcloud.plugins

import com.jackleow.wordcloud.services.WordCloudService
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Duration
import kotlin.time.Duration.Companion.milliseconds

suspend fun Flow<Frame>.collectInto(outgoing: SendChannel<Frame>) {
    val closed: MutableSharedFlow<Frame?> = MutableSharedFlow()
    outgoing.invokeOnClose {
        runBlocking { closed.emit(null) }
    }

    merge(this, closed)
        .takeWhile { it != null }
        .filterNotNull()
        .collect(outgoing::send)
}

@OptIn(FlowPreview::class)
fun Application.configureRouting(service: WordCloudService) {
    install(WebSockets) {
        timeout = Duration.ofSeconds(300)
    }

    routing {
        webSocket("/") {
            service.wordCounts
                .sample(100.milliseconds)
                .map { Frame.Text(Json.encodeToString(it)) }
                .collectInto(outgoing)
        }
    }
}
