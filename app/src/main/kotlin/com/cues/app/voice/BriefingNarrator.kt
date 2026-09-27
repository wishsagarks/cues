package com.cues.app.voice

import android.content.Context
import android.media.MediaPlayer
import com.cues.app.net.SarvamClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Speaks the Now tab's BRIEFING digest in whichever language the small
 * Hi/Eng switch on that card has selected. The digest text itself is
 * fixed, hand-written copy for each language (see NowScreen's
 * `BRIEFING_NARRATION_EN`/`_HI`) — this class never asks Sarvam to
 * translate it, only to speak it, so there is no live-translation risk on
 * stage. Each language's clip is generated through Sarvam once per app
 * install and cached to disk; every tap after that plays the cached
 * clip — a prerecording, just made on first use instead of at build time.
 */
class BriefingNarrator(private val context: Context, private val client: SarvamClient) {

    private var player: MediaPlayer? = null

    suspend fun speak(text: String, languageCode: String) {
        val cached = cacheFile(languageCode)
        val wav = if (cached.exists()) cached.readBytes() else {
            client.textToSpeech(text, languageCode = languageCode).also { cached.writeBytes(it) }
        }
        withContext(Dispatchers.Main) { play(wav) }
    }

    private fun cacheFile(languageCode: String) = File(context.cacheDir, "cues-briefing-$languageCode.wav")

    private fun play(wav: ByteArray) {
        stop()
        val file = File.createTempFile("cues-briefing-play", ".wav", context.cacheDir)
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
