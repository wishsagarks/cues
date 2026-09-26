package com.cues.app.runtime

import android.content.Context
import android.content.Intent
import android.os.Build
import com.cues.core.model.Routine
import com.cues.core.model.Trigger
import com.cues.core.ports.ListenerHealth
import com.cues.core.ports.SignalAdapter

/**
 * Starts [PlaceWatcherService] only while at least one armed routine's
 * trigger is a place arrival/departure — [com.cues.core.signals.AdapterSupervisor]
 * already filters `armed` to routines whose adapter key matches [key]
 * before calling [start], the same way it does for [WifiAdapter].
 *
 * Re-sent on every arm/disarm rather than started once: Android tolerates a
 * repeat `startForegroundService` call on an already-running service, and
 * [PlaceWatcherService.onStartCommand] uses it to keep its watched-place set
 * in sync with which place-trigger routines are actually armed. See
 * CLEANUP.md CL-13 for why this is foreground-only, not a Play Services
 * `GeofencingClient`.
 */
class PlaceAdapter(private val context: Context) : SignalAdapter {
    override val key = "place"
    private var running = false
    private var detail: String? = "Not armed"

    override fun start(armed: List<Routine>) {
        val placeIds = armed.mapNotNull { (it.trigger as? Trigger.PlaceTransition)?.placeId }.toSet()
        if (placeIds.isEmpty()) {
            stop()
            return
        }
        val intent = Intent(context, PlaceWatcherService::class.java)
            .putExtra(PlaceWatcherService.EXTRA_PLACE_IDS, placeIds.toTypedArray())
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            running = true
            detail = null
        } catch (e: SecurityException) {
            running = false
            detail = e.message ?: "Foreground service start was denied"
        }
    }

    override fun stop() {
        if (!running) return
        context.stopService(Intent(context, PlaceWatcherService::class.java))
        running = false
        detail = "Not armed"
    }

    override fun health() = ListenerHealth(key, running, detail)
}
