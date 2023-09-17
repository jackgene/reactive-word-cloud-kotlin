package com.jackleow.wordcloud.services

import com.jackleow.wordcloud.models.ChatMessage
import com.jackleow.wordcloud.models.ChatMessageAndWords
import com.jackleow.wordcloud.models.DebuggingCounts
import com.jackleow.wordcloud.models.ExtractedWord
import com.jackleow.wordcloud.services.WordCloudService.Companion.NON_LETTER_PATTERN
import io.ktor.server.config.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*

class DebuggingWordCloudService(
    config: ApplicationConfig, chatMessages: Flow<ChatMessage>
) {
    private val wordCloudConfig: ApplicationConfig = config.config("wordCloud")
    private val maxWordsPerPerson: Int = wordCloudConfig.property("maxWordsPerPerson").getString().toInt()
    private val minWordLength: Int = wordCloudConfig.property("minWordLength").getString().toInt()
    private val maxWordLength: Int = wordCloudConfig.property("maxWordLength").getString().toInt()
    private val stopWords: Set<String> = wordCloudConfig.property("stopWords").getList().toSet()

    val wordCounts: Flow<DebuggingCounts> = chatMessages
        .map { msg: ChatMessage ->
            val normalizedText: String = msg.text.replace(NON_LETTER_PATTERN, " ").trim().lowercase()
            val words: List<String> = normalizedText.split(" ").reversed()

            Triple(
                msg, normalizedText,
                words.map { word ->
                    ExtractedWord(
                        word,
                        word.length in minWordLength..maxWordLength
                                && !stopWords.contains(word)
                    )
                }
            )
        }
        .runningFold(
            DebuggingCounts(listOf(), mapOf(), mapOf())
        ) { accum: DebuggingCounts, (msg: ChatMessage, normalizedText: String, extractedWords: List<ExtractedWord>) ->
            val validWords: List<String> = extractedWords
                .mapNotNull { if (it.isValid) it.word else null }
            val wordsBySender: Map<String, List<String>> =
                accum.wordsBySender.let { wordsBySender: Map<String, List<String>> ->
                    val oldWords: List<String> = wordsBySender[msg.sender] ?: listOf()
                    val newWords: List<String> = (validWords + oldWords)
                        .distinct()
                        .take(maxWordsPerPerson)

                    wordsBySender + (msg.sender to newWords)
                }
            accum.copy(
                chatMessagesAndWords = accum.chatMessagesAndWords + ChatMessageAndWords(
                    msg, normalizedText, extractedWords
                ),
                wordsBySender = wordsBySender,
                countsByWord = wordsBySender
                    .flatMap { it.value.map { token -> token to it.key } }
                    .groupBy({ it.first }, { it.second }).mapValues { it.value.size }
            )
        }
        .shareIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, 1)
}