package com.cues.app.drafting

import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * A user-gesture-only speech path that never substitutes a network recognizer.
 *
 * Cues creates Android's on-device recognizer directly and requests offline
 * recognition. If the service or its language support is unavailable, this
 * returns an explicit error for the typed-input fallback; it never calls
 * [SpeechRecognizer.createSpeechRecognizer].
 */
class LocalSpeechInput(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    fun start(onTranscript: (String) -> Unit, onUnavailable: (String) -> Unit) {
        stop()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            onUnavailable("Local speech needs Android 12 or later. Type your cue instead.")
            return
        }
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            onUnavailable("Local speech is not available on this phone. Type your cue instead.")
            return
        }

        val activeRecognizer = try {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } catch (error: UnsupportedOperationException) {
            onUnavailable("Local speech could not start. Type your cue instead.")
            return
        }
        recognizer = activeRecognizer
        activeRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: android.os.Bundle) {
                val transcript = results
                    .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                stop()
                if (transcript.isNullOrEmpty()) {
                    onUnavailable("No speech was recognised. You can correct or type your cue instead.")
                } else {
                    onTranscript(transcript)
                }
            }

            override fun onError(error: Int) {
                stop()
                onUnavailable(errorMessage(error))
            }

            override fun onReadyForSpeech(params: android.os.Bundle) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: android.os.Bundle) = Unit
            override fun onEvent(eventType: Int, params: android.os.Bundle) = Unit
        })

        try {
            activeRecognizer.startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, ENGLISH_INDIA)
                    .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false),
            )
        } catch (error: RuntimeException) {
            stop()
            onUnavailable("Local speech could not start. Type your cue instead.")
        }
    }

    fun stop() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun errorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
            "English (India) local speech is unavailable. Type your cue instead."

        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            "Microphone access was not granted. Type your cue instead."

        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
            "No speech was recognised. You can correct or type your cue instead."

        else -> "Local speech could not finish (error $error). Type your cue instead."
    }

    private companion object {
        const val ENGLISH_INDIA = "en-IN"
    }
}
