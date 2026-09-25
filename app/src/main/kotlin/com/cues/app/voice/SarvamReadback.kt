package com.cues.app.voice

import android.content.Context
import android.media.MediaPlayer
import com.cues.app.net.SarvamClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Reads back a *translation* of text Cues already rendered — never a
 * paraphrase, and deliberately not [ReplySpeaker].
 *
 * [ReplySpeaker]'s whole contract is that it only ever speaks text Cues
 * already produced verbatim. A Sarvam translation is genuinely different
 * text — a cloud call's output — so it does not reuse that class or its
 * guarantee; it is its own, clearly separate path. Every caller must show
 * the original English (the real text of record) alongside whatever this
 * speaks, labeled "Translated via Sarvam (online)" — this class only
 * performs the translation and playback, it does not enforce that labeling.
 *
 * See docs/FDD.md's "Optional cloud assist" section and CLEANUP.md CL-35.
 */
class SarvamReadback(private val context: Context, private val client: SarvamClient) {

    private var player: MediaPlayer? = null

    /** Translates [englishText] (already rendered by ReviewCopy/ReplyCopy) into [targetLanguageCode] and speaks it. */
    suspend fun translateAndSpeak(englishText: String, targetLanguageCode: String): String {
        val translation = client.translate(englishText, sourceLanguageCode = "en-IN", targetLanguageCode = targetLanguageCode)
        val wav = client.textToSpeech(translation.translatedText, languageCode = targetLanguageCode)
        withContext(Dispatchers.Main) { play(wav) }
        return translation.translatedText
    }

    private fun play(wav: ByteArray) {
        stop()
        val file = File.createTempFile("cues-readback", ".wav", context.cacheDir)
        file.writeBytes(wav)
        player = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnCompletionListener {
                it.release()
                file.delete()
            }
            prepare()
            start()
        }
    }

    fun stop() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
    }
}
