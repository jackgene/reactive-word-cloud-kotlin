package com.jackleow.wordcloud

import com.jackleow.wordcloud.flows.KafkaChatMessageFlow
import com.jackleow.wordcloud.models.ChatMessage
import com.jackleow.wordcloud.plugins.configureRouting
import com.jackleow.wordcloud.services.WordCloudService
import io.ktor.server.application.*
import kotlinx.coroutines.flow.Flow

fun main(args: Array<String>) {
    io.ktor.server.cio.EngineMain.main(args)
}

fun Application.module() {
    val chatMessages: Flow<ChatMessage> = KafkaChatMessageFlow(
        bootstrapServers = environment.config.property("kafka.bootstrapServers").getString(),
        groupId = environment.config.property("kafka.groupId").getString(),
        topicName = environment.config.property("kafka.topicName.chatMessage").getString()
    )
    val service = WordCloudService(environment.config, chatMessages)

    configureRouting(service)
}
