package com.cues.app.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cues.app.CuesApplication
import com.cues.core.model.EventKind
import com.cues.core.model.TriggerEvent

class TimeTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ROUTINE_ID) ?: return
        val app = context.applicationContext as CuesApplication
        val routine = app.cueService.list().firstOrNull { it.id == id } ?: return
        val now = System.currentTimeMillis()
        app.monitoring.recordEvent("time", now)
        GraceScheduler.apply(app, app.cueService.onDeviceEvent(
            TriggerEvent(EventKind.TIME_REACHED, now), charging = Readings.charging(app, now), wifi = WifiReadings.current(app, now),
        ))
        // This one-shot alarm just consumed itself; its trigger data is
        // unchanged, so without this the adapter's own diff would treat it
        // as still current and skip rescheduling its next occurrence.
        app.timeAdapter.fired(id)
        app.adapterSupervisor.sync(app.cueService.list().filter { it.status == com.cues.core.model.RoutineStatus.ARMED })
    }
    companion object { const val EXTRA_ROUTINE_ID = "com.cues.android.EXTRA_ROUTINE_ID" }
}
