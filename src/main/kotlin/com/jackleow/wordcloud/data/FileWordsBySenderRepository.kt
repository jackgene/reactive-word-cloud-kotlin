package com.jackleow.wordcloud.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class FileWordsBySenderRepository(val store: File) : WordsBySenderRepository {
    override suspend fun save(wordsBySender: Map<String, List<String>>) {
        Json.encodeToString(wordsBySender).let { json ->
            store.writeText(json)
        }
    }

    override suspend fun load(): Map<String, List<String>> {
        store.readText().let { json ->
            return Json.decodeFromString(json)
        }
    }
}
