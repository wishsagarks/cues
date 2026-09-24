package com.cues.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cues.core.imports.TimetableEntry
import com.cues.core.imports.TimetableExtractionResult

/**
 * "Here is what I could read — you decide what becomes a cue."
 *
 * Every row is a proposal, never a routine: tapping "Review" on one runs its
 * [TimetableEntry.proposedSentence] through the ordinary drafter and opens
 * the ordinary Review screen for it, exactly as if the user had typed that
 * sentence themselves. Nothing here can arm a cue directly, and a row
 * [TimetableEntry.needsReview] flags is shown with its reason rather than
 * silently included or silently dropped. Rows the extractor could not shape
 * into a proposal at all — including anything that reads like an attempted
 * instruction rather than a timetable row — are listed as unrecognized text,
 * never acted on.
 */
@Composable
fun ImportReviewScreen(
    result: TimetableExtractionResult,
    onReviewEntry: (TimetableEntry) -> Unit,
    onBack: () -> Unit,
) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Import review", style = MaterialTheme.typography.headlineSmall)
            OutlinedButton(onClick = onBack) { Text("Done") }
        }
        Text(
            "Nothing here is a cue yet. Review each row you want, one at a time, before it's armed.",
            style = MaterialTheme.typography.bodySmall,
            color = cuesColors.ink200,
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
        )

        if (result.entries.isEmpty() && result.unrecognizedLines.isEmpty()) {
            Text(
                "No text was recognized in that image.",
                style = MaterialTheme.typography.bodyMedium,
                color = cuesColors.ink200,
            )
            return@Column
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(result.entries) { entry ->
                ImportEntryCard(entry, onReview = { onReviewEntry(entry) })
            }
            if (result.unrecognizedLines.isNotEmpty()) {
                item {
                    Text(
                        "NOT RECOGNIZED AS A TIMETABLE ROW",
                        style = MaterialTheme.typography.labelSmall,
                        color = cuesColors.ink200,
                        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                    )
                }
                items(result.unrecognizedLines) { line ->
                    Surface(
                        color = cuesColors.bg300,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall,
                            color = cuesColors.ink200,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportEntryCard(entry: TimetableEntry, onReview: () -> Unit) {
    Surface(
        color = if (entry.needsReview) cuesColors.bg300 else cuesColors.bg200,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(entry.rawLine, style = MaterialTheme.typography.labelSmall, color = cuesColors.ink200)
            Text(
                buildString {
                    append(entry.label)
                    entry.room?.let { append(" — Room ").append(it) }
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                buildString {
                    append(entry.day?.name ?: "day unclear")
                    append(", ")
                    append(entry.startTime?.toString() ?: "time unclear")
                    entry.endTime?.let { append(" – ").append(it) }
                },
                style = MaterialTheme.typography.bodySmall,
                color = cuesColors.ink200,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (entry.needsReview) {
                Text(
                    entry.reviewReason ?: "This row needs a closer look.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Button(
                onClick = onReview,
                enabled = entry.proposedSentence() != null,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            ) { Text(if (entry.proposedSentence() != null) "Review this cue" else "Not enough to propose a cue") }
        }
    }
}
