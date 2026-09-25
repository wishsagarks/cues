package com.cues.app.drafting

import com.cues.app.net.SarvamClient
import com.cues.core.corpus.BakeOff
import com.cues.core.corpus.BakeOffReport
import com.cues.core.corpus.Corpus
import com.cues.core.drafting.GrammarParser
import com.cues.core.drafting.OnDeviceLlmDrafter
import com.cues.core.drafting.PairedDevice

/**
 * The same [BakeOff] engine `./dev bo` runs from `:core`, extended here with
 * the real, side-loaded on-device model (via `LiteRtLmSession`, `:app`-only
 * since it needs `Context`) and, when an API key is configured, the cloud
 * one. [OnDeviceLlmDrafter] itself is a plain `:core` type — the laptop CLI
 * (`core/.../cli/LlmMain.kt`) can and does construct it directly, against an
 * Ollama-backed session instead; what stays `:app`-only is [SarvamChatDrafter]
 * (real network credentials) and the LiteRT-LM/Android binding this bake-off
 * hands to [OnDeviceLlmDrafter] below. This is what docs/DEVICE_RUNBOOK.md's
 * step 1 means by "the parser and side-loaded-model bake-offs": it runs from
 * the phone, not the laptop, and today the model row is an honest "not wired
 * up" result rather than a fabricated one.
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
