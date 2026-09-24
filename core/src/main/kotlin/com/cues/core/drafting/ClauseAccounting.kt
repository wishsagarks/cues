package com.cues.core.drafting

import com.cues.core.model.DraftSourceId

/**
 * Accounts for every token in the request. This deliberately answers a
 * stricter question than friendly unsupported-copy: text that is neither part
 * of a parsed clause nor harmless connective language cannot be approved.
 */
object ClauseAccounting {
    /**
     * Grammatical connectives only — words like "please" and "the" that carry
     * no meaning of their own. Domain words (timer, quiet, charging, connect,
     * pin, remind, ...) belong here only if GrammarParser genuinely cannot be
     * made to mark them `consumed`; every one that could be fixed at the
     * source was, so a parsing gap for those words surfaces as UNACCOUNTED
     * instead of being waved through.
     *
     * "plug" is the one proven exception: `parseTrigger`'s CHARGER_WORDS scan
     * stops at the first matching word in its list, so "plug in the charger"
     * consumes only "charger" and never reaches "plugged in"/"plug in" — see
     * corpus/paraphrases.txt's "when I plug in the charger..." line.
     */
    private val filler = setOf(
        "a", "an", "and", "at", "after", "before", "by", "do", "end", "for", "from",
        "i", "if", "in", "is", "it", "me", "my", "of", "on", "out", "please", "run", "start", "take", "the",
        "then", "to", "until", "when", "whenever", "while", "with", "about", "plug",
        // Decorative time-of-day flavor, never the sole time specifier — the
        // grammar only ever derives an actual time from a clock value
        // ("after 7 pm", "before 9am"), never from these words alone.
        "morning", "afternoon", "evening", "night",
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

    /**
     * Stamps a draft with its drafter and locally derived accounting. Only the
     * parser's own spans are trusted, decided by source, never by whether a
     * drafter happened to leave `clauses` empty.
     */
    fun stamp(text: String, result: DraftResult.Drafted): DraftResult.Drafted {
        val clauses = if (result.source == DraftSourceId.GRAMMAR_PARSER) result.clauses else classify(text, emptyList())
        return result.copy(
            clauses = clauses,
            routine = result.routine.copy(
                draftedBy = result.source,
                unaccountedClauses = clauses.filter { it.kind == ClauseKind.UNACCOUNTED }.map { it.text },
            ),
        )
    }
}
