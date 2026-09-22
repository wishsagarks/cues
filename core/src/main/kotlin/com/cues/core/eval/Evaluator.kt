package com.cues.core.eval

import com.cues.core.model.Condition
import com.cues.core.model.ContextSnapshot
import com.cues.core.model.ContextValue
import com.cues.core.model.Day
import com.cues.core.model.DeviceTransition
import com.cues.core.model.EventKind
import com.cues.core.model.LocalTimeOfDay
import com.cues.core.model.PowerTransition
import com.cues.core.model.Routine
import com.cues.core.model.Trigger
import com.cues.core.model.TriggerEvent
import com.cues.core.model.UnknownReason

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
) {
    companion object {
        /** Rehearsal supplies its own timestamps, so ageing them out is noise. */
        val NONE = FreshnessPolicy(chargingMaxAgeMillis = 0, connectedDevicesMaxAgeMillis = 0)
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
        val triggerReason = matchTrigger(routine.trigger, event)
        if (triggerReason.truth != Truth.MATCH) {
            // No point reporting weekday conditions for an event that was never
            // this routine's business.
            return Decision(triggerReason.truth, listOf(triggerReason))
        }

        val conditionReasons = routine.conditions.map { evaluateCondition(it, context, freshness) }
        val reasons = listOf(triggerReason) + conditionReasons
        return Decision(conditionReasons.map { it.truth }.conjoin(), reasons)
    }

    // ------------------------------------------------------------ triggers

    private fun matchTrigger(trigger: Trigger, event: TriggerEvent): Reason = when (trigger) {
        is Trigger.BluetoothConnection -> {
            val wantedKind = when (trigger.transition) {
                DeviceTransition.CONNECTED -> EventKind.BLUETOOTH_CONNECTED
                DeviceTransition.DISCONNECTED -> EventKind.BLUETOOTH_DISCONNECTED
            }
            when {
                event.kind != wantedKind -> Reason(
                    ReasonCode.TRIGGER_KIND_MISMATCH,
                    Truth.NO_MATCH,
                    "Event was ${event.kind}, this cue listens for $wantedKind.",
                )

                event.deviceId != trigger.deviceId -> Reason(
                    ReasonCode.TRIGGER_DEVICE_MISMATCH,
                    Truth.NO_MATCH,
                    "A different device triggered this, not ${trigger.deviceLabel}.",
                )

                else -> Reason(
                    ReasonCode.TRIGGER_MATCHED,
                    Truth.MATCH,
                    "${trigger.deviceLabel} ${trigger.transition.name.lowercase()}.",
                )
            }
        }

        is Trigger.Charging -> {
            val wantedKind = when (trigger.transition) {
                PowerTransition.PLUGGED_IN -> EventKind.POWER_CONNECTED
                PowerTransition.UNPLUGGED -> EventKind.POWER_DISCONNECTED
            }
            if (event.kind == wantedKind) {
                Reason(ReasonCode.TRIGGER_MATCHED, Truth.MATCH, "Power ${trigger.transition.name.lowercase()}.")
            } else {
                Reason(
                    ReasonCode.TRIGGER_KIND_MISMATCH,
                    Truth.NO_MATCH,
                    "Event was ${event.kind}, this cue listens for $wantedKind.",
                )
            }
        }

        is Trigger.Manual ->
            if (event.kind == EventKind.MANUAL_RUN) {
                Reason(ReasonCode.TRIGGER_MATCHED, Truth.MATCH, "Run by hand.")
            } else {
                Reason(
                    ReasonCode.TRIGGER_KIND_MISMATCH,
                    Truth.NO_MATCH,
                    "Event was ${event.kind}, this cue only runs by hand.",
                )
            }
    }

    // ---------------------------------------------------------- conditions

    private fun evaluateCondition(
        condition: Condition,
        context: ContextSnapshot,
        freshness: FreshnessPolicy,
    ): Reason = when (condition) {
        is Condition.DaysOfWeek -> when (val day = context.localDay) {
            is ContextValue.Unknown -> Reason(
                ReasonCode.DAY_UNKNOWN,
                Truth.UNKNOWN,
                "The day could not be read (${day.reason.describe()}).",
            )

            is ContextValue.Known -> if (day.value in condition.days) {
                Reason(ReasonCode.DAY_IN_SET, Truth.MATCH, "${day.value.full()} is included.")
            } else {
                Reason(
                    ReasonCode.DAY_NOT_IN_SET,
                    Truth.NO_MATCH,
                    "${day.value.full()} is outside ${condition.days.describe()}.",
                )
            }
        }

        is Condition.TimeWindow -> when (val time = context.localTime) {
            is ContextValue.Unknown -> Reason(
                ReasonCode.TIME_UNKNOWN,
                Truth.UNKNOWN,
                "The local time could not be read (${time.reason.describe()}).",
            )

            is ContextValue.Known -> if (condition.contains(time.value)) {
                Reason(
                    ReasonCode.TIME_IN_WINDOW,
                    Truth.MATCH,
                    "${time.value} is within ${condition.describe()}.",
                )
            } else {
                Reason(
                    ReasonCode.TIME_OUTSIDE_WINDOW,
                    Truth.NO_MATCH,
                    "${time.value} is outside ${condition.describe()}.",
                )
            }
        }

        is Condition.ChargingState -> {
            val wanted = condition.charging
            when (val charging = context.charging.freshened(context.nowMillis, freshness.chargingMaxAgeMillis)) {
                is ContextValue.Unknown -> Reason(
                    ReasonCode.CHARGING_UNKNOWN,
                    Truth.UNKNOWN,
                    "Charging state could not be read (${charging.reason.describe()}).",
                )

                is ContextValue.Known -> if (charging.value == wanted) {
                    Reason(
                        ReasonCode.CHARGING_AS_REQUIRED,
                        Truth.MATCH,
                        "The phone is ${charging.value.chargingWord()}, as required.",
                    )
                } else {
                    Reason(
                        ReasonCode.CHARGING_NOT_AS_REQUIRED,
                        Truth.NO_MATCH,
                        "The phone is ${charging.value.chargingWord()}, but this cue needs it ${wanted.chargingWord()}.",
                    )
                }
            }
        }
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

private fun UnknownReason.describe(): String = when (this) {
    UnknownReason.PERMISSION_DENIED -> "permission denied"
    UnknownReason.ADAPTER_UNAVAILABLE -> "the adapter was unavailable"
    UnknownReason.NEVER_OBSERVED -> "it was never observed"
    UnknownReason.STALE -> "the reading was too old to trust"
    UnknownReason.REDACTED_BY_OS -> "the system withheld it"
}

private fun Boolean.chargingWord(): String = if (this) "charging" else "not charging"

private fun Day.full(): String = when (this) {
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
