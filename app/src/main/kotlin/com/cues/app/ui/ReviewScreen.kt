package com.cues.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cues.core.approval.Approvals
import com.cues.core.compile.Severity
import com.cues.core.model.Capability
import com.cues.core.model.Routine
import com.cues.core.rehearsal.RehearsalRow
import com.cues.core.registry.ActionRegistry
import com.cues.core.review.ReviewCopy

/**
 * Review: WHEN / IF / DO / UNTIL / RESTORE, required access, a rehearsal and
 * the approve control — the one screen RV-01/RV-02 describe, and the
 * screen a judge is meant to be able to read without seeing JSON.
 */
@Composable
fun ReviewScreen(
    routine: Routine,
    review: Approvals.ReviewResult,
    rehearsal: List<RehearsalRow>,
    missingCapabilities: Set<Capability>,
    onApprove: () -> Unit,
    onBack: () -> Unit,
    /**
     * Called right after a grant action that resolves synchronously (a
     * runtime permission dialog), so the missing-capability list updates
     * immediately rather than waiting for the next `ON_RESUME` — which
     * still fires, and still matters, for the settings-screen grants below
     * that actually leave the app (CL-26).
     */
    onCapabilitiesChanged: () -> Unit = {},
) {
    val haptics = LocalHapticFeedback.current
    androidx.activity.compose.BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Review", style = MaterialTheme.typography.headlineSmall)
            Text(
                "drafted by ${routine.draftedBy?.name?.lowercase()?.replace('_', ' ') ?: "unknown"}",
                style = MaterialTheme.typography.labelSmall,
                color = cuesColors.ink200,
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item { TrustBoundaryCard() }
            item { SectionLabel("How this cue ends") }
            item { ReviewRow("UNTIL", ReviewCopy.untilText(routine)) }
            item { ReviewRow("RESTORE", ReviewCopy.restoreText(routine)) }

            item { SectionLabel("What this cue does") }
            item { ReviewRow("WHEN", ReviewCopy.whenText(routine)) }
            item { ReviewRow("IF", ReviewCopy.ifText(routine)) }
            item { ReviewRow("DO", ReviewCopy.doText(routine)) }
            item { ReviewRow("REPEAT", ReviewCopy.repeatText(routine)) }

            item { SectionLabel("Action risk") }
            items(routine.actions) { action -> ActionRiskRow(action.actionId) }

            item { SectionLabel("Required access") }
            items(routine.requiredCapabilities.sortedBy { it.name }) { capability ->
                PermissionCheckRow(capability, capability in missingCapabilities, onCapabilitiesChanged)
            }

            if (!review.validation.isValid || review.validation.warnings.isNotEmpty()) {
                item { SectionLabel("Checks") }
                items(review.validation.findings) { finding ->
                    Text(
                        (if (finding.severity == Severity.ERROR) "⛔ " else "⚠ ") + finding.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (finding.severity == Severity.ERROR) cuesColors.stop else cuesColors.amber,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }

            item { SectionLabel("Rehearsal — sample events, nothing on your phone changes") }
            items(rehearsal) { row -> RehearsalRowView(row) }

            item { Spacer(Modifier.height(80.dp)) }
        }

        Surface(color = cuesColors.bg200, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                if (missingCapabilities.isNotEmpty()) {
                    Text(
                        "Missing: ${missingCapabilities.joinToString(", ") { with(ReviewCopy) { it.friendlyName() } }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = cuesColors.stop,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onBack()
                    }, modifier = Modifier.weight(1f)) { Text("Back") }
                    Button(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onApprove()
                        },
                        enabled = review.validation.isValid,
                        modifier = Modifier.weight(2f),
                    ) { Text("Approve & arm") }
                }
            }
        }
    }
}

@Composable
private fun ReviewRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = cuesColors.ink100,
            modifier = Modifier.width(72.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TrustBoundaryCard() {
    Surface(
        color = cuesColors.bg300,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "After approval: no model, no network.",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Only this checked version can respond to its declared device events.",
                style = MaterialTheme.typography.bodySmall,
                color = cuesColors.ink200,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                "Editing creates a new version that needs approval again.",
                style = MaterialTheme.typography.bodySmall,
                color = cuesColors.ink200,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = cuesColors.ink200,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun ActionRiskRow(actionId: com.cues.core.model.ActionId) {
    val definition = ActionRegistry.definition(actionId) ?: return
    Surface(color = cuesColors.bg300, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(definition.label, style = MaterialTheme.typography.bodyMedium)
            Text(
                with(ReviewCopy) { definition.risk.friendlyName() },
                style = MaterialTheme.typography.labelSmall,
                color = cuesColors.ink200,
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun PermissionCheckRow(capability: Capability, isMissing: Boolean, onCapabilitiesChanged: () -> Unit) {
    val copy = with(ReviewCopy) { capability.permissionCheckCopy() }
    val context = LocalContext.current

    // POST_NOTIFICATIONS and BLUETOOTH_CONNECT are the two capabilities here
    // with a synchronous runtime-permission dialog rather than a settings
    // screen; the launcher has to exist unconditionally so Compose keeps its
    // slot stable across recompositions, even on rows that never launch it.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { onCapabilitiesChanged() }

    Surface(
        color = if (isMissing) cuesColors.stopBg else cuesColors.bg300,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    with(ReviewCopy) { capability.friendlyName() },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isMissing) cuesColors.stop else cuesColors.ink100,
                )
                if (isMissing) {
                    val grantLabel = capability.grantLabel()
                    if (grantLabel != null) {
                        TextButton(onClick = {
                            when (capability) {
                                Capability.POST_NOTIFICATIONS ->
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                Capability.BLUETOOTH_CONNECT ->
                                    permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                                else -> context.startActivity(
                                    capability.settingsIntent(context.packageName)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }) { Text(grantLabel) }
                    }
                }
            }
            Text(
                copy.purpose,
                style = MaterialTheme.typography.bodySmall,
                color = cuesColors.ink200,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                copy.frequency,
                style = MaterialTheme.typography.bodySmall,
                color = cuesColors.ink200,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}

/**
 * `null` for a capability with no direct grant surface from here (location,
 * battery/network reads that need no permission, or the accessibility
 * service, which is its own consent flow on the Utility Bindings screen —
 * duplicating a settings deep link here would just be a second, easily
 * inconsistent path to the same toggle).
 */
private fun Capability.grantLabel(): String? = when (this) {
    Capability.POST_NOTIFICATIONS -> "Allow"
    Capability.BLUETOOTH_CONNECT -> "Allow"
    Capability.NOTIFICATION_POLICY_ACCESS -> "Open settings"
    Capability.EXACT_ALARM -> "Open settings"
    else -> null
}

/**
 * Only called for a capability [grantLabel] names as a settings screen, i.e.
 * never [Capability.POST_NOTIFICATIONS] or [Capability.BLUETOOTH_CONNECT],
 * which the caller launches as a runtime-permission request instead.
 *
 * `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` is API 31+ only, but so is ever
 * seeing [Capability.EXACT_ALARM] as missing —
 * [com.cues.app.runtime.AndroidCapabilityProvider] reports it granted
 * unconditionally below API 31 — so there is no older-API branch to write
 * here.
 */
private fun Capability.settingsIntent(packageName: String): Intent = when (this) {
    Capability.NOTIFICATION_POLICY_ACCESS -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
    Capability.EXACT_ALARM -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
    else -> error("$this has no settings screen; grantLabel() should have returned null")
}

@Composable
private fun RehearsalRowView(row: RehearsalRow) {
    Surface(color = cuesColors.bg300, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(row.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                val tone = if (row.outcome.startsWith("Skipped")) Tone.AMBER else Tone.GO
                StatusChip(row.outcome, tone)
            }
            row.explanation.forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = cuesColors.ink200,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}
