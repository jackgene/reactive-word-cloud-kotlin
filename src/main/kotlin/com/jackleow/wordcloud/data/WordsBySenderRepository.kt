package com.jackleow.wordcloud.data

interface WordsBySenderRepository {
    suspend fun save(wordsBySender: Map<String, List<String>>)
    suspend fun load(): Map<String, List<String>>
}