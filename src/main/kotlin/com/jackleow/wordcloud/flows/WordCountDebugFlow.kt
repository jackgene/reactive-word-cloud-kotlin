package com.jackleow.wordcloud.flows

import com.jackleow.wordcloud.models.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.serialization.Serializable

object WordCountDebugFlow {
    @Serializable
    data class ExtractedWord(
        val rawWord: String,
        val normalizedWord: String,
        val isValid: Boolean
    )

    @Serializable
    data class ChatMessageAndWords(
        val chatMessage: ChatMessage,
        val words: List<ExtractedWord>
    )

    @Serializable
    data class Counts(
        val chatMessagesAndWords: List<ChatMessageAndWords>,
        val wordsBySender: Map<String, List<String>>,
        val countsByWord: Map<String, Int>
    )

    private const val MIN_WORD_LENGTH: Int = 0
    private const val MAX_WORD_LENGTH: Int = 0
    private val STOP_WORDS: Set<String> = setOf()
    private val VALID_WORD_PATTERN = Regex("")
    private val WORD_SEPARATOR_PATTERN = Regex("")

    operator fun invoke(maxWordsPerSender: Int, chatMessageSource: Flow<ChatMessage>): Flow<Counts> =
        chatMessageSource
            .map { msg: ChatMessage ->
                val words: List<String> = WORD_SEPARATOR_PATTERN
                    .split(msg.text.trim())

                Pair(
                    msg,
                    words.map { word ->
                        ExtractedWord(
                            word, word.lowercase().trim('-'),
                            VALID_WORD_PATTERN.matches(word)
                                    && word.length in MIN_WORD_LENGTH..MAX_WORD_LENGTH
                                    && !STOP_WORDS.contains(word)
                        )
                    }
                )
            }
            .runningFold(
                Counts(listOf(), mapOf(), mapOf())
            ) { accum: Counts, (msg: ChatMessage, extractedWords: List<ExtractedWord>) ->
                val validWords: List<String> = extractedWords
                    .mapNotNull { if (it.isValid) it.normalizedWord else null }
                val wordsBySender: Map<String, List<String>> =
                    accum.wordsBySender.let { wordsBySender: Map<String, List<String>> ->
                        val oldWords: List<String> = wordsBySender[msg.sender] ?: listOf()
                        val newWords: List<String> = (validWords + oldWords)
                            .distinct()
                            .take(maxWordsPerSender)

                        wordsBySender + (msg.sender to newWords)
                    }
                accum.copy(
                    chatMessagesAndWords = accum.chatMessagesAndWords + ChatMessageAndWords(msg, extractedWords),
                    wordsBySender = wordsBySender,
                    countsByWord = wordsBySender
                        .flatMap { it.value.map { token -> token to it.key } }
                        .groupBy({ it.first }, { it.second }).mapValues { it.value.size }
                )
            }
}