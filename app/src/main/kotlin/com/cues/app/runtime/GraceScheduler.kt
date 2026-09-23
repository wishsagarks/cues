package com.cues.app.runtime

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.cues.core.session.EngineResult

/**
 * Schedules and cancels the reconnect-grace alarm.
 *
 * [com.cues.core.session.SessionEngine] decides *whether* a disconnect
 * schedules an exit or resumes one already pending — see
 * [EngineResult.ExitScheduled] and [EngineResult.ExitCancelled] — but it has
 * no way to make anything fire later; that's this file's job, the same
 * division of labour [AndroidActionExecutor] already has with the deadline
 * alarm. Without this, a grace window would only ever end when some other
 * event happened to arrive afterward.
 */
object GraceScheduler {

    fun schedule(context: Context, sessionId: String, atMillis: Long) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = pendingIntent(context, sessionId)
        try {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent)
        } catch (e: SecurityException) {
            // The grace window is a short, forgiving margin, not a promise
            // like the session deadline is — an inexact fallback still ends
            // it, just possibly a little late, which is a safe direction to
            // be wrong in here. Never leave the reconnect grace unscheduled
            // just because exact-alarm access was revoked separately.
            alarms.set(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent)
        }
    }

    fun cancel(context: Context, sessionId: String) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarms.cancel(pendingIntent(context, sessionId))
    }

    /**
     * Applies whatever [EngineResult]s a dispatch produced to the grace
     * alarm: schedule it, cancel it on a reconnect, or cancel it because the
     * session ended some other way (deadline, manual stop) while it was
     * pending.
     */
    fun apply(context: Context, results: List<EngineResult>) {
        results.forEach { result ->
            when (result) {
                is EngineResult.ExitScheduled -> schedule(context, result.session.id, result.atMillis)
                is EngineResult.ExitCancelled -> cancel(context, result.session.id)
                is EngineResult.Ended -> cancel(context, result.session.id)
                else -> Unit
            }
        }
    }

    private fun pendingIntent(context: Context, sessionId: String): PendingIntent {
        val intent = Intent(context, GraceReceiver::class.java)
            .putExtra(GraceReceiver.EXTRA_SESSION_ID, sessionId)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        // A distinct request-code namespace from the deadline alarm's, so the
        // two PendingIntents for the same session never collide.
        return PendingIntent.getBroadcast(context, ("grace-$sessionId").hashCode(), intent, flags)
    }
}
