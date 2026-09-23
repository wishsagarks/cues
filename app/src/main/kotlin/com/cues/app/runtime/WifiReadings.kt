package com.cues.app.runtime

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.cues.core.model.ContextSource
import com.cues.core.model.ContextValue
import com.cues.core.model.UnknownReason
import com.cues.core.model.WifiState

/**
 * Reads only whether any Wi-Fi transport is connected; SSID identity is
 * deliberately not guessed.
 *
 * This checks every network the OS reports, not just `activeNetwork` (the
 * single preferred default route). A device can hold Wi-Fi connected while
 * routing its default traffic over mobile data, and "any-Wi-Fi" must not
 * read as `false` just because Wi-Fi wasn't the one the OS picked as
 * default — a wrong `false` is worse than an honest `Unknown`.
 */
object WifiReadings {
    fun current(context: Context, atMillis: Long): ContextValue<WifiState> = try {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return ContextValue.Unknown(UnknownReason.ADAPTER_UNAVAILABLE, ContextSource.WIFI_MANAGER)
        val networks = manager.allNetworks
        val capabilities = networks.mapNotNull { manager.getNetworkCapabilities(it) }
        when {
            networks.isEmpty() -> ContextValue.Known(WifiState(false), ContextSource.WIFI_MANAGER, atMillis)
            capabilities.isEmpty() -> ContextValue.Unknown(UnknownReason.ADAPTER_UNAVAILABLE, ContextSource.WIFI_MANAGER)
            else -> ContextValue.Known(
                WifiState(capabilities.any { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) }),
                ContextSource.WIFI_MANAGER,
                atMillis,
            )
        }
    } catch (_: SecurityException) {
        ContextValue.Unknown(UnknownReason.PERMISSION_DENIED, ContextSource.WIFI_MANAGER)
    }
}
