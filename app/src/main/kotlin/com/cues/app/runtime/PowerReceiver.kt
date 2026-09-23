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
        val kind = when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> EventKind.POWER_CONNECTED
            Intent.ACTION_POWER_DISCONNECTED -> EventKind.POWER_DISCONNECTED
            else -> return
        }

        val atMillis = System.currentTimeMillis()
        val event = TriggerEvent(kind = kind, atMillis = atMillis)
        val charging = Readings.charging(context, atMillis)

        val app = context.applicationContext as CuesApplication
        app.monitoring.recordEvent(MonitoringRepository.POWER, atMillis)
        val results = app.cueService.onDeviceEvent(event, charging = charging)
        GraceScheduler.apply(context, results)
        // R6: an unrelated event is a free opportunity to notice a Bluetooth
        // disconnect that was missed while nothing else happened to check.
        app.cueService.checkBluetoothCoverage(BluetoothCoverage.currentlyConnectedDeviceIds(context))
    }
}
