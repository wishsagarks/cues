package com.cues.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/**
 * Cues' own theme: Kinetic Obsidian (dark, default) / Kinetic Daylight
 * (light, follows the system — redesign plan decision, confirmed with the
 * user). This is the *new* design system (the `ui/theme` package); the legacy
 * `com.cues.app.ui.CuesTheme` in Theme.kt is kept only until every screen
 * has moved over (redesign plan P1/P8), then deleted.
 */
@Composable
fun CuesTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val tokens = if (darkTheme) DarkCuesColors else LightCuesColors

    // Map onto the M3 ColorScheme too, so stock components (Switch, Checkbox,
    // Scaffold insets) inherit the same palette without a second source of truth.
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = tokens.doYellow,
            onPrimary = Color(0xFF0B0C10),
            secondary = tokens.go,
            onSecondary = Color(0xFF0B0C10),
            tertiary = tokens.whenTeal,
            onTertiary = Color(0xFF0B0C10),
            error = tokens.stop,
            onError = Color(0xFF0B0C10),
            background = tokens.voidSurface,
            onBackground = tokens.inkPrimary,
            surface = tokens.slab,
            onSurface = tokens.inkPrimary,
            surfaceVariant = tokens.raised,
            onSurfaceVariant = tokens.inkSecondary,
            outline = tokens.hairline,
            outlineVariant = tokens.hairline,
        )
    } else {
        lightColorScheme(
            primary = tokens.doYellow,
            onPrimary = Color.White,
            secondary = tokens.go,
            onSecondary = Color.White,
            tertiary = tokens.whenTeal,
            onTertiary = Color.White,
            error = tokens.stop,
            onError = Color.White,
            background = tokens.voidSurface,
            onBackground = tokens.inkPrimary,
            surface = tokens.slab,
            onSurface = tokens.inkPrimary,
            surfaceVariant = tokens.raised,
            onSurfaceVariant = tokens.inkSecondary,
            outline = tokens.hairline,
            outlineVariant = tokens.hairline,
        )
    }

    CompositionLocalProvider(LocalCuesColors provides tokens) {
        MaterialTheme(colorScheme = colorScheme, typography = CuesTypography, shapes = CuesShapes, content = content)
    }
}

/** The resolved semantic token bundle for the current theme. */
val cuesTokens: CuesColorTokens
    @Composable get() = LocalCuesColors.current
