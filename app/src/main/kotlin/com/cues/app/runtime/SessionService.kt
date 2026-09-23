package com.cues.app.runtime

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.cues.app.MainActivity
import com.cues.app.R
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
        sessionId = intent?.getStringExtra(EXTRA_SESSION_ID) ?: sessionId
        deadlineMillis = intent?.getLongExtra(EXTRA_DEADLINE_MILLIS, deadlineMillis) ?: deadlineMillis

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

        // Not START_STICKY: if this process dies, the exact alarm still fires
        // (AlarmManager survives independently of the service), and the
        // notification is restored — or the session is reconciled as
        // expired — by BootReceiver/onBoot, not by the OS blindly restarting
        // a service with no session context to restart it with.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(getString(R.string.session_notification_title))
        .setContentText(remainingText())
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(openAppIntent())
        .addAction(0, getString(R.string.session_notification_stop), stopIntent())
        .build()

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
        private const val CHANNEL_ID = "cues_session"
        private const val NOTIFICATION_ID = 1
    }
}
