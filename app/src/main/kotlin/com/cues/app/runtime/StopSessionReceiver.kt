package com.cues.app.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.cues.app.CuesApplication

/**
 * The session notification's own stop action (4.4: "manual stop from the
 * notification").
 *
 * A second, independent path to [com.cues.core.CueService.onManualStop]
 * alongside the in-app stop button on [com.cues.app.ui.RoutineDetailScreen] —
 * both exist because the notification is what stays reachable while the app
 * itself is backgrounded, which is the whole point of Sprint 4's "unattended"
 * proof.
 */
class StopSessionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return

        val app = context.applicationContext as CuesApplication
        val result = app.cueService.onManualStop(sessionId)
        GraceScheduler.cancel(context, sessionId)
        Log.i(TAG, "manual stop (notification) for $sessionId -> $result")
    }

    companion object {
        const val EXTRA_SESSION_ID = "com.cues.android.EXTRA_SESSION_ID"
        private const val TAG = "CuesSession"
    }
}
