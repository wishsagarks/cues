package com.cues.app.ui.ask

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.cues.app.drafting.RunnerState
import com.cues.app.runtime.InstalledApp
import com.cues.app.ui.AppPickerDialog
import com.cues.app.ui.AssistantHistory
import com.cues.app.ui.DevicePickerDialog
import com.cues.app.ui.TemplateGallery
import com.cues.app.ui.components.KineticButton
import com.cues.app.ui.components.DraftPipelineStrip
import com.cues.app.ui.components.GeminiGlowFrame
import com.cues.app.ui.components.ModelStatusBar
import com.cues.app.ui.components.SlabCard
import com.cues.app.ui.components.SlabTier
import com.cues.app.ui.theme.CuesShape
import com.cues.app.ui.theme.CuesType
import com.cues.app.ui.theme.cuesTokens
import com.cues.app.voice.AuthoringLanguage
import com.cues.core.assistant.PendingCommand
import com.cues.core.assistant.Turn
import com.cues.core.drafting.DraftTrace
import com.cues.core.drafting.PairedDevice
import com.cues.core.ports.ModelAvailability

/**
 * Ask: compose, converse and review drafts (redesign plan §3.3). Reuses
 * [TemplateGallery], [DevicePickerDialog] and [AppPickerDialog] from
 * HomeScreen.kt rather than re-implementing them — same permission flow,
 * same picker semantics, only the shell around them is new.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun AskScreen(
    isDrafting: Boolean,
    onDraft: (String) -> Unit,
    modelAvailability: ModelAvailability,
    runnerState: RunnerState,
    lastTrace: DraftTrace?,
    onDeviceModelEnabled: Boolean,
    onToggleOnDeviceModel: (Boolean) -> Unit,
    onStartVoice: (onTranscript: (String) -> Unit, onUnavailable: (String) -> Unit) -> Unit,
    onStopVoice: () -> Unit = {},
    cloudAssistAvailable: Boolean = false,
    cloudAssistEnabled: Boolean = false,
    onToggleCloudAssist: () -> Unit = {},
    authoringLanguage: AuthoringLanguage = AuthoringLanguage.English,
    onAuthoringLanguageChange: (AuthoringLanguage) -> Unit = {},
    onStartCloudVoice: (languageCode: String, onResult: (String) -> Unit, onError: (String) -> Unit) -> Unit =
        { _, _, onError -> onError("Cloud assist is not available.") },
    onStopCloudVoice: () -> Unit = {},
    isTryingCloudAssist: Boolean = false,
    onTranslateReadback: (englishText: String, onDone: (String) -> Unit, onError: (String) -> Unit) -> Unit =
        { _, _, onError -> onError("Cloud assist is not available.") },
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
    var isCloudListening by remember { mutableStateOf(false) }
    var cloudOriginalTranscript by remember { mutableStateOf<String?>(null) }
    var cloudReadbackText by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val t = cuesTokens

    val startVoice = {
        speechMessage = null
        isListening = true
        isCloudListening = cloudAssistEnabled
        if (cloudAssistEnabled) {
            onStartCloudVoice(
                authoringLanguage.languageCode,
                { original -> text = original; cloudOriginalTranscript = original; isListening = false; isCloudListening = false },
                { message -> speechMessage = message; isListening = false; isCloudListening = false },
            )
        } else {
            onStartVoice(
                { transcript -> text = transcript; isListening = false; isCloudListening = false },
                { message -> speechMessage = message; isListening = false; isCloudListening = false },
            )
        }
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoice() else speechMessage = "Microphone access was not granted. Type your cue instead."
    }
    val submitDraft = {
        onDraft(text)
        text = ""
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ModelStatusBar(
                availability = modelAvailability,
                runnerState = runnerState,
                enabled = onDeviceModelEnabled,
                onEnabledChange = onToggleOnDeviceModel,
            )
        }

        if (cloudAssistAvailable) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Cloud language assist (Sarvam)", style = CuesType.labelSmall, color = t.inkPrimary)
                    }
                    Switch(checked = cloudAssistEnabled, onCheckedChange = { onToggleCloudAssist() })
                }
                Text(
                    "Authoring language",
                    style = CuesType.labelSmall,
                    color = t.inkPrimary,
                    modifier = Modifier.padding(top = 8.dp),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AuthoringLanguage.choices.forEach { language ->
                        FilterChip(
                            selected = authoringLanguage == language,
                            onClick = { onAuthoringLanguageChange(language) },
                            enabled = cloudAssistEnabled || onDeviceModelEnabled || language == AuthoringLanguage.English,
                            label = { Text(language.displayLabel) },
                        )
                    }
                }
            }
        }

        // TemplateGallery renders its own "TRY A TEMPLATE" label internally.
        item { TemplateGallery(onPick = { template -> text = template.sentence }) }

        item {
            GeminiGlowFrame(modifier = Modifier.fillMaxWidth(), rotating = false) {
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
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                if (isListening) {
                    com.cues.app.ui.components.GhostButton(
                        text = "Stop",
                        enabled = true,
                        onClick = {
                            isListening = false
                            isCloudListening = false
                            if (cloudAssistEnabled) onStopCloudVoice() else onStopVoice()
                        },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    com.cues.app.ui.components.GhostButton(
                        text = "Speak",
                        enabled = !isDrafting,
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                startVoice()
                            } else {
                                microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                KineticButton(
                    text = "Ask Cues",
                    onClick = submitDraft,
                    enabled = !isDrafting && text.isNotBlank(),
                    loading = isDrafting,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (cloudAssistEnabled) {
            cloudOriginalTranscript?.let { original ->
                item {
                    Text(
                        "Heard in ${authoringLanguage.displayLabel} (via Sarvam): $original",
                        style = CuesType.labelSmall,
                        color = t.inkSlate,
                    )
                }
            }
        }

        speechMessage?.let { message ->
            item { Text(message, style = CuesType.labelSmall, color = t.warn) }
        }

        if (isDrafting || lastTrace != null) {
            item { DraftPipelineStrip(trace = lastTrace, runnerState = runnerState, isDrafting = isDrafting) }
        }

        item {
            AnimatedVisibility(visible = isListening || isCloudListening || isTryingCloudAssist) {
                Column {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = t.doYellow, trackColor = t.raised)
                    Text(
                        when {
                            isListening && isCloudListening -> "Listening, then sending to Sarvam (online)…"
                            isListening -> "Listening on this phone…"
                            isTryingCloudAssist -> "Trying cloud assist (Sarvam, online)…"
                            else -> "Turning your words into a reviewable cue…"
                        },
                        style = CuesType.labelSmall,
                        color = t.inkSlate,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }

        item {
            Text(
                if (cloudAssistEnabled) {
                    "The default mic keeps speech on this phone. Cloud language assist, when used, sends audio or text to Sarvam over the network."
                } else {
                    "Speech stays on this phone. If it's unavailable, type or correct the transcript here."
                },
                style = CuesType.labelSmall,
                color = t.inkSlate,
            )
        }

        item {
            Text(
                "RECENT",
                style = CuesType.labelSmall,
                color = t.inkSlate,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
            )
        }
        item {
            SlabCard(tier = SlabTier.ONE, modifier = Modifier.fillMaxWidth()) {
                Column {
                    AssistantHistory(assistantTurns, onConfirmCommand, onHandoffToJovi)
                    if (cloudAssistEnabled && assistantTurns.isNotEmpty()) {
                        TextButton(onClick = {
                            cloudReadbackText = null
                            onTranslateReadback(
                                assistantTurns.last().reply.text,
                                { translated -> cloudReadbackText = translated },
                                { message -> speechMessage = message },
                            )
                        }) {
                            Text("Translate & speak (Sarvam, online)")
                        }
                        cloudReadbackText?.let { translated ->
                            Text(
                                "Translated via Sarvam (online): $translated",
                                style = CuesType.labelSmall,
                                color = t.inkSlate,
                            )
                        }
                    }
                }
            }
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
