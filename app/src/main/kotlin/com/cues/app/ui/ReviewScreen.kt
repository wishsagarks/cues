package com.cues.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cues.core.approval.Approvals
import com.cues.core.compile.Severity
import com.cues.core.model.Capability
import com.cues.core.model.Routine
import com.cues.core.rehearsal.RehearsalRow
import com.cues.core.review.ReviewCopy

/**
 * Review: WHEN / IF / DO / UNTIL / RESTORE, required access, a rehearsal and
 * the approve control — the one screen RV-01/RV-02 describe, and the
 * screen a judge is meant to be able to read without seeing JSON.
 */
@Composable
fun ReviewScreen(
    routine: Routine,
    review: Approvals.ReviewResult,
    rehearsal: List<RehearsalRow>,
    missingCapabilities: Set<Capability>,
    onApprove: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Review", style = MaterialTheme.typography.headlineSmall)
            Text(
                "drafted by ${routine.draftedBy?.name?.lowercase()?.replace('_', ' ') ?: "unknown"}",
                style = MaterialTheme.typography.labelSmall,
                color = cuesColors.ink200,
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item { ReviewRow("WHEN", ReviewCopy.whenText(routine)) }
            item { ReviewRow("IF", ReviewCopy.ifText(routine)) }
            item { ReviewRow("DO", ReviewCopy.doText(routine)) }
            item { ReviewRow("UNTIL", ReviewCopy.untilText(routine)) }
            item { ReviewRow("RESTORE", ReviewCopy.restoreText(routine)) }
            item { ReviewRow("REPEAT", ReviewCopy.repeatText(routine)) }

            item { SectionLabel("Required access") }
            item {
                AccessRow(routine.requiredCapabilities, missingCapabilities)
            }

            if (!review.validation.isValid || review.validation.warnings.isNotEmpty()) {
                item { SectionLabel("Checks") }
                items(review.validation.findings) { finding ->
                    Text(
                        (if (finding.severity == Severity.ERROR) "⛔ " else "⚠ ") + finding.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (finding.severity == Severity.ERROR) cuesColors.stop else cuesColors.amber,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }

            item { SectionLabel("Rehearsal — sample events, nothing on your phone changes") }
            items(rehearsal) { row -> RehearsalRowView(row) }

            item { Spacer(Modifier.height(80.dp)) }
        }

        Surface(color = cuesColors.bg200, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                if (missingCapabilities.isNotEmpty()) {
                    Text(
                        "Missing: ${missingCapabilities.joinToString(", ") { with(ReviewCopy) { it.friendlyName() } }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = cuesColors.stop,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
                    Button(
                        onClick = onApprove,
                        enabled = review.validation.isValid,
                        modifier = Modifier.weight(2f),
                    ) { Text("Approve & arm") }
                }
            }
        }
    }
}

@Composable
private fun ReviewRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = cuesColors.ink100,
            modifier = Modifier.width(72.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = cuesColors.ink200,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun AccessRow(required: Set<Capability>, missing: Set<Capability>) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        // A simple wrap would need FlowRow (not in this version of Compose's
        // foundation layout); a short capability list fits one row in
        // practice, so this stays a Row rather than pulling in a new API.
        required.forEach { capability ->
            val isMissing = capability in missing
            Surface(
                color = if (isMissing) cuesColors.stopBg else cuesColors.bg300,
                contentColor = if (isMissing) cuesColors.stop else cuesColors.ink200,
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(
                    with(ReviewCopy) { capability.friendlyName() },
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun RehearsalRowView(row: RehearsalRow) {
    Surface(color = cuesColors.bg300, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(row.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                val tone = if (row.outcome.startsWith("Skipped")) Tone.AMBER else Tone.GO
                StatusChip(row.outcome, tone)
            }
            row.explanation.forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = cuesColors.ink200,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}
