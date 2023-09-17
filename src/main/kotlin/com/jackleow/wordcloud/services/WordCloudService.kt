@file:Suppress("TYPEALIAS_EXPANSION_DEPRECATION")

package com.jackleow.wordcloud.services

import com.jackleow.wordcloud.data.WordsByPersonRepository
import com.jackleow.wordcloud.models.ChatMessage
import com.jackleow.wordcloud.models.Counts
import io.github.nomisRev.kafka.*
import io.ktor.server.config.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

class WordCloudService(
    config: ApplicationConfig,
    wordsByPersonRepository: WordsByPersonRepository,
    chatMessages: Flow<ChatMessage>
) {
    companion object {
        private data class PersonAndText(
            val person: String,
            val text: String
        )

        private data class PersonAndWord(
            val person: String,
            val word: String
        )

        val VALID_WORD_PATTERN = Regex("""(\p{L}+(?:-\p{L}+)*)""")
        val NON_LETTER_PATTERN = Regex("""[^\p{L}]+""")
    }

    private val wordCloudConfig: ApplicationConfig = config.config("wordCloud")
    private val maxWordsPerPerson: Int = wordCloudConfig.property("maxWordsPerPerson").getString().toInt()
    private val minWordLength: Int = wordCloudConfig.property("minWordLength").getString().toInt()
    private val maxWordLength: Int = wordCloudConfig.property("maxWordLength").getString().toInt()
    private val stopWords: Set<String> = wordCloudConfig.property("stopWords").getList().toSet()

    private fun normalizeText(chatMessage: ChatMessage): PersonAndText =
        PersonAndText(
            chatMessage.sender,
            chatMessage.text
                .replace(NON_LETTER_PATTERN, " ")
                .trim()
                .lowercase()
        )

    private fun splitIntoWords(personAndText: PersonAndText): Flow<PersonAndWord> = personAndText.text
        .split(" ")
        .map { word: String -> PersonAndWord(personAndText.person, word) }
        .reversed()
        .asFlow()

    private fun isValidWord(personAndWord: PersonAndWord): Boolean =
        VALID_WORD_PATTERN.matches(personAndWord.word)
                && personAndWord.word.length in minWordLength..maxWordLength
                && !stopWords.contains(personAndWord.word)

    private fun updateWordsForPerson(
        wordsByPerson: Map<String, List<String>>,
        personAndWord: PersonAndWord
    ): Map<String, List<String>> {
        val oldWords: List<String> = wordsByPerson[personAndWord.person] ?: listOf()
        val newWords: List<String> = (listOf(personAndWord.word) + oldWords).distinct().take(maxWordsPerPerson)

        return wordsByPerson + (personAndWord.person to newWords)
    }

    private fun countWords(wordsByPerson: Map<String, List<String>>): Map<String, Int> = wordsByPerson
        .flatMap { it.value.map { word -> word to it.key } }
        .groupBy({ it.first }, { it.second }).mapValues { it.value.size }

    @OptIn(ExperimentalCoroutinesApi::class)
    val wordCounts: Flow<Counts> = flow { emit(wordsByPersonRepository.load()) }
        .flatMapLatest { initialWordsByPerson: Map<String, List<String>> ->
            chatMessages
                .map(::normalizeText)
                .flatMapConcat(::splitIntoWords)
                .filter(::isValidWord)
                .runningFold(initialWordsByPerson, ::updateWordsForPerson)
                .onEach(wordsByPersonRepository::save)
                .map(::countWords)
                .map(::Counts)
                .shareIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, 1)
        }
}
