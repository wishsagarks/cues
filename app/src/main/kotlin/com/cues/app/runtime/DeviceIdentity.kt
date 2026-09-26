package com.cues.app.runtime

import android.os.Build

/**
 * Static device identity strings for the Now screen's device snapshot card.
 *
 * Nothing here is a live signal — it's read once from [Build] — so unlike
 * [Readings] it needs no [com.cues.core.model.ContextValue] wrapping.
 */
object DeviceIdentity {

    fun phoneName(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        return when {
            model.isBlank() -> manufacturer.ifBlank { "Unknown phone" }
            model.startsWith(manufacturer, ignoreCase = true) || manufacturer.isBlank() -> model
            else -> "$manufacturer $model"
        }
    }

    /** [Build.SOC_MODEL] (API 31+) falling back to [Build.HARDWARE] on older phones. */
    fun chipset(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val socModel = Build.SOC_MODEL
            if (!socModel.isNullOrBlank() && socModel != Build.UNKNOWN) return socModel
        }
        val hardware = Build.HARDWARE
        if (!hardware.isNullOrBlank() && hardware != Build.UNKNOWN) return hardware
        return "Unknown chipset"
    }
}
