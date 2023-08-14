package com.jackleow.wordcloud.model

import kotlinx.serialization.Serializable

@Serializable
data class Counts(
    val countsByWord: Map<String, Int>
)
