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
    private var scheduled = 0
    private var detail: String? = null
    private val pendingByRoutine = mutableMapOf<String, PendingIntent>()

    override fun start(armed: List<Routine>) {
        stop()
        armed.forEach { routine ->
            val trigger = routine.trigger as? Trigger.AtTime ?: return@forEach
            schedule(routine, trigger)
        }
    }

    override fun stop() {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        pendingByRoutine.values.forEach { manager.cancel(it) }
        pendingByRoutine.clear()
        scheduled = 0
    }

    override fun health() = ListenerHealth(key, scheduled > 0, detail ?: "$scheduled occurrence(s) scheduled")

    private fun schedule(routine: Routine, trigger: Trigger.AtTime) {
        val zone = if (trigger.zoneId == "system") java.time.ZoneId.systemDefault() else java.time.ZoneId.of(trigger.zoneId)
        var next = ZonedDateTime.now(zone).withHour(trigger.time.hour).withMinute(trigger.time.minute).withSecond(0).withNano(0)
        if (!next.isAfter(ZonedDateTime.now(zone))) next = next.plusDays(1)
        while (trigger.days.isNotEmpty() && next.dayOfWeek.name.take(3) !in trigger.days.map { it.name }.toSet()) next = next.plusDays(1)
        val intent = Intent(context, TimeTriggerReceiver::class.java).putExtra(TimeTriggerReceiver.EXTRA_ROUTINE_ID, routine.id)
        val pending = PendingIntent.getBroadcast(context, routine.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        pendingByRoutine[routine.id] = pending
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try { manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pending) }
        catch (e: SecurityException) { manager.set(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pending); detail = "Exact alarm unavailable; using inexact delivery." }
        scheduled++
    }
}
