package com.cues.app.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.cues.app.CuesApplication

/**
 * Fires when a pending disconnect's reconnect-grace window elapses without a
 * reconnect.
 *
 * Mirrors [DeadlineReceiver]: carries only the session id, and every actual
 * decision — is this session still EXIT_PENDING, has the window really
 * passed — is [com.cues.core.session.SessionEngine.onGraceElapsed]'s job, not
 * this receiver's.
 */
class GraceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return

        val app = context.applicationContext as CuesApplication
        val result = app.cueService.onGraceElapsed(sessionId)
        Log.i(TAG, "grace elapsed for $sessionId -> $result")
    }

    companion object {
        const val EXTRA_SESSION_ID = "com.cues.android.EXTRA_SESSION_ID"
        private const val TAG = "CuesSession"
    }
}
