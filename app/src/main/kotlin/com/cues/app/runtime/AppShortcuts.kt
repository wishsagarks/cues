package com.cues.app.runtime

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.cues.app.MainActivity
import com.cues.app.R
import com.cues.core.model.Routine
import com.cues.core.model.RoutineStatus
import com.cues.core.model.Trigger

/**
 * App shortcuts (Task 9's FDD line): "New cue", plus a "Start ‹cue›" entry
 * for every armed, manually-triggered cue.
 *
 * All dynamic, deliberately — a *static* shortcut's `<intent>` needs a
 * hardcoded target package in `res/xml`, which this app's debug build would
 * get wrong (`applicationIdSuffix = ".debug"`, so the real package is
 * `com.cues.android.debug`, not the release id a static resource would have
 * to hardcode). Building every intent as `Intent(context, MainActivity::class.java)`
 * instead means it is always correct for whichever build is actually
 * installed. The one thing this trades away is availability before the
 * app's first launch, which a static shortcut would have had.
 *
 * Only `Trigger.Manual` cues get a "Start" shortcut — the same reasoning
 * `CuesAppFunctionService.startCue` already documents: this can do only
 * what a manual cue's own review already disclosed, never start something a
 * device event or a schedule owns.
 *
 * WRITTEN AGAINST REAL APIs, VERIFIED ON NOTHING — same caveat as the rest
 * of this package; see CLEANUP.md.
 */
object AppShortcuts {

    const val EXTRA_START_ROUTINE_ID = "com.cues.app.EXTRA_START_ROUTINE_ID"
    const val EXTRA_NEW_CUE = "com.cues.app.EXTRA_SHORTCUT_NEW_CUE"

    private const val NEW_CUE_ID = "new-cue"
    private const val START_ID_PREFIX = "start-cue-"

    /**
     * Replaces the full shortcut set: "New cue" first, then one entry per
     * armed manual cue. Safe to call often — `setDynamicShortcuts` is
     * idempotent for an unchanged list, and this is cheap next to anything
     * else that already runs on resume.
     */
    fun refresh(context: Context, routines: List<Routine>) {
        val icon = IconCompat.createWithResource(context, R.drawable.cues_logo)
        val maxCount = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context).takeIf { it > 0 } ?: 4

        val newCue = ShortcutInfoCompat.Builder(context, NEW_CUE_ID)
            .setShortLabel("New cue")
            .setLongLabel("Describe a new cue")
            .setIcon(icon)
            .setIntent(
                Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    putExtra(EXTRA_NEW_CUE, true)
                },
            )
            .build()

        val startEntries = routines
            .asSequence()
            .filter { it.trigger is Trigger.Manual && it.status == RoutineStatus.ARMED }
            .take((maxCount - 1).coerceAtLeast(0))
            .map { routine ->
                ShortcutInfoCompat.Builder(context, START_ID_PREFIX + routine.id)
                    .setShortLabel(routine.title.take(25))
                    .setLongLabel("Start ${routine.title}")
                    .setIcon(icon)
                    .setIntent(
                        Intent(context, MainActivity::class.java).apply {
                            action = Intent.ACTION_VIEW
                            putExtra(EXTRA_START_ROUTINE_ID, routine.id)
                        },
                    )
                    .build()
            }
            .toList()

        ShortcutManagerCompat.setDynamicShortcuts(context, listOf(newCue) + startEntries)
    }
}
