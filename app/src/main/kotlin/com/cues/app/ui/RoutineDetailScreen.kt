package com.cues.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.cues.core.model.Patch
import com.cues.core.model.PatchKind
import com.cues.core.model.Routine
import com.cues.core.model.RoutineStatus
import com.cues.core.review.ReviewCopy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Routine detail: the approved version, its current status, the next
 * relevant deadline if one is running, and a manual stop / pause / resume /
 * delete path.
 */
@Composable
fun RoutineDetailScreen(
    routine: Routine,
    onBack: () -> Unit,
    onPauseResume: () -> Unit,
    onDelete: () -> Unit,
    onManualStop: (() -> Unit)?,
    onDryRun: () -> com.cues.core.rehearsal.RehearsalRow,
    deleteBlockedReason: String?,
    activePatch: Patch? = null,
    onSkipToday: (() -> Unit)? = null,
    /** Absolute epoch millis this pause should hold until. */
    onPauseUntil: ((epochMillis: Long) -> Unit)? = null,
    onClearPatch: (() -> Unit)? = null,
    onShareAsCard: (() -> Unit)? = null,
    /**
     * Opens Review for a routine that never got there through the ordinary
     * draft flow — a coach-suggested edit or an AppFunctions-drafted request
     * (Task 17), both of which persist a DRAFT/REVIEWABLE routine with no
     * in-memory screen already pointed at it. Non-null only when the caller
     * considers [routine] not yet approved; Detail itself doesn't guess.
     */
    onReview: (() -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current
    val dryRun = remember(routine.id) { mutableStateOf<com.cues.core.rehearsal.RehearsalRow?>(null) }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().padding(16.dp).animateContentSize()) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(routine.title, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "v${routine.version} · ${ReviewCopy.whenText(routine)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = cuesColors.ink200,
                )
            }
            StatusChip(routine.status.name.lowercase(), routine.status.toTone())
        }

        Spacer(Modifier.height(20.dp))
        DetailRow("When", ReviewCopy.whenText(routine))
        DetailRow("If", ReviewCopy.ifText(routine))
        DetailRow("Do", ReviewCopy.doText(routine))
        DetailRow("Until", ReviewCopy.untilText(routine))
        DetailRow("Restore", ReviewCopy.restoreText(routine))
        DetailRow("Repeat", ReviewCopy.repeatText(routine))

        Spacer(Modifier.height(24.dp))

        if (onReview != null) {
            Button(onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onReview()
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Review & approve")
            }
            Spacer(Modifier.height(10.dp))
        }

        OutlinedButton(onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            dryRun.value = onDryRun()
        }, modifier = Modifier.fillMaxWidth()) {
            Text("What would happen right now?")
        }
        if (onShareAsCard != null) {
            OutlinedButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onShareAsCard()
                },
                // Workbench drag/drop (Task 9): long-press instead drags the
                // exact same Cue Card payload CueCardShareScreen's own
                // "Share as text" button sends — CueCards.encode's output,
                // never a live link back into this phone. A tap still opens
                // the ordinary QR share screen; this only adds a second way
                // to reach the same, already-tested payload.
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    .dragAndDropTextSource(view, routine.title) {
                        com.cues.core.share.CueCards.encode(com.cues.core.share.CueCards.from(routine))
                    },
            ) {
                // Never "share this app's behavior on you" — this cue's own
                // WHEN/IF/DO/UNTIL, and nothing this phone approved.
                Text("Share as a Cue Card")
            }
        }
        dryRun.value?.let { row ->
            Text("${row.label}: ${row.outcome}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            row.explanation.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = cuesColors.ink200) }
        }
        Spacer(Modifier.height(10.dp))

        if (onManualStop != null) {
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onManualStop()
                },
                colors = ButtonDefaults.buttonColors(containerColor = cuesColors.stop),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Stop this session") }
            Spacer(Modifier.height(10.dp))
        }

        if (routine.status == RoutineStatus.ARMED || routine.status == RoutineStatus.PAUSED) {
            OutlinedButton(onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onPauseResume()
            }, modifier = Modifier.fillMaxWidth()) {
                Text(if (routine.status == RoutineStatus.PAUSED) "Resume" else "Pause")
            }
            Spacer(Modifier.height(10.dp))
        }

        if (routine.status == RoutineStatus.ARMED && onSkipToday != null && onPauseUntil != null) {
            TemporaryPatchCard(
                patch = activePatch,
                onSkipToday = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSkipToday()
                },
                onPauseThreeHours = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onPauseUntil(System.currentTimeMillis() + 3 * 60 * 60 * 1000L)
                },
                onPauseUntilTomorrow = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onPauseUntil(tomorrowMorningMillis())
                },
                onClear = onClearPatch?.let {
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        it()
                    }
                },
            )
            Spacer(Modifier.height(10.dp))
        }

        OutlinedButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onDelete()
            },
            enabled = deleteBlockedReason == null,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = cuesColors.stop),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Delete") }

        if (deleteBlockedReason != null) {
            Text(
                deleteBlockedReason,
                style = MaterialTheme.typography.bodySmall,
                color = cuesColors.stop,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onBack()
        }, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = cuesColors.ink200)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * A visible, self-expiring override — never a second copy of the routine.
 *
 * [Patch] deliberately does not touch [Routine.approvedDigest]: skipping today
 * or pausing until a time is a temporary read the session engine consults
 * before it evaluates the trigger at all, not a rule edit. It disappears on
 * its own once it expires, and an edit to the routine's own version drops it
 * rather than silently carrying a stale skip forward (`CueService.patchReason`).
 */
@Composable
private fun TemporaryPatchCard(
    patch: Patch?,
    onSkipToday: () -> Unit,
    onPauseThreeHours: () -> Unit,
    onPauseUntilTomorrow: () -> Unit,
    onClear: (() -> Unit)?,
) {
    Surface(color = cuesColors.bg300, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text("TEMPORARY PATCH", style = MaterialTheme.typography.labelSmall, color = cuesColors.ink200)
            if (patch != null) {
                Text(patch.describe(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                if (onClear != null) {
                    TextButton(onClick = onClear, modifier = Modifier.padding(top = 4.dp)) { Text("Clear") }
                }
            } else {
                Text(
                    "Skip just today, or pause without changing the approved rule.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cuesColors.ink200,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onSkipToday, modifier = Modifier.weight(1f)) { Text("Skip today") }
                    OutlinedButton(onClick = onPauseThreeHours, modifier = Modifier.weight(1f)) { Text("Pause 3h") }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onPauseUntilTomorrow, modifier = Modifier.fillMaxWidth()) { Text("Pause until tomorrow morning") }
            }
        }
    }
}

private val PATCH_TIME_FORMAT = SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault())

private fun Patch.describe(): String = when (val kind = kind) {
    is PatchKind.SkipOccurrence -> "Skipped today (${kind.date})."
    is PatchKind.SkipUntil -> "Paused until ${PATCH_TIME_FORMAT.format(Date(kind.epochMillis))}."
}

private fun tomorrowMorningMillis(): Long {
    val calendar = java.util.Calendar.getInstance()
    calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
    calendar.set(java.util.Calendar.HOUR_OF_DAY, 8)
    calendar.set(java.util.Calendar.MINUTE, 0)
    calendar.set(java.util.Calendar.SECOND, 0)
    calendar.set(java.util.Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}
