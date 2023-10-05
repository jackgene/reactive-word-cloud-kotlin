package com.jackleow.wordcloud.services

import com.jackleow.wordcloud.models.ChatMessage
import com.jackleow.wordcloud.models.Event
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
            Pair(DebuggingCounts(listOf(), mapOf()), mapOf<String, List<String>>())
        ) { (accum: DebuggingCounts, oldWordsBySender: Map<String, List<String>>), (msg: ChatMessage, normalizedText: String, extractedWords: List<ExtractedWord>) ->
            val validWords: List<String> = extractedWords
                .mapNotNull { if (it.isValid) it.word else null }
            val oldWords: List<String> = oldWordsBySender[msg.sender] ?: listOf()
            val newWords: List<String> = (validWords + oldWords)
                .distinct()
                .take(maxWordsPerPerson)
            val newWordsBySender: Map<String, List<String>> =
                oldWordsBySender + (msg.sender to newWords)
            val countsByWord = newWordsBySender
                .flatMap { it.value.map { token -> token to it.key } }
                .groupBy({ it.first }, { it.second }).mapValues { it.value.size }
            Pair(
                accum.copy(
                    history = accum.history + Event(
                        msg, normalizedText, extractedWords, newWordsBySender, countsByWord
                    ),
                    countsByWord = countsByWord
                ),
                newWordsBySender
            )
        }
        .map { (counts: DebuggingCounts, _) -> counts }
        .shareIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, 1)
}