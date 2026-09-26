package com.cues.app.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cues.app.CuesApplication
import com.cues.core.model.EventKind
import com.cues.core.model.LocalTimeOfDay
import com.cues.core.model.Trigger
import com.cues.core.model.TriggerEvent
import java.time.Instant
import java.time.ZoneId

class TimeTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ROUTINE_ID) ?: return
        val app = context.applicationContext as CuesApplication
        val routine = app.cueService.list().firstOrNull { it.id == id } ?: return
        val now = System.currentTimeMillis()
        val trigger = routine.trigger as? Trigger.AtTime
        val zone = trigger?.let {
            runCatching { if (it.zoneId == "system") ZoneId.systemDefault() else ZoneId.of(it.zoneId) }
                .getOrElse { ZoneId.systemDefault() }
        }
        val local = zone?.let { Instant.ofEpochMilli(now).atZone(it) }
        app.monitoring.recordEvent("time", now)
        GraceScheduler.apply(app, app.cueService.onDeviceEvent(
            TriggerEvent(
                kind = EventKind.TIME_REACHED,
                atMillis = now,
                localTime = local?.let { LocalTimeOfDay(it.hour, it.minute) },
                zoneId = trigger?.zoneId,
            ),
            charging = Readings.charging(app, now),
            wifi = WifiReadings.current(app, now),
        ))
        // This one-shot alarm just consumed itself; its trigger data is
        // unchanged, so without this the adapter's own diff would treat it
        // as still current and skip rescheduling its next occurrence.
        app.timeAdapter.fired(id)
        app.adapterSupervisor.sync(app.cueService.list().filter { it.status == com.cues.core.model.RoutineStatus.ARMED })
    }
    companion object { const val EXTRA_ROUTINE_ID = "com.cues.android.EXTRA_ROUTINE_ID" }
}
