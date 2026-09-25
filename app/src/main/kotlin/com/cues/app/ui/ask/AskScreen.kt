package com.cues.app.ui.ask

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.cues.app.runtime.InstalledApp
import com.cues.app.ui.AppPickerDialog
import com.cues.app.ui.AssistantHistory
import com.cues.app.ui.DevicePickerDialog
import com.cues.app.ui.TemplateGallery
import com.cues.app.ui.components.KineticButton
import com.cues.app.ui.components.SlabCard
import com.cues.app.ui.components.SlabTier
import com.cues.app.ui.components.TrustChip
import com.cues.app.ui.theme.CuesShape
import com.cues.app.ui.theme.CuesType
import com.cues.app.ui.theme.cuesTokens
import com.cues.core.assistant.PendingCommand
import com.cues.core.assistant.Turn
import com.cues.core.drafting.PairedDevice

/**
 * Ask: compose, converse and review drafts (redesign plan §3.3). Reuses
 * [TemplateGallery], [DevicePickerDialog] and [AppPickerDialog] from
 * HomeScreen.kt rather than re-implementing them — same permission flow,
 * same picker semantics, only the shell around them is new.
 */
@Composable
fun AskScreen(
    isDrafting: Boolean,
    onDraft: (String) -> Unit,
    drafterLabel: String,
    onStartVoice: (onTranscript: (String) -> Unit, onUnavailable: (String) -> Unit) -> Unit,
    deviceCandidates: List<PairedDevice>?,
    onSelectDevice: (PairedDevice) -> Unit,
    onDismissDevicePicker: () -> Unit,
    appQuery: String?,
    appCandidates: List<InstalledApp>?,
    onSelectApp: (InstalledApp) -> Unit,
    onDismissAppPicker: () -> Unit,
    assistantTurns: List<Turn>,
    onConfirmCommand: (PendingCommand) -> Unit,
    onHandoffToJovi: () -> Unit,
    incomingText: String?,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    LaunchedEffect(incomingText) {
        if (!incomingText.isNullOrBlank()) text = incomingText
    }
    var isListening by remember { mutableStateOf(false) }
    var speechMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val t = cuesTokens

    val startVoice = {
        speechMessage = null
        isListening = true
        onStartVoice(
            { transcript -> text = transcript; isListening = false },
            { message -> speechMessage = message; isListening = false },
        )
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoice() else speechMessage = "Microphone access was not granted. Type your cue instead."
    }
    var pendingDeviceDraft by remember { mutableStateOf<String?>(null) }
    val bluetoothPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val pending = pendingDeviceDraft
        pendingDeviceDraft = null
        if (granted && pending != null) onDraft(pending)
        else if (!granted) speechMessage = "Bluetooth access is needed to pick a paired device. You can still describe a charging cue."
    }
    val submitDraft = {
        val needsBluetoothPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            com.cues.app.ui.textMentionsDevice(text) &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        if (needsBluetoothPermission) {
            pendingDeviceDraft = text
            bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            onDraft(text)
            text = ""
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { TrustChip("DRAFTING: $drafterLabel") }

        if (assistantTurns.isNotEmpty()) {
            item {
                SlabCard(tier = SlabTier.ONE, modifier = Modifier.fillMaxWidth()) {
                    AssistantHistory(assistantTurns, onConfirmCommand, onHandoffToJovi)
                }
            }
        }

        // TemplateGallery renders its own "TRY A TEMPLATE" label internally.
        item { TemplateGallery(onPick = { template -> text = template.sentence }) }

        item {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Describe a cue, or ask about one") },
                placeholder = { Text("When my earbuds connect after 6 PM on weekdays…") },
                minLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = t.doYellow,
                    unfocusedContainerColor = t.recess,
                    focusedContainerColor = t.recess,
                    unfocusedTextColor = t.inkPrimary,
                    focusedTextColor = t.inkPrimary,
                ),
                shape = CuesShape.card,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                com.cues.app.ui.components.GhostButton(
                    text = if (isListening) "Listening…" else "Speak",
                    enabled = !isListening && !isDrafting,
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            startVoice()
                        } else {
                            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                KineticButton(
                    text = "Ask Cues",
                    onClick = submitDraft,
                    enabled = !isDrafting && text.isNotBlank(),
                    loading = isDrafting,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        speechMessage?.let { message ->
            item { Text(message, style = CuesType.labelSmall, color = t.warn) }
        }

        item {
            AnimatedVisibility(visible = isListening || isDrafting) {
                Column {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = t.doYellow, trackColor = t.raised)
                    Text(
                        if (isListening) "Listening on this phone…" else "Turning your words into a reviewable cue…",
                        style = CuesType.labelSmall,
                        color = t.inkSlate,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }

        item {
            Text(
                "Speech stays on this phone. If it's unavailable, type or correct the transcript here.",
                style = CuesType.labelSmall,
                color = t.inkSlate,
            )
        }

        item { Spacer(Modifier.height(72.dp)) }
    }

    deviceCandidates?.let { candidates ->
        DevicePickerDialog(candidates = candidates, onSelectDevice = onSelectDevice, onDismiss = onDismissDevicePicker)
    }
    appCandidates?.let { candidates ->
        AppPickerDialog(query = appQuery.orEmpty(), candidates = candidates, onSelectApp = onSelectApp, onDismiss = onDismissAppPicker)
    }
}
