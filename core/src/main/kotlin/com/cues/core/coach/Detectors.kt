package com.cues.core.coach

import com.cues.core.model.Day
import kotlin.math.abs

data class EvidenceLine(val text: String, val count: Int)

enum class SuggestionKind {
    SHORTER_DURATION,
    REMOVE_DAY,
    ADD_TIME_TRIGGER,
    FIX_PERMISSION,
    CHECK_OR_REMOVE_ACTION,
    ADD_SIGNAL_CUE,
}

data class Suggestion(
    val patternKey: String,
    val kind: SuggestionKind,
    val evidence: List<EvidenceLine>,
    /** A sentence that re-enters the ordinary drafter, never an executable rule. */
    val proposal: String,
)

sealed interface Detection {
    data class Suggest(val suggestion: Suggestion) : Detection
    data class NoPattern(val reason: String) : Detection
    data class NotEnoughData(val reason: String = "Not enough reliable coverage.") : Detection
}

object Detectors {
    private const val DAY = 86_400_000L
    private const val WINDOW = 14 * DAY

    fun earlyStop(events: List<LedgerEvent>, now: Long): Detection = guarded(events, now) {
        val recent = events.filterIsInstance<LedgerEvent.SessionEnded>().sortedByDescending { it.atMillis }.take(5)
        val early = recent.filter { it.plannedMinutes > 0 && it.actualMinutes < it.plannedMinutes * 0.6 }
        if (recent.size >= 5 && early.size >= 3) Detection.Suggest(
            Suggestion(
                "early-stop:${recent.first().routineId}", SuggestionKind.SHORTER_DURATION,
                listOf(EvidenceLine("Stopped early ${early.size} of the last ${recent.size} times.", early.size)),
                "make ${recent.first().routineId} ${early.map { it.actualMinutes }.average().toInt()} minutes",
            ),
        ) else Detection.NoPattern("Fewer than three early stops in the last five sessions.")
    }

    fun recurringSkip(events: List<LedgerEvent>, now: Long): Detection = guarded(events, now) {
        val group = events.filterIsInstance<LedgerEvent.PatchCreated>()
            .filter { it.kind == "skip-today" }
            .groupBy { it.weekday }.maxByOrNull { it.value.size }
        if (group != null && group.value.size >= 3) Detection.Suggest(
            Suggestion(
                "recurring-skip:${group.key}", SuggestionKind.REMOVE_DAY,
                listOf(EvidenceLine("Skipped on ${group.key} in ${group.value.size} weeks.", group.value.size)),
                "remove ${group.key.name.lowercase()} from this cue",
            ),
        ) else Detection.NoPattern("No weekday was skipped in three weeks.")
    }

    fun manualRoutine(events: List<LedgerEvent>, now: Long): Detection = guarded(events, now) {
        val starts = events.filterIsInstance<LedgerEvent.ManualStart>().takeLast(7)
        if (starts.size < 4) return@guarded Detection.NoPattern("Fewer than four manual starts.")
        val median = starts.map { it.minuteOfDay }.sorted()[starts.size / 2]
        val clustered = starts.filter { abs(it.minuteOfDay - median) <= 20 }
        if (clustered.size >= 4 && clustered.map { it.weekday }.toSet().size >= 4) Detection.Suggest(
            Suggestion(
                "manual-time:$median", SuggestionKind.ADD_TIME_TRIGGER,
                listOf(EvidenceLine("Started within 20 minutes of ${formatMinute(median)} on ${clustered.size} days.", clustered.size)),
                "start this cue at ${formatMinute(median)}",
            ),
        ) else Detection.NoPattern("Manual starts did not cluster within twenty minutes.")
    }

    fun unknownBlocker(events: List<LedgerEvent>, now: Long): Detection = guarded(events, now) {
        val group = events.filterIsInstance<LedgerEvent.Skipped>().filter { it.capability != null }
            .groupBy { it.capability!! }.maxByOrNull { it.value.size }
        if (group != null && group.value.size >= 2) Detection.Suggest(
            Suggestion(
                "unknown:${group.key}", SuggestionKind.FIX_PERMISSION,
                listOf(EvidenceLine("${group.key} was unreadable ${group.value.size} times.", group.value.size)),
                "check ${group.key} access",
            ),
        ) else Detection.NoPattern("No repeated unknown capability.")
    }

    fun blockedAction(events: List<LedgerEvent>, now: Long): Detection = guarded(events, now) {
        val group = events.filterIsInstance<LedgerEvent.ActionBlocked>().groupBy { it.actionId }.maxByOrNull { it.value.size }
        if (group != null && group.value.size >= 2) Detection.Suggest(
            Suggestion(
                "blocked:${group.key}", SuggestionKind.CHECK_OR_REMOVE_ACTION,
                listOf(EvidenceLine("${group.key} was blocked ${group.value.size} times.", group.value.size)),
                "check or remove ${group.key} from this cue",
            ),
        ) else Detection.NoPattern("No action was blocked twice.")
    }

    fun signalWithoutCue(events: List<LedgerEvent>, now: Long): Detection = guarded(events, now) {
        val group = events.filterIsInstance<LedgerEvent.SignalObserved>()
            .groupBy { Triple(it.kind, it.key, it.minuteOfDay / 60) }
            .maxByOrNull { (_, values) -> values.map { it.weekday }.toSet().size }
        val days = group?.value?.map { it.weekday }?.toSet().orEmpty()
        if (group != null && days.size >= 4) Detection.Suggest(
            Suggestion(
                "signal:${group.key.first}:${group.key.second}:${group.key.third}", SuggestionKind.ADD_SIGNAL_CUE,
                listOf(EvidenceLine("Observed ${group.key.first} ${group.key.second} in this hour on ${days.size} weekdays.", days.size)),
                "when ${group.key.second} ${group.key.first} is observed, draft a cue",
            ),
        ) else Detection.NoPattern("No uncovered signal repeated on four weekdays.")
    }

    fun all(events: List<LedgerEvent>, now: Long): List<Suggestion> = listOf(
        earlyStop(events, now), recurringSkip(events, now), manualRoutine(events, now),
        unknownBlocker(events, now), blockedAction(events, now), signalWithoutCue(events, now),
    ).filterIsInstance<Detection.Suggest>().map { it.suggestion }

    private inline fun guarded(events: List<LedgerEvent>, now: Long, detect: () -> Detection): Detection {
        val start = now - WINDOW
        val missing = events.filterIsInstance<LedgerEvent.CoverageGap>().sumOf { gap ->
            (minOf(now, gap.toMillis) - maxOf(start, gap.fromMillis)).coerceAtLeast(0)
        }
        return if (missing > WINDOW * 0.30) Detection.NotEnoughData() else detect()
    }

    private fun formatMinute(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)
}
