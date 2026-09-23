package com.cues.core.cli

import com.cues.core.corpus.BakeOff
import com.cues.core.corpus.Corpus
import com.cues.core.drafting.GrammarParser
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val text = checkNotNull(object {}.javaClass.getResourceAsStream("/corpus/paraphrases.txt"))
        .bufferedReader().readText()
    val report = BakeOff.run(listOf(GrammarParser()), Corpus.parse(text))
    println(report.render())
}
