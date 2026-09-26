package com.cues.app.voice

/** Languages intentionally exposed in the first regional authoring release. */
data class AuthoringLanguage(
    val label: String,
    val nativeLabel: String,
    val languageCode: String,
) {
    val displayLabel: String get() = "$label · $nativeLabel"

    companion object {
        val English = AuthoringLanguage("English", "English", "en-IN")
        val Hindi = AuthoringLanguage("Hindi", "हिन्दी", "hi-IN")
        val Bengali = AuthoringLanguage("Bengali", "বাংলা", "bn-IN")
        val Marathi = AuthoringLanguage("Marathi", "मराठी", "mr-IN")
        val Telugu = AuthoringLanguage("Telugu", "తెలుగు", "te-IN")
        val Tamil = AuthoringLanguage("Tamil", "தமிழ்", "ta-IN")

        /** English plus the five regional languages offered in the UI. */
        val choices = listOf(English, Hindi, Bengali, Marathi, Telugu, Tamil)
    }
}
