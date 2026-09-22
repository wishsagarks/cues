package com.cues.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cues.core.model.RoutineStatus
import com.cues.core.model.SessionState

/**
 * The state pill from the design system: `go`/`stop`/`amber`, never the
 * brand accent. One shared composable so every screen's status reads the
 * same way.
 */
@Composable
fun StatusChip(label: String, tone: Tone, modifier: Modifier = Modifier) {
    val colors = cuesColors
    val (fg, bg) = when (tone) {
        Tone.GO -> colors.go to colors.goBg
        Tone.STOP -> colors.stop to colors.stopBg
        Tone.AMBER -> colors.amber to colors.amberBg
    }
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(999.dp), modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

enum class Tone { GO, STOP, AMBER }

fun RoutineStatus.toTone(): Tone = when (this) {
    RoutineStatus.ARMED -> Tone.GO
    RoutineStatus.PAUSED -> Tone.AMBER
    RoutineStatus.DRAFT, RoutineStatus.REVIEWABLE -> Tone.AMBER
    RoutineStatus.INVALID, RoutineStatus.DISABLED -> Tone.STOP
}

fun SessionState.toTone(): Tone = when (this) {
    SessionState.ACTIVE, SessionState.STARTING, SessionState.COMPLETED -> Tone.GO
    SessionState.EXIT_PENDING, SessionState.PARTIAL, SessionState.CLEANUP_PENDING -> Tone.AMBER
    SessionState.ENDING, SessionState.CANCELLED, SessionState.FAILED -> Tone.STOP
}
