package com.jackleow.wordcloud.services

import com.jackleow.wordcloud.models.*
import io.ktor.server.config.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

class WordCloudService(
    config: ApplicationConfig,
    chatMessages: Flow<ChatMessage>
) {
    companion object {
        private data class SenderAndText(
            val sender: String,
            val text: String
        )

        private data class SenderAndWord(
            val sender: String,
            val word: String
        )

        val NON_LETTER_PATTERN = Regex("""[^\p{L}]+""")
    }

    private val wordCloudConfig: ApplicationConfig = config.config("wordCloud")
    private val maxWordsPerSender: Int = wordCloudConfig.property("maxWordsPerSender").getString().toInt()
    private val minWordLength: Int = wordCloudConfig.property("minWordLength").getString().toInt()
    private val maxWordLength: Int = wordCloudConfig.property("maxWordLength").getString().toInt()
    private val stopWords: Set<String> = wordCloudConfig.property("stopWords").getList().toSet()

    private fun normalizeText(chatMessage: ChatMessage): SenderAndText =
        SenderAndText(
            chatMessage.sender,
            chatMessage.text
                .replace(NON_LETTER_PATTERN, " ")
                .trim()
                .lowercase()
        )

    private fun splitIntoWords(senderAndText: SenderAndText): Flow<SenderAndWord> = senderAndText.text
        .split(" ")
        .map { word: String -> SenderAndWord(senderAndText.sender, word) }
        .reversed()
        .asFlow()

    private fun isValidWord(senderAndWord: SenderAndWord): Boolean =
        senderAndWord.word.length in minWordLength..maxWordLength
                && !stopWords.contains(senderAndWord.word)

    private fun updateWordsForSender(
        wordsBySender: Map<String, List<String>>,
        senderAndWord: SenderAndWord
    ): Map<String, List<String>> {
        val oldWords: List<String> = wordsBySender[senderAndWord.sender] ?: listOf()
        val newWords: List<String> = (listOf(senderAndWord.word) + oldWords).distinct().take(maxWordsPerSender)

        return wordsBySender + (senderAndWord.sender to newWords)
    }

    private fun countWords(wordsBySender: Map<String, List<String>>): Map<String, Int> = wordsBySender
        .flatMap { it.value.map { word -> word to it.key } }
        .groupBy({ it.first }, { it.second }).mapValues { it.value.size }

    @OptIn(ExperimentalCoroutinesApi::class)
    val wordCounts: Flow<Counts> = chatMessages
        .map(::normalizeText)
        .flatMapConcat(::splitIntoWords)
        .filter(::isValidWord)
        .runningFold(mapOf(), ::updateWordsForSender)
        .map(::countWords)
        .map(::Counts)
        .shareIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, 1)
}
