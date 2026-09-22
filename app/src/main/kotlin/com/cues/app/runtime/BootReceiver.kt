package com.cues.app.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.cues.app.CuesApplication

/**
 * Reconciles persisted sessions after a reboot.
 *
 * Delegates entirely to [com.cues.core.CueService.onBoot], which in turn
 * calls [com.cues.core.session.SessionEngine.reconcile]: an expired session
 * is cleaned up, never replayed. There is no logic to get right here beyond
 * making the call — which is exactly the point of keeping this decision in
 * `:core`, tested, rather than in a receiver nobody exercises until reboot
 * day.
 *
 * A force-stopped app never receives BOOT_COMPLETED at all. That is a real,
 * documented limitation (FDD: "a force-stopped app cannot promise reliable
 * unattended recovery"), not something this receiver can work around.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val app = context.applicationContext as CuesApplication
        val reconciled = app.cueService.onBoot()
        Log.i(TAG, "reconciled ${reconciled.size} session(s) after boot")
    }

    private companion object {
        const val TAG = "CuesSession"
    }
}
