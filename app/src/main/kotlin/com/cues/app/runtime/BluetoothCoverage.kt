package com.cues.app.runtime

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Reads which bonded device ids Android currently reports as connected, for
 * [com.cues.core.CueService.checkBluetoothCoverage] (R6, task 4.6).
 *
 * Deliberately narrow: [BluetoothManager.getConnectedDevices] only answers
 * for [BluetoothProfile.GATT], the one profile constant that call is
 * documented to accept. Classic-audio-only earbuds that never expose a GATT
 * service (no battery/ANC characteristic) would not show up here even while
 * genuinely connected — see CLEANUP.md. A false coverage-gap report is the
 * failure this would cause; the check is still net-useful because it never
 * fires unless the *session's own* store already believes a session is live,
 * so it only narrows an existing suspicion, but this gap is why R6 stays
 * open until it is exercised on the loaner with real earbuds.
 */
object BluetoothCoverage {

    fun currentlyConnectedDeviceIds(context: Context): Set<String> {
        if (!hasBluetoothConnect(context)) return emptySet()
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: return emptySet()
        return try {
            manager.getConnectedDevices(BluetoothProfile.GATT).map { it.address }.toSet()
        } catch (e: SecurityException) {
            emptySet()
        }
    }

    private fun hasBluetoothConnect(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
}
