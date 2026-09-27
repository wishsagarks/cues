package com.cues.core.signals

import com.cues.core.compile.Finding
import com.cues.core.compile.Severity
import com.cues.core.eval.Reason
import com.cues.core.eval.ReasonCode
import com.cues.core.eval.Truth
import com.cues.core.model.Capability
import com.cues.core.model.EventKind
import com.cues.core.model.EventProvenance
import com.cues.core.model.RecurrenceUnit
import com.cues.core.model.Trigger
import com.cues.core.model.TriggerEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.reflect.KClass

/**
 * "Every N days/months/years", unlike [Trigger.AtTime]'s recurring
 * weekly time-of-day. Matching is delegated to [RecurrenceCalculator] so the
 * same day/month/year arithmetic (including end-of-month clamping) backs both
 * the evaluator's match check and `:app`'s alarm-rescheduling adapter.
 */
object RecurringIntervalKit : TriggerKit<Trigger.RecurringInterval> {
    override val type: KClass<Trigger.RecurringInterval> = Trigger.RecurringInterval::class
    override val adapterKey = "recurring"
    override val eventKinds = setOf(EventKind.RECURRING_REACHED)

    override fun match(trigger: Trigger.RecurringInterval, event: TriggerEvent): Reason {
        if (event.kind != EventKind.RECURRING_REACHED) {
            return Reason(
                ReasonCode.RECURRING_TRIGGER_KIND_MISMATCH, Truth.NO_MATCH,
                "Event was ${event.kind}, this cue listens for RECURRING_REACHED.",
            )
        }
        val zone = zone(trigger.zoneId)
        val candidate = Instant.ofEpochMilli(event.atMillis).atZone(zone).toLocalDate()
        val anchor = LocalDate.ofEpochDay(trigger.anchorEpochDay)
        val due = RecurrenceCalculator.isDue(anchor, trigger.intervalValue, trigger.intervalUnit, candidate)
        return if (due) {
            Reason(
                ReasonCode.RECURRING_TRIGGER_MATCHED, Truth.MATCH,
                "It has been ${trigger.intervalValue} ${trigger.intervalUnit.word()} since this cue was armed.",
            )
        } else {
            Reason(
                ReasonCode.RECURRING_TRIGGER_NOT_DUE, Truth.NO_MATCH,
                "$candidate is not one of this cue's every-${trigger.intervalValue}-${trigger.intervalUnit.word()} dates.",
            )
        }
    }

    override fun reverses(trigger: Trigger.RecurringInterval, event: TriggerEvent) = false

    override fun semanticForm(trigger: Trigger.RecurringInterval) =
        "recurringInterval:${trigger.intervalValue}:${trigger.intervalUnit.name}:anchor=${trigger.anchorEpochDay}" +
            ":time=${trigger.time}:zone=${trigger.zoneId}"

    override fun validate(trigger: Trigger.RecurringInterval): List<Finding> = buildList {
        if (trigger.intervalValue < 1) {
            add(Finding(Severity.ERROR, "trigger.intervalValue", "A recurring interval must be at least 1."))
        }
        if (trigger.zoneId != "system") {
            runCatching { ZoneId.of(trigger.zoneId) }.onFailure {
                add(Finding(Severity.ERROR, "trigger.zoneId", "Use a valid visible time zone, not '${trigger.zoneId}'."))
            }
        }
    }

    override fun capabilities(trigger: Trigger.RecurringInterval) = setOf(Capability.EXACT_ALARM)

    override fun describe(trigger: Trigger.RecurringInterval) =
        "every ${trigger.intervalValue} ${trigger.intervalUnit.word()} at ${trigger.time}"

    override fun noun(trigger: Trigger.RecurringInterval) = "the scheduled reminder"

    override fun rehearsalEvents(trigger: Trigger.RecurringInterval, atMillis: Long) = listOf(
        TriggerEvent(
            EventKind.RECURRING_REACHED,
            atMillis,
            localTime = trigger.time,
            zoneId = trigger.zoneId,
            provenance = EventProvenance.REHEARSAL,
        ),
    )

    private fun zone(zoneId: String): ZoneId =
        if (zoneId == "system") ZoneId.systemDefault() else runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault())

    private fun RecurrenceUnit.word(): String = when (this) {
        RecurrenceUnit.DAYS -> "days"
        RecurrenceUnit.MONTHS -> "months"
        RecurrenceUnit.YEARS -> "years"
    }
}
