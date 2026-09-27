package com.cues.app.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cues.app.ui.components.FixedSegmentedControl
import com.cues.app.ui.components.KineticSwitch
import com.cues.app.ui.components.SectionHeader
import com.cues.app.ui.components.SlabCard
import com.cues.app.ui.components.SlabTier
import com.cues.app.ui.components.StatusPill
import com.cues.app.ui.components.StatusTone
import com.cues.app.ui.theme.CuesType
import com.cues.app.ui.theme.cuesTokens
import com.cues.core.drafting.DraftTrace
import com.cues.core.inference.CostBasis
import com.cues.core.inference.InferenceBackend
import com.cues.core.inference.InferenceCost
import com.cues.core.insights.InsightsReport
import com.cues.core.insights.InsightsWindow
import com.cues.core.insights.SkipFamily
import com.cues.core.model.DraftSourceId

/**
 * Insights (redesign plan §3.2), now reading the real `:core` [Insights]
 * aggregator via [InsightsReport] instead of computing its own ledger/
 * forecast summary — see CLEANUP.md CL-33/CL-34. Every number here traces to
 * a session, a structured receipt or the ledger; a null field renders its
 * honest "no data" state rather than a zero.
 */
@Composable
fun InsightsScreen(
    report: InsightsReport,
    window: InsightsWindow,
    onWindowChange: (InsightsWindow) -> Unit,
    onToggleLedger: (Boolean) -> Unit,
    drafterLabel: String,
    /**
     * Every drafter the most recent draft actually asked, from
     * [com.cues.core.CueService.diagnostics]. `null` renders no badge and no
     * credit line — honest for a fresh install or a build with no model —
     * never a guessed backend (CLEANUP.md CL-18).
     */
    lastTrace: DraftTrace? = null,
    onExportConsole: () -> Unit,
    onFixCapability: () -> Unit,
    onToggleUsageTracking: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val t = cuesTokens

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text("WINDOW", style = CuesType.labelSmall, color = t.inkSlate)
                FixedSegmentedControl(
                    options = listOf("LAST 7 DAYS", "LAST 30 DAYS"),
                    selected = if (window == InsightsWindow.LAST_7_DAYS) 0 else 1,
                    onSelect = { onWindowChange(if (it == 0) InsightsWindow.LAST_7_DAYS else InsightsWindow.LAST_30_DAYS) },
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        item { SectionHeader("OVERVIEW", meta = "local, verified") }

        item { com.cues.app.ui.components.ProjectedSavingsTrendSection() }

        item {
            SlabCard(tier = SlabTier.TWO, modifier = Modifier.fillMaxWidth()) {
                Text("TIME IN CUES", style = CuesType.labelSmall, color = t.inkSlate)
                val time = report.timeInCues
                if (time == null) {
                    Text("No sessions ended in this window.", style = CuesType.body, color = t.inkSecondary)
                } else {
                    val hours = time.totalMillis / 3_600_000
                    val minutes = (time.totalMillis / 60_000) % 60
                    Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                        Text("${hours}h ${minutes}m", style = CuesType.display, color = t.inkPrimary)
                        val delta = time.deltaMillis
                        if (delta != null) {
                            val sign = if (delta >= 0) "+" else "-"
                            val deltaMin = kotlin.math.abs(delta) / 60_000
                            Text(
                                "  $sign${deltaMin}m vs previous",
                                style = CuesType.labelSmall,
                                color = if (delta >= 0) t.go else t.warn,
                                modifier = Modifier.padding2(bottom = 6.dp),
                            )
                        } else {
                            Text("  · no earlier data", style = CuesType.labelSmall, color = t.inkSlate, modifier = Modifier.padding2(bottom = 6.dp))
                        }
                    }
                    Text("${time.endedSessions} ended session(s)", style = CuesType.labelSmall, color = t.inkSlate)
                }
            }
        }

        val counts = report.counts
        if (counts != null) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    KpiTile("STARTED", counts.started.toString(), t.inkPrimary, Modifier.weight(1f))
                    KpiTile("SKIPPED", counts.skipped?.toString() ?: "—", t.inkPrimary, Modifier.weight(1f))
                    KpiTile(
                        "NEEDS ATTENTION",
                        counts.needsAttention.toString(),
                        if (counts.needsAttention > 0) t.stop else t.go,
                        Modifier.weight(1f),
                    )
                }
            }
        }

        if (report.skipReasons.isNotEmpty()) {
            item { SectionHeader("WHY THINGS WERE SKIPPED") }
            item {
                SlabCard(tier = SlabTier.ONE, modifier = Modifier.fillMaxWidth()) {
                    report.skipReasons.forEach { group ->
                        val isUnknown = group.family == SkipFamily.UNKNOWN
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding2(top = if (isUnknown) 0.dp else 4.dp)) {
                            Text(
                                group.family.name.lowercase().replace('_', ' '),
                                style = CuesType.body,
                                color = if (isUnknown) t.untilOrange else t.inkSecondary,
                            )
                            Text("${group.count}", style = CuesType.labelSmall, color = if (isUnknown) t.untilOrange else t.inkSlate)
                        }
                        if (isUnknown && group.unreadable.isNotEmpty()) {
                            group.unreadable.forEach { u ->
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding2(top = 2.dp)) {
                                    Text("  ${u.source.name.lowercase()} · ${u.reason.name.lowercase().replace('_', ' ')}", style = CuesType.labelSmall, color = t.inkSlate)
                                    if (u.remedy != null) {
                                        Text("Fix →", style = CuesType.labelSmall, color = t.doYellow, modifier = Modifier.clickable2(onFixCapability))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val cleanup = report.cleanup
        if (cleanup != null) {
            item { SectionHeader("CLEANUP LEDGER") }
            item {
                SlabCard(tier = SlabTier.ONE, edgeColor = if (cleanup.outstanding.isNotEmpty()) t.dangerEdge else null, modifier = Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Released this window", style = CuesType.body, color = t.inkSecondary)
                        Text("${cleanup.released}", style = CuesType.bodyMedium, color = t.go)
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding2(top = 4.dp)) {
                        Text("Outstanding", style = CuesType.body, color = t.inkSecondary)
                        StatusPill(
                            "${cleanup.outstanding.size}",
                            if (cleanup.outstanding.isEmpty()) StatusTone.GO else StatusTone.STOP,
                        )
                    }
                    cleanup.outstanding.forEach { o ->
                        Text(
                            "  ${o.resource.name.lowercase().replace('_', ' ')} · ${o.failureDetail ?: "unreleased"}",
                            style = CuesType.labelSmall,
                            color = t.stop,
                            modifier = Modifier.padding2(top = 4.dp),
                        )
                    }
                }
            }
        }

        if (report.blocked.isNotEmpty()) {
            item { SectionHeader("BLOCKED ACTIONS") }
            item {
                SlabCard(tier = SlabTier.ONE, edgeColor = t.dangerEdge, modifier = Modifier.fillMaxWidth()) {
                    report.blocked.forEach { b ->
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(b.actionId.name.lowercase().replace('_', ' '), style = CuesType.body, color = t.stop)
                            Text("${b.count}", style = CuesType.labelSmall, color = t.stop)
                        }
                    }
                }
            }
        }

        if (report.perRoutine.isNotEmpty()) {
            item { SectionHeader("PER-CUE ATTRIBUTION") }
            items2(report.perRoutine) { stat ->
                SlabCard(tier = SlabTier.ONE, modifier = Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(stat.title ?: "(deleted cue)", style = CuesType.bodyMedium, color = t.inkPrimary)
                        Text("${stat.runs} run(s)", style = CuesType.labelSmall, color = t.inkSlate)
                    }
                    val median = stat.medianDurationMillis
                    Text(
                        buildString {
                            if (median != null) append("median ${median / 60_000}m · ")
                            append("${stat.skips ?: 0} skip(s)")
                            val topFamily = stat.topSkipFamily
                            if (topFamily != null) append(" (mostly ${topFamily.name.lowercase()})")
                            if (stat.blockedActions > 0) append(" · ${stat.blockedActions} blocked")
                        },
                        style = CuesType.labelSmall,
                        color = t.inkSlate,
                        modifier = Modifier.padding2(top = 2.dp),
                    )
                }
            }
        }

        val coverage = report.coverage
        if (coverage != null && coverage.sessionsEndedByGap > 0) {
            item { SectionHeader("COVERAGE") }
            item {
                SlabCard(tier = SlabTier.ONE, edgeColor = t.dangerEdge, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "${coverage.sessionsEndedByGap} session(s) ended from an inferred gap — Cues lost track while backgrounded.",
                        style = CuesType.body,
                        color = t.warn,
                    )
                    if (coverage.coachConclusionsSuppressed == true) {
                        Text(
                            "Coach suggestions are held back: gaps cover too much of this window to trust a pattern.",
                            style = CuesType.labelSmall,
                            color = t.inkSlate,
                            modifier = Modifier.padding2(top = 4.dp),
                        )
                    }
                }
            }
        }

        item {
            SlabCard(tier = SlabTier.ONE, modifier = Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("LEARNING", style = CuesType.labelSmall, color = t.inkSlate)
                        Text(
                            "Records events locally for 14 days, to power skip reasons and the coach.",
                            style = CuesType.labelSmall,
                            color = t.inkSlate,
                        )
                    }
                    KineticSwitch(checked = report.ledgerEnabled, onCheckedChange = onToggleLedger)
                }
            }
        }

        item {
            SlabCard(tier = SlabTier.ONE, modifier = Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text("DRAFTING PATH", style = CuesType.labelSmall, color = t.inkSlate)
                        Text(drafterLabel, style = CuesType.bodyMedium, color = t.inkPrimary)
                    }
                    val backend = lastTrace?.attempts?.firstNotNullOfOrNull { it.inferenceReport }?.backend
                    backend?.let { BackendPill(it) }
                }
                // Every attempt the last draft made, not only the winner —
                // Sprint 8 replaced the always-null "fell back" line with
                // this, since a real fallback reason now lives on the trace.
                lastTrace?.attempts?.forEach { attempt ->
                    if (attempt.reasonCode != null) {
                        Text("${attempt.source.name.lowercase()}: ${attempt.reasonCode}", style = CuesType.labelSmall, color = t.warn)
                    }
                }
            }
        }

        val usage = report.inferenceUsage
        if (usage != null) {
            item { SectionHeader("MODEL USAGE & COST") }
            item {
                SlabCard(tier = SlabTier.ONE, modifier = Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("ON-DEVICE", style = CuesType.labelSmall, color = t.inkSlate)
                        Text("${usage.onDeviceCalls} call(s)", style = CuesType.labelSmall, color = t.inkSlate)
                    }
                    Text(
                        "${usage.onDeviceTokens} tokens (est.) · \$0.00" +
                            (usage.onDeviceLatencyMedianMs?.let { " · median ${it}ms" } ?: ""),
                        style = CuesType.body,
                        color = t.go,
                        modifier = Modifier.padding2(top = 2.dp),
                    )
                    if (usage.onDeviceTokens > 0) {
                        Text(
                            savedVsCloudLine(usage.onDeviceTokens),
                            style = CuesType.labelSmall,
                            color = t.inkSlate,
                            modifier = Modifier.padding2(top = 2.dp),
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding2(top = 12.dp),
                    ) {
                        Text("CLOUD (SARVAM)", style = CuesType.labelSmall, color = t.inkSlate)
                        Text("${usage.cloudCalls} call(s)", style = CuesType.labelSmall, color = t.inkSlate)
                    }
                    Text(
                        "${usage.cloudTokens} tokens (est.) · ${costLine(usage.cloudCostUsd, usage.cloudCostBasis)}" +
                            (usage.cloudLatencyMedianMs?.let { " · median ${it}ms" } ?: ""),
                        style = CuesType.body,
                        color = t.inkSecondary,
                        modifier = Modifier.padding2(top = 2.dp),
                    )
                }
            }
        }

        item {
            SlabCard(tier = SlabTier.ONE, modifier = Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("USAGE & COST TRACKING", style = CuesType.labelSmall, color = t.inkSlate)
                        Text(
                            "Records each model call's token estimate and cost locally for 30 days.",
                            style = CuesType.labelSmall,
                            color = t.inkSlate,
                        )
                    }
                    KineticSwitch(checked = report.usageTrackingEnabled, onCheckedChange = onToggleUsageTracking)
                }
            }
        }

        item {
            com.cues.app.ui.components.GhostButton("Export Cue Console (HTML)", onExportConsole, modifier = Modifier.fillMaxWidth())
        }

        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun KpiTile(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    SlabCard(tier = SlabTier.ONE, modifier = modifier) {
        Text(label, style = CuesType.labelSmall, color = cuesTokens.inkSlate)
        Text(value, style = CuesType.headline, color = color, modifier = Modifier.padding2(top = 2.dp))
    }
}

/** Never a fabricated `$0.00` for an unverified rate — this says so plainly instead. */
private fun costLine(costUsd: Double?, basis: CostBasis?): String = when (basis) {
    CostBasis.CLOUD_METERED -> "$" + "%.4f".format(costUsd ?: 0.0)
    CostBasis.ON_DEVICE_FREE -> "$0.00"
    CostBasis.UNVERIFIED, null -> "cost not verified"
}

/**
 * What the on-device tokens *would* have cost on Sarvam's own rate — reuses
 * [InferenceCost.costFor] rather than a second cost formula, so this can
 * never silently disagree with the CLOUD (SARVAM) line above it. Renders
 * "not verified" instead of a number until a real Sarvam rate is pinned
 * (CLEANUP.md CL-37) — same discipline as [costLine].
 */
private fun savedVsCloudLine(onDeviceTokens: Long): String {
    val (saved, basis) = InferenceCost.costFor(DraftSourceId.SARVAM_CLOUD, onDeviceTokens.toInt())
    return if (basis == CostBasis.CLOUD_METERED) "≈ \$${"%.4f".format(saved)} saved vs. cloud" else "savings not verified — no Sarvam rate on file"
}

/** NPU/GPU are a local, accelerated tier; CPU is the honest fallback; CLOUD never ran on this phone at all. */
@Composable
private fun BackendPill(backend: InferenceBackend) {
    val (label, tone) = when (backend) {
        InferenceBackend.NPU -> "NPU" to StatusTone.GO
        InferenceBackend.GPU -> "GPU" to StatusTone.GO
        InferenceBackend.CPU -> "CPU" to StatusTone.WARN
        InferenceBackend.CLOUD -> "CLOUD" to StatusTone.UNKNOWN
    }
    StatusPill(label, tone)
}

private fun Modifier.padding2(top: androidx.compose.ui.unit.Dp = 0.dp, bottom: androidx.compose.ui.unit.Dp = 0.dp): Modifier =
    this.then(Modifier.padding(top = top, bottom = bottom))

private fun Modifier.clickable2(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))

private fun <T> androidx.compose.foundation.lazy.LazyListScope.items2(list: List<T>, content: @Composable (T) -> Unit) {
    items(list.size) { index -> content(list[index]) }
}
