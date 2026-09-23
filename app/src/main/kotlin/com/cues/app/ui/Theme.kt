package com.cues.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Compose tokens for the Cues design system published alongside
 * this app (see the "iQOO Design System" artifact: color, type, spacing,
 * radius, shadow — this file carries the same values into Kotlin).
 *
 * Semantic state colors (go/stop/amber) are kept separate from Material's
 * primary/secondary slots on purpose, matching the system's own rule: state
 * is never the brand accent. [CuesColors] exposes them directly rather than
 * forcing them through `MaterialTheme.colorScheme`, which has no slot for a
 * three-way semantic palette.
 */
object CuesColors {
    // Dark theme (the resting state — see the design system README).
    val bg100Dark = Color(0xFF0E1118)
    val bg200Dark = Color(0xFF161B25)
    val bg300Dark = Color(0xFF202735)
    val borderDark = Color(0x1FFFFFFF)
    val ink100Dark = Color(0xFFF5F7FF)
    val ink200Dark = Color(0xFFB7C0D3)

    // Light theme.
    val bg100Light = Color(0xFFF5F7FB)
    val bg200Light = Color(0xFFFFFFFF)
    val bg300Light = Color(0xFFEAF0FA)
    val borderLight = Color(0x1A172033)
    val ink100Light = Color(0xFF172033)
    val ink200Light = Color(0xFF5E6B80)

    // The signature electric-blue accent — spent once per screen.
    val accentDark = Color(0xFF8DACFF)
    val accentLight = Color(0xFF2864FF)

    // Semantic state — never the accent.
    val goDark = Color(0xFF50D8B0)
    val goLight = Color(0xFF008D6A)
    val stopDark = Color(0xFFFF8E85)
    val stopLight = Color(0xFFC43E36)
    val amberDark = Color(0xFFFFCE6A)
    val amberLight = Color(0xFF9A6500)
}

data class CuesSemanticColors(
    val bg100: Color,
    val bg200: Color,
    val bg300: Color,
    val border: Color,
    val ink100: Color,
    val ink200: Color,
    val go: Color,
    val stop: Color,
    val amber: Color,
) {
    val goBg: Color get() = go.copy(alpha = 0.14f)
    val stopBg: Color get() = stop.copy(alpha = 0.14f)
    val amberBg: Color get() = amber.copy(alpha = 0.14f)
}

private val DarkSemantic = CuesSemanticColors(
    bg100 = CuesColors.bg100Dark, bg200 = CuesColors.bg200Dark, bg300 = CuesColors.bg300Dark,
    border = CuesColors.borderDark, ink100 = CuesColors.ink100Dark, ink200 = CuesColors.ink200Dark,
    go = CuesColors.goDark, stop = CuesColors.stopDark, amber = CuesColors.amberDark,
)

private val LightSemantic = CuesSemanticColors(
    bg100 = CuesColors.bg100Light, bg200 = CuesColors.bg200Light, bg300 = CuesColors.bg300Light,
    border = CuesColors.borderLight, ink100 = CuesColors.ink100Light, ink200 = CuesColors.ink200Light,
    go = CuesColors.goLight, stop = CuesColors.stopLight, amber = CuesColors.amberLight,
)

val LocalCuesColors = androidx.compose.runtime.staticCompositionLocalOf { DarkSemantic }

private val CuesShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
)

/** A compact hierarchy with the crisp, calm cadence of a system interface. */
private val CuesTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-1.1).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.6).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.4).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 23.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.7.sp,
    ),
)

@Composable
fun CuesTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val semantic = if (darkTheme) DarkSemantic else LightSemantic

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = CuesColors.accentDark,
            onPrimary = Color.White,
            secondary = semantic.go,
            onSecondary = semantic.bg100,
            background = semantic.bg100,
            surface = semantic.bg200,
            surfaceVariant = semantic.bg300,
            onBackground = semantic.ink100,
            onSurface = semantic.ink100,
            outline = semantic.border,
        )
    } else {
        lightColorScheme(
            primary = CuesColors.accentLight,
            onPrimary = Color.White,
            secondary = semantic.go,
            onSecondary = Color.White,
            background = semantic.bg100,
            surface = semantic.bg200,
            surfaceVariant = semantic.bg300,
            onBackground = semantic.ink100,
            onSurface = semantic.ink100,
            outline = semantic.border,
        )
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalCuesColors provides semantic) {
        MaterialTheme(colorScheme = colorScheme, typography = CuesTypography, shapes = CuesShapes, content = content)
    }
}

/** The one accent-filled control per screen, the semantic state palette. Never mixed. */
val cuesColors: CuesSemanticColors
    @Composable get() = LocalCuesColors.current
