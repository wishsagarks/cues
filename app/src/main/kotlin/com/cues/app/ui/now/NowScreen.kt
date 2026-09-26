package com.cues.app.ui.now

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cues.app.runtime.AdapterStatus
import com.cues.app.ui.components.ClauseBlock
import com.cues.app.ui.components.CountdownRing
import com.cues.app.ui.components.EmptyState
import com.cues.app.ui.components.FilterChipRow
import com.cues.app.ui.components.GhostButton
import com.cues.app.ui.components.HaltButton
import com.cues.app.ui.components.KineticButton
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
import com.cues.app.ui.theme.CuesType
import com.cues.app.ui.theme.cuesTokens
import com.cues.core.model.ContextSnapshot
import com.cues.core.model.ContextValue
import com.cues.core.model.Routine
import com.cues.core.model.RoutineStatus
import com.cues.core.model.Session
import com.cues.core.model.SessionState
import com.cues.core.model.Trigger
import com.cues.core.model.UnknownReason
import com.cues.core.registry.ActionRegistry
import com.cues.core.registry.ActionRisk
import com.cues.core.review.ForecastItem
import com.cues.core.review.ForecastStatus
import com.cues.core.review.ReviewCopy
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/**
 * The Now cockpit (redesign plan §3.1). Everything on this screen is
 * provable from `:core` or the live device snapshot — see the plan's §7
 * honesty table for what was cut from the Stitch mockup and why.
 */
@Composable
fun NowScreen(
    routines: List<Routine>,
    liveSessions: List<Session>,
    snapshot: ContextSnapshot,
    chipset: String,
    phoneName: String,
    forecast: List<ForecastItem>,
    titleFor: (String) -> String,
    drafterLabel: String,
    onCreateCue: () -> Unit,
    onStartManualCue: (Routine) -> Unit,
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
            DeviceSnapshotCard(snapshot, chipset, phoneName, onFixCapability = onGrantCapability)
        }

        item {
            RuntimeContractPanel(drafterLabel)
        }

        item {
            ShortcutDeck(
                manualCues = routines.filter { it.status == RoutineStatus.ARMED && it.trigger is Trigger.Manual },
                onCreateCue = onCreateCue,
                onStartManualCue = onStartManualCue,
            )
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

/**
 * The app's visible equivalent of an Apple Shortcut: an action with an
 * explicit scope and result. Creating always enters review; running is only
 * offered for a cue that is already approved, armed, and manual.
 */
@Composable
private fun ShortcutDeck(
    manualCues: List<Routine>,
    onCreateCue: () -> Unit,
    onStartManualCue: (Routine) -> Unit,
) {
    val t = cuesTokens
    SlabCard(tier = SlabTier.ONE, modifier = Modifier.fillMaxWidth()) {
        SectionHeader("SHORTCUTS", meta = "reviewed actions")
        Text(
            "AI can help draft a cue. Only a reviewed, armed manual cue can run from here.",
            style = CuesType.labelSmall,
            color = t.inkSlate,
            modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
        )
        KineticButton("Create a cue", onCreateCue, modifier = Modifier.fillMaxWidth())
        manualCues.take(2).forEach { routine ->
            GhostButton(
                text = "Run ${routine.title}",
                onClick = { onStartManualCue(routine) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
        if (manualCues.isEmpty()) {
            Text(
                "Approve a cue with a manual trigger to make it runnable as a shortcut.",
                style = CuesType.labelSmall,
                color = t.inkSlate,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun RuntimeContractPanel(drafterLabel: String) {
    val t = cuesTokens
    SlabCard(tier = SlabTier.ONE, modifier = Modifier.fillMaxWidth()) {
        Text("RUNTIME CONTRACT", style = CuesType.label, color = t.inkSlate)
        Text(
            "What Cues is allowed to use after approval",
            style = CuesType.body,
            color = t.inkSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
        )
        TrustChip("NO MODEL AT RUNTIME", modifier = Modifier.fillMaxWidth())
        TrustChip("NO NETWORK AT RUNTIME", modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        TrustChip("DRAFTING: $drafterLabel", modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
    }
}

/**
 * The device snapshot (redesign plan follow-up): what used to be the "SIGNAL
 * GATES" checklist is now the first thing rendered on Now — a single,
 * well-composed card carrying phone identity, the live clock and the four
 * signals a cue can actually condition on (Wi-Fi, Bluetooth, battery,
 * charging). Unknown stays unknown here too: a tile whose value is
 * [ContextValue.Unknown] never quietly reads as "off", it shows the reason
 * and, when it's a fixable permission gap, a tap to Checks.
 */
@Composable
private fun DeviceSnapshotCard(
    snapshot: ContextSnapshot,
    chipset: String,
    phoneName: String,
    onFixCapability: () -> Unit,
) {
    val t = cuesTokens
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1000)
        }
    }
    val zone = remember(snapshot.zoneId) {
        runCatching { ZoneId.of(snapshot.zoneId) }.getOrElse { ZoneId.systemDefault() }
    }
    val instant = remember(nowMillis) { Instant.ofEpochMilli(nowMillis) }

    SlabCard(tier = SlabTier.TWO, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.weight(1f)) {
                Text(phoneName, style = CuesType.title, color = t.inkPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(chipset, style = CuesType.labelSmall, color = t.inkSlate, modifier = Modifier.padding(top = 2.dp))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(TIME_FORMAT.withZone(zone).format(instant), style = CuesType.headline, color = t.inkPrimary)
                Text(DATE_FORMAT.withZone(zone).format(instant), style = CuesType.labelSmall, color = t.inkSlate)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            val wifi = snapshot.wifi
            SignalTile(
                icon = if ((wifi as? ContextValue.Known)?.value?.connected == true) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                label = "WI-FI",
                value = when (wifi) {
                    is ContextValue.Known -> if (wifi.value.connected) wifi.value.networkLabel ?: "Connected" else "Disconnected"
                    is ContextValue.Unknown -> unknownReasonLabel(wifi.reason)
                },
                fixable = (wifi as? ContextValue.Unknown)?.reason == UnknownReason.PERMISSION_DENIED,
                onFixCapability = onFixCapability,
                modifier = Modifier.weight(1f),
            )
            val bluetooth = snapshot.connectedDeviceIds
            SignalTile(
                icon = Icons.Filled.Bluetooth,
                label = "BLUETOOTH",
                value = when (bluetooth) {
                    is ContextValue.Known -> if (bluetooth.value.isEmpty()) "None connected" else "${bluetooth.value.size} connected"
                    is ContextValue.Unknown -> unknownReasonLabel(bluetooth.reason)
                },
                fixable = (bluetooth as? ContextValue.Unknown)?.reason == UnknownReason.PERMISSION_DENIED,
                onFixCapability = onFixCapability,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            val battery = snapshot.batteryPercent
            val charging = snapshot.charging
            val isCharging = (charging as? ContextValue.Known)?.value == true
            SignalTile(
                icon = if (isCharging) Icons.Filled.BatteryChargingFull else Icons.Filled.BatteryFull,
                label = "BATTERY",
                value = when (battery) {
                    is ContextValue.Known -> "${battery.value}%"
                    is ContextValue.Unknown -> unknownReasonLabel(battery.reason)
                },
                fixable = (battery as? ContextValue.Unknown)?.reason == UnknownReason.PERMISSION_DENIED,
                onFixCapability = onFixCapability,
                modifier = Modifier.weight(1f),
            )
            SignalTile(
                icon = if (isCharging) Icons.Filled.BatteryChargingFull else Icons.Filled.PowerOff,
                label = "CHARGING",
                value = when (charging) {
                    is ContextValue.Known -> if (charging.value) "Plugged in" else "On battery"
                    is ContextValue.Unknown -> unknownReasonLabel(charging.reason)
                },
                fixable = (charging as? ContextValue.Unknown)?.reason == UnknownReason.PERMISSION_DENIED,
                onFixCapability = onFixCapability,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SignalTile(
    icon: ImageVector,
    label: String,
    value: String,
    fixable: Boolean,
    onFixCapability: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = cuesTokens
    Column(
        modifier = modifier
            .background(t.island, com.cues.app.ui.theme.CuesShape.card)
            .then(if (fixable) Modifier.clickable { onFixCapability() } else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = t.inkSecondary, modifier = Modifier.size(16.dp))
            Text(label, style = CuesType.labelSmall, color = t.inkSlate, modifier = Modifier.padding(start = 6.dp))
        }
        Text(
            value,
            style = CuesType.bodyMedium,
            color = if (fixable) t.warn else t.inkPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (fixable) {
            Text("Grant →", style = CuesType.labelSmall, color = t.doYellow, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

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

private fun unknownReasonLabel(reason: UnknownReason): String = when (reason) {
    UnknownReason.PERMISSION_DENIED -> "permission denied"
    UnknownReason.ADAPTER_UNAVAILABLE -> "unavailable"
    UnknownReason.NEVER_OBSERVED -> "never observed"
    UnknownReason.STALE -> "stale"
    UnknownReason.REDACTED_BY_OS -> "redacted by OS"
}
