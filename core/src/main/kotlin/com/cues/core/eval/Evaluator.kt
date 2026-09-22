package com.cues.core.eval

import com.cues.core.model.ContextSnapshot
import com.cues.core.model.Routine
import com.cues.core.model.TriggerEvent
import com.cues.core.model.UnknownReason
import com.cues.core.signals.SignalRegistry

/** The evaluator's verdict on one event. */
data class Decision(
    val truth: Truth,
    val reasons: List<Reason>,
) {
    /**
     * The only question the runtime asks.
     *
     * Deliberately not `truth != NO_MATCH`. UNKNOWN does not grant permission
     * to act; it grants permission to explain why nothing happened.
     */
    val shouldStart: Boolean get() = truth == Truth.MATCH
}

/**
 * How old a reading may be before it stops counting as known.
 *
 * Applied uniformly by the evaluator so no adapter can quietly opt out of
 * freshness. Zero or negative disables the check for that signal.
 */
data class FreshnessPolicy(
    val chargingMaxAgeMillis: Long = 60_000,
    val connectedDevicesMaxAgeMillis: Long = 60_000,
    val wifiMaxAgeMillis: Long = 60_000,
) {
    companion object {
        /** Rehearsal supplies its own timestamps, so ageing them out is noise. */
        val NONE = FreshnessPolicy(chargingMaxAgeMillis = 0, connectedDevicesMaxAgeMillis = 0, wifiMaxAgeMillis = 0)
    }
}

/**
 * Decides whether an event and a context snapshot satisfy a routine.
 *
 * Pure: same inputs, same output, no clock of its own, no I/O, no model. The
 * live runtime and the rehearsal screen both call this exact function, which
 * is the only reason a rehearsal can be said to predict anything at all.
 */
object Evaluator {

    fun evaluate(
        routine: Routine,
        event: TriggerEvent,
        context: ContextSnapshot,
        freshness: FreshnessPolicy = FreshnessPolicy(),
    ): Decision {
        val triggerReason = SignalRegistry.match(routine.trigger, event)
        if (triggerReason.truth != Truth.MATCH) {
            // No point reporting weekday conditions for an event that was never
            // this routine's business.
            return Decision(triggerReason.truth, listOf(triggerReason))
        }

        val conditionReasons = routine.conditions.map { SignalRegistry.evaluate(it, context, freshness) }
        val reasons = listOf(triggerReason) + conditionReasons
        return Decision(conditionReasons.map { it.truth }.conjoin(), reasons)
    }

}

/**
 * Demotes a reading that has aged past its adapter's freshness rule.
 *
 * A stale value is not a wrong value, it is an unusable one, so it becomes
 * UNKNOWN rather than being negated. The receipt then says the reading was too
 * old instead of asserting something about the phone that nobody checked.
 */
internal fun <T> ContextValue<T>.freshened(nowMillis: Long, maxAgeMillis: Long): ContextValue<T> = when {
    this !is ContextValue.Known -> this
    maxAgeMillis <= 0L -> this
    nowMillis - observedAtMillis <= maxAgeMillis -> this
    else -> ContextValue.Unknown(UnknownReason.STALE, source)
}

/** True when [time] falls in the window, handling windows that wrap past midnight. */
internal fun Condition.TimeWindow.contains(time: LocalTimeOfDay): Boolean {
    val start = startInclusive.minutesOfDay
    val end = endExclusive.minutesOfDay
    val at = time.minutesOfDay
    // A window whose end is not after its start wraps: 22:00–06:00 is one night.
    return if (start < end) at in start until end else at >= start || at < end
}

internal fun Condition.TimeWindow.describe(): String {
    val allDay = startInclusive.minutesOfDay == 0 && endExclusive.minutesOfDay == 0
    return when {
        allDay -> "any time"
        // Rendered the way the user said it, not as a synthetic 18:00–00:00.
        endExclusive.minutesOfDay == 0 -> "at or after $startInclusive"
        else -> "$startInclusive to $endExclusive"
    }
}

internal fun UnknownReason.describe(): String = when (this) {
    UnknownReason.PERMISSION_DENIED -> "permission denied"
    UnknownReason.ADAPTER_UNAVAILABLE -> "the adapter was unavailable"
    UnknownReason.NEVER_OBSERVED -> "it was never observed"
    UnknownReason.STALE -> "the reading was too old to trust"
    UnknownReason.REDACTED_BY_OS -> "the system withheld it"
}

internal fun Boolean.chargingWord(): String = if (this) "charging" else "not charging"

internal fun Day.full(): String = when (this) {
    Day.MON -> "Monday"
    Day.TUE -> "Tuesday"
    Day.WED -> "Wednesday"
    Day.THU -> "Thursday"
    Day.FRI -> "Friday"
    Day.SAT -> "Saturday"
    Day.SUN -> "Sunday"
}

/** Names the common sets so a receipt reads "Monday–Friday", not a seven-item list. */
internal fun Set<Day>.describe(): String = when (this) {
    com.cues.core.model.WEEKDAYS -> "Monday–Friday"
    com.cues.core.model.WEEKEND -> "Saturday–Sunday"
    else -> Day.entries.filter { it in this }.joinToString(", ") { it.full() }
}
