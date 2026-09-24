package com.cues.app.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cues.app.ui.components.EmptyState
import com.cues.app.ui.components.KineticSwitch
import com.cues.app.ui.components.SectionHeader
import com.cues.app.ui.components.SlabCard
import com.cues.app.ui.components.SlabTier
import com.cues.app.ui.theme.CuesType
import com.cues.app.ui.theme.cuesTokens
import com.cues.core.coach.LedgerEvent
import com.cues.core.review.ForecastItem
import com.cues.core.review.ForecastStatus

/**
 * Insights (redesign plan §3.2): computed only from what's actually
 * measured — the ledger (opt-in) and today's forecast. No invented "reclaim
 * index" or "intercepts" — see the plan's §7 honesty table. A full
 * cross-session engine (`:core Insights`) lands in a later phase; until
 * then this reads today plus the ledger directly.
 */
@Composable
fun InsightsScreen(
    forecast: List<ForecastItem>,
    titleFor: (String) -> String,
    ledgerEnabled: Boolean,
    onToggleLedger: (Boolean) -> Unit,
    ledgerEvents: List<LedgerEvent>,
    drafterLabel: String,
    lastFallbackReason: String?,
    onExportConsole: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = cuesTokens
    val sessionEnds = ledgerEvents.filterIsInstance<LedgerEvent.SessionEnded>()
    val totalMinutes = sessionEnds.sumOf { it.actualMinutes }
    val skips = ledgerEvents.filterIsInstance<LedgerEvent.Skipped>()
    val skipByReason = skips.groupingBy { it.reasonCode }.eachCount()
    val blocked = ledgerEvents.filterIsInstance<LedgerEvent.ActionBlocked>()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SlabCard(tier = SlabTier.TWO, modifier = Modifier.fillMaxWidth()) {
                Text("TIME IN CUES", style = CuesType.labelSmall, color = t.inkSlate)
                if (ledgerEnabled && sessionEnds.isNotEmpty()) {
                    Text("${totalMinutes / 60}h ${totalMinutes % 60}m", style = CuesType.display, color = t.inkPrimary)
                    Text(
                        "${sessionEnds.size} recorded session(s), held 14 days",
                        style = CuesType.labelSmall,
                        color = t.inkSlate,
                    )
                } else if (!ledgerEnabled) {
                    Text(
                        "Turn on Learning below to see time in cues, computed on this phone from your own sessions.",
                        style = CuesType.body,
                        color = t.inkSecondary,
                    )
                } else {
                    Text("No sessions recorded yet.", style = CuesType.body, color = t.inkSecondary)
                }
            }
        }

        item { SectionHeader("TODAY", meta = "${forecast.count { it.status == ForecastStatus.WILL_ARM }} will arm") }
        if (forecast.isEmpty()) {
            item { EmptyState("Nothing forecast", "Arm a cue to see today's forecast here.") }
        } else {
            item {
                SlabCard(tier = SlabTier.ONE, modifier = Modifier.fillMaxWidth()) {
                    forecast.forEach { item ->
                        val (color, label) = when (item.status) {
                            ForecastStatus.WILL_ARM -> t.go to "will arm"
                            ForecastStatus.SKIPPED_BY_PATCH -> t.warn to "skipped"
                            ForecastStatus.CANNOT_TELL -> t.unknown to "can't tell"
                            ForecastStatus.NOT_TODAY -> t.inkSlate to "not today"
                        }
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(titleFor(item.routineId), style = CuesType.body, color = t.inkSecondary, modifier = Modifier.weight(1f))
                            Text(label, style = CuesType.labelSmall, color = color)
                        }
                    }
                }
            }
        }

        if (ledgerEnabled) {
            item { SectionHeader("WHY THINGS WERE SKIPPED", meta = "${skips.size} recorded") }
            if (skipByReason.isEmpty()) {
                item { Text("No skips recorded yet.", style = CuesType.body, color = t.inkSlate) }
            } else {
                item {
                    SlabCard(tier = SlabTier.ONE, modifier = Modifier.fillMaxWidth()) {
                        skipByReason.entries.sortedByDescending { it.value }.forEach { (reason, count) ->
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(reason.lowercase().replace('_', ' '), style = CuesType.body, color = t.inkSecondary)
                                Text("$count", style = CuesType.labelSmall, color = t.inkSlate)
                            }
                        }
                    }
                }
            }

            item { SectionHeader("BLOCKED ACTIONS", meta = "${blocked.size} recorded") }
            if (blocked.isEmpty()) {
                item { Text("Nothing blocked.", style = CuesType.body, color = t.inkSlate) }
            } else {
                item {
                    SlabCard(tier = SlabTier.ONE, edgeColor = t.dangerEdge, modifier = Modifier.fillMaxWidth()) {
                        blocked.groupingBy { it.actionId }.eachCount().entries.forEach { (actionId, count) ->
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(actionId.lowercase().replace('_', ' '), style = CuesType.body, color = t.stop)
                                Text("$count", style = CuesType.labelSmall, color = t.stop)
                            }
                        }
                    }
                }
            }
        }

        item {
            SlabCard(tier = SlabTier.ONE, modifier = Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text("LEARNING", style = CuesType.labelSmall, color = t.inkSlate)
                        Text(
                            "Records events locally for 14 days, to power skip reasons and the coach.",
                            style = CuesType.labelSmall,
                            color = t.inkSlate,
                        )
                    }
                    KineticSwitch(checked = ledgerEnabled, onCheckedChange = onToggleLedger)
                }
            }
        }

        item {
            SlabCard(tier = SlabTier.ONE, modifier = Modifier.fillMaxWidth()) {
                Text("DRAFTING PATH", style = CuesType.labelSmall, color = t.inkSlate)
                Text(drafterLabel, style = CuesType.bodyMedium, color = t.inkPrimary)
                if (lastFallbackReason != null) {
                    Text("Fell back: $lastFallbackReason", style = CuesType.labelSmall, color = t.warn)
                }
            }
        }

        item {
            com.cues.app.ui.components.GhostButton("Export Cue Console (HTML)", onExportConsole, modifier = Modifier.fillMaxWidth())
        }

        item { Spacer(Modifier.height(72.dp)) }
    }
}
