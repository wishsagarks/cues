package com.cues.app.runtime

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.cues.core.model.ContextSource
import com.cues.core.model.ContextValue
import com.cues.core.model.UnknownReason
import com.cues.core.model.WifiState

/** Reads only whether an active Wi-Fi transport exists; SSID identity is deliberately not guessed. */
object WifiReadings {
    fun current(context: Context, atMillis: Long): ContextValue<WifiState> = try {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return ContextValue.Unknown(UnknownReason.ADAPTER_UNAVAILABLE, ContextSource.WIFI_MANAGER)
        val network = manager.activeNetwork
            ?: return ContextValue.Known(WifiState(false), ContextSource.WIFI_MANAGER, atMillis)
        val capabilities = manager.getNetworkCapabilities(network)
            ?: return ContextValue.Unknown(UnknownReason.ADAPTER_UNAVAILABLE, ContextSource.WIFI_MANAGER)
        ContextValue.Known(
            WifiState(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)),
            ContextSource.WIFI_MANAGER,
            atMillis,
        )
    } catch (_: SecurityException) {
        ContextValue.Unknown(UnknownReason.PERMISSION_DENIED, ContextSource.WIFI_MANAGER)
    }
}
