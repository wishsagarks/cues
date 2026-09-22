package com.cues.app.runtime

import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Results from the target-phone spike in Sprint 3.0.
 *
 * The speech checks use public Android APIs. Jovi's mic/assist behaviour and
 * OriginOS's permission-usage suggestion are deliberately recorded as manual
 * observations: an ordinary app has no trustworthy API for either, and an
 * "not detected" result would be a misleading claim.
 */
data class DeviceDiagnostics(
    val checkedAtMillis: Long? = null,
    val os: String = "Not checked yet.",
    val onDeviceSpeech: String = "Not checked yet.",
    val englishIndiaPack: String = "Not checked yet.",
    val joviMicOrAssist: ManualObservation = ManualObservation.NOT_CHECKED,
    val permissionMonitor: ManualObservation = ManualObservation.NOT_CHECKED,
)

enum class ManualObservation(val label: String) {
    NOT_CHECKED("Not checked"),
    NO_ISSUE_OBSERVED("No issue observed"),
    ISSUE_OBSERVED("Issue observed"),
}

/** Persists the device-spike record locally so it survives leaving the screen. */
class DeviceDiagnosticsRepository(private val context: Context) {

    private val preferences = context.getSharedPreferences("device-diagnostics", Context.MODE_PRIVATE)

    fun latest(): DeviceDiagnostics = DeviceDiagnostics(
        checkedAtMillis = preferences.getLong(KEY_CHECKED_AT, 0).takeIf { it > 0 },
        os = preferences.getString(KEY_OS, null) ?: "Not checked yet.",
        onDeviceSpeech = preferences.getString(KEY_ON_DEVICE_SPEECH, null) ?: "Not checked yet.",
        englishIndiaPack = preferences.getString(KEY_ENGLISH_INDIA, null) ?: "Not checked yet.",
        joviMicOrAssist = preferences.getString(KEY_JOVI, null)
            ?.let { runCatching { ManualObservation.valueOf(it) }.getOrNull() }
            ?: ManualObservation.NOT_CHECKED,
        permissionMonitor = preferences.getString(KEY_PERMISSION_MONITOR, null)
            ?.let { runCatching { ManualObservation.valueOf(it) }.getOrNull() }
            ?: ManualObservation.NOT_CHECKED,
    )

    /**
     * Runs the automatic checks. This must be called from the main thread:
     * SpeechRecognizer creation and destruction both have that requirement.
     */
    fun refresh(onComplete: (DeviceDiagnostics) -> Unit) {
        val base = latest().copy(
            checkedAtMillis = System.currentTimeMillis(),
            os = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}); ${Build.MANUFACTURER} ${Build.MODEL}",
        )

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            save(
                base.copy(
                    onDeviceSpeech = "Unavailable: Android's on-device speech API requires Android 12 (API 31).",
                    englishIndiaPack = "Not checked: Android's language-support query requires Android 13 (API 33).",
                ),
                onComplete,
            )
            return
        }

        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            save(
                base.copy(
                    onDeviceSpeech = "Unavailable: no on-device speech recognition service was reported.",
                    englishIndiaPack = "Not checked: no on-device speech recognition service is available.",
                ),
                onComplete,
            )
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            save(
                base.copy(
                    onDeviceSpeech = "Available: Android reported an on-device speech recognition service.",
                    englishIndiaPack = "Not checked: Android's language-support query requires Android 13 (API 33).",
                ),
                onComplete,
            )
            return
        }

        checkEnglishIndiaSupport(base, onComplete)
    }

    fun recordJoviMicOrAssist(observation: ManualObservation): DeviceDiagnostics =
        latest().copy(joviMicOrAssist = observation).also { save(it) }

    fun recordPermissionMonitor(observation: ManualObservation): DeviceDiagnostics =
        latest().copy(permissionMonitor = observation).also { save(it) }

    private fun checkEnglishIndiaSupport(base: DeviceDiagnostics, onComplete: (DeviceDiagnostics) -> Unit) {
        val recognizer = try {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } catch (error: UnsupportedOperationException) {
            save(
                base.copy(
                    onDeviceSpeech = "Unavailable: ${error.message ?: "the on-device recognizer could not be created."}",
                    englishIndiaPack = "Not checked: the on-device recognizer could not be created.",
                ),
                onComplete,
            )
            return
        }

        val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(
            RecognizerIntent.EXTRA_LANGUAGE,
            ENGLISH_INDIA,
        )
        recognizer.checkRecognitionSupport(
            request,
            context.mainExecutor,
            object : RecognitionSupportCallback {
                override fun onSupportResult(support: RecognitionSupport) {
                    recognizer.destroy()
                    save(
                        base.copy(
                            onDeviceSpeech = "Available: Android reported an on-device speech recognition service.",
                            englishIndiaPack = englishIndiaStatus(support),
                        ),
                        onComplete,
                    )
                }

                override fun onError(error: Int) {
                    recognizer.destroy()
                    save(
                        base.copy(
                            onDeviceSpeech = "Available: Android reported an on-device speech recognition service.",
                            englishIndiaPack = "Could not check English (India) support (recognizer error $error).",
                        ),
                        onComplete,
                    )
                }
            },
        )
    }

    private fun englishIndiaStatus(support: RecognitionSupport): String = when {
        support.installedOnDeviceLanguages.containsLanguage(ENGLISH_INDIA) ->
            "Installed: English (India) is ready for local speech recognition."

        support.pendingOnDeviceLanguages.containsLanguage(ENGLISH_INDIA) ->
            "Pending download: English (India) has been requested but is not ready yet."

        support.supportedOnDeviceLanguages.containsLanguage(ENGLISH_INDIA) ->
            "Download required: English (India) is supported but is not installed yet."

        else -> "Unavailable: English (India) was not reported as an on-device language."
    }

    private fun List<String>.containsLanguage(target: String): Boolean = any { tag ->
        Locale.forLanguageTag(tag).toLanguageTag().equals(target, ignoreCase = true)
    }

    private fun save(snapshot: DeviceDiagnostics, onComplete: ((DeviceDiagnostics) -> Unit)? = null) {
        preferences.edit()
            .putLong(KEY_CHECKED_AT, snapshot.checkedAtMillis ?: 0)
            .putString(KEY_OS, snapshot.os)
            .putString(KEY_ON_DEVICE_SPEECH, snapshot.onDeviceSpeech)
            .putString(KEY_ENGLISH_INDIA, snapshot.englishIndiaPack)
            .putString(KEY_JOVI, snapshot.joviMicOrAssist.name)
            .putString(KEY_PERMISSION_MONITOR, snapshot.permissionMonitor.name)
            .apply()
        onComplete?.invoke(snapshot)
    }

    private companion object {
        const val ENGLISH_INDIA = "en-IN"
        const val KEY_CHECKED_AT = "checked-at"
        const val KEY_OS = "os"
        const val KEY_ON_DEVICE_SPEECH = "on-device-speech"
        const val KEY_ENGLISH_INDIA = "english-india"
        const val KEY_JOVI = "jovi-mic-or-assist"
        const val KEY_PERMISSION_MONITOR = "permission-monitor"
    }
}
