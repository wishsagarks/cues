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

    /**
     * Whether this chipset's own *family* is documented to ship an on-die AI
     * accelerator — Qualcomm's Hexagon NPU, MediaTek's APU, Google's Tensor
     * TPU, or Samsung's Exynos NPU — matched against each vendor's own
     * published SoC codename convention.
     *
     * This is a different, broader claim than
     * [com.cues.app.drafting.LiteRtLmSession.npuSocEligible]: that function
     * asks "does Cues' own side-loaded `.litertlm` asset have a build
     * compiled for this *exact* `Build.SOC_MODEL`" (Google's LiteRT-LM
     * NPU-model table currently covers only SM8750/SM8650/SM8550 — see
     * CLEANUP.md CL-18 and docs/API_VERIFICATION.md). A chip can — and, for
     * every Snapdragon/Dimensity/Tensor SoC shipped in the last several
     * years, does — carry a real NPU while sitting outside that narrower,
     * asset-specific table; conflating the two would either hide real
     * hardware or overclaim what Cues' own model can use. Never merge this
     * result into [com.cues.app.drafting.LiteRtLmSession.npuSocEligible] or
     * its allowlist — the two answer different questions and the screen
     * that renders both must keep saying so.
     *
     * Codename coverage is deliberately conservative (recognized vendor
     * prefixes only): an unmatched chipset string renders as "not
     * identified", never as "no NPU" — the same UNKNOWN-is-not-false rule
     * every other signal in this app follows.
     */
    fun npuHardwareFamily(chipset: String): String? {
        val soc = chipset.trim().uppercase()
        return when {
            Regex("^SM8\\d{3}$").matches(soc) -> "Qualcomm Hexagon NPU (Snapdragon 8-series)"
            Regex("^SM7\\d{3}$").matches(soc) -> "Qualcomm Hexagon NPU (Snapdragon 7-series)"
            Regex("^SM6\\d{3}$").matches(soc) -> "Qualcomm Hexagon NPU (Snapdragon 6-series)"
            Regex("^MT6\\d{3}$").matches(soc) -> "MediaTek APU (Dimensity)"
            Regex("^(ZUMA\\w*|GS10[12]|GS201)$").matches(soc) -> "Google Tensor TPU"
            Regex("^S5E\\d+$").matches(soc) -> "Samsung NPU (Exynos)"
            else -> null
        }
    }
}
