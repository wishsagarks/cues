package com.cues.app.drafting

import com.cues.app.net.SarvamClient
import com.cues.core.corpus.BakeOff
import com.cues.core.corpus.BakeOffReport
import com.cues.core.corpus.Corpus
import com.cues.core.drafting.GrammarParser
import com.cues.core.drafting.PairedDevice

/**
 * The same [BakeOff] engine `./dev bo` runs from `:core`, extended with the
 * on-device model and, when an API key is configured, the cloud one —
 * something `:core`'s CLI can never do, since [OnDeviceLlmDrafter] and
 * [SarvamChatDrafter] live here in `:app` precisely because they need real
 * Android/network APIs, and `:core` takes neither dependency. This is what
 * docs/DEVICE_RUNBOOK.md's step 1 means by "the parser and side-loaded-model
 * bake-offs": it runs from the phone, not the laptop, and today the model row
 * is an honest "not wired up" result rather than a fabricated one.
 *
 * CL-35: the Sarvam row is the safe place to score a cloud drafter against
 * the corpus before it is ever offered in the app — this call needs real
 * network to produce a non-trivial result, but it is a dev-only evaluation
 * tool, not the shipped runtime path.
 */
object AppBakeOff {
    suspend fun run(pairedDevices: List<PairedDevice>, sarvamApiKey: String = ""): BakeOffReport {
        val text = checkNotNull(object {}.javaClass.getResourceAsStream("/corpus/paraphrases.txt")) {
            "corpus/paraphrases.txt missing from resources"
        }.bufferedReader().readText()
        val sarvamSession = if (sarvamApiKey.isBlank()) {
            UnconfiguredSarvamChatSession()
        } else {
            SarvamHttpChatSession(SarvamClient(sarvamApiKey))
        }
        return BakeOff.run(
            listOf(GrammarParser(pairedDevices), OnDeviceLlmDrafter(), SarvamChatDrafter(sarvamSession)),
            Corpus.parse(text),
        )
    }
}
