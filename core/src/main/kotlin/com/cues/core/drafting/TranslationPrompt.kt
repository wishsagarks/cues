package com.cues.core.drafting

/**
 * Builds the prompt asking the on-device model to translate a regional-
 * language cue request into English — nothing more. Translation and cue
 * normalization stay two separate passes, the same shape Sarvam's own
 * translate-then-normalize pipeline already uses: this prompt never asks
 * the model to also enforce the closed cue grammar, and its output still
 * goes through [OnDeviceLlmDrafter]/[GrammarParser] afterward like any
 * other candidate sentence — never trusted as structure on its own.
 *
 * Lives in `:core` because it is pure prompt/text construction, no
 * Android or network dependency; the actual model call is made by
 * whichever [LlmSession] the caller already has (on the phone, that's
 * `LiteRtLmSession` in `:app`).
 */
object TranslationPrompt {
    fun build(text: String, sourceLanguageLabel: String): String =
        """
            Translate the following $sourceLanguageLabel request into plain, natural English.
            Preserve people's names, device names, times, and any quoted message text exactly
            as given — do not translate proper nouns or quoted text. Reply with only the
            English translation and nothing else: no explanation, no label, no quotation marks
            around your answer.

            Request: $text
        """.trimIndent()

    /** Strips the same harmless model wrappers [OnDeviceLlmDrafter.cleanCandidate] does. */
    fun clean(raw: String): String = raw
        .replace("```", "")
        .trim()
        .removePrefix("Translation:")
        .removePrefix("English:")
        .removePrefix("Output:")
        .trim()
        .removeSurrounding("\"", "\"")
        .removeSurrounding("'", "'")
        .trim()
}
