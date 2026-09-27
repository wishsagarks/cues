package com.cues.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.cues.app.ui.theme.CuesShape
import com.cues.app.ui.theme.CuesSpacing
import com.cues.app.ui.theme.cuesTokens

/** How far off the void-surface baseline a slab sits. Higher tiers are used for foreground/interactive content. */
enum class SlabTier { ONE, TWO, THREE }

/**
 * The "glass slab" surface used throughout Now/Review/Receipts/Workbench.
 *
 * Compose has no real backdrop blur below API 31 (`Modifier.blur` blurs the
 * element's own content, not what's behind it), so instead of chasing the
 * Stitch mockups' `backdrop-filter`, this fakes depth with layered opaque
 * surfaces, a 1px hairline border and a subtle top-edge highlight gradient —
 * cheap, identical on OLED, and it reads the same in both themes. See the
 * redesign plan §1 / §4.
 */
@Composable
fun SlabCard(
    modifier: Modifier = Modifier,
    tier: SlabTier = SlabTier.ONE,
    shape: Shape = CuesShape.card,
    edgeColor: Color? = null,
    accentBrush: Brush? = null,
    contentPadding: PaddingValues = PaddingValues(CuesSpacing.md),
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = cuesTokens
    val surface = when (tier) {
        SlabTier.ONE -> t.slab
        SlabTier.TWO -> t.island
        SlabTier.THREE -> t.raised
    }
    val edge = edgeColor ?: t.hairline
    Column(
        modifier = modifier
            .clip(shape)
            .background(surface)
            .background(
                Brush.linearGradient(
                    colors = if (t.isDark) {
                        listOf(Color.White.copy(alpha = 0.08f), Color.Transparent)
                    } else {
                        listOf(Color.Black.copy(alpha = 0.05f), Color.Transparent)
                    },
                    start = Offset(0f, 0f),
                    end = Offset(0f, 120f),
                ),
            )
            .then(if (accentBrush != null) Modifier.background(accentBrush) else Modifier)
            .border(1.dp, edge, shape)
            .padding(contentPadding),
        content = content,
    )
}

/**
 * A faint engineering-grid backdrop for full-bleed screens (Now, Review),
 * matching the Stitch "micro-grid" texture at low enough opacity to never
 * compete with content. Pure `drawBehind`, no bitmap.
 */
@Composable
fun Modifier.cuesGridBackground(): Modifier {
    val t = cuesTokens
    val density = LocalDensity.current
    val lineColor = t.inkPrimary.copy(alpha = if (t.isDark) 0.03f else 0.035f)
    val step = with(density) { 16.dp.toPx() }
    return this
        .background(t.voidSurface)
        .drawBehind {
            var x = 0f
            while (x < size.width) {
                drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += step
            }
        }
}
