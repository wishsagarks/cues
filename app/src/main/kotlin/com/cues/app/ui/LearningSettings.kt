package com.cues.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cues.core.coach.LedgerEvent

@Composable
fun LearningSettings(
    enabled: Boolean,
    events: List<LedgerEvent>,
    onEnabledChange: (Boolean) -> Unit,
    onWipe: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Learning", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onBack) { Text("Back") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Notice on-device patterns")
                Text(
                    "Optional 14-day signal log. It stays on this phone; the app has no internet permission.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cuesColors.ink200,
                )
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
        Text("${events.size} local event${if (events.size == 1) "" else "s"} retained", modifier = Modifier.padding(top = 16.dp))
        events.takeLast(20).reversed().forEach { event ->
            Text(event.toString(), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
        }
        Button(onClick = onWipe, modifier = Modifier.padding(top = 20.dp)) { Text("Wipe learning history") }
    }
}
