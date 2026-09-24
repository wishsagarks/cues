package com.cues.app.runtime

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.cues.core.model.ContextSource
import com.cues.core.model.ContextValue
import com.cues.core.model.UnknownReason

/**
 * Reads whether any calendar shows a busy event covering a moment — nothing
 * about *which* event, its title or its attendees, matching the FDD's
 * "Cues does not read event titles" boundary for `Condition.CalendarBusy`/
 * `CalendarNotBusy`. `READ_CALENDAR` denied is [ContextValue.Unknown], never
 * read as free.
 *
 * WRITTEN AGAINST REAL APIs, VERIFIED ON NOTHING — same caveat as every other
 * class in this package; see CLEANUP.md.
 */
object CalendarReadings {

    fun busy(context: Context, atMillis: Long): ContextValue<Boolean> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return ContextValue.Unknown(UnknownReason.PERMISSION_DENIED, ContextSource.CALENDAR_PROVIDER)
        }

        return try {
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                .appendPath(atMillis.toString())
                .appendPath(atMillis.toString())
                .build()
            // Any instance covering this exact millisecond counts as busy;
            // an all-day or a free/transparent event is intentionally not
            // excluded here — refining "busy" beyond "some instance exists"
            // is future work, not something this pass claims to have solved.
            context.contentResolver.query(
                uri,
                arrayOf(CalendarContract.Instances.EVENT_ID),
                null,
                null,
                null,
            )?.use { cursor ->
                ContextValue.Known(cursor.count > 0, ContextSource.CALENDAR_PROVIDER, atMillis)
            } ?: ContextValue.Unknown(UnknownReason.ADAPTER_UNAVAILABLE, ContextSource.CALENDAR_PROVIDER)
        } catch (e: SecurityException) {
            ContextValue.Unknown(UnknownReason.PERMISSION_DENIED, ContextSource.CALENDAR_PROVIDER)
        }
    }
}
