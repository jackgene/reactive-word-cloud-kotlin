package com.jackleow.wordcloud.flow

import com.jackleow.wordcloud.model.Counts
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.*

val wordsFlow: MutableSharedFlow<String> = MutableSharedFlow()
val wordFountsFlow: Flow<Counts> = wordsFlow
    .scan(Counts(mapOf())) { counts: Counts, word: String ->
        val countsByWord: Map<String, Int> = counts.countsByWord
        counts.copy(countsByWord = countsByWord + (word to ((countsByWord[word] ?: 0) + 1)))
    }
    .shareIn(GlobalScope, SharingStarted.Eagerly, 1)
