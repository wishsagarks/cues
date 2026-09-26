package com.cues.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.cues.core.receipt.ReceiptRecord
import com.cues.core.store.ReceiptEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Receipt history: what actually happened, most recent first, built straight
 * from what [com.cues.core.CueService] already wrote through [com.cues.core.ports.ReceiptSink] —
 * no separate explanation layer, because a receipt that isn't the actual
 * record of what ran is the one thing this product is built not to do.
 *
 * [loadReceipts] is polled rather than read once (4.7): the store this reads
 * from is a plain directory of files with no change notification of its own,
 * so "observes the store" means asking it again while this screen is open,
 * not `remember { store.receipts() }` on entry. A live session ending while
 * this screen is on-screen now shows up without leaving and reopening it —
 * the second CL-02 gap. Polling starts when this composable enters
 * composition and stops the moment it leaves (`onBack` or navigating away),
 * since [LaunchedEffect] is cancelled with its call site.
 */
@Composable
fun ReceiptScreen(
    loadReceipts: () -> List<ReceiptEntry>,
    /** Structured evidence is preferred; pre-v2 text receipts remain visible as a fallback. */
    loadReceiptRecords: () -> List<ReceiptRecord> = { emptyList() },
    onBack: () -> Unit,
    /** Reads a receipt's exact rendered text aloud — see ReplySpeaker. Optional so this screen stays previewable without one. */
    onSpeak: (String) -> Unit = {},
) {
    var receipts by remember { mutableStateOf(loadReceipts()) }
    var records by remember { mutableStateOf(loadReceiptRecords()) }
    val haptics = LocalHapticFeedback.current
    androidx.activity.compose.BackHandler(onBack = onBack)
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(RECEIPTS_POLL_MILLIS)
            receipts = loadReceipts()
            records = loadReceiptRecords()
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Receipts", style = MaterialTheme.typography.headlineSmall)
        Text(
            "What Cues observed, decided and did — newest first.",
            style = MaterialTheme.typography.bodySmall,
            color = cuesColors.ink200,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        if (records.isEmpty() && receipts.isEmpty()) {
            Text(
                "Nothing has happened yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = cuesColors.ink200,
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                if (records.isNotEmpty()) {
                    items(records.asReversed(), key = { "${it.atMillis}-${it.key}-${it.kind}" }) { record ->
                        StructuredReceiptCard(record, onSpeak)
                    }
                } else {
                    items(receipts, key = { it.sessionId + it.atMillis }) { entry ->
                        LegacyReceiptCard(entry, onSpeak)
                    }
                }
            }
        }

        OutlinedButton(onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onBack()
        }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Text("Back")
        }
    }
}

@Composable
private fun LegacyReceiptCard(entry: ReceiptEntry, onSpeak: (String) -> Unit) {
    val lines = entry.text.lines().filter { it.isNotBlank() }
    val headline = lines.firstOrNull().orEmpty()
    val detail = lines.drop(1)
    val view = LocalView.current

    Surface(
        color = cuesColors.bg300,
        shape = RoundedCornerShape(14.dp),
        // Workbench drag/drop (Task 9): long-press drags this receipt's own
        // rendered text — exactly entry.text, never a summary — into
        // whatever the OS drop target is (notes, chat, the other Workbench
        // pane). See DragAndDrop.kt for why this uses the platform View API
        // rather than Compose's own drag modifier.
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).animateContentSize()
            .dragAndDropTextSource(view, "Cues receipt") { entry.text },
    ) {
        Column(Modifier.padding(13.dp)) {
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(TIME_FORMAT.format(Date(entry.atMillis)), style = MaterialTheme.typography.labelSmall, color = cuesColors.ink200)
                // Speaks exactly entry.text — the same string on screen,
                // never a summary — see ReplySpeaker.
                OutlinedButton(onClick = { onSpeak(entry.text) }) { Text("Read aloud") }
            }
            Text(headline, style = MaterialTheme.typography.bodyMedium)
            detail.forEach {
                Text(
                    it.trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = cuesColors.ink200,
                    modifier = Modifier.padding(top = 2.dp, start = 8.dp),
                )
            }
        }
    }
}

/** Renders persisted evidence directly, without trying to infer it from prose. */
@Composable
private fun StructuredReceiptCard(record: ReceiptRecord, onSpeak: (String) -> Unit) {
    val view = LocalView.current
    val renderedText = record.textLines.joinToString(separator = "\n")
    val metadata = buildList {
        record.provenance?.let { add("${it.name.lowercase().replace('_', ' ')} event") }
        record.sessionState?.let { add(it.name.lowercase().replace('_', ' ')) }
        record.endReason?.let { add(it.name.lowercase().replace('_', ' ')) }
    }.joinToString(" · ")

    Surface(
        color = cuesColors.bg300,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).animateContentSize()
            .dragAndDropTextSource(view, "Cues receipt") { renderedText },
    ) {
        Column(Modifier.padding(13.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(TIME_FORMAT.format(Date(record.atMillis)), style = MaterialTheme.typography.labelSmall, color = cuesColors.ink200)
                    Text(record.kind.name.replace('_', ' '), style = MaterialTheme.typography.labelMedium, color = cuesColors.ink100)
                }
                OutlinedButton(onClick = { onSpeak(renderedText) }) { Text("Read aloud") }
            }
            Text(record.headline, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            if (metadata.isNotBlank()) {
                Text(metadata, style = MaterialTheme.typography.labelSmall, color = cuesColors.ink200, modifier = Modifier.padding(top = 3.dp))
            }
            record.lines.forEach { line ->
                Text(line, style = MaterialTheme.typography.bodySmall, color = cuesColors.ink200, modifier = Modifier.padding(top = 4.dp, start = 8.dp))
            }
            record.reasons.forEach { reason ->
                Text("Gate: ${reason.detail}", style = MaterialTheme.typography.bodySmall, color = cuesColors.ink200, modifier = Modifier.padding(top = 4.dp, start = 8.dp))
            }
        }
    }
}

private val TIME_FORMAT = SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault())
private const val RECEIPTS_POLL_MILLIS = 1_500L
