package com.cues.core.assistant

import com.cues.core.model.Day

/**
 * A deterministic phrase grammar over a small, closed set of edit sentences —
 * [GrammarParser][com.cues.core.drafting.GrammarParser]'s own discipline,
 * one level down: for edits, not fresh drafts.
 *
 * [OnDeviceRefinePhraser] hands this exactly one sentence the model was asked
 * to produce in this vocabulary; the model's own words are never trusted as
 * the operation, only as candidate prose for this same closed compiler —
 * `null` here means "not a recognized edit," never a guess.
 */
class RefineGrammarParser {
    fun parse(text: String): RefineOperation? {
        val normalized = text.trim().lowercase()

        DURATION_PATTERN.find(normalized)?.let { m ->
            val minutes = m.groupValues[1].toIntOrNull() ?: return@let
            if (minutes in 1..999) return RefineOperation.SetDuration(minutes)
        }

        ADD_DAY_PATTERN.find(normalized)?.let { m ->
            DAY_WORDS[m.groupValues[1]]?.let { day -> return RefineOperation.AddDays(setOf(day)) }
        }

        REMOVE_DAY_PATTERN.find(normalized)?.let { m ->
            DAY_WORDS[m.groupValues[1]]?.let { day -> return RefineOperation.RemoveDays(setOf(day)) }
        }

        return null
    }

    private companion object {
        val DURATION_PATTERN = Regex("\\bset duration to (\\d{1,3})\\s*(?:minutes?|mins?)\\b")
        val ADD_DAY_PATTERN = Regex("\\badd day (\\w+)\\b")
        val REMOVE_DAY_PATTERN = Regex("\\bremove day (\\w+)\\b")

        val DAY_WORDS = mapOf(
            "monday" to Day.MON, "tuesday" to Day.TUE, "wednesday" to Day.WED,
            "thursday" to Day.THU, "friday" to Day.FRI, "saturday" to Day.SAT,
            "sunday" to Day.SUN,
        )
    }
}
