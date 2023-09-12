@file:Suppress("TYPEALIAS_EXPANSION_DEPRECATION")

package com.jackleow.wordcloud.flows

import com.jackleow.wordcloud.models.ChatMessage
import io.github.nomisRev.kafka.map
import io.github.nomisRev.kafka.receiver.AutoOffsetReset
import io.github.nomisRev.kafka.receiver.KafkaReceiver
import io.github.nomisRev.kafka.receiver.ReceiverSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.serialization.json.Json
import org.apache.kafka.common.serialization.StringDeserializer

object KafkaChatMessageFlow {
    operator fun invoke(bootstrapServers: String, groupId: String, topicName: String): Flow<ChatMessage> =
        ReceiverSettings<String, ChatMessage>(
            bootstrapServers = bootstrapServers,
            keyDeserializer = StringDeserializer(),
            valueDeserializer = StringDeserializer().map(Json::decodeFromString),
            groupId = groupId,
            autoOffsetReset = AutoOffsetReset.Earliest
        ).let { settings ->
            KafkaReceiver(settings)
                .receive(topicName)
                .map {
                    it.offset.acknowledge()
                    it.value()
                }
                .shareIn(CoroutineScope(Dispatchers.Default), SharingStarted.Lazily)
        }
}