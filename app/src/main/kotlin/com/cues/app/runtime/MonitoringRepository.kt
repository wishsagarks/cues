package com.cues.app.runtime

import android.content.Context
import com.cues.core.ports.ListenerHealth

/**
 * What Home's monitoring section shows (4.6 / MH-01): when each adapter last
 * saw an event, and whether its receiver is declared at all.
 *
 * "Live" is deliberately not claimed here. [BluetoothReceiver] and
 * [PowerReceiver] are manifest-declared broadcast receivers, not started
 * services with a health check of their own — the FDD is explicit that "a
 * receiver declaration is not proof of delivery" (CLEANUP.md CL-06, spike
 * R1). What this can honestly report is that the receiver exists in the
 * manifest, and when it last actually fired.
 */
data class AdapterStatus(
    val key: String,
    val label: String,
    val lastEventAtMillis: Long?,
    val running: Boolean? = null,
    val detail: String? = null,
)

/** Persists the last-event timestamp per adapter so it survives leaving the screen. */
class MonitoringRepository(context: Context) {

    private val preferences = context.getSharedPreferences("monitoring", Context.MODE_PRIVATE)

    fun recordEvent(adapterKey: String, atMillis: Long) {
        preferences.edit().putLong(keyFor(adapterKey), atMillis).apply()
    }

    fun statuses(health: List<ListenerHealth> = emptyList()): List<AdapterStatus> = listOf(
        AdapterStatus("bluetooth", "Bluetooth", lastEventAtMillis(BLUETOOTH)),
        AdapterStatus("power", "Charging", lastEventAtMillis(POWER)),
    ) + health.map { AdapterStatus(it.key, it.key.replaceFirstChar { c -> c.uppercase() }, lastEventAtMillis(it.key), it.running, it.detail) }

    private fun lastEventAtMillis(adapterKey: String): Long? =
        preferences.getLong(keyFor(adapterKey), 0).takeIf { it > 0 }

    private fun keyFor(adapterKey: String) = "last-event-$adapterKey"

    companion object {
        const val BLUETOOTH = "bluetooth"
        const val POWER = "power"
    }
}
