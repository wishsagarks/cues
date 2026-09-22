package com.cues.core.signals

import com.cues.core.compile.Finding
import com.cues.core.compile.Severity
import com.cues.core.eval.Reason
import com.cues.core.eval.ReasonCode
import com.cues.core.eval.Truth
import com.cues.core.model.Capability
import com.cues.core.model.Day
import com.cues.core.model.EndCondition
import com.cues.core.model.EventKind
import com.cues.core.model.Session
import com.cues.core.model.Trigger
import com.cues.core.model.TriggerEvent
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.reflect.KClass

object AtTimeKit : TriggerKit<Trigger.AtTime> {
    override val type: KClass<Trigger.AtTime> = Trigger.AtTime::class
    override val adapterKey = "time"
    override val eventKinds = setOf(EventKind.TIME_REACHED)

    override fun match(trigger: Trigger.AtTime, event: TriggerEvent): Reason {
        if (event.kind != EventKind.TIME_REACHED) {
            return Reason(
                ReasonCode.TIME_TRIGGER_KIND_MISMATCH, Truth.NO_MATCH,
                "Event was ${event.kind}, this cue listens for TIME_REACHED.",
            )
        }
        if (trigger.zoneId != "system" && event.zoneId != null && event.zoneId != trigger.zoneId) {
            return Reason(
                ReasonCode.TIME_TRIGGER_ZONE_MISMATCH, Truth.NO_MATCH,
                "The event used time zone ${event.zoneId}, but this cue uses ${trigger.zoneId}.",
            )
        }
        val actualDay = event.localTime?.let { dayFrom(event.atMillis, trigger.zoneId) }
        if (trigger.days.isNotEmpty() && actualDay !in trigger.days) {
            return Reason(
                ReasonCode.TIME_TRIGGER_DAY_MISMATCH, Truth.NO_MATCH,
                "The time was reached on ${actualDay}, outside ${trigger.days}.",
            )
        }
        return Reason(
            ReasonCode.TIME_TRIGGER_MATCHED, Truth.MATCH,
            "It is ${trigger.time} in ${trigger.zoneId}.",
        )
    }

    override fun reverses(trigger: Trigger.AtTime, event: TriggerEvent) = false

    override fun semanticForm(trigger: Trigger.AtTime) =
        "atTime:${trigger.time}:days=" + trigger.days.sortedBy { it.ordinal }.joinToString(",") +
            ":zone=${trigger.zoneId}"

    override fun validate(trigger: Trigger.AtTime) = zoneFinding("trigger.zoneId", trigger.zoneId)
    override fun capabilities(trigger: Trigger.AtTime) = setOf(Capability.EXACT_ALARM)
    override fun describe(trigger: Trigger.AtTime) = "at ${trigger.time} (${trigger.zoneId})"
    override fun noun(trigger: Trigger.AtTime) = "the scheduled time"

    override fun rehearsalEvents(trigger: Trigger.AtTime, atMillis: Long) = listOf(
        TriggerEvent(
            EventKind.TIME_REACHED,
            atMillis,
            localTime = trigger.time,
            zoneId = trigger.zoneId,
            provenance = com.cues.core.model.EventProvenance.REHEARSAL,
        ),
    )
}

object TriggerReversedKit : EndKit<EndCondition.TriggerReversed> {
    override val type: KClass<EndCondition.TriggerReversed> = EndCondition.TriggerReversed::class
    override fun semanticForm(end: EndCondition.TriggerReversed) = "triggerReversed"
    override fun describe(end: EndCondition.TriggerReversed) = "when the trigger goes away"
    override fun reviewText(end: EndCondition.TriggerReversed) = "goes away"
}

object DurationEndKit : EndKit<EndCondition.Duration> {
    override val type: KClass<EndCondition.Duration> = EndCondition.Duration::class
    override fun semanticForm(end: EndCondition.Duration) = "duration:${end.minutes}"
    override fun validate(end: EndCondition.Duration) = if (end.minutes <= 0) {
        listOf(Finding(Severity.ERROR, "endConditions.duration", "An ending duration must be positive."))
    } else emptyList()
    override fun describe(end: EndCondition.Duration) = "after ${end.minutes} minutes"
    override fun reviewText(end: EndCondition.Duration) = "${end.minutes} minutes have passed"
    override fun schedule(end: EndCondition.Duration, session: Session) =
        session.startedAtMillis + end.minutes * 60_000L
}

object ManualStopKit : EndKit<EndCondition.ManualStop> {
    override val type: KClass<EndCondition.ManualStop> = EndCondition.ManualStop::class
    override fun semanticForm(end: EndCondition.ManualStop) = "manualStop"
    override fun describe(end: EndCondition.ManualStop) = "when you stop it"
    override fun reviewText(end: EndCondition.ManualStop) = "you stop it"
}

object AtTimeEndKit : EndKit<EndCondition.AtTime> {
    override val type: KClass<EndCondition.AtTime> = EndCondition.AtTime::class

    override fun semanticForm(end: EndCondition.AtTime) =
        "atTime:${end.time}:days=" + end.days.sortedBy { it.ordinal }.joinToString(",") +
            ":zone=${end.zoneId}"

    override fun validate(end: EndCondition.AtTime) = zoneFinding("endConditions.atTime.zoneId", end.zoneId)
    override fun capabilities(end: EndCondition.AtTime) = setOf(Capability.EXACT_ALARM)
    override fun describe(end: EndCondition.AtTime) =
        "at ${end.time} (${end.zoneId})" + if (end.days.isEmpty()) " (next occurrence)" else ""
    override fun reviewText(end: EndCondition.AtTime) = "${end.time} arrives in ${end.zoneId}"

    override fun schedule(end: EndCondition.AtTime, session: Session): Long? {
        val zone = zone(end.zoneId, session.observedInputs.zoneId) ?: return null
        val started = Instant.ofEpochMilli(session.startedAtMillis).atZone(zone)
        val wantedDays = end.days.ifEmpty { Day.entries.toSet() }
        for (offset in 0..7) {
            val date = started.toLocalDate().plusDays(offset.toLong())
            val candidate = ZonedDateTime.of(
                date,
                java.time.LocalTime.of(end.time.hour, end.time.minute),
                zone,
            )
            if (candidate.toInstant().toEpochMilli() > session.startedAtMillis &&
                date.dayOfWeek.toCuesDay() in wantedDays
            ) {
                return candidate.toInstant().toEpochMilli()
            }
        }
        return null
    }
}

private fun zoneFinding(field: String, zoneId: String): List<Finding> = try {
    if (zoneId == "system") ZoneId.systemDefault() else ZoneId.of(zoneId)
    emptyList()
} catch (_: Exception) {
    listOf(Finding(Severity.ERROR, field, "Use a valid visible time zone, not '${zoneId}'."))
}

private fun zone(requested: String, fallback: String): ZoneId? = runCatching {
    if (requested == "system") {
        if (fallback == "system") ZoneId.systemDefault() else ZoneId.of(fallback)
    } else ZoneId.of(requested)
}.getOrNull()

private fun dayFrom(atMillis: Long, zoneId: String): Day =
    Instant.ofEpochMilli(atMillis).atZone(runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault()))
        .dayOfWeek.toCuesDay()

private fun java.time.DayOfWeek.toCuesDay(): Day = when (this) {
    java.time.DayOfWeek.MONDAY -> Day.MON
    java.time.DayOfWeek.TUESDAY -> Day.TUE
    java.time.DayOfWeek.WEDNESDAY -> Day.WED
    java.time.DayOfWeek.THURSDAY -> Day.THU
    java.time.DayOfWeek.FRIDAY -> Day.FRI
    java.time.DayOfWeek.SATURDAY -> Day.SAT
    java.time.DayOfWeek.SUNDAY -> Day.SUN
}
