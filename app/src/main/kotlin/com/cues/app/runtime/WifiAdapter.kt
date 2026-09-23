package com.cues.app.runtime

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.cues.app.CuesApplication
import com.cues.core.model.EventKind
import com.cues.core.model.Routine
import com.cues.core.model.TriggerEvent
import com.cues.core.ports.ListenerHealth
import com.cues.core.ports.SignalAdapter

/** Registers a callback only while at least one any-Wi-Fi cue is armed. */
class WifiAdapter(private val context: Context) : SignalAdapter {
    override val key = "wifi"
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var detail: String? = null

    override fun start(armed: List<Routine>) {
        if (callback != null) return
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: run { detail = "ConnectivityManager unavailable"; return }
        callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = dispatch(EventKind.WIFI_CONNECTED)
            override fun onLost(network: Network) = dispatch(EventKind.WIFI_DISCONNECTED)
        }
        try {
            manager.registerNetworkCallback(
                android.net.NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
                requireNotNull(callback),
            )
            detail = null
        } catch (e: SecurityException) {
            callback = null
            detail = e.message ?: "Network callback was denied"
        }
    }

    override fun stop() {
        val current = callback ?: return
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        try { manager?.unregisterNetworkCallback(current) } catch (_: Exception) { }
        callback = null
    }

    override fun health() = ListenerHealth(key, callback != null, detail)

    private fun dispatch(kind: EventKind) {
        val now = System.currentTimeMillis()
        val app = context.applicationContext as CuesApplication
        app.monitoring.recordEvent(key, now)
        GraceScheduler.apply(app, app.cueService.onDeviceEvent(
            TriggerEvent(kind, now), charging = Readings.charging(app, now), wifi = WifiReadings.current(app, now),
        ))
    }
}
