package com.cues.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cues.core.assistant.PendingCommand
import com.cues.core.assistant.ReplyChip
import com.cues.core.assistant.Turn

/** Compact chat history embedded above Home's authoring field. */
@Composable
fun AssistantHistory(
    turns: List<Turn>,
    onConfirm: (PendingCommand) -> Unit,
    onHandoff: () -> Unit,
) {
    if (turns.isEmpty()) return
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    ) {
        turns.takeLast(4).forEach { turn ->
            Surface(color = cuesColors.bg300, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(turn.userText, style = MaterialTheme.typography.bodySmall, color = cuesColors.ink200)
                    Text(turn.reply.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 5.dp))
                    Text(
                        "answered from ${turn.reply.answeredFrom.name.lowercase().replace('_', ' ')}",
                        style = MaterialTheme.typography.labelSmall,
                        color = cuesColors.ink200,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                    Row {
                        turn.reply.chips.forEach { chip ->
                            when (chip) {
                                is ReplyChip.Confirm -> TextButton(onClick = {
                                    turn.pendingCommand?.takeIf { it.id == chip.commandId }?.let(onConfirm)
                                }) { Text(chip.label) }
                                is ReplyChip.Handoff -> TextButton(onClick = onHandoff) { Text(chip.label) }
                                is ReplyChip.Choice -> Text("${chip.label} · ", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
