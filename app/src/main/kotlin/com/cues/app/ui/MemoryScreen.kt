package com.cues.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cues.core.model.Fact

@Composable
fun MemoryScreen(facts: List<Fact>, onDelete: (Fact) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Declared Memory", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onBack) { Text("Back") }
        }
        Text(
            "Only facts you explicitly said or reviewed. Each fact shows its source and can be deleted.",
            style = MaterialTheme.typography.bodySmall,
            color = cuesColors.ink200,
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 16.dp)) {
            items(facts, key = { it.id }) { fact ->
                Surface(color = cuesColors.bg300, shape = MaterialTheme.shapes.medium) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(fact.label, style = MaterialTheme.typography.titleMedium)
                            Text(fact.value)
                            Text(
                                "${fact.kind.name.lowercase()} · ${fact.source.name.lowercase().replace('_', ' ')} · v${fact.version}",
                                style = MaterialTheme.typography.labelSmall,
                                color = cuesColors.ink200,
                            )
                        }
                        TextButton(onClick = { onDelete(fact) }) { Text("Delete") }
                    }
                }
            }
        }
    }
}
