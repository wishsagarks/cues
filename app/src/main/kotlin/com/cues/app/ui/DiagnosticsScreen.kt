package com.cues.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cues.app.runtime.DeviceDiagnostics
import com.cues.app.runtime.DeviceHealthSnapshot
import com.cues.app.runtime.ManualObservation
import com.cues.app.drafting.RunnerState
import com.cues.core.CueService
import com.cues.core.drafting.DraftTrace
import com.cues.core.inference.ModelProvisionState
import com.cues.core.inference.InferenceLedgerEntry
import com.cues.core.inference.InferenceBackend
import com.cues.core.model.DraftSourceId
import java.text.DateFormat
import java.util.Date

/** The recorded, target-phone-only checks from Sprint 3.0. */
@Composable
fun DiagnosticsScreen(
    diagnostics: DeviceDiagnostics,
    deviceHealth: DeviceHealthSnapshot? = null,
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
    /** Set only when [com.cues.core.model.ModelCatalog.knownDispatchFailureFor] has a real, on-device-confirmed finding for this exact SoC — never a guess. */
    npuKnownIssue: String? = null,
    runnerState: RunnerState = RunnerState.Cold,
    onDeviceModelEnabled: Boolean = false,
    onToggleOnDeviceModel: ((Boolean) -> Unit)? = null,
    onWarmModel: (() -> Unit)? = null,
    onDownloadModel: (() -> Unit)? = null,
    onChooseModel: (() -> Unit)? = null,
    onCancelDownloadModel: (() -> Unit)? = null,
    onRemoveModel: (() -> Unit)? = null,
    externalGemmaEnabled: Boolean = false,
    onToggleExternalGemma: ((Boolean) -> Unit)? = null,
    externalModelIdentity: String = "no BYOM model installed",
    onChooseExternalModel: (() -> Unit)? = null,
    recentExternalCalls: List<com.cues.app.devkit.ExternalCallLogEntry> = emptyList(),
    approvedExternalCallers: Set<String> = emptySet(),
    onApproveExternalCaller: ((String) -> Unit)? = null,
    onRevokeExternalCaller: ((String) -> Unit)? = null,
    inferenceEntries: List<InferenceLedgerEntry> = emptyList(),
    isProbingNpu: Boolean = false,
    npuProbeResult: String? = null,
    onProbeNpu: (() -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    androidx.activity.compose.BackHandler(onBack = onBack)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
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
        deviceHealth?.let {
            Spacer(Modifier.height(8.dp))
            DeviceHealthCard(
                it,
                npuEligible = npuEligible,
                npuKnownIssue = npuKnownIssue,
                lastInferenceReport = cueDiagnostics?.lastTrace.modelReport(),
                isProbingNpu = isProbingNpu,
                npuProbeResult = npuProbeResult,
                onProbeNpu = onProbeNpu,
            )
            Spacer(Modifier.height(8.dp))
        }
        DiagnosticCard("On-device speech", diagnostics.onDeviceSpeech)
        DiagnosticCard("English (India) pack", diagnostics.englishIndiaPack)
        cueDiagnostics?.let { DiagnosticCard("Drafting path", it.render()) }

        if (inferenceEntries.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            GovernedIntelligenceCard(inferenceEntries)
        }

        if (onDownloadModel != null) {
            Spacer(Modifier.height(8.dp))
            ModelBrainCard(
                state = modelProvisionState,
                canDownload = canDownloadModel,
                isOnWifi = isOnWifi,
                npuEligible = npuEligible,
                npuKnownIssue = npuKnownIssue,
                lastInferenceReport = cueDiagnostics?.lastTrace.modelReport(),
                enabled = onDeviceModelEnabled,
                onToggleEnabled = onToggleOnDeviceModel,
                runnerState = runnerState,
                onWarmModel = onWarmModel,
                onDownload = onDownloadModel,
                onChoose = onChooseModel,
                onCancel = onCancelDownloadModel,
                onRemove = onRemoveModel,
            )
        }

        if (onToggleExternalGemma != null) {
            Spacer(Modifier.height(8.dp))
            DeveloperSurfaceCard(
                enabled = externalGemmaEnabled,
                onToggleEnabled = onToggleExternalGemma,
                modelIdentity = externalModelIdentity,
                onChooseModel = onChooseExternalModel,
                recentCalls = recentExternalCalls,
                approvedCallers = approvedExternalCallers,
                onApproveCaller = onApproveExternalCaller,
                onRevokeCaller = onRevokeExternalCaller,
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
            title = "System assist gesture",
            instruction = "Try the phone's microphone and assist gesture. Record whether the system assistant (Google Assistant on this device) prevented Cues from receiving the intended user gesture.",
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

/** The model's own last real report, from whichever attempt in [trace] carried one — never eligibility, never a guess. */
private fun DraftTrace?.modelReport(): com.cues.core.inference.InferenceReport? =
    this?.attempts?.firstNotNullOfOrNull { it.inferenceReport }

/**
 * The product thesis in live data: models may propose or complete bounded
 * local requests, while the approved runtime remains deterministic. This
 * does not infer a backend from the phone model; every count comes from the
 * existing opt-in inference ledger.
 */
@Composable
private fun GovernedIntelligenceCard(entries: List<InferenceLedgerEntry>) {
    val local = entries.filter { it.backend != InferenceBackend.CLOUD }
    val hub = entries.filter { it.source == DraftSourceId.EXTERNAL_GEMMA_CALL || it.source == DraftSourceId.SYSTEM_AGENT_CALL }
    val callers = hub.mapNotNull { it.callerPackage }.distinct()
    val latest = entries.maxByOrNull { it.atMillis }
    Surface(
        color = cuesColors.bg300,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Governed intelligence", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Text(
                "Models propose; approved cues execute deterministically. Runtime model calls: 0 by design.",
                style = MaterialTheme.typography.bodySmall,
                color = cuesColors.ink200,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                "${local.size} local calls · ${hub.size} hub calls · ${callers.size} external callers",
                style = MaterialTheme.typography.labelSmall,
                color = cuesColors.ink200,
                modifier = Modifier.padding(top = 8.dp),
            )
            latest?.let {
                Text(
                    "Latest: ${it.source.name.replace('_', ' ')} on ${it.backend.name} · ${"%.1f".format(it.estimatedTokens * 1_000.0 / it.latencyMs.coerceAtLeast(1))} tok/s",
                    style = MaterialTheme.typography.labelSmall,
                    color = cuesColors.ink200,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/**
 * Names the drafter setup that's actually configured and, when a model has
 * drafted at least once, which backend loaded it plus every attempt's own
 * outcome — never a claim about what *would* run, only what already did.
 * See [com.cues.core.inference.InferenceReport] and CLEANUP.md CL-18.
 */
private fun CueService.Diagnostics.render(): String = buildString {
    append(
        when (setup.model) {
            com.cues.core.ports.ModelAvailability.READY -> "model on"
            com.cues.core.ports.ModelAvailability.INSTALLED_OFF -> "model installed, off"
            com.cues.core.ports.ModelAvailability.NOT_INSTALLED -> "no model installed"
        },
    )
    lastTrace?.let { trace ->
        trace.modelReport()?.let { report ->
            append(" — last ran on ${report.backend.name}")
            append(", ${report.loadMs}ms load, ~${"%.1f".format(report.tokensPerSecond)} tok/s")
        }
        trace.attempts.filter { it.reasonCode != null }.forEach { attempt ->
            append(" — ${attempt.source.name.lowercase()}: ${attempt.reasonCode}")
        }
    }
}

/**
 * The "basic symbols" group: battery health, this process's CPU share, the
 * on-device NPU, and the sensor inventory, each an icon + a one-line honest
 * reading — never a fabricated system-wide number where Android exposes none
 * (see [com.cues.app.runtime.DeviceHealthReadings]).
 *
 * There's no root-free API for NPU *utilization* either, but unlike GPU,
 * Cues already has two real, non-root readings for it: [npuEligible] (the
 * published SoC allow-list check behind [ModelBrainCard]'s own eligibility
 * line) and, once a draft has actually run, [lastInferenceReport] — the same
 * [InferenceReport] that never claims NPU accelerated anything beyond what a
 * real run's own `backend` says (CL-18).
 */
@Composable
private fun DeviceHealthCard(
    health: DeviceHealthSnapshot,
    npuEligible: Boolean,
    npuKnownIssue: String? = null,
    lastInferenceReport: com.cues.core.inference.InferenceReport?,
    isProbingNpu: Boolean = false,
    npuProbeResult: String? = null,
    onProbeNpu: (() -> Unit)? = null,
) {
    Surface(
        color = cuesColors.bg300,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("DEVICE HEALTH", style = MaterialTheme.typography.labelMedium, color = cuesColors.ink200)

            Text(
                "BATTERY",
                style = MaterialTheme.typography.labelSmall,
                color = cuesColors.ink200,
                modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            ) {
                HealthTile(
                    Icons.Filled.HealthAndSafety,
                    "Health",
                    health.batteryHealth,
                    Modifier.weight(1f).fillMaxHeight(),
                )
                HealthTile(
                    Icons.Filled.Thermostat,
                    "Temperature",
                    health.batteryTempC?.let { "%.1f°C".format(it) } ?: "Not reported",
                    Modifier.weight(1f).fillMaxHeight(),
                )
            }

            Text(
                "PERFORMANCE",
                style = MaterialTheme.typography.labelSmall,
                color = cuesColors.ink200,
                modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            ) {
                HealthTile(
                    Icons.Filled.Memory,
                    "CPU · own process",
                    buildString {
                        append("${health.cpuCoreCount} core(s)")
                        health.cpuUsagePercent?.let { append(", %.1f%% avg".format(it)) }
                    },
                    Modifier.weight(1f).fillMaxHeight(),
                )
                HealthTile(
                    Icons.Filled.Psychology,
                    "NPU",
                    npuReading(health.npuHardwareFamily, npuEligible, npuKnownIssue, lastInferenceReport),
                    Modifier.weight(1f).fillMaxHeight(),
                )
            }
            if (onProbeNpu != null) {
                OutlinedButton(
                    onClick = onProbeNpu,
                    enabled = !isProbingNpu,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text(if (isProbingNpu) "Testing NPU…" else "Test NPU on this chip anyway") }
                Text(
                    "Forces a real attempt on this exact chip, bypassing the allow-list above. " +
                        "May fail safely, or may crash if the native NPU dispatch layer can't run on this Hexagon version.",
                    style = MaterialTheme.typography.labelSmall,
                    color = cuesColors.ink200,
                    modifier = Modifier.padding(top = 4.dp),
                )
                npuProbeResult?.let { result ->
                    Text(
                        result,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (result.startsWith("NPU loaded")) cuesColors.go else cuesColors.amber,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            Text(
                "SENSORS",
                style = MaterialTheme.typography.labelSmall,
                color = cuesColors.ink200,
                modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
            )
            if (health.sensorGroups.isEmpty()) {
                Text(
                    "No sensor list reported by this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cuesColors.ink200,
                )
            } else {
                health.sensorGroups.chunked(2).forEach { rowGroups ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    ) {
                        rowGroups.forEach { group ->
                            HealthTile(Icons.Filled.Sensors, group.label, "${group.count}", Modifier.weight(1f))
                        }
                        if (rowGroups.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthTile(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(cuesColors.bg200, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = cuesColors.ink200, modifier = Modifier.size(16.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = cuesColors.ink200,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
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
    npuKnownIssue: String? = null,
    lastInferenceReport: com.cues.core.inference.InferenceReport?,
    enabled: Boolean,
    onToggleEnabled: ((Boolean) -> Unit)?,
    runnerState: RunnerState,
    onWarmModel: (() -> Unit)?,
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
                        eligibilityLine(npuEligible, npuKnownIssue),
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
                        eligibilityLine(npuEligible, npuKnownIssue),
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
                    Text(
                        runnerState.checksLabel(),
                        style = MaterialTheme.typography.labelSmall,
                        color = cuesColors.ink200,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    OutlinedButton(
                        onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onWarmModel?.invoke() },
                        enabled = enabled && onWarmModel != null && runnerState !is RunnerState.Loading && runnerState !is RunnerState.Generating,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) { Text(if (runnerState is RunnerState.Warm) "Refresh warm model" else "Warm model (no prompt)") }
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

/**
 * CL-38: the developer-facing local Gemma surface — other apps on this same
 * phone can call `content://<applicationId>.gemma` for an on-device
 * completion, once this switch is on. Off by default every launch, same
 * discipline as [ModelBrainCard]'s own "Use for drafting" switch. A model
 * being installed for authoring does not mean this surface has one too — see
 * its own "bring your own model" button and honest absence state below.
 */
@Composable
private fun DeveloperSurfaceCard(
    enabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    modelIdentity: String,
    onChooseModel: (() -> Unit)?,
    recentCalls: List<com.cues.app.devkit.ExternalCallLogEntry>,
    approvedCallers: Set<String>,
    onApproveCaller: ((String) -> Unit)?,
    onRevokeCaller: ((String) -> Unit)?,
) {
    val haptics = LocalHapticFeedback.current
    Surface(
        color = cuesColors.bg300,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).animateContentSize(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Developer surface", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Text(
                "Lets another app on this phone ask Cues' on-device Gemma for a completion. " +
                    "Unverified beyond this build — see CLEANUP.md CL-38.",
                style = MaterialTheme.typography.bodySmall,
                color = cuesColors.ink200,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            Text(
                "Model: $modelIdentity",
                style = MaterialTheme.typography.labelSmall,
                color = cuesColors.ink200,
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Expose to other apps", style = MaterialTheme.typography.bodySmall)
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleEnabled(it)
                    },
                )
            }
            if (onChooseModel != null) {
                OutlinedButton(
                    onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onChooseModel() },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text("Bring your own model for this surface") }
            }
            if (recentCalls.isNotEmpty()) {
                Text(
                    "Recent callers",
                    style = MaterialTheme.typography.labelSmall,
                    color = cuesColors.ink200,
                    modifier = Modifier.padding(top = 12.dp),
                )
                recentCalls.take(10).forEach { call ->
                    Text(
                        "${call.callerPackage} — ${if (call.allowed) "allowed" else "denied: ${call.reason}"} — " +
                            DateFormat.getTimeInstance().format(Date(call.atMillis)),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (call.allowed) cuesColors.ink200 else cuesColors.amber,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (!call.allowed && call.callerPackage != "unknown" && call.reason?.startsWith("Consent required") == true) {
                        OutlinedButton(
                            onClick = { onApproveCaller?.invoke(call.callerPackage) },
                            enabled = onApproveCaller != null,
                            modifier = Modifier.padding(top = 3.dp),
                        ) { Text("Allow ${call.callerPackage}") }
                    }
                }
            }
            approvedCallers.forEach { caller ->
                OutlinedButton(
                    onClick = { onRevokeCaller?.invoke(caller) },
                    enabled = onRevokeCaller != null,
                    modifier = Modifier.padding(top = 4.dp),
                ) { Text("Revoke $caller") }
            }
        }
    }
}

/**
 * Never "NPU accelerated" — only what the SoC allowlist says is eligible,
 * per CL-18's rule applied to this UI. [npuKnownIssue], when set, means a
 * real on-device attempt already confirmed *why* this chip isn't eligible —
 * a different, more precise fact than "nothing published for this chip yet"
 * (see [com.cues.core.model.ModelCatalog.knownDispatchFailureFor]).
 */
private fun eligibilityLine(npuEligible: Boolean, npuKnownIssue: String? = null): String = when {
    npuEligible -> "Eligible for: NPU, GPU, CPU (this SoC is on the published table)"
    npuKnownIssue != null -> "Eligible for: GPU, CPU. $npuKnownIssue"
    else -> "Eligible for: GPU, CPU (this SoC has no published NPU build)"
}

private fun RunnerState.checksLabel(): String = when (this) {
    RunnerState.Cold -> "Runtime: cold — no engine is held in memory."
    is RunnerState.Loading -> "Runtime: loading ${tier.name}."
    is RunnerState.Warm -> "Runtime: warm on ${tier.name} — loaded in ${loadMs}ms."
    is RunnerState.Generating -> "Runtime: generating on ${tier.name}."
    is RunnerState.Failed -> "Runtime: unavailable — $reasonCode"
}

/**
 * The Device Health tile's NPU reading — two separate, honestly-labelled
 * facts, never merged into one claim:
 *
 * 1. [npuHardwareFamily] — does this chipset's own family ship an NPU at
 *    all (Qualcomm Hexagon, MediaTek APU, ...), per
 *    [com.cues.app.runtime.DeviceIdentity.npuHardwareFamily]. A documented
 *    hardware fact, independent of anything Cues ships.
 * 2. Whether Cues' own on-device model is confirmed for *this exact* chip
 *    ([npuEligible], the same allow-list [eligibilityLine] reads) and, only
 *    once a draft has actually run, the real measured numbers from
 *    [com.cues.core.inference.InferenceReport] — never a claim about what
 *    *would* happen.
 *
 * A phone can show a real Hexagon/APU line here while line 2 still says
 * "not confirmed" — that's not a contradiction, it's Cues declining to
 * claim its own model uses hardware nobody has verified it on yet.
 */
private fun npuReading(
    npuHardwareFamily: String?,
    npuEligible: Boolean,
    npuKnownIssue: String? = null,
    lastInferenceReport: com.cues.core.inference.InferenceReport?,
): String {
    val hardwareLine = npuHardwareFamily ?: "No NPU family identified from this chipset"
    val cuesLine = when {
        lastInferenceReport?.backend == com.cues.core.inference.InferenceBackend.NPU ->
            "Cues ran last on NPU: ${lastInferenceReport.loadMs}ms load, ${"%.1f".format(lastInferenceReport.tokensPerSecond)} tok/s"
        npuKnownIssue != null -> npuKnownIssue
        !npuEligible -> "Cues' model has no confirmed NPU build for this exact chip yet"
        lastInferenceReport != null -> "Cues model eligible, but last draft ran on ${lastInferenceReport.backend.name}"
        else -> "Cues model eligible, no draft has used it yet"
    }
    return "$hardwareLine\n$cuesLine"
}

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
