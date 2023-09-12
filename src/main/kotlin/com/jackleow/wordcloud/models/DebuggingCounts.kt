package com.jackleow.wordcloud.models

import kotlinx.serialization.Serializable

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
data class DebuggingCounts(
    val chatMessagesAndWords: List<ChatMessageAndWords>,
    val wordsBySender: Map<String, List<String>>,
    val countsByWord: Map<String, Int>
)
