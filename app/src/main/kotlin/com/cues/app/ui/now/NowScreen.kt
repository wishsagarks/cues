package com.cues.app.ui.now

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cues.app.runtime.AdapterStatus
import com.cues.app.ui.components.ClauseBlock
import com.cues.app.ui.components.CountdownRing
import com.cues.app.ui.components.EmptyState
import com.cues.app.ui.components.FilterChipRow
import com.cues.app.ui.components.HaltButton
import com.cues.app.ui.components.KineticSwitch
import com.cues.app.ui.components.MonoReadout
import com.cues.app.ui.components.RiskTag
import com.cues.app.ui.components.SectionHeader
import com.cues.app.ui.components.SlabCard
import com.cues.app.ui.components.SlabTier
import com.cues.app.ui.components.StatusPill
import com.cues.app.ui.components.TrustChip
import com.cues.app.ui.components.formatCountdown
import com.cues.app.ui.components.label
import com.cues.app.ui.components.tone
import com.cues.app.ui.theme.CuesShape
import com.cues.app.ui.theme.CuesType
import com.cues.app.ui.theme.cuesTokens
import com.cues.core.model.ContextValue
import com.cues.core.model.Routine
import com.cues.core.model.RoutineStatus
import com.cues.core.model.Session
import com.cues.core.model.SessionState
import com.cues.core.registry.ActionRegistry
import com.cues.core.registry.ActionRisk
import com.cues.core.review.ForecastItem
import com.cues.core.review.ForecastStatus
import com.cues.core.review.ReviewCopy

/**
 * The Now cockpit (redesign plan §3.1). Everything on this screen is
 * provable from `:core` or the live device snapshot — see the plan's §7
 * honesty table for what was cut from the Stitch mockup and why.
 */
@Composable
fun NowScreen(
    routines: List<Routine>,
    liveSessions: List<Session>,
    signalGates: List<SignalGate>,
    forecast: List<ForecastItem>,
    titleFor: (String) -> String,
    drafterLabel: String,
    onOpenRoutine: (Routine) -> Unit,
    onArmPause: (Routine) -> Unit,
    onManualStop: (Session) -> Unit,
    onGrantCapability: () -> Unit,
    adapterStatuses: List<AdapterStatus>,
    modifier: Modifier = Modifier,
) {
    var filter by remember { mutableStateOf(0) }
    val t = cuesTokens
    val counts = remember(routines) {
        listOf(
            routines.count { it.status == RoutineStatus.ARMED },
            routines.count { it.status == RoutineStatus.PAUSED },
            routines.count { it.status == RoutineStatus.DRAFT || it.status == RoutineStatus.REVIEWABLE },
            routines.size,
        )
    }
    val filtered = remember(routines, filter) {
        when (filter) {
            0 -> routines.filter { it.status == RoutineStatus.ARMED }
            1 -> routines.filter { it.status == RoutineStatus.PAUSED }
            2 -> routines.filter { it.status == RoutineStatus.DRAFT || it.status == RoutineStatus.REVIEWABLE }
            else -> routines
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (liveSessions.isNotEmpty()) {
            items(liveSessions, key = { "live-" + it.id }) { session ->
                val routine = routines.firstOrNull { it.id == session.routineId }
                if (routine != null) LiveSessionHero(routine, session, onManualStop = { onManualStop(session) })
            }
        }

        item {
            // A horizontally scrolling strip, not a plain Row: three chips
            // routinely overflow a phone-width screen, and a Row's children
            // (with no `weight`) are still measured with the *item's* loose
            // maxWidth as their own upper bound — on a narrow device that
            // starved the label text of room and wrapped it into a tall
            // column of single characters, ballooning the whole row's
            // height. A scrollable Row gives every chip its natural,
            // unconstrained width instead.
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()),
            ) {
                TrustChip("NO MODEL AT RUNTIME")
                TrustChip("NO INTERNET PERMISSION")
                TrustChip("DRAFTING: $drafterLabel")
            }
        }

        if (signalGates.isNotEmpty()) {
            item {
                SectionHeader("SIGNAL GATES", meta = "${signalGates.count { it.isKnown }}/${signalGates.size} known")
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(signalGates, key = { it.label }) { gate -> SignalGateTile(gate, onFix = onGrantCapability) }
                }
            }
        }

        if (forecast.isNotEmpty()) {
            item { SectionHeader("TODAY", meta = "${forecast.count { it.status == ForecastStatus.WILL_ARM }} will arm") }
            item { TodayStrip(forecast, titleFor) }
        }

        item {
            SectionHeader("YOUR CUES", meta = "${routines.size} total") {}
        }
        item {
            FilterChipRow(
                options = listOf("Armed" to counts[0], "Paused" to counts[1], "Drafts" to counts[2], "All" to counts[3]),
                selected = filter,
                onSelect = { filter = it },
            )
        }

        if (filtered.isEmpty()) {
            item {
                EmptyState(
                    title = if (routines.isEmpty()) "Speak your first cue" else "Nothing in this filter",
                    body = if (routines.isEmpty()) "Head to Ask and describe what should happen, and when it should stop." else "Try a different filter above.",
                )
            }
        } else {
            items(filtered, key = { it.id }) { routine ->
                val activeSession = liveSessions.firstOrNull { it.routineId == routine.id }
                CueSlabCard(routine, activeSession, onClick = { onOpenRoutine(routine) }, onArmPause = { onArmPause(routine) })
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun LiveSessionHero(routine: Routine, session: Session, onManualStop: () -> Unit) {
    val t = cuesTokens
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session.id) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }
    val deadline = session.deadlineMillis
    val remaining = deadline?.let { it - nowMillis }
    val total = deadline?.let { it - session.startedAtMillis }
    val progress = if (deadline != null && total != null && total > 0) {
        ((nowMillis - session.startedAtMillis).toFloat() / total).coerceIn(0f, 1f)
    } else 0f

    SlabCard(tier = SlabTier.TWO, edgeColor = t.activeEdge, modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            StatusPill(if (session.state == SessionState.EXIT_PENDING) "ENDING SOON" else "CUE ACTIVE NOW", session.state.tone())
        }
        Spacer(Modifier.height(10.dp))
        Text(routine.title, style = CuesType.headline, color = t.inkPrimary)
        Text(ReviewCopy.whenText(routine), style = CuesType.body, color = t.inkSecondary, modifier = Modifier.padding(top = 2.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
            if (remaining != null) {
                CountdownRing(progress = progress, size = 64.dp, strokeWidth = 4.dp) {
                    MonoReadout(formatCountdown(remaining), color = t.doYellow)
                }
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                val owns = session.obligations.joinToString(", ") { it.resource.name.lowercase().replace('_', ' ') }
                if (owns.isNotBlank()) {
                    Text("Owns: $owns", style = CuesType.labelSmall, color = t.inkSlate)
                }
                val blocked = session.actions.filter {
                    it.state == com.cues.core.model.ActionState.BLOCKED || it.state == com.cues.core.model.ActionState.FAILED
                }
                blocked.forEach {
                    Text("${it.actionId.name}: ${it.detail ?: "blocked"}", style = CuesType.labelSmall, color = t.stop)
                }
            }
        }
        val pendingExitAt = session.pendingExitAtMillis
        if (pendingExitAt != null) {
            val secsLeft = ((pendingExitAt - nowMillis) / 1000).coerceAtLeast(0)
            Text(
                "Reconnect within ${secsLeft}s to keep going",
                style = CuesType.labelSmall,
                color = t.warn,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        HaltButton("End & Restore now", onManualStop, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun TodayStrip(forecast: List<ForecastItem>, titleFor: (String) -> String) {
    val t = cuesTokens
    SlabCard(tier = SlabTier.ONE, modifier = Modifier.fillMaxWidth()) {
        forecast.take(6).forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                val (dotColor, label) = when (item.status) {
                    ForecastStatus.WILL_ARM -> t.go to "will arm"
                    ForecastStatus.SKIPPED_BY_PATCH -> t.warn to "skipped"
                    ForecastStatus.CANNOT_TELL -> t.unknown to "can't tell"
                    ForecastStatus.NOT_TODAY -> t.inkSlate to "not today"
                }
                Box(Modifier.height(6.dp).width(6.dp).background(dotColor, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(titleFor(item.routineId), style = CuesType.body, color = t.inkSecondary, modifier = Modifier.weight(1f))
                Text(label, style = CuesType.labelSmall, color = dotColor)
            }
        }
    }
}

@Composable
private fun CueSlabCard(routine: Routine, activeSession: Session?, onClick: () -> Unit, onArmPause: () -> Unit) {
    val t = cuesTokens
    val topRisk = remember(routine) {
        routine.actions.mapNotNull { ActionRegistry.definition(it.actionId)?.risk }.maxByOrNull { it.ordinal } ?: ActionRisk.LOCAL_NOTICE
    }
    SlabCard(
        tier = if (activeSession != null) SlabTier.TWO else SlabTier.ONE,
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RiskTag(topRisk)
                    Spacer(Modifier.width(6.dp))
                    StatusPill(
                        if (activeSession != null) activeSession.state.label() else routine.status.label(),
                        if (activeSession != null) activeSession.state.tone() else routine.status.tone(),
                    )
                }
                Text(routine.title, style = CuesType.title, color = t.inkPrimary, modifier = Modifier.padding(top = 6.dp))
            }
            KineticSwitch(checked = routine.status == RoutineStatus.ARMED, onCheckedChange = { onArmPause() })
        }
        Spacer(Modifier.height(8.dp))
        ClauseBlock(routine, compact = true)
    }
}

/** One row of the Signal Gates grid — a live [ContextValue] with a human label. */
data class SignalGate(val label: String, val value: ContextValue<String>) {
    val isKnown: Boolean get() = value is ContextValue.Known
}

@Composable
private fun SignalGateTile(gate: SignalGate, onFix: () -> Unit) {
    val t = cuesTokens
    SlabCard(
        tier = SlabTier.ONE,
        modifier = Modifier.width(150.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp),
    ) {
        Text(gate.label.uppercase(), style = CuesType.labelSmall, color = t.inkSlate)
        when (val v = gate.value) {
            is ContextValue.Known -> Text(v.value, style = CuesType.bodyMedium, color = t.inkPrimary, modifier = Modifier.padding(top = 4.dp))
            is ContextValue.Unknown -> {
                Text(
                    "UNKNOWN · ${unknownReasonLabel(v.reason)}",
                    style = CuesType.labelSmall,
                    color = t.unknown,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (v.reason == com.cues.core.model.UnknownReason.PERMISSION_DENIED) {
                    Text(
                        "Grant →",
                        style = CuesType.labelSmall,
                        color = t.doYellow,
                        modifier = Modifier.padding(top = 4.dp).clickable { onFix() },
                    )
                }
            }
        }
    }
}

private fun unknownReasonLabel(reason: com.cues.core.model.UnknownReason): String = when (reason) {
    com.cues.core.model.UnknownReason.PERMISSION_DENIED -> "permission denied"
    com.cues.core.model.UnknownReason.ADAPTER_UNAVAILABLE -> "unavailable"
    com.cues.core.model.UnknownReason.NEVER_OBSERVED -> "never observed"
    com.cues.core.model.UnknownReason.STALE -> "stale"
    com.cues.core.model.UnknownReason.REDACTED_BY_OS -> "redacted by OS"
}
