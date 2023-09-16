package com.jackleow.wordcloud.services

import com.jackleow.wordcloud.models.*
import io.ktor.server.config.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly

class WordCloudService(config: ApplicationConfig, chatMessages: Flow<ChatMessage>) {
    companion object {
        private data class SenderAndText(
            val sender: String,
            val text: String
        )

        private data class SenderAndWord(
            val sender: String,
            val word: String
        )
    }

    private val wordCloudConfig: ApplicationConfig = config.config("wordCloud")
    private val maxWordsPerSender: Int = wordCloudConfig.property("maxWordsPerSender").getString().toInt()
    private val minWordLength: Int = wordCloudConfig.property("minWordLength").getString().toInt()
    private val maxWordLength: Int = wordCloudConfig.property("maxWordLength").getString().toInt()
    private val stopWords: Set<String> = wordCloudConfig.property("stopWords").getList().toSet()

    val NON_LETTER_PATTERN = Regex("""[^\p{L}]+""")
    private fun normalizeText(msg: ChatMessage): SenderAndText =
        SenderAndText(
            msg.sender,
            msg.text
                .replace(NON_LETTER_PATTERN, " ")
                .trim()
                .lowercase()
        )

    private fun splitIntoWords(
        senderText: SenderAndText
    ): Flow<SenderAndWord> = senderText.text
        .split(" ")
        .map { SenderAndWord(senderText.sender, it) }
        .reversed()
        .asFlow()

    private fun isValidWord(senderWord: SenderAndWord): Boolean =
        senderWord.word.length in minWordLength..maxWordLength
                && !stopWords.contains(senderWord.word)

    private fun updateWordsForSender(
        wordsBySender: Map<String, List<String>>,
        senderWord: SenderAndWord
    ): Map<String, List<String>> {
        val oldWords: List<String> =
            wordsBySender[senderWord.sender] ?: listOf()
        val newWords: List<String> =
            (listOf(senderWord.word) + oldWords).distinct()
                .take(maxWordsPerSender)
        return wordsBySender + (senderWord.sender to newWords)
    }

    private fun countWords(
        wordsBySender: Map<String, List<String>>
    ): Map<String, Int> = wordsBySender
        .flatMap { it.value.map { word -> word to it.key } }
        .groupBy({ it.first }, { it.second })
        .mapValues { it.value.size }

    @OptIn(ExperimentalCoroutinesApi::class)
    val wordCounts: Flow<Counts> = chatMessages
        .map(::normalizeText)
        .flatMapConcat(::splitIntoWords)
        .filter(::isValidWord)
        .runningFold(mapOf(), ::updateWordsForSender)
        .map(::countWords).map(::Counts)
        .shareIn(CoroutineScope(Default), Eagerly, 1)
}
