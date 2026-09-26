package com.cues.app.runtime

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.cues.app.CuesApplication
import com.cues.app.MainActivity
import com.cues.app.R
import com.cues.core.model.EventKind
import com.cues.core.model.Place
import com.cues.core.model.TriggerEvent

/**
 * Detects arrival at / departure from a saved [Place] while its own
 * notification is visible — plain [android.location.LocationManager] behind
 * a foreground service, not a Play Services `GeofencingClient` with
 * `ACCESS_BACKGROUND_LOCATION`. `:app` has no `play-services-location`
 * dependency, that permission needs a two-step consent flow and store
 * disclosure this build doesn't have hours for, and a foreground service can
 * read location continuously on `ACCESS_FINE_LOCATION` alone.
 *
 * WRITTEN AGAINST REAL APIS, VERIFIED ON NOTHING — R13 (delivery under the
 * OEM battery policy, screen off) has not been run on the loaner; see
 * CLEANUP.md CL-13 and docs/DEVICE_MATRIX.md. This survives screen-off and
 * the app being swiped away, the same as [SessionService]; it does not
 * survive the OS killing this process outright, and that is an honest limit
 * to demo around, not a bug to paper over — see docs/DEMO.md's cut-list
 * entry for this feature.
 *
 * Dwell/debounce is hand-rolled here rather than reused from anywhere else
 * in the runtime: [com.cues.core.model.RearmPolicy]'s reconnect grace
 * smooths losing an already-running session — a different problem from
 * deciding whether a raw GPS fix is confident enough to call "arrived" in
 * the first place. The first confirmed read for a place is a silent
 * baseline, never a dispatched transition — the same reasoning
 * [PowerReceiver] applies to its own restart baseline: arming a cue while
 * already standing at the place must not itself look like an arrival.
 */
class PlaceWatcherService : Service() {

    private var locationManager: LocationManager? = null
    private var listener: LocationListener? = null
    private var watchedPlaces: List<Place> = emptyList()

    private val confirmedInside = mutableMapOf<String, Boolean?>()
    private val candidateInside = mutableMapOf<String, Boolean>()
    private val candidateSinceMillis = mutableMapOf<String, Long>()

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ids = intent?.getStringArrayExtra(EXTRA_PLACE_IDS)?.toSet().orEmpty()
        val app = applicationContext as CuesApplication
        watchedPlaces = ids.mapNotNull { app.store.findPlace(it) }

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (watchedPlaces.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        registerListener()

        // Not START_STICKY: PlaceAdapter re-sends a fresh start with the
        // current watched-place set on every arm/disarm sync, the same
        // "recovery is someone else's job, not blind OS restart" reasoning
        // SessionService documents for its own foreground service.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        unregisterListener()
        super.onDestroy()
    }

    private fun registerListener() {
        val manager = locationManager ?: return
        if (listener != null) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            // Review never arms a place-trigger cue without this grant; a
            // race here just means updates start once it's actually held.
            return
        }
        val provider = selectProvider(manager) ?: return
        val newListener = LocationListener { location -> onLocation(location) }
        try {
            manager.requestLocationUpdates(provider, MIN_TIME_MILLIS, MIN_DISTANCE_METERS, newListener)
            listener = newListener
        } catch (_: SecurityException) {
            // Permission was revoked between the check above and this call.
        }
    }

    private fun unregisterListener() {
        val current = listener ?: return
        locationManager?.removeUpdates(current)
        listener = null
    }

    private fun selectProvider(manager: LocationManager): String? {
        val available = manager.getProviders(true)
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && available.contains(LocationManager.FUSED_PROVIDER) ->
                LocationManager.FUSED_PROVIDER
            available.contains(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            available.contains(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
    }

    private fun onLocation(location: Location) {
        val now = System.currentTimeMillis()
        var evaluatedPlace = false
        watchedPlaces.forEach { place ->
            if (!isReliableFixFor(location, place, now)) return@forEach
            val distance = location.distanceTo(placeLocation(place))
            evaluateDwell(place.id, isInsideNow = distance <= place.radiusMeters, now = now)
            evaluatedPlace = true
        }
        // Preserve the last known reading when the platform cannot provide a
        // fresh, sufficiently accurate fix. Guessing an arrival is worse
        // than waiting for the next update.
        if (!evaluatedPlace) return
        val insideNow = watchedPlaces.filter { confirmedInside[it.id] == true }.map { it.id }.toSet()
        PlaceReadings.record(this, insideNow, now)
    }

    private fun isReliableFixFor(location: Location, place: Place, now: Long): Boolean {
        val ageMillis = now - location.time
        if (ageMillis !in 0..MAX_LOCATION_AGE_MILLIS) return false
        if (!location.hasAccuracy()) return false
        return location.accuracy <= minOf(place.radiusMeters.toFloat(), MAX_ACCEPTED_ACCURACY_METERS)
    }

    private fun placeLocation(place: Place): Location = Location(PROVIDER_LABEL).apply {
        latitude = place.latitude
        longitude = place.longitude
    }

    /**
     * A raw fix only becomes a confirmed transition once the candidate
     * inside/outside state has held for [DWELL_MILLIS] — the hand-rolled
     * equivalent of `GeofencingRequest.setLoiteringDelay()`, since this
     * isn't using `GeofencingClient`. See the class doc for why the very
     * first confirmation is a silent baseline rather than a dispatch.
     */
    private fun evaluateDwell(placeId: String, isInsideNow: Boolean, now: Long) {
        if (candidateInside[placeId] != isInsideNow) {
            candidateInside[placeId] = isInsideNow
            candidateSinceMillis[placeId] = now
            return
        }
        val candidateSince = candidateSinceMillis[placeId] ?: return
        if (now - candidateSince < DWELL_MILLIS) return

        val wasConfirmed = confirmedInside[placeId]
        if (wasConfirmed == isInsideNow) return
        confirmedInside[placeId] = isInsideNow

        if (wasConfirmed == null) return // First-ever read for this place: silent baseline.

        dispatchTransition(placeId, entered = isInsideNow, atMillis = now)
    }

    private fun dispatchTransition(placeId: String, entered: Boolean, atMillis: Long) {
        val app = applicationContext as CuesApplication
        val kind = if (entered) EventKind.PLACE_ENTERED else EventKind.PLACE_EXITED
        app.monitoring.recordEvent(KEY, atMillis)
        GraceScheduler.apply(
            app,
            app.cueService.onDeviceEvent(
                TriggerEvent(kind, atMillis, deviceId = placeId),
                charging = Readings.charging(app, atMillis),
                wifi = WifiReadings.current(app, atMillis),
                insidePlaces = PlaceReadings.current(app, atMillis),
            ),
        )
    }

    private fun buildNotification(): android.app.Notification {
        val label = watchedPlaces.joinToString(", ") { it.label }
            .ifEmpty { getString(R.string.place_watcher_notification_fallback) }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.place_watcher_notification_title))
            .setContentText(getString(R.string.place_watcher_notification_text, label))
            .setSmallIcon(R.drawable.ic_stat_cue)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())
            .build()
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(this, 0, intent, flags)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.place_watcher_channel_name), NotificationManager.IMPORTANCE_LOW),
        )
    }

    companion object {
        const val EXTRA_PLACE_IDS = "com.cues.android.EXTRA_PLACE_IDS"
        private const val KEY = "place"
        private const val PROVIDER_LABEL = "cues-place"
        private const val CHANNEL_ID = "cues_place_watcher"
        private const val NOTIFICATION_ID = 2
        private const val MIN_TIME_MILLIS = 20_000L
        private const val MIN_DISTANCE_METERS = 25f
        private const val MAX_LOCATION_AGE_MILLIS = 2 * 60_000L
        private const val MAX_ACCEPTED_ACCURACY_METERS = 75f

        /** How long a candidate inside/outside state must hold before it counts as arrived/left. */
        private const val DWELL_MILLIS = 60_000L
    }
}
