package com.cues.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cues.app.ui.theme.CuesShape
import com.cues.app.ui.theme.CuesType
import com.cues.app.ui.theme.cuesTokens

/**
 * The primary yellow "arm/approve" control. Presses scale to 0.98 with zero
 * spring-slop, matching the Stitch component spec ("instant mechanical press
 * down-scale") and the cues-android skill's tap-haptic guidance.
 */
@Composable
fun KineticButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    disabledReason: String? = null,
) {
    val t = cuesTokens
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, label = "kinetic-press")
    val haptics = com.cues.app.ui.theme.rememberCuesHaptics()

    // A disabled state built from a low-alpha doYellow reads as a murky
    // olive on the dark theme's near-black surfaces — alpha-blending a
    // saturated accent color over a dark background doesn't desaturate the
    // way it does over white. A neutral raised surface + slate text is the
    // standard disabled treatment and stays legible (and clearly "not the
    // accent") in both themes.
    val fill = if (enabled) t.doYellow else t.raised
    val content = if (enabled) Color(0xFF0B0C10) else t.inkSlate

    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .scale(scale)
            .clip(CuesShape.capsule)
            .background(fill)
            .then(if (!enabled) Modifier.border(1.dp, t.hairline, CuesShape.capsule) else Modifier)
            .then(
                if (enabled) {
                    Modifier.clickableNoIndication(interaction) { haptics.confirm(); onClick() }
                } else {
                    Modifier.clickableNoIndication(interaction) { haptics.reject() }
                },
            )
            .padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.height(18.dp).widthIn(max = 18.dp),
                color = content,
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                text = text,
                style = CuesType.title,
                color = content,
            )
        }
    }
    if (!enabled && disabledReason != null) {
        Text(
            text = disabledReason,
            style = CuesType.labelSmall,
            color = t.stop,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** The orange-edged halt/disarm control — ends a running session, stops a macro, etc. Long-press haptic. */
@Composable
fun HaltButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val t = cuesTokens
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, label = "halt-press")
    val haptics = com.cues.app.ui.theme.rememberCuesHaptics()

    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .scale(scale)
            .clip(CuesShape.capsule)
            .background(t.stop.copy(alpha = if (t.isDark) 0.12f else 0.10f))
            .border(1.dp, t.stop.copy(alpha = 0.5f), CuesShape.capsule)
            .clickableNoIndication(interaction) { haptics.longPress(); onClick() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(text, style = CuesType.bodyMedium, color = t.stop)
    }
}

/** A neutral outlined control for secondary actions (Edit, Discard, Back). */
@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val t = cuesTokens
    val interaction = remember { MutableInteractionSource() }
    val haptics = com.cues.app.ui.theme.rememberCuesHaptics()
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(CuesShape.capsule)
            .border(1.dp, t.hairline.copy(alpha = 0.6f), CuesShape.capsule)
            .clickableNoIndication(interaction, enabled) { haptics.tap(); onClick() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(text, style = CuesType.bodyMedium, color = if (enabled) t.inkPrimary else t.inkSlate)
    }
}

/** An arm/pause toggle, styled as a compact kinetic switch rather than stock M3. */
@Composable
fun KineticSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val t = cuesTokens
    val haptics = com.cues.app.ui.theme.rememberCuesHaptics()
    // Keep the visual switch stable while preserving a comfortable 48dp
    // touch target for every screen that uses this shared control.
    Box(
        modifier = modifier.width(52.dp).height(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Switch(
            checked = checked,
            onCheckedChange = { haptics.tap(); onCheckedChange(it) },
            enabled = enabled,
            modifier = Modifier.width(52.dp).height(32.dp),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF0B0C10),
                checkedTrackColor = t.doYellow,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = t.inkSlate,
                uncheckedTrackColor = t.raised,
                uncheckedBorderColor = t.hairline,
            ),
        )
    }
}

/** Clickable that tracks its own press state (for the 0.98 scale-down), using the platform's default ripple. */
@Composable
private fun Modifier.clickableNoIndication(
    interaction: MutableInteractionSource,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = this.clickable(
    interactionSource = interaction,
    indication = LocalIndication.current,
    enabled = enabled,
    onClick = onClick,
)
