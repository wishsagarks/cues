package com.cues.core.review

import com.cues.core.eval.FreshnessPolicy
import com.cues.core.model.*
import com.cues.core.ports.NamedContextStore
import com.cues.core.ports.PatchStore
import com.cues.core.signals.ContextualStores
import com.cues.core.signals.SignalRegistry
import java.time.Instant
import java.time.ZoneId

enum class ForecastStatus { WILL_ARM, SKIPPED_BY_PATCH, CANNOT_TELL, NOT_TODAY }

/** Deterministic eligibility forecast — events are never claimed to be predicted. */
data class ForecastItem(
    val routineId: String,
    val window: String,
    val status: ForecastStatus,
    val reasons: List<String>,
)

const val FORECAST_LABEL = "Forecast from your cues, not a promise."

fun forecastToday(
    routines: List<Routine>,
    patches: List<Patch>,
    snapshot: ContextSnapshot,
    zone: ZoneId,
    contexts: NamedContextStore? = null,
): List<ForecastItem> {
    contexts?.let { ContextualStores.contexts = it }
    val today = Instant.ofEpochMilli(snapshot.nowMillis).atZone(zone).toLocalDate().toString()
    return routines.map { routine ->
        val window = when (val trigger = routine.trigger) {
            is Trigger.AtTime -> "at ${trigger.time}"
            else -> "if ${SignalRegistry.reviewText(trigger)} happens today"
        }
        val patch = patches.firstOrNull { it.routineId == routine.id }
        if (patch != null && patch.baseVersion == routine.version) {
            val active = when (val kind = patch.kind) {
                is PatchKind.SkipUntil -> snapshot.nowMillis < kind.epochMillis
                is PatchKind.SkipOccurrence -> kind.date == today
            }
            if (active) return@map ForecastItem(routine.id, window, ForecastStatus.SKIPPED_BY_PATCH, listOf("A temporary patch skips this cue."))
        }
        val dayGate = routine.conditions.filterIsInstance<Condition.DaysOfWeek>().flatMap { it.days }.takeIf { it.isNotEmpty() }
        if (dayGate != null && snapshot.localDay is ContextValue.Known && (snapshot.localDay as ContextValue.Known).value !in dayGate) {
            return@map ForecastItem(routine.id, window, ForecastStatus.NOT_TODAY, listOf("It is outside the cue's selected days."))
        }
        val reasons = routine.conditions.map { SignalRegistry.evaluate(it, snapshot, FreshnessPolicy()) }
        when {
            reasons.any { it.truth == com.cues.core.eval.Truth.UNKNOWN } -> ForecastItem(routine.id, window, ForecastStatus.CANNOT_TELL, reasons.filter { it.truth == com.cues.core.eval.Truth.UNKNOWN }.map { it.detail })
            routine.trigger is Trigger.AtTime -> ForecastItem(routine.id, window, ForecastStatus.WILL_ARM, reasons.filter { it.truth != com.cues.core.eval.Truth.MATCH }.map { it.detail })
            else -> ForecastItem(routine.id, window, ForecastStatus.WILL_ARM, listOf("It can run if that event happens and these observed conditions still hold."))
        }
    }
}
