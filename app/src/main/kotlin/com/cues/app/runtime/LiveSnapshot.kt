package com.cues.app.runtime

import android.content.Context
import com.cues.core.context.SnapshotBuilder
import java.time.ZoneId

/** One honest, point-in-time context snapshot for a dry run. */
object LiveSnapshot {
    fun current(context: Context, atMillis: Long = System.currentTimeMillis()) = SnapshotBuilder.build(
        nowMillis = atMillis,
        zoneId = ZoneId.systemDefault(),
        charging = Readings.charging(context, atMillis),
        batteryPercent = Readings.batteryPercent(context, atMillis),
        audioOutputs = AudioOutputAdapter.currentOutputs(context, atMillis),
        connectedDeviceIds = com.cues.core.model.ContextValue.Known(
            BluetoothCoverage.currentlyConnectedDeviceIds(context),
            com.cues.core.model.ContextSource.BLUETOOTH_ADAPTER,
            atMillis,
        ),
        wifi = WifiReadings.current(context, atMillis),
    )
}
