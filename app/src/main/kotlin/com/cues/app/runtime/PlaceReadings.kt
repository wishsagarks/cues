package com.cues.app.runtime

import android.content.Context
import com.cues.core.model.ContextSource
import com.cues.core.model.ContextValue
import com.cues.core.model.UnknownReason

/**
 * The confirmed set of place IDs [PlaceWatcherService] currently believes
 * the device is inside, after its dwell window has held.
 *
 * Unlike [Readings]/[WifiReadings], this cannot be answered by a live,
 * synchronous system-service query — "am I inside this place" needs an
 * ongoing location fix and a debounce window, not a point-in-time read. So
 * the confirmed state is written here by [PlaceWatcherService] each time it
 * settles, and read back from the same "monitoring" [android.content.SharedPreferences]
 * every other adapter's debounce state already lives in (see
 * [PowerReceiver]) — any dispatch path sees what the watcher last
 * confirmed, not a value private to its own process lifetime.
 *
 * No staleness cutoff is applied here: like every other Readings object in
 * this package, that judgement belongs to the evaluator's own
 * `FreshnessPolicy`, not to the reading itself.
 */
object PlaceReadings {
    private const val PREFS = "monitoring"
    private const val KEY_PLACE_IDS = "place-inside-ids"
    private const val KEY_CONFIRMED_AT = "place-inside-confirmed-at"

    fun current(context: Context, atMillis: Long): ContextValue<Set<String>> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val confirmedAt = prefs.getLong(KEY_CONFIRMED_AT, 0L)
        if (confirmedAt <= 0L) {
            return ContextValue.Unknown(UnknownReason.NEVER_OBSERVED, ContextSource.LOCATION_MANAGER)
        }
        val ids = prefs.getStringSet(KEY_PLACE_IDS, emptySet()).orEmpty()
        return ContextValue.Known(ids, ContextSource.LOCATION_MANAGER, confirmedAt)
    }

    fun record(context: Context, insideIds: Set<String>, atMillis: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet(KEY_PLACE_IDS, insideIds)
            .putLong(KEY_CONFIRMED_AT, atMillis)
            .apply()
    }
}
