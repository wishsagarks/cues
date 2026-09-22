package com.cues.app.runtime

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.cues.app.CuesApplication
import com.cues.core.model.EventKind
import com.cues.core.model.TriggerEvent

/**
 * The device connection/disconnection adapter.
 *
 * WRITTEN AGAINST A REAL API, VERIFIED ON NOTHING. Whether ACL_CONNECTED and
 * ACL_DISCONNECTED actually deliver to a manifest-registered receiver while
 * this app is backgrounded on OriginOS 7 is exactly what the FDD calls the
 * "receiver declaration is not proof of delivery" problem — the first thing
 * to prove on the loaner, via `./dev l`.
 *
 * No connection-session identifier is synthesized here. [TriggerEvent] leaves
 * it null and [com.cues.core.session.SessionEngine] falls back to keying
 * admission on the device id, which already collapses Android's habit of
 * delivering the same physical connection more than once — that's the point
 * of the fallback, not a gap this receiver needs to fill.
 */
class BluetoothReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val kind = when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> EventKind.BLUETOOTH_CONNECTED
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> EventKind.BLUETOOTH_DISCONNECTED
            else -> return
        }

        if (!hasBluetoothConnect(context)) {
            Log.w(TAG, "BLUETOOTH_CONNECT not granted; cannot resolve the connecting device")
            return
        }

        @Suppress("DEPRECATION") // EXTRA_DEVICE's typed getParcelableExtra needs API 33+; this covers both.
        val device: BluetoothDevice = (
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }
            ) ?: return

        val atMillis = System.currentTimeMillis()
        val event = TriggerEvent(kind = kind, atMillis = atMillis, deviceId = device.address)
        val charging = Readings.charging(context, atMillis)

        val app = context.applicationContext as CuesApplication
        app.cueService.onDeviceEvent(event, charging = charging)
    }

    private fun hasBluetoothConnect(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "CuesSession"
    }
}
