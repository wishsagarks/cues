package com.cues.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.cues.core.drafting.PairedDevice
import com.cues.core.model.Routine
import com.cues.core.review.ReviewCopy

/**
 * Home: gesture-started local speech, editable typed input, and the list of
 * cues. A failed local-speech attempt is disclosed here and leaves typed input
 * available; it never falls back to a network recognizer.
 */
@Composable
fun HomeScreen(
    routines: List<Routine>,
    isDrafting: Boolean,
    onDraft: (String) -> Unit,
    onOpenRoutine: (Routine) -> Unit,
    onOpenReceipts: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onStartVoice: (onTranscript: (String) -> Unit, onUnavailable: (String) -> Unit) -> Unit,
    deviceCandidates: List<PairedDevice>?,
    onSelectDevice: (PairedDevice) -> Unit,
    onDismissDevicePicker: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var speechMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val startVoice = {
        speechMessage = null
        isListening = true
        onStartVoice(
            { transcript ->
                text = transcript
                isListening = false
            },
            { message ->
                speechMessage = message
                isListening = false
            },
        )
    }
    val microphonePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startVoice()
        else speechMessage = "Microphone access was not granted. Type your cue instead."
    }
    var pendingDeviceDraft by remember { mutableStateOf<String?>(null) }
    val bluetoothPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val pending = pendingDeviceDraft
        pendingDeviceDraft = null
        if (granted && pending != null) {
            onDraft(pending)
        } else if (!granted) {
            speechMessage = "Bluetooth access is needed to pick a paired device. You can still describe a charging cue."
        }
    }
    val submitDraft = {
        val needsBluetoothPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            textMentionsDevice(text) &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        if (needsBluetoothPermission) {
            pendingDeviceDraft = text
            bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            onDraft(text)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Cues", style = MaterialTheme.typography.headlineMedium)
            Row {
                TextButton(onClick = onOpenDiagnostics) { Text("Checks") }
                TextButton(onClick = onOpenReceipts) { Text("Receipts") }
            }
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
        androidx.compose.material3.OutlinedButton(
            onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    startVoice()
                } else {
                    microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            enabled = !isListening && !isDrafting,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (isListening) "Listening locally…" else "Speak a cue") }
        Text(
            "Speech stays on this phone. If it is unavailable, you can type or correct the transcript here.",
            style = MaterialTheme.typography.bodySmall,
            color = cuesColors.ink200,
            modifier = Modifier.padding(top = 6.dp),
        )
        speechMessage?.let { message ->
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = cuesColors.amber,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = submitDraft,
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

    deviceCandidates?.let { candidates ->
        DevicePickerDialog(
            candidates = candidates,
            onSelectDevice = onSelectDevice,
            onDismiss = onDismissDevicePicker,
        )
    }
}

@Composable
private fun DevicePickerDialog(
    candidates: List<PairedDevice>,
    onSelectDevice: (PairedDevice) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Which paired device?") },
        text = {
            Column {
                Text(
                    "Choose the device this cue should listen for. Cues will bind the rule to that device, not to a guessed category.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cuesColors.ink200,
                )
                candidates.forEach { device ->
                    androidx.compose.material3.TextButton(
                        onClick = { onSelectDevice(device) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(device.label) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun textMentionsDevice(text: String): Boolean = Regex(
    "\\b(earbuds|ear buds|buds|headphones|headset|airpods|speaker|watch|car)\\b",
    RegexOption.IGNORE_CASE,
).containsMatchIn(text)

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
