package com.cues.app.runtime

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.cues.app.MainActivity
import com.cues.app.R
import com.cues.app.widget.NowNextWidget
import java.util.concurrent.TimeUnit

/**
 * Hosts a running session's visible presence: a foreground notification
 * showing what's active and when it ends.
 *
 * WRITTEN AGAINST A REAL API, VERIFIED ON NOTHING. Whether this survives the
 * OEM battery policy long enough to matter is a real device question
 * (CLEANUP.md CL-06), not something a foreground service type resolves on
 * its own.
 *
 * This service does not decide anything and does not itself end the session.
 * The actual deadline is a separate exact alarm [AndroidActionExecutor]
 * schedules directly with [android.app.AlarmManager] — duplicating that
 * here, in a component the OS can kill and restart on its own schedule,
 * is exactly the "depending on a clock app to finish precisely" mistake the
 * FDD warns against. This only has to remain visible for as long as it's
 * alive; recovering after it's killed is [BootReceiver]'s job.
 */
class SessionService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var deadlineMillis: Long = 0
    private var startedAtMillis: Long = 0
    private var sessionId: String = ""

    private val tick = object : Runnable {
        override fun run() {
            updateNotification()
            handler.postDelayed(this, TimeUnit.SECONDS.toMillis(30))
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SESSION) {
            if (intent.getStringExtra(EXTRA_SESSION_ID) == sessionId) stopSelf()
            return START_NOT_STICKY
        }
        sessionId = intent?.getStringExtra(EXTRA_SESSION_ID) ?: sessionId
        deadlineMillis = intent?.getLongExtra(EXTRA_DEADLINE_MILLIS, deadlineMillis) ?: deadlineMillis
        startedAtMillis = intent?.getLongExtra(EXTRA_STARTED_AT_MILLIS, startedAtMillis)
            ?.takeIf { it > 0 } ?: startedAtMillis.takeIf { it > 0 } ?: System.currentTimeMillis()

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        handler.removeCallbacks(tick)
        handler.postDelayed(tick, TimeUnit.SECONDS.toMillis(30))
        NowNextWidget.refresh(this)

        // Not START_STICKY: if this process dies, the exact alarm still fires
        // (AlarmManager survives independently of the service), and the
        // notification is restored — or the session is reconciled as
        // expired — by BootReceiver/onBoot, not by the OS blindly restarting
        // a service with no session context to restart it with.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        NowNextWidget.refresh(this)
        super.onDestroy()
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    /**
     * Redesign plan §5.3: a determinate progress bar plus a real
     * countdown chronometer, colorized in the brand yellow, rather than a
     * plain two-line notification re-typed every 30s. Still no
     * ProgressStyle/Live-Update promotion — that stays out per CL-15's
     * recorded decision; this only restyles the standard notification that
     * already existed.
     */
    private fun buildNotification(): android.app.Notification {
        val now = System.currentTimeMillis()
        val totalMillis = (deadlineMillis - startedAtMillis).coerceAtLeast(1L)
        val elapsedMillis = (now - startedAtMillis).coerceIn(0L, totalMillis)
        val progressPercent = ((elapsedMillis * 100) / totalMillis).toInt().coerceIn(0, 100)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.session_notification_title))
            .setContentText(remainingText())
            .setSmallIcon(R.drawable.ic_stat_cue)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setColor(Color.parseColor("#FFE600"))
            .setColorized(true)
            .setProgress(100, progressPercent, false)
            .apply {
                if (deadlineMillis > 0) {
                    setUsesChronometer(true)
                    setChronometerCountDown(true)
                    setWhen(deadlineMillis)
                }
            }
            .setContentIntent(openAppIntent())
            .addAction(0, getString(R.string.session_notification_stop), stopIntent())
            .build()
    }

    /**
     * The notification's own stop action (4.4). Broadcasts to
     * [StopSessionReceiver] rather than calling [com.cues.core.CueService]
     * directly — this service only hosts the visible countdown, it never
     * makes lifecycle decisions itself.
     */
    private fun stopIntent(): PendingIntent {
        val intent = Intent(this, StopSessionReceiver::class.java)
            .putExtra(StopSessionReceiver.EXTRA_SESSION_ID, sessionId)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(this, sessionId.hashCode(), intent, flags)
    }

    private fun remainingText(): String {
        val remainingMinutes = ((deadlineMillis - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0)
        return getString(R.string.session_notification_remaining, remainingMinutes)
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(this, 0, intent, flags)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.session_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val EXTRA_SESSION_ID = "com.cues.android.EXTRA_SESSION_ID"
        const val EXTRA_DEADLINE_MILLIS = "com.cues.android.EXTRA_DEADLINE_MILLIS"
        const val EXTRA_STARTED_AT_MILLIS = "com.cues.android.EXTRA_STARTED_AT_MILLIS"
        const val ACTION_STOP_SESSION = "com.cues.android.action.STOP_SESSION"
        private const val CHANNEL_ID = "cues_session"
        private const val NOTIFICATION_ID = 1
    }
}
