@file:Suppress("TYPEALIAS_EXPANSION_DEPRECATION")

package com.jackleow.wordcloud.plugins

import com.jackleow.wordcloud.flows.WordCountDebugFlow
import com.jackleow.wordcloud.flows.WordCountFlow
import com.jackleow.wordcloud.models.ChatMessage
import io.github.nomisRev.kafka.map
import io.github.nomisRev.kafka.receiver.AutoOffsetReset
import io.github.nomisRev.kafka.receiver.KafkaReceiver
import io.github.nomisRev.kafka.receiver.ReceiverSettings
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.kafka.common.serialization.StringDeserializer
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
fun Application.configureRouting() {
    install(WebSockets) {
        timeout = Duration.ofSeconds(300)
    }

    routing {
        val kafkaSettings: ReceiverSettings<String, ChatMessage> = ReceiverSettings(
            "localhost:9092",
            StringDeserializer(),
            StringDeserializer().map(Json::decodeFromString),
            groupId = "word-cloud-app",
            autoOffsetReset = AutoOffsetReset.Earliest
        )
        val chatMessages: Flow<ChatMessage> = KafkaReceiver(kafkaSettings)
            .receive("word-cloud.chat-message")
            .map {
                it.offset.acknowledge()
                it.value()
            }
            .shareIn(CoroutineScope(Dispatchers.Default), SharingStarted.Lazily)
        val wordCounts: Flow<WordCountFlow.Counts> = WordCountFlow(3, chatMessages)
            .shareIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, 1)
        val wordCountsWithDebug: Flow<WordCountDebugFlow.Counts> = WordCountDebugFlow(3, chatMessages)
            .shareIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, 1)

        webSocket("/word-count") {
            if (call.parameters["debug"].toBoolean())
                wordCountsWithDebug
                    .sample(100.milliseconds)
                    .map { Frame.Text(Json.encodeToString(it)) }
                    .collectInto(outgoing)
            else
                wordCounts
                    .sample(100.milliseconds)
                    .map { Frame.Text(Json.encodeToString(it)) }
                    .collectInto(outgoing)
        }
    }
}
