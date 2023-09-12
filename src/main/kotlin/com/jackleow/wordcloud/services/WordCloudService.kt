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
import kotlinx.serialization.Serializable

class WordCloudService(
    config: ApplicationConfig,
    private val wordsByPersonRepository: WordsByPersonRepository,
    val chatMessages: Flow<ChatMessage>
) {
    companion object {
        @Serializable
        private data class PersonAndWord(
            val person: String,
            val word: String
        )

        val VALID_WORD_PATTERN = Regex("""(\p{L}+(?:-\p{L}+)*)""")
        val WORD_SEPARATOR_PATTERN = Regex("""[^\p{L}\-]+""")
    }

    private val wordCloudConfig: ApplicationConfig = config.config("wordCloud")
    private val maxWordsPerPerson: Int = wordCloudConfig.property("maxWordsPerPerson").getString().toInt()
    private val minWordLength: Int = wordCloudConfig.property("minWordLength").getString().toInt()
    private val maxWordLength: Int = wordCloudConfig.property("maxWordLength").getString().toInt()
    private val stopWords: Set<String> = wordCloudConfig.property("stopWords").getList().toSet()

    private fun splitIntoWords(chatMessage: ChatMessage): Flow<PersonAndWord> = WORD_SEPARATOR_PATTERN
        .split(chatMessage.text.trim())
        .map { word: String -> PersonAndWord(chatMessage.sender, word) }
        .reversed()
        .asFlow()

    private fun normalizeWord(personAndWord: PersonAndWord): PersonAndWord =
        personAndWord.copy(word = personAndWord.word.lowercase().trim('-'))

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
        .flatMap { it.value.map { token -> token to it.key } }
        .groupBy({ it.first }, { it.second }).mapValues { it.value.size }

    @OptIn(ExperimentalCoroutinesApi::class)
    val wordCounts: Flow<Counts> = flow { emit(wordsByPersonRepository.load()) }
        .flatMapLatest { initialWordsByPerson: Map<String, List<String>> ->
            chatMessages
                .flatMapConcat(::splitIntoWords)
                .map(::normalizeWord)
                .filter(::isValidWord)
                .runningFold(initialWordsByPerson, ::updateWordsForPerson)
                .onEach(wordsByPersonRepository::save)
                .map(::countWords)
                .map(::Counts)
                .shareIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, 1)
        }
}
