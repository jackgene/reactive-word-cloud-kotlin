package com.jackleow.wordcloud.flows

import com.jackleow.wordcloud.models.ChatMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable

object WordCountFlow {
    @Serializable
    data class Counts internal constructor(
        val countsByWord: Map<String, Int>
    )

    const val MIN_WORD_LENGTH = 3
    const val MAX_WORD_LENGTH = 24
    val STOP_WORDS: Set<String> = setOf(
        "about",
        "above",
        "after",
        "again",
        "against",
        "all",
        "and",
        "any",
        "are",
        "because",
        "been",
        "before",
        "being",
        "below",
        "between",
        "both",
        "but",
        "can",
        "did",
        "does",
        "doing",
        "down",
        "during",
        "each",
        "few",
        "for",
        "from",
        "further",
        "had",
        "has",
        "have",
        "having",
        "her",
        "here",
        "hers",
        "herself",
        "him",
        "himself",
        "his",
        "how",
        "into",
        "its",
        "itself",
        "just",
        "me",
        "more",
        "most",
        "myself",
        "nor",
        "not",
        "now",
        "off",
        "once",
        "only",
        "other",
        "our",
        "ours",
        "ourselves",
        "out",
        "over",
        "own",
        "same",
        "she",
        "should",
        "some",
        "such",
        "than",
        "that",
        "the",
        "their",
        "theirs",
        "them",
        "themselves",
        "then",
        "there",
        "these",
        "they",
        "this",
        "those",
        "through",
        "too",
        "under",
        "until",
        "very",
        "was",
        "were",
        "what",
        "when",
        "where",
        "which",
        "while",
        "who",
        "whom",
        "why",
        "will",
        "with",
        "you",
        "your",
        "yours",
        "yourself",
        "yourselves"
    )
    val VALID_WORD_PATTERN = Regex("""(\p{L}+(?:-\p{L}+)*)""")
    val WORD_SEPARATOR_PATTERN = Regex("""[^\p{L}\-]+""")

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(maxWordsPerSender: Int, chatMessageSource: Flow<ChatMessage>): Flow<Counts> =
        chatMessageSource.flatMapConcat { chatMessage: ChatMessage ->
            WORD_SEPARATOR_PATTERN
                .split(chatMessage.text.trim())
                .map { word: String -> chatMessage.sender to word }
                .reversed()
                .asFlow()
        }.map { (sender: String, word: String) ->
            Pair(sender, word.lowercase().trim('-'))
        }.filter { (_, word: String) ->
            VALID_WORD_PATTERN.matches(word)
                    && word.length in MIN_WORD_LENGTH..MAX_WORD_LENGTH
                    && !STOP_WORDS.contains(word)
        }.runningFold(mapOf()) { wordsBySender: Map<String, List<String>>, (sender: String, word: String) ->
            val oldWords: List<String> = wordsBySender[sender] ?: listOf()
            val newWords: List<String> = (listOf(word) + oldWords).distinct().take(maxWordsPerSender)
            wordsBySender + (sender to newWords)
        }.map { wordsBySender: Map<String, List<String>> ->
            Counts(
                wordsBySender
                    .flatMap { it.value.map { token -> token to it.key } }
                    .groupBy({ it.first }, { it.second }).mapValues { it.value.size }
            )
        }
}