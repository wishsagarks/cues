package com.cues.app.drafting

import android.content.Context
import android.content.Intent
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Speech input via the phone's microphone and the installed recognition service
 * (Google's engine on this device). The on-device-only path was dropped: the
 * on-device recognizer has no en-IN model on OriginOS 6 / SM8850 (CL-19).
 */
class LocalSpeechInput(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    fun start(onTranscript: (String) -> Unit, onUnavailable: (String) -> Unit) {
        stop()

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onUnavailable("Speech recognition is not available on this phone. Type your cue instead.")
            return
        }

        val activeRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
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
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
                    .putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false),
            )
        } catch (error: RuntimeException) {
            stop()
            onUnavailable("Speech could not start. Type your cue instead.")
        }
    }

    fun stop() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun errorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            "Microphone access was not granted. Type your cue instead."
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
            "No speech was recognised. You can correct or type your cue instead."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
            "Speech recogniser is busy. Try again in a moment."
        else -> "Speech could not finish (error $error). Type your cue instead."
    }
}
