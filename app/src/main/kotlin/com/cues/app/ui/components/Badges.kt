package com.cues.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cues.app.ui.theme.CuesShape
import com.cues.app.ui.theme.CuesSpacing
import com.cues.app.ui.theme.CuesType
import com.cues.core.eval.Truth
import com.cues.core.model.ActionState
import com.cues.core.model.EndReason
import com.cues.core.model.RoutineStatus
import com.cues.core.model.SessionState
import com.cues.core.registry.ActionRisk

/** The five WHEN/IF/DO/UNTIL/RESTORE clause kinds, each with its own hue (Stitch DESIGN_SYSTEM §Components). */
enum class ClauseKind { WHEN, IF, DO, UNTIL, RESTORE }

@Composable
private fun clauseColors(kind: ClauseKind): Pair<Color, Color> {
    val t = com.cues.app.ui.theme.cuesTokens
    return when (kind) {
        ClauseKind.WHEN -> t.whenTeal to t.whenBg
        ClauseKind.IF -> t.ifSlate to t.ifBg
        ClauseKind.DO -> t.doYellow to t.doBg
        ClauseKind.UNTIL -> t.untilOrange to t.untilBg
        ClauseKind.RESTORE -> t.restoreGhost to t.restoreBg
    }
}

/** A small uppercase mono pill: `WHEN`, `IF`, `DO`, `UNTIL`, `RESTORE`. */
@Composable
fun ClauseBadge(kind: ClauseKind, modifier: Modifier = Modifier) {
    val (fg, bg) = clauseColors(kind)
    val onFilled = kind == ClauseKind.DO && com.cues.app.ui.theme.cuesTokens.isDark
    Text(
        text = kind.name,
        style = CuesType.clause,
        color = if (onFilled) Color(0xFF0B0C10) else fg,
        modifier = modifier
            .background(if (onFilled) fg else bg, CuesShape.badge)
            .border(1.dp, if (onFilled) Color.Transparent else fg.copy(alpha = 0.4f), CuesShape.badge)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

enum class StatusTone { GO, WARN, STOP, UNKNOWN }

@Composable
private fun toneColors(tone: StatusTone): Pair<Color, Color> {
    val t = com.cues.app.ui.theme.cuesTokens
    return when (tone) {
        StatusTone.GO -> t.go to t.goBg
        StatusTone.WARN -> t.warn to t.warnBg
        StatusTone.STOP -> t.stop to t.stopBg
        StatusTone.UNKNOWN -> t.unknown to t.unknownBg
    }
}

/** A status word, never color alone — every [StatusTone] carries a distinct label from its caller. */
@Composable
fun StatusPill(label: String, tone: StatusTone, modifier: Modifier = Modifier) {
    val (fg, bg) = toneColors(tone)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(bg, CuesShape.capsule)
            .border(1.dp, fg.copy(alpha = 0.35f), CuesShape.capsule)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        StatusDot(fg)
        Text(label, style = CuesType.labelSmall, color = fg, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun StatusDot(color: Color) = androidx.compose.foundation.layout.Box(
    modifier = Modifier.size(6.dp).background(color, CircleShape),
)

fun RoutineStatus.label(): String = when (this) {
    RoutineStatus.DRAFT -> "DRAFT"
    RoutineStatus.INVALID -> "NEEDS FIXING"
    RoutineStatus.REVIEWABLE -> "NEEDS REVIEW"
    RoutineStatus.ARMED -> "ARMED"
    RoutineStatus.PAUSED -> "PAUSED"
    RoutineStatus.DISABLED -> "DISABLED"
}

fun RoutineStatus.tone(): StatusTone = when (this) {
    RoutineStatus.ARMED -> StatusTone.GO
    RoutineStatus.PAUSED, RoutineStatus.REVIEWABLE, RoutineStatus.DRAFT -> StatusTone.WARN
    RoutineStatus.INVALID, RoutineStatus.DISABLED -> StatusTone.STOP
}

fun SessionState.label(): String = when (this) {
    SessionState.STARTING -> "STARTING"
    SessionState.ACTIVE -> "RUNNING"
    SessionState.EXIT_PENDING -> "ENDING SOON"
    SessionState.ENDING -> "ENDING"
    SessionState.COMPLETED -> "ENDED CLEAN"
    SessionState.CANCELLED -> "CANCELLED"
    SessionState.PARTIAL -> "PARTIAL"
    SessionState.CLEANUP_PENDING -> "CLEANUP PENDING"
    SessionState.FAILED -> "FAILED"
}

fun SessionState.tone(): StatusTone = when (this) {
    SessionState.ACTIVE, SessionState.STARTING, SessionState.COMPLETED -> StatusTone.GO
    SessionState.EXIT_PENDING, SessionState.ENDING -> StatusTone.WARN
    SessionState.PARTIAL, SessionState.CLEANUP_PENDING, SessionState.FAILED, SessionState.CANCELLED -> StatusTone.STOP
}

fun ActionState.label(): String = when (this) {
    ActionState.NOT_STARTED -> "not started"
    ActionState.IN_PROGRESS -> "in progress"
    ActionState.SUCCEEDED -> "done"
    ActionState.BLOCKED -> "blocked"
    ActionState.FAILED -> "failed"
    ActionState.COMPENSATED -> "released"
    ActionState.COMPENSATION_FAILED -> "could not be released"
    ActionState.PENDING -> "waiting for you"
}

fun ActionState.tone(): StatusTone = when (this) {
    ActionState.SUCCEEDED, ActionState.COMPENSATED -> StatusTone.GO
    ActionState.IN_PROGRESS, ActionState.PENDING, ActionState.NOT_STARTED -> StatusTone.WARN
    ActionState.BLOCKED, ActionState.FAILED, ActionState.COMPENSATION_FAILED -> StatusTone.STOP
}

fun EndReason.label(): String = when (this) {
    EndReason.DEADLINE_REACHED -> "timer reached its deadline"
    EndReason.TRIGGER_REVERSED -> "the trigger ended"
    EndReason.MANUAL_STOP -> "you stopped it"
    EndReason.ROUTINE_PAUSED -> "the cue was paused"
    EndReason.RECONCILED_EXPIRED -> "resumed after a restart, past its deadline"
    EndReason.START_FAILED -> "it could not start"
    EndReason.COVERAGE_GAP -> "we lost track of the device while backgrounded"
}

fun ActionRisk.label(): String = when (this) {
    ActionRisk.OWNED_AND_REVERSIBLE -> "OWNED & REVERSIBLE"
    ActionRisk.LOCAL_NOTICE -> "LOCAL NOTICE"
    ActionRisk.HANDOFF -> "HANDOFF"
    ActionRisk.EXTERNAL_UNOWNED -> "EXTERNAL"
    ActionRisk.UI_AUTOMATION -> "UI AUTOMATION"
}

@Composable
fun ActionRisk.color(): Color {
    val t = com.cues.app.ui.theme.cuesTokens
    return when (this) {
        ActionRisk.OWNED_AND_REVERSIBLE -> t.whenTeal
        ActionRisk.LOCAL_NOTICE -> t.ifSlate
        ActionRisk.HANDOFF -> t.ifSlate
        ActionRisk.EXTERNAL_UNOWNED -> t.warn
        ActionRisk.UI_AUTOMATION -> t.untilOrange
    }
}

/** A risk-tier tag, e.g. "OWNED & REVERSIBLE" in teal, "UI AUTOMATION" in orange. */
@Composable
fun RiskTag(risk: ActionRisk, modifier: Modifier = Modifier) {
    val color = risk.color()
    Text(
        text = risk.label(),
        style = CuesType.labelSmall,
        color = color,
        textAlign = TextAlign.Center,
        modifier = modifier
            .background(color.copy(alpha = 0.10f), CuesShape.badge)
            .border(1.dp, color.copy(alpha = 0.4f), CuesShape.badge)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** A live gate readout: `now: MATCH` / `NO MATCH` / `UNKNOWN (reason)` — never color-only. */
@Composable
fun TruthChip(truth: Truth, reason: String? = null, modifier: Modifier = Modifier) {
    val t = com.cues.app.ui.theme.cuesTokens
    val (color, label) = when (truth) {
        Truth.MATCH -> t.go to "MATCH"
        Truth.NO_MATCH -> t.stop to "NO MATCH"
        Truth.UNKNOWN -> t.unknown to "UNKNOWN"
    }
    val text = if (truth == Truth.UNKNOWN && reason != null) "$label · $reason" else label
    Text(
        text = text,
        style = CuesType.labelSmall,
        color = color,
        modifier = modifier
            .background(color.copy(alpha = 0.12f), CuesShape.pill)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
