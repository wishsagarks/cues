package com.cues.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cues.core.model.Routine
import com.cues.core.model.RoutineStatus
import com.cues.core.review.ReviewCopy

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
    deleteBlockedReason: String?,
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
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

        if (onManualStop != null) {
            Button(
                onClick = onManualStop,
                colors = ButtonDefaults.buttonColors(containerColor = cuesColors.stop),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Stop this session") }
            Spacer(Modifier.height(10.dp))
        }

        if (routine.status == RoutineStatus.ARMED || routine.status == RoutineStatus.PAUSED) {
            OutlinedButton(onClick = onPauseResume, modifier = Modifier.fillMaxWidth()) {
                Text(if (routine.status == RoutineStatus.PAUSED) "Resume" else "Pause")
            }
            Spacer(Modifier.height(10.dp))
        }

        OutlinedButton(
            onClick = onDelete,
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
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = cuesColors.ink200)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
