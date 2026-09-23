package com.cues.app.runtime

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.cues.core.model.Routine
import com.cues.core.model.Trigger
import com.cues.core.ports.ListenerHealth
import com.cues.core.ports.SignalAdapter
import java.time.ZonedDateTime

/** Schedules one next occurrence per armed at-time cue and reschedules after delivery. */
class TimeAdapter(private val context: Context) : SignalAdapter {
    override val key = "time"
    private var detail: String? = null
    private val pendingByRoutine = mutableMapOf<String, PendingIntent>()
    private val scheduledFor = mutableMapOf<String, Trigger.AtTime>()

    /**
     * `sync()` calls this on nearly every screen resume and refresh, not only
     * when the armed set actually changes — cancelling and rescheduling every
     * alarm every time would mean needless AlarmManager churn on almost every
     * UI interaction. Only routines that are newly armed, no longer armed, or
     * whose trigger actually changed are touched.
     */
    override fun start(armed: List<Routine>) {
        val wanted = armed.mapNotNull { routine ->
            (routine.trigger as? Trigger.AtTime)?.let { routine.id to it }
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
     * Called by [TimeTriggerReceiver] right after its one-shot alarm fires.
     * The trigger's own data (time/days/zone) hasn't changed, so the diff in
     * [start] would otherwise treat this routine as still current and skip
     * rescheduling it — clearing our bookkeeping (not the OS alarm, which
     * already consumed itself on firing) makes the next `start` reschedule
     * it for its next occurrence instead.
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

    private fun schedule(routine: Routine, trigger: Trigger.AtTime) {
        val zone = if (trigger.zoneId == "system") java.time.ZoneId.systemDefault() else java.time.ZoneId.of(trigger.zoneId)
        var next = ZonedDateTime.now(zone).withHour(trigger.time.hour).withMinute(trigger.time.minute).withSecond(0).withNano(0)
        if (!next.isAfter(ZonedDateTime.now(zone))) next = next.plusDays(1)
        while (trigger.days.isNotEmpty() && next.dayOfWeek.name.take(3) !in trigger.days.map { it.name }.toSet()) next = next.plusDays(1)
        val intent = Intent(context, TimeTriggerReceiver::class.java).putExtra(TimeTriggerReceiver.EXTRA_ROUTINE_ID, routine.id)
        val pending = PendingIntent.getBroadcast(context, routine.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        pendingByRoutine[routine.id] = pending
        scheduledFor[routine.id] = trigger
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try { manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pending) }
        catch (e: SecurityException) { manager.set(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pending); detail = "Exact alarm unavailable; using inexact delivery." }
    }
}
