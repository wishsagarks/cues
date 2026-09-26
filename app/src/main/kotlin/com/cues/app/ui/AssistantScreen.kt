package com.cues.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cues.app.ui.theme.CuesShape
import com.cues.app.ui.theme.CuesType
import com.cues.app.ui.theme.cuesTokens
import com.cues.core.assistant.PendingCommand
import com.cues.core.assistant.ReplyChip
import com.cues.core.assistant.Turn

/** Chat-style history of the current conversation, embedded in AskScreen. */
@Composable
fun AssistantHistory(
    turns: List<Turn>,
    onConfirm: (PendingCommand) -> Unit,
    onHandoff: () -> Unit,
) {
    val t = cuesTokens
    if (turns.isEmpty()) {
        Text(
            "No conversation yet — ask about a cue or describe one above.",
            style = CuesType.labelSmall,
            color = t.inkSlate,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        return
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        turns.takeLast(6).forEach { turn ->
            // User bubble — right-aligned
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(
                    modifier = Modifier
                        .background(t.doYellow.copy(alpha = 0.12f), CuesShape.card)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(turn.userText, style = CuesType.body, color = t.inkPrimary)
                }
            }
            // Cues reply bubble — left-aligned
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                Column(
                    modifier = Modifier
                        .background(t.raised, CuesShape.card)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(turn.reply.text, style = CuesType.body, color = t.inkSecondary)
                    Text(
                        "via ${turn.reply.answeredFrom.name.lowercase().replace('_', ' ')}",
                        style = CuesType.labelSmall,
                        color = t.inkSlate,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (turn.reply.chips.isNotEmpty()) {
                        Row(modifier = Modifier.padding(top = 4.dp)) {
                            turn.reply.chips.forEach { chip ->
                                when (chip) {
                                    is ReplyChip.Confirm -> TextButton(onClick = {
                                        turn.pendingCommand?.takeIf { it.id == chip.commandId }?.let(onConfirm)
                                    }) { Text(chip.label, style = CuesType.labelSmall, color = t.doYellow) }
                                    is ReplyChip.Handoff -> TextButton(onClick = onHandoff) {
                                        Text(chip.label, style = CuesType.labelSmall, color = t.doYellow)
                                    }
                                    is ReplyChip.Choice -> Text(
                                        "${chip.label}  ",
                                        style = CuesType.labelSmall,
                                        color = t.inkSlate,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
