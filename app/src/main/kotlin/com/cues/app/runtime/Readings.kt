package com.cues.app.runtime

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.cues.core.model.ContextSource
import com.cues.core.model.ContextValue
import com.cues.core.model.UnknownReason

/**
 * Reads the one context signal every adapter can supply regardless of which
 * event fired: the phone's current charging state.
 *
 * Cues doesn't only ask "did the charger just connect" — a routine can gate
 * on charging state independent of what actually triggered it — so every
 * adapter passes this along, not just [PowerReceiver].
 */
object Readings {

    fun charging(context: Context, atMillis: Long): ContextValue<Boolean> {
        // ACTION_BATTERY_CHANGED is a sticky broadcast: registering for it
        // with a null receiver returns the last one immediately, with no
        // need to actually listen.
        val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return ContextValue.Unknown(UnknownReason.ADAPTER_UNAVAILABLE, ContextSource.BATTERY_MANAGER)

        val status = sticky.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        if (status == -1) {
            return ContextValue.Unknown(UnknownReason.ADAPTER_UNAVAILABLE, ContextSource.BATTERY_MANAGER)
        }

        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return ContextValue.Known(charging, ContextSource.BATTERY_MANAGER, atMillis)
    }
}
