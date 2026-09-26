package com.cues.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.cues.app.runtime.AdapterStatus
import com.cues.app.R
import androidx.compose.foundation.lazy.LazyRow
import com.cues.core.drafting.PairedDevice
import com.cues.core.model.Routine
import com.cues.core.review.ReviewCopy
import com.cues.core.review.Template
import com.cues.core.review.Templates
import com.cues.core.assistant.PendingCommand
import com.cues.core.assistant.Turn
import com.cues.core.coach.Suggestion
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Home: gesture-started local speech, editable typed input, and the list of
 * cues. A failed local-speech attempt is disclosed here and leaves typed input
 * available; it never falls back to a network recognizer.
 */
@Composable
fun HomeScreen(
    routines: List<Routine>,
    adapterStatuses: List<AdapterStatus>,
    isDrafting: Boolean,
    onDraft: (String) -> Unit,
    onOpenRoutine: (Routine) -> Unit,
    onOpenReceipts: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenToday: () -> Unit,
    onOpenContexts: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenLearning: () -> Unit,
    onOpenUtilityBindings: () -> Unit = {},
    onOpenTimetableCapture: () -> Unit = {},
    onOpenCueCardScan: () -> Unit = {},
    onExportConsole: () -> Unit = {},
    speakReplies: Boolean = false,
    onToggleSpeakReplies: () -> Unit = {},
    onStartVoice: (onTranscript: (String) -> Unit, onUnavailable: (String) -> Unit) -> Unit,
    deviceCandidates: List<PairedDevice>?,
    onSelectDevice: (PairedDevice) -> Unit,
    onDismissDevicePicker: () -> Unit,
    appQuery: String? = null,
    appCandidates: List<com.cues.app.runtime.InstalledApp>? = null,
    onSelectApp: (com.cues.app.runtime.InstalledApp) -> Unit = {},
    onDismissAppPicker: () -> Unit = {},
    assistantTurns: List<Turn> = emptyList(),
    onConfirmCommand: (PendingCommand) -> Unit = { _ -> },
    onHandoffToJovi: () -> Unit = {},
    coachSuggestion: Suggestion? = null,
    onAcceptSuggestion: (Suggestion) -> Unit = { _ -> },
    onDismissSuggestion: (Suggestion, Boolean) -> Unit = { _, _ -> },
    incomingText: String? = null,
) {
    var text by remember { mutableStateOf("") }
    // A "Cue this screen" capture (Task 16) or a shared text/plain (Task 9's
    // share target) lands here as plain draft text — data the user can edit
    // or delete, never something that arms itself. LaunchedEffect keyed on
    // the value so it only fires once per distinct incoming text, not on
    // every recomposition.
    androidx.compose.runtime.LaunchedEffect(incomingText) {
        if (!incomingText.isNullOrBlank()) text = incomingText
    }
    var isListening by remember { mutableStateOf(false) }
    var speechMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val logoScale by animateFloatAsState(if (isListening) 1.08f else 1f, spring(stiffness = 420f), label = "logo listening")
    val logoLift by animateFloatAsState(if (isListening) -4f else 0f, spring(stiffness = 420f), label = "logo lift")
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

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Surface(
            color = Color.Transparent,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                // A restrained, iQOO-like performance wash:
                                // bright at the entry edge, then falling back
                                // into Cues' readable surface rather than
                                // turning the whole hero into a neon banner.
                                Color(0xFF00E5FF).copy(alpha = 0.22f),
                                cuesColors.go.copy(alpha = 0.18f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                cuesColors.bg200.copy(alpha = 0.84f),
                                cuesColors.bg200,
                            ),
                        ),
                    )
                    .padding(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        Text("Cues", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text("Context you\ndeclare.", style = MaterialTheme.typography.headlineMedium)
                    }
                    androidx.compose.foundation.Image(
                        painter = painterResource(R.drawable.cues_logo),
                        contentDescription = "Cues logo",
                        modifier = Modifier.size(64.dp).offset(y = logoLift.dp).graphicsLayer(scaleX = logoScale, scaleY = logoScale),
                    )
                    Row {
                        TextButton(onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenDiagnostics()
                        }) { Text("Checks") }
                        TextButton(onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenReceipts()
                        }) { Text("History") }
                    }
                }
                Text(
                    "Name the context. Review the boundary. Let the phone do only what you approved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cuesColors.ink200,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Row(modifier = Modifier.padding(top = 6.dp)) {
                    TextButton(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenToday()
                    }) { Text("Today") }
                    TextButton(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenContexts()
                    }) { Text("Contexts & places") }
                    TextButton(onClick = onOpenMemory) { Text("Memory") }
                    TextButton(onClick = onOpenLearning) { Text("Learning") }
                    TextButton(onClick = onOpenTimetableCapture) { Text("Import timetable") }
                    TextButton(onClick = onOpenCueCardScan) { Text("Scan a Cue Card") }
                    TextButton(onClick = onExportConsole) { Text("Export Cue Console") }
                    TextButton(onClick = onOpenUtilityBindings) { Text("Utility bindings") }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        if (assistantTurns.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "ASK CUES",
                    style = MaterialTheme.typography.labelSmall,
                    color = cuesColors.ink200,
                )
                // Reads back Cues' own exact reply text — never a paraphrase,
                // never something that can approve or confirm on its own. See
                // ReplySpeaker.
                TextButton(onClick = onToggleSpeakReplies) {
                    Text(if (speakReplies) "Speaking replies" else "Speak replies")
                }
            }
        }
        AssistantHistory(assistantTurns, onConfirmCommand, onHandoffToJovi)
        coachSuggestion?.let { suggestion ->
            // CL-27: named by the cue's own title, never the raw routine id
            // suggestion.proposal used to embed ("make routine-<uuid> 22
            // minutes"). routines is this screen's own live list — the same
            // one Home already renders cue cards from — so this is a lookup,
            // never a second source of truth for what a cue is called.
            val targetTitle = suggestion.routineId?.let { id -> routines.firstOrNull { it.id == id }?.title }
            Surface(color = cuesColors.bg300, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Cues noticed a pattern", style = MaterialTheme.typography.titleMedium)
                    suggestion.evidence.forEach { Text(it.text, style = MaterialTheme.typography.bodySmall, color = cuesColors.ink200) }
                    Text(
                        if (targetTitle != null) "For “$targetTitle”: ${suggestion.proposal}" else suggestion.proposal,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Row {
                        // Only offer to act when there's a real, resolvable
                        // edit behind it (CL-27) — otherwise this card is
                        // evidence only, same as before this fix.
                        if (suggestion.operation != null) {
                            TextButton(onClick = { onAcceptSuggestion(suggestion) }) { Text("Review idea") }
                        }
                        TextButton(onClick = { onDismissSuggestion(suggestion, false) }) { Text("Not now") }
                        TextButton(onClick = { onDismissSuggestion(suggestion, true) }) { Text("Never") }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Ask Cues about your cues") },
            placeholder = { Text("When my earbuds connect after 6 PM on weekdays...") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        TemplateGallery(onPick = { template -> text = template.sentence })
        Spacer(Modifier.height(12.dp))
        androidx.compose.material3.OutlinedButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                submitDraft()
            },
            enabled = !isDrafting && text.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isDrafting) {
                CircularProgressIndicator(modifier = Modifier.height(18.dp))
            } else {
                Text("Ask Cues")
            }
        }

        AnimatedVisibility(visible = isListening || isDrafting) {
            Column(Modifier.padding(top = 10.dp)) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    if (isListening) "Listening on this phone…" else "Turning your words into a reviewable cue…",
                    style = MaterialTheme.typography.bodySmall,
                    color = cuesColors.ink200,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        MonitoringCard(adapterStatuses)

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

    appCandidates?.let { candidates ->
        AppPickerDialog(
            query = appQuery.orEmpty(),
            candidates = candidates,
            onSelectApp = onSelectApp,
            onDismiss = onDismissAppPicker,
        )
    }
}

/**
 * Monitoring health (4.6 / MH-01): when each adapter last saw an event.
 *
 * Deliberately does not claim a listener is "live" — a manifest receiver has
 * no such signal to report, and claiming one would be exactly the
 * overclaiming the FDD's "a receiver declaration is not proof of delivery"
 * line rules out. Coverage gaps this session finds show up as ordinary
 * receipts (EndReason.COVERAGE_GAP), not a separate banner here.
 */
@Composable
private fun MonitoringCard(statuses: List<AdapterStatus>) {
    Surface(
        color = cuesColors.bg300,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("MONITORING", style = MaterialTheme.typography.labelSmall, color = cuesColors.ink200)
            statuses.forEach { status ->
                Text(
                    "${status.label}: " + (status.running?.let { if (it) "listening; " else "not listening; " }.orEmpty()) + (
                        status.lastEventAtMillis?.let { MONITORING_TIME_FORMAT.format(Date(it)) }
                            ?: "no event received yet"
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                "Background delivery remains unverified on this phone; listener registration is not delivery proof (R1/R7/R8).",
                style = MaterialTheme.typography.labelSmall,
                color = cuesColors.ink200,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

private val MONITORING_TIME_FORMAT = SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault())

/**
 * Curated starting sentences. Tapping one fills the input, exactly what
 * typing it by hand would do — it still goes through the same drafter,
 * validator and review, and the review still names whichever drafter ran.
 * A template is a suggestion for what to type, never a shortcut around them.
 */
@Composable
internal fun TemplateGallery(onPick: (Template) -> Unit) {
    val templates = remember { Templates.load() }
    if (templates.isEmpty()) return
    val haptics = LocalHapticFeedback.current
    val t = com.cues.app.ui.theme.cuesTokens
    Column {
        Text(
            "TRY A TEMPLATE",
            style = com.cues.app.ui.theme.CuesType.labelSmall,
            color = t.inkSlate,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(templates, key = { it.label }) { template ->
                Surface(
                    color = t.island,
                    contentColor = t.inkSecondary,
                    shape = com.cues.app.ui.theme.CuesShape.capsule,
                    border = androidx.compose.foundation.BorderStroke(1.dp, t.hairline),
                    modifier = Modifier.clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPick(template)
                    },
                ) {
                    Text(
                        template.label,
                        style = com.cues.app.ui.theme.CuesType.labelSmall,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun DevicePickerDialog(
    candidates: List<PairedDevice>,
    onSelectDevice: (PairedDevice) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
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
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectDevice(device)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(device.label) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Which installed app "open X" meant. The list is never empty text a model
 * guessed at — [candidates] comes from an actual `PackageManager` query,
 * ranked against [query], and the choice re-drafts with that exact package
 * name and label — see [com.cues.core.drafting.GrammarParser]'s
 * `action.app` clarification.
 */
@Composable
internal fun AppPickerDialog(
    query: String,
    candidates: List<com.cues.app.runtime.InstalledApp>,
    onSelectApp: (com.cues.app.runtime.InstalledApp) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Which app did you mean?") },
        text = {
            if (candidates.isEmpty()) {
                Text(
                    "No installed app matches \"$query\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = cuesColors.ink200,
                )
            } else {
                LazyColumn(modifier = Modifier.height(280.dp)) {
                    items(candidates, key = { it.packageName }) { app ->
                        androidx.compose.material3.TextButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectApp(app)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(app.label) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

internal fun textMentionsDevice(text: String): Boolean = Regex(
    "\\b(earbuds|ear buds|buds|headphones|headset|airpods|speaker|watch|car)\\b",
    RegexOption.IGNORE_CASE,
).containsMatchIn(text)

@Composable
private fun CueCard(routine: Routine, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Surface(
        color = cuesColors.bg300,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
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
