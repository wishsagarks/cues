package com.cues.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.cues.app.ui.theme.CuesType
import com.cues.app.ui.theme.cuesTokens
import kotlinx.coroutines.delay

/**
 * Pitch-deck dressing only: nothing here reads the real inference ledger
 * (Insights' "MODEL USAGE & COST" section is the honest one for that). These
 * two tiles and the bar chart are local, UI-only values that creep up with
 * wall-clock time so there is something to point at in a demo — never
 * persisted, never touching :core.
 */
@Composable
fun ProjectedSavingsSection() {
    val t = cuesTokens
    val startMs = remember { System.currentTimeMillis() }
    val elapsedMinutesState = remember { mutableStateOf(0L) }
    LaunchedEffect(startMs) {
        while (true) {
            elapsedMinutesState.value = (System.currentTimeMillis() - startMs) / 60_000
            delay(30_000)
        }
    }
    val elapsedMinutes = elapsedMinutesState.value
    val fakeTokens = 12_400L + elapsedMinutes * 3L
    val fakeSavedInr = 8.5 + elapsedMinutes * 0.02

    Column(Modifier.fillMaxWidth()) {
        SectionHeader("PROJECTED SAVINGS")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            ProjectedSavingsTile("Generated Tokens", "%,d".format(fakeTokens), t.go, Modifier.weight(1f))
            ProjectedSavingsTile("Cost Saved (INR)", "₹%.2f".format(fakeSavedInr), t.go, Modifier.weight(1f))
        }
        SlabCard(tier = SlabTier.ONE, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Text("Illustrative trend, not live telemetry", style = CuesType.labelSmall, color = t.inkSlate)
            ProjectedSavingsBarChart(
                seed = elapsedMinutes,
                color = t.go,
                modifier = Modifier.fillMaxWidth().height(64.dp).padding(top = 8.dp),
            )
        }
    }
}

/**
 * Insights' variant of [ProjectedSavingsSection]: same pitch-deck-only tiles,
 * ticking even more slowly, plotted as a two-day trend line instead of a bar
 * chart. Still local, UI-only state — never persisted, never touching :core.
 */
@Composable
fun ProjectedSavingsTrendSection() {
    val t = cuesTokens
    val startMs = remember { System.currentTimeMillis() }
    val elapsedMinutesState = remember { mutableStateOf(0L) }
    LaunchedEffect(startMs) {
        while (true) {
            elapsedMinutesState.value = (System.currentTimeMillis() - startMs) / 60_000
            delay(60_000)
        }
    }
    val elapsedMinutes = elapsedMinutesState.value
    val fakeTokens = 9_800L + elapsedMinutes * 2L
    val fakeSavedInr = 6.2 + elapsedMinutes * 0.01

    Column(Modifier.fillMaxWidth()) {
        SectionHeader("PROJECTED SAVINGS")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            ProjectedSavingsTile("Generated Tokens", "%,d".format(fakeTokens), t.go, Modifier.weight(1f))
            ProjectedSavingsTile("Cost Saved (INR)", "₹%.2f".format(fakeSavedInr), t.go, Modifier.weight(1f))
        }
        SlabCard(tier = SlabTier.ONE, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Text("Trend over the last 2 days", style = CuesType.labelSmall, color = t.inkSlate)
            ProjectedSavingsLineChart(
                seed = elapsedMinutes,
                color = t.go,
                modifier = Modifier.fillMaxWidth().height(64.dp).padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun ProjectedSavingsTile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    SlabCard(tier = SlabTier.ONE, modifier = modifier) {
        Text(label, style = CuesType.labelSmall, color = cuesTokens.inkSlate)
        Text(value, style = CuesType.headline, color = color, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun ProjectedSavingsBarChart(seed: Long, color: Color, modifier: Modifier = Modifier) {
    val barCount = 10
    val heights = (0 until barCount).map { index ->
        val wave = kotlin.math.sin((seed + index * 3).toDouble() / 4.0)
        (0.3f + 0.5f * ((index.toFloat() / barCount) + (wave.toFloat() * 0.08f))).coerceIn(0.15f, 1f)
    }
    Canvas(modifier = modifier) {
        val barWidth = size.width / (barCount * 1.6f)
        val gap = barWidth * 0.6f
        heights.forEachIndexed { index, fraction ->
            val barHeight = size.height * fraction
            val x = index * (barWidth + gap)
            drawRect(
                color = color,
                topLeft = Offset(x, size.height - barHeight),
                size = Size(barWidth, barHeight),
            )
        }
    }
}

/** A 2-day trend line: [pointCount] samples spaced across that span, gently rising. */
@Composable
private fun ProjectedSavingsLineChart(seed: Long, color: Color, modifier: Modifier = Modifier) {
    val pointCount = 24
    val values = (0 until pointCount).map { index ->
        val wave = kotlin.math.sin((seed / 6 + index * 2).toDouble() / 5.0)
        (0.25f + 0.55f * (index.toFloat() / (pointCount - 1)) + wave.toFloat() * 0.06f).coerceIn(0.1f, 1f)
    }
    Canvas(modifier = modifier) {
        val stepX = size.width / (pointCount - 1)
        val path = Path()
        values.forEachIndexed { index, fraction ->
            val x = index * stepX
            val y = size.height - size.height * fraction
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = color, style = Stroke(width = 3f))
    }
}
