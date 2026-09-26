package com.cues.app.ui.review

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.cues.app.ui.components.ClauseBadge
import com.cues.app.ui.components.ClauseKind
import com.cues.app.ui.components.GhostButton
import com.cues.app.ui.components.KineticButton
import com.cues.app.ui.components.RiskTag
import com.cues.app.ui.components.SlabCard
import com.cues.app.ui.components.SlabTier
import com.cues.app.ui.components.StatusPill
import com.cues.app.ui.components.StatusTone
import com.cues.app.ui.theme.CuesShape
import com.cues.app.ui.theme.CuesType
import com.cues.app.ui.theme.cuesTokens
import com.cues.core.approval.Approvals
import com.cues.core.compile.Severity
import com.cues.core.drafting.DraftTrace
import com.cues.core.model.Capability
import com.cues.core.model.Routine
import com.cues.core.rehearsal.RehearsalRow
import com.cues.core.registry.ActionRegistry
import com.cues.core.review.DraftCredit
import com.cues.core.review.ReviewCopy

/**
 * Review, restyled (redesign plan §3.4) — the screen that has to win. Same
 * data and the same permission-grant logic as the original `ReviewScreen`
 * (kept alongside, unused, as a reference during the transition), new
 * visual language: a trust banner, clause-accounted source text, one
 * [SlabCard] per clause with its [ClauseBadge], and the approve control as
 * a [KineticButton] that states its own disabled reason.
 */
@Composable
fun ReviewScreenV2(
    routine: Routine,
    review: Approvals.ReviewResult,
    rehearsal: List<RehearsalRow>,
    missingCapabilities: Set<Capability>,
    onApprove: () -> Unit,
    onBack: () -> Unit,
    onCapabilitiesChanged: () -> Unit = {},
    /**
     * Every drafter this routine's own draft call actually asked — from
     * `Turn.trace`/`DraftResult.trace` — so the header can say "confirmed
     * by on-device model" or "model off" instead of only naming the winner.
     * `null` for a routine with no trace to show (an import, a coach edit,
     * or a build with no model configured at all) — the header then falls
     * back to naming [Routine.draftedBy] alone, exactly as before this
     * parameter existed.
     */
    trace: DraftTrace? = null,
) {
    val t = cuesTokens
    BackHandler(onBack = onBack)

    val errors = review.validation.findings.count { it.severity == Severity.ERROR }
    val disabledReason = when {
        routine.unaccountedClauses.isNotEmpty() -> "${routine.unaccountedClauses.size} word(s) unaccounted for"
        errors > 0 -> "$errors check(s) failing"
        missingCapabilities.isNotEmpty() -> "Grant access first"
        else -> null
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("REVIEW", style = CuesType.headline, color = t.inkPrimary)
            Text(
                routine.draftedBy?.let { DraftCredit.credit(trace, it).label() } ?: "drafted by unknown",
                style = CuesType.labelSmall,
                color = t.inkSlate,
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { TrustBanner() }

            item { SourceTextCard(routine) }

            item {
                ClauseCard(ClauseKind.WHEN, ReviewCopy.whenText(routine))
            }
            item {
                ClauseCard(ClauseKind.IF, ReviewCopy.ifText(routine))
            }
            item {
                Column {
                    ClauseCardHeader(ClauseKind.DO)
                    SlabCard(tier = SlabTier.ONE, modifier = Modifier.fillMaxWidth()) {
                        routine.actions.forEach { action ->
                            val definition = ActionRegistry.definition(action.actionId)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(definition?.label ?: action.actionId.name, style = CuesType.body, color = t.inkSecondary)
                                if (definition != null) RiskTag(definition.risk)
                            }
                        }
                    }
                }
            }
            item {
                ClauseCard(ClauseKind.UNTIL, ReviewCopy.untilText(routine))
            }
            item {
                ClauseCard(ClauseKind.RESTORE, ReviewCopy.restoreText(routine))
            }
            item {
                Text(
                    ReviewCopy.repeatText(routine),
                    style = CuesType.labelSmall,
                    color = t.inkSlate,
                )
            }

            item { SectionLabel("Required access") }
            items(routine.requiredCapabilities.sortedBy { it.name }) { capability ->
                PermissionCheckRowV2(capability, capability in missingCapabilities, onCapabilitiesChanged)
            }

            if (!review.validation.isValid || review.validation.warnings.isNotEmpty()) {
                item { SectionLabel("Checks") }
                items(review.validation.findings) { finding ->
                    Text(
                        (if (finding.severity == Severity.ERROR) "⛔ " else "⚠ ") + finding.message,
                        style = CuesType.body,
                        color = if (finding.severity == Severity.ERROR) t.stop else t.warn,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }

            item { SectionLabel("Rehearsal · sample events, synthetic — nothing on your phone changes") }
            items(rehearsal) { row -> RehearsalRowCard(row) }

            item { Spacer(Modifier.height(88.dp)) }
        }

        SlabCard(
            tier = SlabTier.TWO,
            shape = androidx.compose.ui.graphics.RectangleShape,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                GhostButton(text = "Back", onClick = onBack, modifier = Modifier.weight(1f))
                Column(Modifier.weight(2f)) {
                    KineticButton(
                        text = "APPROVE & ARM",
                        onClick = onApprove,
                        enabled = disabledReason == null,
                        disabledReason = disabledReason,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun TrustBanner() {
    val t = cuesTokens
    SlabCard(tier = SlabTier.ONE, edgeColor = t.dangerEdge, modifier = Modifier.fillMaxWidth()) {
        Text("NATURAL LANGUAGE IS UNTRUSTED INPUT", style = CuesType.label, color = t.untilOrange)
        Text(
            "This draft can't run anything until you approve this exact version.",
            style = CuesType.labelSmall,
            color = t.inkSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Source text with every unaccounted fragment underlined in orange — the honest replacement for a fake "99.4% confidence". */
@Composable
private fun SourceTextCard(routine: Routine) {
    val t = cuesTokens
    val text = routine.sourceText
    val unaccounted = routine.unaccountedClauses.filter { it.isNotBlank() }
    SlabCard(tier = SlabTier.ONE, modifier = Modifier.fillMaxWidth()) {
        val annotated = buildAnnotatedString {
            append(text)
            unaccounted.forEach { fragment ->
                var start = text.indexOf(fragment)
                while (start >= 0) {
                    addStyle(
                        SpanStyle(color = t.untilOrange, textDecoration = TextDecoration.Underline),
                        start,
                        start + fragment.length,
                    )
                    start = text.indexOf(fragment, start + fragment.length)
                }
            }
        }
        Text(annotated, style = CuesType.bodyLarge, color = t.inkPrimary)
        val mapped = text.split(Regex("\\s+")).size - unaccounted.sumOf { it.split(Regex("\\s+")).size }
        Text(
            if (unaccounted.isEmpty()) {
                "Every word accounted for."
            } else {
                "${unaccounted.size} word(s) unaccounted for — approval is blocked until they are."
            },
            style = CuesType.labelSmall,
            color = if (unaccounted.isEmpty()) t.go else t.untilOrange,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun ClauseCardHeader(kind: ClauseKind) {
    ClauseBadge(kind, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun ClauseCard(kind: ClauseKind, text: String) {
    val t = cuesTokens
    SlabCard(tier = SlabTier.ONE, modifier = Modifier.fillMaxWidth()) {
        ClauseCardHeader(kind)
        Text(text, style = CuesType.body, color = t.inkSecondary)
    }
}

@Composable
private fun SectionLabel(text: String) {
    val t = cuesTokens
    Text(
        text.uppercase(),
        style = CuesType.labelSmall,
        color = t.inkSlate,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun PermissionCheckRowV2(capability: Capability, isMissing: Boolean, onCapabilitiesChanged: () -> Unit) {
    val t = cuesTokens
    val copy = with(ReviewCopy) { capability.permissionCheckCopy() }
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onCapabilitiesChanged() }

    SlabCard(
        tier = SlabTier.ONE,
        edgeColor = if (isMissing) t.dangerEdge else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                with(ReviewCopy) { capability.friendlyName() },
                style = CuesType.bodyMedium,
                color = if (isMissing) t.stop else t.inkPrimary,
            )
            if (isMissing) {
                val grantLabel = capability.grantLabel()
                if (grantLabel != null) {
                    GhostButton(text = grantLabel, onClick = {
                        when (capability) {
                            Capability.POST_NOTIFICATIONS -> permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            Capability.BLUETOOTH_CONNECT -> permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                            else -> context.startActivity(capability.settingsIntent(context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    })
                }
            } else {
                StatusPill("GRANTED", StatusTone.GO)
            }
        }
        Text(copy.purpose, style = CuesType.labelSmall, color = t.inkSlate, modifier = Modifier.padding(top = 4.dp))
        Text(copy.frequency, style = CuesType.labelSmall, color = t.inkSlate, modifier = Modifier.padding(top = 2.dp))
    }
}

private fun Capability.grantLabel(): String? = when (this) {
    Capability.POST_NOTIFICATIONS -> "Allow"
    Capability.BLUETOOTH_CONNECT -> "Allow"
    Capability.NOTIFICATION_POLICY_ACCESS -> "Open settings"
    Capability.EXACT_ALARM -> "Open settings"
    else -> null
}

private fun Capability.settingsIntent(packageName: String): Intent = when (this) {
    Capability.NOTIFICATION_POLICY_ACCESS -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
    Capability.EXACT_ALARM -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
    else -> error("$this has no settings screen; grantLabel() should have returned null")
}

@Composable
private fun RehearsalRowCard(row: RehearsalRow) {
    val t = cuesTokens
    SlabCard(tier = SlabTier.ONE, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(row.label, style = CuesType.bodyMedium, color = t.inkPrimary)
            StatusPill(row.outcome, if (row.outcome.startsWith("Skipped")) StatusTone.WARN else StatusTone.GO)
        }
        row.explanation.forEach {
            Text(it, style = CuesType.labelSmall, color = t.inkSlate, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
