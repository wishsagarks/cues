package com.cues.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cues.core.model.Routine
import com.cues.core.review.ReviewCopy

/**
 * Home: the mic (typed, this sprint — see CLEANUP.md on speech), the list of
 * cues, and each one's status at a glance.
 *
 * Speech input is deliberately not here. [com.cues.core.ports.SpeechInput]
 * has no implementation yet — see CLAUDE.md's note on that — so typed input
 * is the path that actually works, and it exercises the same [CueService]
 * call a spoken request would.
 */
@Composable
fun HomeScreen(
    routines: List<Routine>,
    isDrafting: Boolean,
    onDraft: (String) -> Unit,
    onOpenRoutine: (Routine) -> Unit,
    onOpenReceipts: () -> Unit,
) {
    var text by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Cues", style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = onOpenReceipts) { Text("Receipts") }
        }
        Text(
            "Context you declare. Behaviour that ends.",
            style = MaterialTheme.typography.bodySmall,
            color = cuesColors.ink200,
            modifier = Modifier.padding(bottom = 20.dp),
        )

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Describe a cue") },
            placeholder = { Text("When my earbuds connect after 6 PM on weekdays...") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { if (text.isNotBlank()) onDraft(text) },
            enabled = !isDrafting && text.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isDrafting) {
                CircularProgressIndicator(modifier = Modifier.height(18.dp))
            } else {
                Text("Draft")
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "YOUR CUES",
            style = MaterialTheme.typography.labelSmall,
            color = cuesColors.ink200,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        if (routines.isEmpty()) {
            Text(
                "No cues yet. Describe one above.",
                style = MaterialTheme.typography.bodyMedium,
                color = cuesColors.ink200,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(routines, key = { it.id }) { routine ->
                    CueCard(routine, onClick = { onOpenRoutine(routine) })
                }
            }
        }
    }
}

@Composable
private fun CueCard(routine: Routine, onClick: () -> Unit) {
    Surface(
        color = cuesColors.bg300,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.fillMaxWidth()) {
            // The accent-to-go rail from the design system's CueCard component.
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(cuesColors.go),
            )
            Column(Modifier.padding(start = 14.dp, top = 16.dp, end = 16.dp, bottom = 16.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(routine.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            ReviewCopy.whenText(routine),
                            style = MaterialTheme.typography.bodySmall,
                            color = cuesColors.ink200,
                        )
                    }
                    StatusChip(routine.status.name.lowercase(), routine.status.toTone())
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    ReviewCopy.doText(routine),
                    style = MaterialTheme.typography.bodySmall,
                    color = cuesColors.ink200,
                )
            }
        }
    }
}
