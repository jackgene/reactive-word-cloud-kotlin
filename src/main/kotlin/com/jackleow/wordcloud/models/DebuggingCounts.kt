package com.jackleow.wordcloud.models

import kotlinx.serialization.Serializable

@Serializable
data class ExtractedWord(
    val word: String,
    val isValid: Boolean
)

@Serializable
data class Event(
    val chatMessage: ChatMessage,
    val normalizedText: String,
    val words: List<ExtractedWord>,
    val wordsBySender: Map<String, List<String>>,
    val countsByWord: Map<String, Int>
)

@Serializable
data class DebuggingCounts(
    val history: List<Event>,
    val countsByWord: Map<String, Int>
)
