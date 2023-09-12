package com.jackleow.wordcloud.models

import kotlinx.serialization.Serializable

@Serializable
data class Counts internal constructor(
    val countsByWord: Map<String, Int>
)
