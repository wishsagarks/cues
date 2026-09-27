package com.cues.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.cues.app.ui.nav.CuesRoutes
import com.cues.app.ui.theme.CuesPalette
import com.cues.app.ui.theme.CuesShape
import com.cues.app.ui.theme.CuesType
import com.cues.app.ui.theme.cuesTokens

private data class TabSpec(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabSpec(CuesRoutes.NOW, "Now", Icons.Filled.Bolt),
    TabSpec(CuesRoutes.INSIGHTS, "Insights", Icons.Filled.Insights),
    TabSpec(CuesRoutes.ASK, "Ask", Icons.Filled.Chat),
    TabSpec(CuesRoutes.WORKBENCH, "Workbench", Icons.Filled.Tune),
)

/** The 5-tab bottom bar (redesign plan §2), icon + label per nav-label-icon (never icon-only). */
@Composable
fun CuesBottomNav(currentRoute: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val t = cuesTokens
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(t.island)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        tabs.forEach { tab ->
            val selected = tab.route == currentRoute
            TabItem(tab.label, tab.icon, selected, Modifier.weight(1f)) { onSelect(tab.route) }
        }
    }
}

@Composable
private fun RowScope.TabItem(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val t = cuesTokens
    val haptics = com.cues.app.ui.theme.rememberCuesHaptics()
    val color = if (selected) t.doYellow else t.inkSlate
    androidx.compose.foundation.layout.Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .padding(vertical = 4.dp)
            .clickableNoIndicationPublic {
                if (!selected) haptics.tap()
                onClick()
            },
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.height(22.dp))
        Text(label, style = CuesType.labelSmall, color = color, modifier = Modifier.padding(top = 2.dp))
    }
}

/**
 * The top bar shown on every tab: logo mark, screen name, the ON-DEVICE trust
 * chip and a Checks shortcut (redesign plan §2).
 */
@Composable
fun CuesTopBar(
    title: String,
    onOpenChecks: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val t = cuesTokens
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .background(t.voidSurface)
            // Edge-to-edge (MainActivity.enableEdgeToEdge) draws behind the
            // status bar, so a plain Row — unlike Material3's own TopAppBar,
            // which does this internally — needs its own inset padding or
            // the logo/title sit directly under the system clock.
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .height(28.dp)
                    .padding(end = 8.dp)
                    .background(t.doYellow, CircleShape)
                    .padding(6.dp),
            ) {
                Icon(Icons.Filled.Bolt, contentDescription = null, tint = androidx.compose.ui.graphics.Color(0xFF0B0C10), modifier = Modifier.height(16.dp))
            }
            androidx.compose.foundation.layout.Column {
                Text("CUES", style = CuesType.label, color = t.inkPrimary)
                Text(title.uppercase(), style = CuesType.labelSmall, color = t.inkSlate)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            trailing?.invoke()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .background(t.island, CuesShape.pill)
                    .clickableNoIndicationPublic(onOpenChecks)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Icon(Icons.Filled.Tune, contentDescription = "Checks & settings", tint = t.inkSecondary, modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * A rotating yellow/orange sweep-gradient ring, iQOO-flavored answer to a
 * "Gemini-like" glowing prompt frame. Wraps a prompt input so the field
 * itself explains that Cues is listening for a description, not just idle.
 */
@Composable
fun GeminiGlowFrame(
    modifier: Modifier = Modifier,
    rotating: Boolean = true,
    content: @Composable () -> Unit,
) {
    val infinite = rememberInfiniteTransition(label = "geminiGlow")
    val angle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)),
        label = "geminiGlowAngle",
    )
    val pulse by infinite.animateFloat(
        initialValue = if (rotating) 0.5f else 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "geminiGlowPulse",
    )
    Box(
        modifier = modifier
            .drawBehind {
                val strokeWidth = 3.dp.toPx()
                val inset = strokeWidth / 2
                val topLeft = Offset(inset, inset)
                val ringSize = androidx.compose.ui.geometry.Size(size.width - strokeWidth, size.height - strokeWidth)
                val cornerPx = 16.dp.toPx()
                if (rotating) {
                    // Orbit the sweep's focal point instead of rotating the
                    // stroked rect itself — rotating the geometry swings the
                    // rounded-rect's corners outside the draw bounds each
                    // frame and gets clipped, which reads as a warped frame
                    // rather than a smooth moving glow.
                    val orbitRadius = kotlin.math.min(size.width, size.height) * 0.18f
                    val radians = Math.toRadians(angle.toDouble())
                    val focus = Offset(
                        x = size.width / 2f + orbitRadius * kotlin.math.cos(radians).toFloat(),
                        y = size.height / 2f + orbitRadius * kotlin.math.sin(radians).toFloat(),
                    )
                    val brush = Brush.sweepGradient(
                        0f to CuesPalette.doYellowDark.copy(alpha = pulse),
                        0.3f to CuesPalette.untilOrangeDark.copy(alpha = pulse * 0.8f),
                        0.55f to CuesPalette.doYellowDark.copy(alpha = pulse * 0.4f),
                        0.8f to CuesPalette.untilOrangeDark.copy(alpha = pulse * 0.8f),
                        1f to CuesPalette.doYellowDark.copy(alpha = pulse),
                        center = focus,
                    )
                    drawRoundRect(
                        brush = brush,
                        topLeft = topLeft,
                        size = ringSize,
                        cornerRadius = CornerRadius(cornerPx, cornerPx),
                        style = Stroke(width = strokeWidth),
                    )
                } else {
                    // A steady, always-fully-lit ring (no rotation, no fading
                    // segments) — the "constant glow" look, as opposed to the
                    // sweeping variant used on Now.
                    val brush = Brush.linearGradient(
                        colors = listOf(
                            CuesPalette.doYellowDark.copy(alpha = pulse),
                            CuesPalette.untilOrangeDark.copy(alpha = pulse),
                            CuesPalette.doYellowDark.copy(alpha = pulse),
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height),
                    )
                    drawRoundRect(
                        brush = brush,
                        topLeft = topLeft,
                        size = ringSize,
                        cornerRadius = CornerRadius(cornerPx, cornerPx),
                        style = Stroke(width = strokeWidth),
                    )
                }
            }
            .padding(3.dp),
    ) {
        content()
    }
}
