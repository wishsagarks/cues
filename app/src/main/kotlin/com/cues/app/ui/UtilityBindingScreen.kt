package com.cues.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cues.app.runtime.CuesAccessibilityService
import com.cues.core.compile.MacroValidator
import com.cues.core.model.UiMacro
import com.cues.core.model.UtilityBinding
import com.cues.core.model.UtilityId
import com.cues.core.model.UtilityState
import com.cues.core.registry.UtilityCatalog

/**
 * Teach, test and manage the on/off macro pair behind each cataloged
 * utility. Reflects the FDD's "Utility Bindings" section directly: the
 * consent line names the exact utilities this can touch, teaching is the
 * user performing the real steps themselves, and every taught macro is
 * shown to [MacroValidator] before it can be saved.
 */
@Composable
fun UtilityBindingScreen(
    bindings: Map<UtilityId, UtilityBinding>,
    onSave: (UtilityId, UtilityState, UiMacro) -> List<String>,
    onDeleteBinding: (UtilityId) -> Unit,
    onTest: (UtilityId, UtilityState) -> Unit,
    lastTestResult: String?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val serviceRunning = CuesAccessibilityService.isRunning()
    val recordingTarget by CuesAccessibilityService.recordingTarget.collectAsState()
    val recordedSteps by CuesAccessibilityService.recordedSteps.collectAsState()
    var pendingErrors by remember { mutableStateOf<List<String>>(emptyList()) }
    var teachLabel by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Utility bindings", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onBack) { Text("Back") }
        }

        Text(
            "Cues works with these iQOO utilities: " +
                UtilityCatalog.all.joinToString(", ") { it.label } +
                ". Turning one on or off replays a macro you teach yourself — Cues never generates or edits the steps.",
            style = MaterialTheme.typography.bodySmall,
            color = cuesColors.ink200,
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
        )

        if (!serviceRunning) {
            Surface(color = cuesColors.bg300, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("\"Cues: iQOO utility bindings\" is not enabled.", style = MaterialTheme.typography.bodyMedium)
                    Button(
                        onClick = {
                            context.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    ) { Text("Open Accessibility settings") }
                }
            }
        }

        lastTestResult?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
        }

        if (recordingTarget != null) {
            val (utilityId, state) = recordingTarget!!
            Surface(color = cuesColors.bg300, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "Recording: ${UtilityCatalog.definition(utilityId).label} ${state.name.lowercase()}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "Now go perform the real toggle in its own app. Cues is listening for your taps. " +
                            "${recordedSteps.size} step${if (recordedSteps.size == 1) "" else "s"} captured so far.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cuesColors.ink200,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    OutlinedTextField(
                        value = teachLabel,
                        onValueChange = { teachLabel = it },
                        label = { Text("Name this macro") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    pendingErrors.forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    Row(modifier = Modifier.padding(top = 8.dp)) {
                        Button(onClick = {
                            val label = teachLabel.ifBlank { "${UtilityCatalog.definition(utilityId).label} ${state.name.lowercase()}" }
                            val macro = CuesAccessibilityService.finishRecording(label)
                            if (macro == null) {
                                pendingErrors = listOf("Nothing was recorded yet.")
                            } else {
                                val errors = onSave(utilityId, state, macro)
                                if (errors.isEmpty()) {
                                    pendingErrors = emptyList()
                                    teachLabel = ""
                                } else {
                                    pendingErrors = errors
                                }
                            }
                        }) { Text("Finish teaching") }
                        TextButton(onClick = {
                            CuesAccessibilityService.stopRecording()
                            pendingErrors = emptyList()
                            teachLabel = ""
                        }, modifier = Modifier.padding(start = 8.dp)) { Text("Cancel") }
                    }
                }
            }
        }

        LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
            items(UtilityCatalog.all) { definition ->
                val binding = bindings[definition.id]
                val onTaught = binding?.onMacroId?.isNotBlank() == true
                val offTaught = binding?.offMacroId?.isNotBlank() == true
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Text(definition.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Mechanism: ${definition.mechanism.name.lowercase().replace('_', ' ')} · read back via ${definition.readBackDescription}",
                        style = MaterialTheme.typography.labelSmall,
                        color = cuesColors.ink200,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        Text(
                            when {
                                onTaught && offTaught -> "On and off both taught"
                                onTaught -> "Only \"on\" is taught"
                                offTaught -> "Only \"off\" is taught"
                                else -> "Not taught"
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Row(modifier = Modifier.padding(top = 6.dp)) {
                        TextButton(
                            enabled = serviceRunning && recordingTarget == null,
                            onClick = { CuesAccessibilityService.startRecording(definition.id, UtilityState.ON) },
                        ) { Text("Teach on") }
                        TextButton(
                            enabled = serviceRunning && recordingTarget == null,
                            onClick = { CuesAccessibilityService.startRecording(definition.id, UtilityState.OFF) },
                        ) { Text("Teach off") }
                        if (onTaught) {
                            TextButton(
                                enabled = serviceRunning,
                                onClick = { onTest(definition.id, UtilityState.ON) },
                            ) { Text("Test on") }
                        }
                        if (offTaught) {
                            TextButton(
                                enabled = serviceRunning,
                                onClick = { onTest(definition.id, UtilityState.OFF) },
                            ) { Text("Test off") }
                        }
                        if (binding != null) {
                            TextButton(onClick = { onDeleteBinding(definition.id) }) { Text("Forget") }
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
