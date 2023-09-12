package com.jackleow.wordcloud.data

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class FileWordsByPersonRepository(private val store: File) : WordsByPersonRepository {
    init {
        if (!store.exists()) {
            store.absoluteFile.parentFile.mkdirs()
            store.createNewFile()
        }
    }

    override suspend fun load(): Map<String, List<String>> =
        try {
            store.bufferedReader().use {
                Json.decodeFromString(it.readText())
            }
        } catch(_: SerializationException) {
            mapOf()
        }

    override suspend fun save(wordsByPerson: Map<String, List<String>>) =
        store.printWriter().use {
            it.println(Json.encodeToString(wordsByPerson))
        }
}