package com.jackleow.wordcloud.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    @SerialName("s") val sender: String,
    @SerialName("r") val recipient: String,
    @SerialName("t") val text: String
) {
    override fun toString(): String = "$sender to $recipient: $text"
}
