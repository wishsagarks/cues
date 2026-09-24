package com.cues.app.runtime

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/** A launchable app, as the installed-app picker shows it. */
data class InstalledApp(val packageName: String, val label: String)

/**
 * Every app with a launcher entry — exactly what the home screen and the
 * app drawer already show, nothing Cues could not already see.
 *
 * WRITTEN AGAINST A REAL API, VERIFIED ON NOTHING — see CLEANUP.md CL-04/CL-06,
 * same disclosure this repo already gives [com.cues.app.CuesApplication.pairedDevices].
 * This exists so [com.cues.core.drafting.DraftResult.NeedsClarification.appQuery]
 * — "which app did you mean" — resolves against a real, installed package the
 * user picks, never a name a drafter or the parser invented.
 */
fun installedApps(context: Context): List<InstalledApp> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

    val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        pm.queryIntentActivities(intent, 0)
    }

    return resolved
        .mapNotNull { info ->
            val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
            // Cues cannot open itself as a "handoff" — that would be a no-op
            // dressed up as an action.
            if (packageName == context.packageName) return@mapNotNull null
            InstalledApp(packageName, info.loadLabel(pm).toString())
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

/** Ranks [apps] against a free-text [query] the way the picker's search box does: label first, then package. */
fun rankInstalledApps(apps: List<InstalledApp>, query: String): List<InstalledApp> {
    val needle = query.trim().lowercase()
    if (needle.isBlank()) return apps
    return apps
        .filter { needle in it.label.lowercase() || needle in it.packageName.lowercase() }
        .sortedWith(
            compareByDescending<InstalledApp> { it.label.lowercase() == needle }
                .thenByDescending { it.label.lowercase().startsWith(needle) }
                .thenBy { it.label.lowercase() },
        )
}
