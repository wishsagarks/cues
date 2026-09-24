package com.cues.core.cli

import com.cues.core.coach.Detectors
import com.cues.core.coach.LedgerEvent
import com.cues.core.model.Day

object CoachMain {
    @JvmStatic
    fun main(args: Array<String>) {
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        val fixture = listOf(
            LedgerEvent.SessionEnded("study", 45, 22, "MANUAL_STOP", now - day),
            LedgerEvent.SessionEnded("study", 45, 25, "MANUAL_STOP", now - 2 * day),
            LedgerEvent.SessionEnded("study", 45, 20, "MANUAL_STOP", now - 3 * day),
            LedgerEvent.SessionEnded("study", 45, 44, "DEADLINE_REACHED", now - 4 * day),
            LedgerEvent.SessionEnded("study", 45, 45, "DEADLINE_REACHED", now - 5 * day),
            LedgerEvent.PatchCreated("skip-today", Day.FRI, now - day),
            LedgerEvent.PatchCreated("skip-today", Day.FRI, now - 8 * day),
            LedgerEvent.PatchCreated("skip-today", Day.FRI, now - 13 * day),
        )
        val suggestions = Detectors.all(fixture, now)
        if (suggestions.isEmpty()) println("No suggestion: not enough repeated evidence.")
        suggestions.forEach { suggestion ->
            println("${suggestion.kind.name.lowercase().replace('_', ' ')}: ${suggestion.proposal}")
            suggestion.evidence.forEach { println("  ${it.text}") }
        }
    }
}
