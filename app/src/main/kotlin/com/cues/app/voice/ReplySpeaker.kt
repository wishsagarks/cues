package com.cues.app.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Reads back exactly the text Cues already produced — an Assistant reply's
 * [com.cues.core.assistant.AssistantReply.text], a receipt's rendered
 * string — never a paraphrase, and never anything a model wrote directly.
 *
 * This is an output convenience only. Nothing here can approve a draft or
 * confirm a [com.cues.core.assistant.PendingCommand]; those stay an explicit
 * tap regardless of whether voice authored the request that led to them —
 * see docs/API_VERIFICATION.md's "Text to speech" entry and the Cues Brain
 * plan's Task 11.
 */
class ReplySpeaker(context: Context) {
    private var ready = false

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) tts.language = Locale.getDefault()
    }

    /** No-ops silently before init finishes or on blank text — never queues a partial or garbled read. */
    fun speak(text: String) {
        if (!ready || text.isBlank()) return
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cues-utterance")
    }

    fun stop() {
        if (ready) tts.stop()
    }

    /** Always safe to call, initialized or not — mirrors [com.cues.app.drafting.LocalSpeechInput.stop]'s idempotence. */
    fun shutdown() {
        runCatching { tts.stop() }
        runCatching { tts.shutdown() }
    }
}
