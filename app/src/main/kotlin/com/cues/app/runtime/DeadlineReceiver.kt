package com.cues.app.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.cues.app.CuesApplication

/**
 * Fires when a session's exact alarm goes off.
 *
 * Carries only the session id — see [com.cues.core.CueService.onDeadline]'s
 * doc for why that's deliberately all a deadline alarm needs to know. The
 * routine, the actual release of owned resources and the receipt are all
 * `:core`'s job from here.
 */
class DeadlineReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return

        val app = context.applicationContext as CuesApplication
        val result = app.cueService.onDeadline(sessionId)
        // Idempotent even if no grace window was ever pending for this
        // session — cancelling an alarm that was never scheduled is a no-op.
        GraceScheduler.cancel(context, sessionId)
        Log.i(TAG, "deadline for $sessionId -> $result")
    }

    companion object {
        const val EXTRA_SESSION_ID = "com.cues.android.EXTRA_SESSION_ID"
        private const val TAG = "CuesSession"
    }
}
