package com.cues.core.drafting

/**
 * Accounts for every token in the request. This deliberately answers a
 * stricter question than friendly unsupported-copy: text that is neither part
 * of a parsed clause nor harmless connective language cannot be approved.
 */
object ClauseAccounting {
    private val filler = setOf(
        "a", "an", "and", "at", "after", "before", "by", "connect", "disconnect", "do", "end", "for", "from",
        "i", "if", "in", "is", "it", "me", "my", "of", "on", "out", "please", "run", "start", "take", "the",
        "then", "to", "until", "when", "with", "focus", "timer", "minute", "minutes", "quiet", "notifications",
        "notification", "silence", "charging", "charger", "plug", "remind", "about", "pin", "note",
    )

    fun classify(text: String, consumed: List<IntRange>): List<ClauseSpan> =
        Regex("[A-Za-z0-9]+|[^\\sA-Za-z0-9]").findAll(text).map { match ->
            val raw = match.value
            val kind = when {
                consumed.any { it.first <= match.range.last && match.range.first <= it.last } -> ClauseKind.MAPPED
                raw.all { !it.isLetterOrDigit() } || raw.lowercase() in filler -> ClauseKind.FILLER
                else -> ClauseKind.UNACCOUNTED
            }
            ClauseSpan(raw, match.range, kind)
        }.toList()

    fun unaccounted(text: String, consumed: List<IntRange>): List<String> =
        classify(text, consumed).filter { it.kind == ClauseKind.UNACCOUNTED }.map { it.text }
}
