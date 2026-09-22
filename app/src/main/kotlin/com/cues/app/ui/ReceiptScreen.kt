package com.cues.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cues.core.store.ReceiptEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Receipt history: what actually happened, most recent first, built straight
 * from what [com.cues.core.CueService] already wrote through [com.cues.core.ports.ReceiptSink] —
 * no separate explanation layer, because a receipt that isn't the actual
 * record of what ran is the one thing this product is built not to do.
 */
@Composable
fun ReceiptScreen(receipts: List<ReceiptEntry>, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Receipts", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Every start, skip and end this app has recorded.",
            style = MaterialTheme.typography.bodySmall,
            color = cuesColors.ink200,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        if (receipts.isEmpty()) {
            Text(
                "Nothing has happened yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = cuesColors.ink200,
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(receipts, key = { it.sessionId + it.atMillis }) { entry ->
                    ReceiptCard(entry)
                }
            }
        }

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Text("Back")
        }
    }
}

@Composable
private fun ReceiptCard(entry: ReceiptEntry) {
    val lines = entry.text.lines().filter { it.isNotBlank() }
    val headline = lines.firstOrNull().orEmpty()
    val detail = lines.drop(1)

    Surface(
        color = cuesColors.bg300,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
    ) {
        Column(Modifier.padding(13.dp)) {
            Text(TIME_FORMAT.format(Date(entry.atMillis)), style = MaterialTheme.typography.labelSmall, color = cuesColors.ink200)
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

private val TIME_FORMAT = SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault())
