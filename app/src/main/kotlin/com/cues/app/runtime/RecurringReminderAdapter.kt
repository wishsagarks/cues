package com.cues.app.runtime

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.cues.core.model.Routine
import com.cues.core.model.Trigger
import com.cues.core.ports.ListenerHealth
import com.cues.core.ports.SignalAdapter
import com.cues.core.signals.RecurrenceCalculator
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Schedules one next occurrence per armed [Trigger.RecurringInterval] cue and
 * reschedules after delivery — the "every N days/months/years" idiom this app
 * cannot express with [Trigger.AtTime]'s recurring weekly time-of-day.
 *
 * Mirrors [TimeAdapter] deliberately: same diff-before-touching-AlarmManager
 * discipline, same one-shot-alarm-that-reschedules-itself shape. The only
 * real difference is the date math, delegated entirely to
 * [RecurrenceCalculator] so the adapter and the pure evaluator
 * ([com.cues.core.signals.RecurringIntervalKit]) can never disagree about
 * which day quo is due.
 */
class RecurringReminderAdapter(private val context: Context) : SignalAdapter {
    override val key = "recurring"
    private var detail: String? = null
    private val pendingByRoutine = mutableMapOf<String, PendingIntent>()
    private val scheduledFor = mutableMapOf<String, Trigger.RecurringInterval>()

    override fun start(armed: List<Routine>) {
        val wanted = armed.mapNotNull { routine ->
            (routine.trigger as? Trigger.RecurringInterval)?.let { routine.id to it }
        }.toMap()

        pendingByRoutine.keys.filter { id -> wanted[id] != scheduledFor[id] }.forEach { cancel(it) }

        armed.forEach { routine ->
            val trigger = wanted[routine.id] ?: return@forEach
            if (routine.id !in pendingByRoutine) schedule(routine, trigger)
        }
    }

    override fun stop() {
        pendingByRoutine.keys.toList().forEach { cancel(it) }
    }

    override fun health() =
        ListenerHealth(key, pendingByRoutine.isNotEmpty(), detail ?: "${pendingByRoutine.size} occurrence(s) scheduled")

    /**
     * Called by [RecurringTriggerReceiver] right after its one-shot alarm
     * fires — see [TimeAdapter.fired]'s doc comment for why clearing our
     * bookkeeping (not the OS alarm, already consumed) is what makes the next
     * [start] reschedule this routine's next occurrence instead of skipping
     * it as unchanged.
     */
    fun fired(routineId: String) {
        pendingByRoutine.remove(routineId)
        scheduledFor.remove(routineId)
    }

    private fun cancel(routineId: String) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        pendingByRoutine.remove(routineId)?.let { manager.cancel(it) }
        scheduledFor.remove(routineId)
    }

    private fun schedule(routine: Routine, trigger: Trigger.RecurringInterval) {
        val zone = zoneOf(trigger.zoneId)
        val anchor = java.time.LocalDate.ofEpochDay(trigger.anchorEpochDay)
        val time = LocalTime.of(trigger.time.hour, trigger.time.minute)

        var occurrenceDate = RecurrenceCalculator.nextOccurrence(
            anchor, trigger.intervalValue, trigger.intervalUnit, ZonedDateTime.now(zone).toLocalDate(),
        )
        var next = ZonedDateTime.of(occurrenceDate, time, zone)
        if (!next.isAfter(ZonedDateTime.now(zone))) {
            // Today is (or was) an occurrence day, but its time already
            // passed — the next one is not "tomorrow", it's whatever the
            // interval actually lands on after today.
            occurrenceDate = RecurrenceCalculator.nextOccurrence(
                anchor, trigger.intervalValue, trigger.intervalUnit, occurrenceDate.plusDays(1),
            )
            next = ZonedDateTime.of(occurrenceDate, time, zone)
        }

        val intent = Intent(context, RecurringTriggerReceiver::class.java)
            .putExtra(RecurringTriggerReceiver.EXTRA_ROUTINE_ID, routine.id)
        val pending = PendingIntent.getBroadcast(
            context, routine.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        pendingByRoutine[routine.id] = pending
        scheduledFor[routine.id] = trigger
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pending)
        } catch (e: SecurityException) {
            manager.set(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pending)
            detail = "Exact alarm unavailable; using inexact delivery."
        }
    }

    private fun zoneOf(zoneId: String): ZoneId =
        if (zoneId == "system") ZoneId.systemDefault() else runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault())
}
