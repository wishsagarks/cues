package com.cues.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.cues.app.R

/**
 * Three bundled variable fonts (OFL-licensed, see docs/design/fonts/), one
 * body typeface in both themes on purpose: swapping the body face when the
 * theme flips would read as two different products (redesign plan §1).
 *
 * All three are variable-weight TTFs. minSdk is 29, so variable-font weight
 * resolution (API 26+) is always available; there is no static-weight
 * fallback file to keep in sync.
 */
@OptIn(ExperimentalTextApi::class)
private fun variableFont(resId: Int, weight: Int): Font = Font(
    resId = resId,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val SpaceGrotesk = FontFamily(
    variableFont(R.font.space_grotesk, 500),
    variableFont(R.font.space_grotesk, 600),
    variableFont(R.font.space_grotesk, 700),
)

val PlusJakartaSans = FontFamily(
    variableFont(R.font.plus_jakarta_sans, 400),
    variableFont(R.font.plus_jakarta_sans, 500),
    variableFont(R.font.plus_jakarta_sans, 600),
)

val JetBrainsMono = FontFamily(
    variableFont(R.font.jetbrains_mono, 500),
    variableFont(R.font.jetbrains_mono, 700),
    variableFont(R.font.jetbrains_mono, 800),
)

/** Tabular figures for timers, counts and money-like readouts — never reflowing digits. Pass to `TextStyle.fontFeatureSettings`. */
const val TabularNumbers = "tnum"

/**
 * Role-based text styles, additional to (and layered onto) [Typography].
 * Compose's [Typography] roles don't cover "clause badge" or "mono label",
 * so those live here and are consumed directly, not through MaterialTheme.
 *
 * Minimum size anywhere in this object is 11sp — Stitch's `label-sm` (9px)
 * fails legibility and is not reproduced (redesign plan §1).
 */
object CuesType {
    val display = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight(700),
        fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.02).em,
    )
    val headline = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight(600),
        fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.01).em,
    )
    val title = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight(600),
        fontSize = 16.sp, lineHeight = 22.sp,
    )
    val bodyLarge = TextStyle(
        fontFamily = PlusJakartaSans, fontWeight = FontWeight(400),
        fontSize = 16.sp, lineHeight = 24.sp,
    )
    val body = TextStyle(
        fontFamily = PlusJakartaSans, fontWeight = FontWeight(400),
        fontSize = 14.sp, lineHeight = 20.sp,
    )
    val bodyMedium = TextStyle(
        fontFamily = PlusJakartaSans, fontWeight = FontWeight(500),
        fontSize = 14.sp, lineHeight = 20.sp,
    )
    val label = TextStyle(
        fontFamily = JetBrainsMono, fontWeight = FontWeight(600),
        fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.02.em,
    )
    val labelSmall = TextStyle(
        fontFamily = JetBrainsMono, fontWeight = FontWeight(500),
        fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.04.em,
    )
    /** WHEN / IF / DO / UNTIL / RESTORE badges: uppercase, +0.08em tracking, always applied by the caller. */
    val clause = TextStyle(
        fontFamily = JetBrainsMono, fontWeight = FontWeight(800),
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.08.em,
    )
    /** Countdowns and other rolling digit readouts. Apply [TabularNumbers] via fontFeatureSettings. */
    val mono = TextStyle(
        fontFamily = JetBrainsMono, fontWeight = FontWeight(700),
        fontSize = 20.sp, lineHeight = 24.sp, fontFeatureSettings = "tnum",
    )
}

/** Maps [CuesType] roles onto Material3's [Typography] slots for stock components (buttons, chips, etc). */
val CuesTypography = Typography(
    displaySmall = CuesType.display,
    headlineMedium = CuesType.headline,
    headlineSmall = CuesType.headline.copy(fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = CuesType.title,
    titleSmall = CuesType.title.copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = CuesType.bodyLarge,
    bodyMedium = CuesType.body,
    bodySmall = CuesType.labelSmall.copy(fontFamily = PlusJakartaSans, letterSpacing = 0.sp),
    labelLarge = CuesType.label,
    labelMedium = CuesType.labelSmall,
    labelSmall = CuesType.labelSmall,
)
