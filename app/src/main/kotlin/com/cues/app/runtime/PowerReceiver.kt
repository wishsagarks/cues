package com.cues.app.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cues.app.CuesApplication
import com.cues.core.model.EventKind
import com.cues.core.model.TriggerEvent

/**
 * The charging-transition adapter.
 *
 * ACTION_POWER_CONNECTED/DISCONNECTED are transitions, not a reading — the
 * FDD is explicit about keeping that distinction, which is why the actual
 * charging *state* comes from [Readings.charging] (a separate battery-status
 * query) rather than being inferred from which broadcast just fired. A
 * "power connected" broadcast racing a near-simultaneous unplug could
 * otherwise report a stale state as current.
 */
class PowerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val preferences = context.getSharedPreferences("monitoring", Context.MODE_PRIVATE)
        val kind = when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> {
                if (preferences.getBoolean(KEY_LAST_CHARGING, false) && preferences.contains(KEY_LAST_CHARGING)) return
                EventKind.POWER_CONNECTED
            }
            Intent.ACTION_POWER_DISCONNECTED -> {
                if (!preferences.getBoolean(KEY_LAST_CHARGING, true) && preferences.contains(KEY_LAST_CHARGING)) return
                EventKind.POWER_DISCONNECTED
            }
            Intent.ACTION_BATTERY_CHANGED -> {
                val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
                val charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == android.os.BatteryManager.BATTERY_STATUS_FULL
                val previous = if (!preferences.contains(KEY_LAST_CHARGING)) null
                else preferences.getBoolean(KEY_LAST_CHARGING, false)
                preferences.edit().putBoolean(KEY_LAST_CHARGING, charging).apply()
                // BatteryChanged is the OEM-safe fallback. Establishing the
                // baseline must not replay a cue just because the app process
                // was restarted while the cable was already connected.
                when {
                    previous == null -> return
                    previous == charging -> return
                    charging -> EventKind.POWER_CONNECTED
                    else -> EventKind.POWER_DISCONNECTED
                }
            }
            else -> return
        }

        val atMillis = System.currentTimeMillis()
        val event = TriggerEvent(kind = kind, atMillis = atMillis)
        val charging = Readings.charging(context, atMillis)

        val app = context.applicationContext as CuesApplication
        preferences.edit().putBoolean(KEY_LAST_CHARGING, kind == EventKind.POWER_CONNECTED).apply()
        app.monitoring.recordEvent(MonitoringRepository.POWER, atMillis)
        val results = app.cueService.onDeviceEvent(event, charging = charging)
        GraceScheduler.apply(context, results)
        // R6: an unrelated event is a free opportunity to notice a Bluetooth
        // disconnect that was missed while nothing else happened to check.
        app.cueService.checkBluetoothCoverage(BluetoothCoverage.currentlyConnectedDeviceIds(context))
    }

    companion object {
        private const val KEY_LAST_CHARGING = "last-charging-state"
    }
}
