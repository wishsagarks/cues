package com.cues.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cues.app.runtime.DeviceDiagnostics
import com.cues.app.runtime.ManualObservation
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
) {
    val haptics = LocalHapticFeedback.current
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Device checks", style = MaterialTheme.typography.headlineSmall)
            OutlinedButton(onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
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

        Button(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
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
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onRecordJoviMicOrAssist(it)
            },
        )
        Spacer(Modifier.height(12.dp))
        ManualCheck(
            title = "OriginOS permission monitor",
            instruction = "After arming a Bluetooth or charging cue, inspect OriginOS's permission activity. Record whether it flags Cues' listeners; Android does not expose this suggestion to apps.",
            observation = diagnostics.permissionMonitor,
            onRecord = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
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
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
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

@Composable
private fun DiagnosticCard(label: String, value: String) {
    Surface(
        color = cuesColors.bg300,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = cuesColors.ink200)
            Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

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
