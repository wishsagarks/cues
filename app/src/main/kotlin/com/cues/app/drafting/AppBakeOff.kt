package com.cues.app.drafting

import com.cues.core.corpus.BakeOff
import com.cues.core.corpus.BakeOffReport
import com.cues.core.corpus.Corpus
import com.cues.core.drafting.GrammarParser
import com.cues.core.drafting.PairedDevice

/**
 * The same [BakeOff] engine `./dev bo` runs from `:core`, extended with the
 * on-device model — something `:core`'s CLI can never do, since
 * [OnDeviceLlmDrafter] lives here in `:app` precisely because it will
 * eventually need real Android/MediaPipe APIs, and `:core` takes no Android
 * dependency. This is what docs/DEVICE_RUNBOOK.md's step 1 means by "the
 * parser and side-loaded-model bake-offs": it runs from the phone, not the
 * laptop, and today the model row is an honest "not wired up" result rather
 * than a fabricated one.
 */
object AppBakeOff {
    suspend fun run(pairedDevices: List<PairedDevice>): BakeOffReport {
        val text = checkNotNull(object {}.javaClass.getResourceAsStream("/corpus/paraphrases.txt")) {
            "corpus/paraphrases.txt missing from resources"
        }.bufferedReader().readText()
        return BakeOff.run(
            listOf(GrammarParser(pairedDevices), OnDeviceLlmDrafter()),
            Corpus.parse(text),
        )
    }
}
