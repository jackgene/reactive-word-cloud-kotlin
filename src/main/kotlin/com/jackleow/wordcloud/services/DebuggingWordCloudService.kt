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
    private val maxWordsPerSender: Int = wordCloudConfig.property("maxWordsPerSender").getString().toInt()
    private val minWordLength: Int = wordCloudConfig.property("minWordLength").getString().toInt()
    private val maxWordLength: Int = wordCloudConfig.property("maxWordLength").getString().toInt()
    private val stopWords: Set<String> = wordCloudConfig.property("stopWords").getList().toSet()

    val wordCounts: Flow<DebuggingCounts> = chatMessages
        .map { msg: ChatMessage ->
            val normalizedText: String = msg.text.replace(NON_LETTER_PATTERN, " ").trim().lowercase()
            val words: List<String> = normalizedText.split(" ")

            Triple(
                msg, normalizedText,
                words.map { word ->
                    Pair(
                        word,
                        word.length in minWordLength..maxWordLength
                                && !stopWords.contains(word)
                    )
                }
            )
        }
        .runningFold(
            Pair(DebuggingCounts(listOf(), mapOf()), mapOf<String, List<String>>())
        ) { (accum: DebuggingCounts, oldWordsBySender: Map<String, List<String>>), (msg: ChatMessage, normalizedText: String, splitWords: List<Pair<String, Boolean>>) ->
            val extractedWords: List<ExtractedWord> = splitWords
                .reversed()
                .runningFold(
                    ExtractedWord(
                        "",
                        false,
                        oldWordsBySender,
                        mapOf()
                    )
                ) { extractedWord: ExtractedWord, nextWord: Pair<String, Boolean> ->
                    val (word: String, isValid: Boolean) = nextWord
                    if (isValid) {
                        val oldWords: List<String> = extractedWord.wordsBySender[msg.sender] ?: listOf()
                        val newWords: List<String> = (listOf(word) + oldWords)
                            .distinct()
                            .take(maxWordsPerSender)
                        val newWordsBySender: Map<String, List<String>> =
                            oldWordsBySender + (msg.sender to newWords)
                        val countsByWord = newWordsBySender
                            .flatMap { it.value.map { token -> token to it.key } }
                            .groupBy({ it.first }, { it.second }).mapValues { it.value.size }

                        extractedWord.copy(
                            word = word,
                            isValid = true,
                            wordsBySender = newWordsBySender,
                            countsByWord = countsByWord
                        )
                    } else {
                        extractedWord.copy(word = word, isValid = false)
                    }
                }
                .drop(1)
                .reversed()
            Pair(
                accum.copy(
                    history = accum.history + Event(
                        msg, normalizedText, extractedWords
                    ),
                    countsByWord = extractedWords.lastOrNull()?.countsByWord ?: mapOf()
                ),
                extractedWords.lastOrNull()?.wordsBySender ?: mapOf()
            )
        }
        .map { (counts: DebuggingCounts, _) -> counts }
        .shareIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, 1)
}