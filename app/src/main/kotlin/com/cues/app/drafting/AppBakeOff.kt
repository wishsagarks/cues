package com.cues.app.drafting

import com.cues.app.net.SarvamClient
import com.cues.core.corpus.BakeOff
import com.cues.core.corpus.BakeOffReport
import com.cues.core.corpus.Corpus
import com.cues.core.drafting.GrammarParser
import com.cues.core.drafting.LlmSession
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
 * the phone, not the laptop.
 *
 * CL-35: the Sarvam row is the safe place to score a cloud drafter against
 * the corpus before it is ever offered in the app — this call needs real
 * network to produce a non-trivial result, but it is a dev-only evaluation
 * tool, not the shipped runtime path.
 */
object AppBakeOff {
    /**
     * [onDeviceSession] is [CuesApplication.onDeviceLlmSession] — the same
     * gated session `drafter` actually uses — passed in rather than
     * constructed here. Before Sprint 8 this always built its own
     * default-constructed [OnDeviceLlmDrafter], which meant an
     * always-throwing [com.cues.core.drafting.UnconfiguredLlmSession]
     * regardless of whether a real model was installed and turned on:
     * the model row could never show anything but "not wired up"
     * (CLEANUP.md CL-18's `AppBakeOff` item).
     */
    suspend fun run(pairedDevices: List<PairedDevice>, onDeviceSession: LlmSession, sarvamApiKey: String = ""): BakeOffReport {
        val text = checkNotNull(object {}.javaClass.getResourceAsStream("/corpus/paraphrases.txt")) {
            "corpus/paraphrases.txt missing from resources"
        }.bufferedReader().readText()
        val sarvamSession = if (sarvamApiKey.isBlank()) {
            UnconfiguredSarvamChatSession()
        } else {
            SarvamHttpChatSession(SarvamClient(sarvamApiKey))
        }
        return BakeOff.run(
            listOf(GrammarParser(pairedDevices), OnDeviceLlmDrafter(session = onDeviceSession), SarvamChatDrafter(sarvamSession)),
            Corpus.parse(text),
        )
    }
}
