package com.cues.app.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Motion tokens (redesign plan §4). Every duration here is short and every
 * transition is interruptible by construction (Compose's animation APIs
 * cancel cleanly on recomposition) — nothing here loops decoratively.
 */
object CuesMotion {
    const val TAB_SWITCH_MS = 200
    const val PUSH_MS = 300
    const val CARD_MS = 200
    const val STAGGER_STEP_MS = 35
    const val STAGGER_MAX_ITEMS = 8
    const val HALT_MS = 220

    val enter = LinearOutSlowInEasing
    val exit = FastOutSlowInEasing

    fun <T> spring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
}

/**
 * True when the system animator-duration scale is 0 (Settings > Accessibility
 * > Remove animations), or when the user has set the in-app override.
 * Components branch on this to replace motion with instant crossfades,
 * never to remove the state change itself.
 */
@Composable
fun rememberReducedMotion(inAppOverride: Boolean = false): Boolean {
    if (inAppOverride) return true
    val context = LocalContext.current
    val systemReduced = androidx.compose.runtime.remember(context) { systemAnimatorScale(context) == 0f }
    return systemReduced
}

private fun systemAnimatorScale(context: Context): Float = try {
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
} catch (e: Settings.SettingNotFoundException) {
    1f
}

val LocalReducedMotion = staticCompositionLocalOf { false }
