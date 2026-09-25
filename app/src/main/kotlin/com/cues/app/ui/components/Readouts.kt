package com.cues.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cues.app.ui.theme.CuesShape
import com.cues.app.ui.theme.CuesType
import com.cues.app.ui.theme.cuesTokens
import java.util.concurrent.TimeUnit

/** A mono readout with tabular digits and per-digit rolling transitions — timers, counts. */
@Composable
fun MonoReadout(text: String, modifier: Modifier = Modifier, color: Color = cuesTokens.inkPrimary, big: Boolean = false) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (slideInVertically { h -> h } + androidx.compose.animation.fadeIn())
                .togetherWith(slideOutVertically { h -> -h } + androidx.compose.animation.fadeOut())
        },
        label = "mono-readout",
        modifier = modifier,
    ) { value ->
        Text(
            text = value,
            style = if (big) CuesType.mono.copy(fontSize = 32.sp, lineHeight = 36.sp) else CuesType.mono,
            color = color,
        )
    }
}

/** Formats millis remaining as `MM:SS` (or `H:MM:SS` past an hour), never negative. */
fun formatCountdown(remainingMillis: Long): String {
    val clamped = remainingMillis.coerceAtLeast(0)
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(clamped)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/** A circular progress ring around a countdown, filled proportionally to elapsed/(elapsed+remaining). */
@Composable
fun CountdownRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 72.dp,
    strokeWidth: androidx.compose.ui.unit.Dp = 5.dp,
    content: @Composable () -> Unit = {},
) {
    val t = cuesTokens
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            drawArc(
                color = t.hairline,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke,
            )
            drawArc(
                color = t.doYellow,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                style = stroke,
            )
        }
        content()
    }
}

/** A pill chip surfacing a provable claim ("NO NETWORK AT RUNTIME"). Tap opens an evidence sheet. */
@Composable
fun TrustChip(text: String, onClick: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val t = cuesTokens
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(t.island, CuesShape.capsule)
            .border(1.dp, t.hairline, CuesShape.capsule)
            .then(if (onClick != null) Modifier.clickableNoIndicationPublic { onClick() } else Modifier)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Box(Modifier.size(6.dp).background(t.go, CircleShape))
        Text(text, style = CuesType.labelSmall, color = t.inkSecondary, modifier = Modifier.padding(start = 6.dp))
    }
}

fun Modifier.clickableNoIndicationPublic(onClick: () -> Unit): Modifier = this.then(
    Modifier.clickable(onClick = onClick),
)

/** A filter chip row with counts, e.g. "Armed (3)  Paused (1)  Drafts (2)  All (6)". */
@Composable
fun FilterChipRow(
    options: List<Pair<String, Int>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = cuesTokens
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        options.forEachIndexed { index, (label, count) ->
            val isSelected = index == selected
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(if (isSelected) t.doYellow else t.island, CuesShape.capsule)
                    .border(1.dp, if (isSelected) Color.Transparent else t.hairline, CuesShape.capsule)
                    .clickableNoIndicationPublic { onSelect(index) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text(
                    text = if (count >= 0) "$label ($count)" else label,
                    style = CuesType.labelSmall,
                    color = if (isSelected) Color(0xFF0B0C10) else t.inkSecondary,
                )
            }
        }
    }
}

/** A section header with a title and small trailing metadata, e.g. "YOUR CUES" / "3 active". */
@Composable
fun SectionHeader(title: String, meta: String? = null, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val t = cuesTokens
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.weight(1f, fill = false)) {
            Text(title, style = CuesType.headline.copy(fontSize = 15.sp), color = t.inkPrimary, maxLines = 1)
            if (meta != null) {
                Text(
                    "  ·  $meta",
                    style = CuesType.labelSmall,
                    color = t.inkSlate,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 1.dp).weight(1f, fill = false),
                )
            }
        }
        action?.invoke()
    }
}

/** A large centered placeholder for an empty list, with an optional action row below. */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val t = cuesTokens
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(vertical = 32.dp, horizontal = 24.dp),
    ) {
        Text(title, style = CuesType.title, color = t.inkPrimary, textAlign = TextAlign.Center)
        Text(
            body,
            style = CuesType.body,
            color = t.inkSlate,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (action != null) {
            Box(Modifier.padding(top = 16.dp)) { action() }
        }
    }
}
