package com.jackleow.wordcloud

import com.jackleow.wordcloud.data.FileWordsBySenderRepository
import com.jackleow.wordcloud.data.WordsBySenderRepository
import com.jackleow.wordcloud.flows.KafkaChatMessageFlow
import com.jackleow.wordcloud.models.ChatMessage
import com.jackleow.wordcloud.plugins.configureRouting
import com.jackleow.wordcloud.services.WordCloudService
import io.ktor.server.application.*
import kotlinx.coroutines.flow.Flow
import java.io.File

fun main(args: Array<String>) {
    io.ktor.server.cio.EngineMain.main(args)
}

fun Application.module() {
    val wordsBySenderRepository: WordsBySenderRepository = FileWordsBySenderRepository(
        store = File(environment.config.property("persistence.store.wordsBySender").getString())
    )
    val chatMessages: Flow<ChatMessage> = KafkaChatMessageFlow(
        bootstrapServers = environment.config.property("kafka.bootstrapServers").getString(),
        groupId = environment.config.property("kafka.groupId").getString(),
        topicName = environment.config.property("kafka.topicName.chatMessage").getString()
    )
    val service = WordCloudService(environment.config, wordsBySenderRepository, chatMessages)

    configureRouting(service)
}
