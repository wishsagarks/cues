package com.cues.app.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.cues.app.net.SarvamClient
import com.cues.core.ports.SpeechInput
import com.cues.core.ports.SpeechResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * The cloud counterpart to [com.cues.app.drafting.LocalSpeechInput]: records
 * raw audio instead of using [android.speech.SpeechRecognizer], and sends it
 * to Sarvam's Saaras model for regional-language transcription. This is the
 * first real implementation of the core `SpeechInput` port (Ports.kt) — it
 * shipped unimplemented because no offline recognizer needed it.
 *
 * Opt-in only: constructed and offered only when the user has turned cloud
 * assist on and an API key is configured (CuesApplication.cloudAssistAvailable,
 * CLEANUP.md CL-35). [LocalSpeechInput] is untouched and stays the default,
 * fully-offline mic button.
 *
 * Records for a fixed [maxDurationMillis] rather than detecting silence — a
 * deliberate simplification for this event, noted in CLEANUP.md CL-35.
 */
class SarvamSpeechInput(
    private val context: Context,
    private val client: SarvamClient,
    private val maxDurationMillis: Long = 6_000,
) : SpeechInput {

    override suspend fun listen(): SpeechResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return SpeechResult.PermissionDenied
        }
        val wav = withContext(Dispatchers.IO) { recordWav() } ?: return SpeechResult.Unavailable
        return try {
            val transcript = client.transcribe(wav).transcript.trim()
            if (transcript.isEmpty()) SpeechResult.NoMatch else SpeechResult.Recognized(transcript)
        } catch (e: Exception) {
            SpeechResult.Failed(e.message ?: "Cloud speech recognition failed.")
        }
    }

    @Suppress("MissingPermission") // RECORD_AUDIO is checked in listen() before this is ever called.
    private fun recordWav(): ByteArray? {
        val sampleRate = 16_000
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBufferSize <= 0) return null
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufferSize * 2,
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return null
        }
        val pcm = ByteArrayOutputStream()
        val buffer = ByteArray(minBufferSize)
        try {
            recorder.startRecording()
            val started = System.currentTimeMillis()
            while (System.currentTimeMillis() - started < maxDurationMillis) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) pcm.write(buffer, 0, read)
            }
        } finally {
            recorder.stop()
            recorder.release()
        }
        return wavHeader(pcm.size(), sampleRate) + pcm.toByteArray()
    }

    private fun wavHeader(pcmSize: Int, sampleRate: Int): ByteArray {
        val byteRate = sampleRate * 2
        val header = ByteArray(44)
        fun writeString(offset: Int, value: String) = value.forEachIndexed { i, c -> header[offset + i] = c.code.toByte() }
        fun writeInt(offset: Int, value: Int) {
            header[offset] = (value and 0xff).toByte()
            header[offset + 1] = ((value shr 8) and 0xff).toByte()
            header[offset + 2] = ((value shr 16) and 0xff).toByte()
            header[offset + 3] = ((value shr 24) and 0xff).toByte()
        }
        fun writeShort(offset: Int, value: Int) {
            header[offset] = (value and 0xff).toByte()
            header[offset + 1] = ((value shr 8) and 0xff).toByte()
        }
        writeString(0, "RIFF")
        writeInt(4, 36 + pcmSize)
        writeString(8, "WAVE")
        writeString(12, "fmt ")
        writeInt(16, 16)
        writeShort(20, 1) // PCM
        writeShort(22, 1) // mono
        writeInt(24, sampleRate)
        writeInt(28, byteRate)
        writeShort(32, 2) // block align
        writeShort(34, 16) // bits per sample
        writeString(36, "data")
        writeInt(40, pcmSize)
        return header
    }
}
