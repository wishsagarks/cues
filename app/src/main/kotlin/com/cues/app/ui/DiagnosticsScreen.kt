package com.cues.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.animateContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cues.app.runtime.DeviceDiagnostics
import com.cues.app.runtime.ManualObservation
import com.cues.core.CueService
import com.cues.core.inference.ModelProvisionState
import java.text.DateFormat
import java.util.Date

/** The recorded, target-phone-only checks from Sprint 3.0. */
@Composable
fun DiagnosticsScreen(
    diagnostics: DeviceDiagnostics,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onRecordJoviMicOrAssist: (ManualObservation) -> Unit,
    onRecordPermissionMonitor: (ManualObservation) -> Unit,
    onBack: () -> Unit,
    isBakingOff: Boolean = false,
    bakeOffReport: String? = null,
    onRunBakeOff: (() -> Unit)? = null,
    cueDiagnostics: CueService.Diagnostics? = null,
    modelProvisionState: ModelProvisionState = ModelProvisionState.NotInstalled,
    canDownloadModel: Boolean = false,
    isOnWifi: Boolean = false,
    npuEligible: Boolean = false,
    onDeviceModelEnabled: Boolean = false,
    onToggleOnDeviceModel: ((Boolean) -> Unit)? = null,
    onDownloadModel: (() -> Unit)? = null,
    onChooseModel: (() -> Unit)? = null,
    onCancelDownloadModel: (() -> Unit)? = null,
    onRemoveModel: (() -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    androidx.activity.compose.BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Device checks", style = MaterialTheme.typography.headlineSmall)
            OutlinedButton(onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onBack()
            }) { Text("Back") }
        }
        Text(
            "Run these checks on the event phone. Automatic checks use Android APIs; OriginOS observations stay manual.",
            style = MaterialTheme.typography.bodySmall,
            color = cuesColors.ink200,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(16.dp))

        DiagnosticCard("Phone", diagnostics.os)
        DiagnosticCard("On-device speech", diagnostics.onDeviceSpeech)
        DiagnosticCard("English (India) pack", diagnostics.englishIndiaPack)
        cueDiagnostics?.let { DiagnosticCard("Drafting path", it.render()) }

        if (onDownloadModel != null) {
            Spacer(Modifier.height(8.dp))
            ModelBrainCard(
                state = modelProvisionState,
                canDownload = canDownloadModel,
                isOnWifi = isOnWifi,
                npuEligible = npuEligible,
                lastInferenceReport = cueDiagnostics?.lastInferenceReport,
                enabled = onDeviceModelEnabled,
                onToggleEnabled = onToggleOnDeviceModel,
                onDownload = onDownloadModel,
                onChoose = onChooseModel,
                onCancel = onCancelDownloadModel,
                onRemove = onRemoveModel,
            )
        }

        Button(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onRefresh()
            },
            enabled = !isRefreshing,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (isRefreshing) "Checking…" else "Check this phone") }
        diagnostics.checkedAtMillis?.let { checkedAt ->
            Text(
                "Last checked: ${DateFormat.getDateTimeInstance().format(Date(checkedAt))}",
                style = MaterialTheme.typography.labelSmall,
                color = cuesColors.ink200,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        Spacer(Modifier.height(20.dp))
        ManualCheck(
            title = "Jovi mic or assist gesture",
            instruction = "Try the phone's microphone and assist gesture. Record whether Jovi prevented Cues from receiving the intended user gesture.",
            observation = diagnostics.joviMicOrAssist,
            onRecord = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onRecordJoviMicOrAssist(it)
            },
        )
        Spacer(Modifier.height(12.dp))
        ManualCheck(
            title = "OriginOS permission monitor",
            instruction = "After arming a Bluetooth or charging cue, inspect OriginOS's permission activity. Record whether it flags Cues' listeners; Android does not expose this suggestion to apps.",
            observation = diagnostics.permissionMonitor,
            onRecord = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onRecordPermissionMonitor(it)
            },
        )

        if (onRunBakeOff != null) {
            Spacer(Modifier.height(20.dp))
            Text(
                "R5 drafter bake-off",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Scores the grammar parser and the on-device model against the checked-in corpus, " +
                    "on this phone. Not a substitute for docs/MEASUREMENTS.md — record the numbers there by hand.",
                style = MaterialTheme.typography.bodySmall,
                color = cuesColors.ink200,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onRunBakeOff()
                },
                enabled = !isBakingOff,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (isBakingOff) "Running…" else "Run bake-off") }
            bakeOffReport?.let { report ->
                Text(
                    report,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/**
 * Names the drafter that actually runs and, when a model has drafted at
 * least once, which backend loaded it — never a claim about what *would*
 * run, only what already did. See [com.cues.core.inference.InferenceReport].
 */
private fun CueService.Diagnostics.render(): String = buildString {
    append(primaryDrafter.name.lowercase().replace('_', ' '))
    lastInferenceReport?.let { report ->
        append(" — last ran on ${report.backend.name}")
        append(", ${report.loadMs}ms load, ~${"%.1f".format(report.tokensPerSecond)} tok/s")
    }
    lastFallbackReason?.let { append(" — fell back: $it") }
}

@Composable
private fun DiagnosticCard(label: String, value: String) {
    Surface(
        color = cuesColors.bg300,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).animateContentSize(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = cuesColors.ink200)
            Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

/**
 * CL-36: the download/install tile for the on-device model ("Cues Brain").
 *
 * The one rule this card must never break: it may say what tier is
 * *eligible* ([npuEligible], a SoC-allowlist check) but never that NPU *is*
 * accelerating anything — that claim only ever comes from [lastInferenceReport],
 * a real run's own [InferenceReport.backend][com.cues.core.inference.InferenceReport],
 * exactly [com.cues.core.inference.InferenceReport]'s own honesty rule.
 */
@Composable
private fun ModelBrainCard(
    state: ModelProvisionState,
    canDownload: Boolean,
    isOnWifi: Boolean,
    npuEligible: Boolean,
    lastInferenceReport: com.cues.core.inference.InferenceReport?,
    enabled: Boolean,
    onToggleEnabled: ((Boolean) -> Unit)?,
    onDownload: () -> Unit,
    onChoose: (() -> Unit)?,
    onCancel: (() -> Unit)?,
    onRemove: (() -> Unit)?,
) {
    val haptics = LocalHapticFeedback.current
    Surface(
        color = cuesColors.bg300,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).animateContentSize(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Cues Brain", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Text(
                "Bring your own Gemma model. Drafting stays on this phone — no cue text leaves it.",
                style = MaterialTheme.typography.bodySmall,
                color = cuesColors.ink200,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )

            when (state) {
                is ModelProvisionState.NotInstalled -> {
                    Text(
                        eligibilityLine(npuEligible),
                        style = MaterialTheme.typography.labelSmall,
                        color = cuesColors.ink200,
                    )
                    if (!canDownload) {
                        Text(
                            "Not configured — no model source is set for this build.",
                            style = MaterialTheme.typography.labelSmall,
                            color = cuesColors.ink200,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    } else if (!isOnWifi) {
                        Text(
                            "Connect to Wi-Fi to download.",
                            style = MaterialTheme.typography.labelSmall,
                            color = cuesColors.ink200,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Button(
                        onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onChoose?.invoke() },
                        enabled = onChoose != null,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) { Text("Install Gemma model") }
                    if (canDownload && isOnWifi) {
                        OutlinedButton(
                            onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onDownload() },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) { Text("Download verified model") }
                    }
                }

                is ModelProvisionState.Downloading -> {
                    val total = state.totalBytes
                    val fraction = total?.let { (state.bytesDownloaded.toFloat() / it).coerceIn(0f, 1f) }
                    Text(
                        if (total != null) {
                            "Downloading… ${mb(state.bytesDownloaded)} / ${mb(total)} MB"
                        } else {
                            "Downloading… ${mb(state.bytesDownloaded)} MB"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = cuesColors.ink200,
                    )
                    if (fraction != null) {
                        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    }
                    if (onCancel != null) {
                        OutlinedButton(
                            onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onCancel() },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) { Text("Cancel") }
                    }
                }

                ModelProvisionState.Verifying -> {
                    Text("Verifying…", style = MaterialTheme.typography.labelSmall, color = cuesColors.ink200)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                }

                is ModelProvisionState.Installed -> {
                    Text(
                        "Installed — ${mb(state.sizeBytes)} MB",
                        style = MaterialTheme.typography.labelSmall,
                        color = cuesColors.ink200,
                    )
                    Text(
                        eligibilityLine(npuEligible),
                        style = MaterialTheme.typography.labelSmall,
                        color = cuesColors.ink200,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(
                        lastInferenceReport?.let { "Last actual run: ${it.backend.name}, ${it.loadMs}ms load" }
                            ?: "No draft has used it yet — nothing has actually run.",
                        style = MaterialTheme.typography.labelSmall,
                        color = cuesColors.ink200,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Use for drafting", style = MaterialTheme.typography.bodySmall)
                        Switch(
                            checked = enabled,
                            onCheckedChange = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggleEnabled?.invoke(it)
                            },
                            enabled = onToggleEnabled != null,
                        )
                    }
                    if (onRemove != null) {
                        OutlinedButton(
                            onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onRemove() },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) { Text("Remove") }
                    }
                }

                is ModelProvisionState.Failed -> {
                    Text(state.reason, style = MaterialTheme.typography.labelSmall, color = cuesColors.ink200)
                    if (state.retryable) {
                        Button(
                            onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onDownload() },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) { Text("Retry") }
                    }
                }
            }
        }
    }
}

/** Never "NPU accelerated" — only what the SoC allowlist says is eligible, per CL-18's rule applied to this UI. */
private fun eligibilityLine(npuEligible: Boolean): String =
    if (npuEligible) "Eligible for: NPU, GPU, CPU (this SoC is on the published table)"
    else "Eligible for: GPU, CPU (this SoC has no published NPU build)"

private fun mb(bytes: Long): String = "%.1f".format(bytes / 1_000_000.0)

@Composable
private fun ManualCheck(
    title: String,
    instruction: String,
    observation: ManualObservation,
    onRecord: (ManualObservation) -> Unit,
) {
    Surface(color = cuesColors.bg300, shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Text(
                instruction,
                style = MaterialTheme.typography.bodySmall,
                color = cuesColors.ink200,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                "Recorded: ${observation.label}",
                style = MaterialTheme.typography.labelSmall,
                color = cuesColors.ink200,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onRecord(ManualObservation.NO_ISSUE_OBSERVED) }) {
                    Text("No issue")
                }
                OutlinedButton(onClick = { onRecord(ManualObservation.ISSUE_OBSERVED) }) {
                    Text("Issue observed")
                }
            }
        }
    }
}
