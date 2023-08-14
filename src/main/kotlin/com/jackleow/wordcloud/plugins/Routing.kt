package com.jackleow.wordcloud.plugins

import com.jackleow.wordcloud.flow.wordsFlow
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Hello World!")
        }
        get("/word/{word}") {
            val word = call.parameters["word"] ?: ""
            wordsFlow.emit(word)
            call.respondText("Accepted word: $word")
        }
    }
}
