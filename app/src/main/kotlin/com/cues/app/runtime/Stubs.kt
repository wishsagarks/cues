package com.cues.app.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.Service
import android.content.IntentFilter
import android.os.IBinder
import android.util.Log

/**
 * Event adapters and the session service.
 *
 * All stubs. They exist so the manifest is complete and the shape of the
 * runtime is visible, and so the event's work is filling in known holes rather
 * than deciding structure at 2am.
 *
 * Each one logs what it received, which makes the first Saturday task concrete:
 * connect the earbuds, watch logcat through `./dev l`, and find out what this
 * phone actually delivers while the app is backgrounded. The FDD is explicit
 * that a receiver declaration is not proof of delivery, and this is how that
 * gets checked.
 */

class BluetoothReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Device identity arrives as an extra and needs BLUETOOTH_CONNECT to
        // read. Resolve it to the routine's stored address rather than matching
        // on a display name, which is neither stable nor unique.
        Log.i(TAG, "bluetooth: ${intent.action}")
    }

    private companion object { const val TAG = "CuesSession" }
}

class PowerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Note the distinction the FDD draws: this is a transition, and the
        // current charging state is a separate reading with its own freshness.
        Log.i(TAG, "power: ${intent.action}")
    }

    private companion object { const val TAG = "CuesSession" }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Reconciliation, never replay: an expired session cleans up, it does
        // not start its actions again. SessionEngine.reconcile already holds
        // that rule; this only has to call it.
        Log.i(TAG, "boot: ${intent.action}")
    }

    private companion object { const val TAG = "CuesSession" }
}

class DeadlineReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "deadline: ${intent.action}")
    }

    private companion object { const val TAG = "CuesSession" }
}

/**
 * Hosts a running session so the countdown is visible and the process is less
 * likely to be reclaimed mid-session.
 */
class SessionService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("CuesSession", "session service start")
        // Must call startForeground promptly once implemented, or the OS kills
        // it. The notification is the session's visible presence, not chrome.
        return START_NOT_STICKY
    }
}
