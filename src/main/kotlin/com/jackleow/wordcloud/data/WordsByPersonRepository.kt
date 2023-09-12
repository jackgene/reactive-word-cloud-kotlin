package com.jackleow.wordcloud.data

interface WordsByPersonRepository {
    suspend fun load(): Map<String, List<String>>
    suspend fun save(wordsByPerson: Map<String, List<String>>)
}
