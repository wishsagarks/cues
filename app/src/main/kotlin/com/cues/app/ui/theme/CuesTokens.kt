package com.cues.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Color tokens for "Kinetic Obsidian" (dark) / "Kinetic Daylight" (light).
 *
 * Adapted from the Stitch prototype at docs/design/stitch/DESIGN_SYSTEM.md,
 * with two deliberate departures recorded there and in the redesign plan:
 *
 * 1. Status color (go/warn/stop/unknown) is kept separate from clause color
 *    (when/if/do/until/restore). Stitch's own text says "status remains
 *    semantic, never conflated with the brand accent" — this file is what
 *    keeps that true in code, not just in prose.
 * 2. Light-theme text/fill colors are darkened versions of Stitch's amber
 *    (`#D97706` -> `#8D4B00`/`#B45309`) because the prototype value fails
 *    WCAG AA (~3.2:1) for body text on white. `#D97706` itself survives only
 *    as a large/decorative fill, never as text.
 *
 * Every screen reads these through [LocalCuesColors] / [cuesColors], never a
 * raw hex — see CuesTheme.kt.
 */
object CuesPalette {
    // ---- Surfaces -----------------------------------------------------
    // Dark ("Kinetic Obsidian"): a tiered obsidian stack, deepest to highest.
    val voidDark = Color(0xFF0B0C10)
    val slabDark = Color(0xFF12141C)
    val islandDark = Color(0xFF181B26)
    val raisedDark = Color(0xFF1D1F28)
    val recessDark = Color(0xFF0C0E16)

    // Light ("Kinetic Daylight").
    val canvasLight = Color(0xFFF8F9FD)
    val tileLight = Color(0xFFFFFFFF)
    val wellLight = Color(0xFFEEF1F8)
    val grooveLight = Color(0xFFE2E6F0)

    // ---- Ink ------------------------------------------------------------
    val inkPrimaryDark = Color(0xFFE1E1ED)
    val inkSecondaryDark = Color(0xFFA9B1C3)
    val inkSlateDark = Color(0xFF7C8BA1)

    val inkPrimaryLight = Color(0xFF0F172A)
    val inkSecondaryLight = Color(0xFF334155)
    val inkSlateLight = Color(0xFF64748B)

    // ---- Clause hues (WHEN / IF / DO / UNTIL / RESTORE) ------------------
    val whenTealDark = Color(0xFF00E5FF)
    val whenTealLight = Color(0xFF0891B2)

    val ifSlateDark = Color(0xFF7C8BA1)
    val ifSlateLight = Color(0xFF64748B)

    val doYellowDark = Color(0xFFFFE600)
    val doYellowLight = Color(0xFFB45309) // text/fill; decorative-only #D97706 lives in Decorative below

    val untilOrangeDark = Color(0xFFFF5E00)
    val untilOrangeLight = Color(0xFFEA580C)

    val restoreGhostDark = Color(0xFFFFFFFF) // used at 40% alpha
    val restoreGhostLight = Color(0xFF334155)

    // ---- Status (semantic, never the clause/brand accent) ---------------
    val goDark = Color(0xFF3DDC97)
    val goLight = Color(0xFF008D6A)

    val warnDark = Color(0xFFFFB020)
    val warnLight = Color(0xFF9A6500)

    val stopDark = Color(0xFFFF6B6B)
    val stopLight = Color(0xFFC43E36)

    val unknownDark = Color(0xFF7C8BA1)
    val unknownLight = Color(0xFF64748B)

    // ---- Edges / hairlines ------------------------------------------------
    val hairlineDark = Color(0x12FFFFFF) // white @ 7%
    val hairlineLight = Color(0x14000000)
    val activeEdgeDark = Color(0x40FFE600) // yellow @ 25%
    val activeEdgeLight = Color(0x408D4B00)
    val dangerEdgeDark = Color(0x59FF5E00) // orange @ 35%
    val dangerEdgeLight = Color(0x59EA580C)

    // ---- Decorative-only (large fills, never body text) -------------------
    val decorativeAmber = Color(0xFFD97706)
}

/** A theme-resolved bundle of semantic tokens. Screens read this, never [CuesPalette] directly. */
data class CuesColorTokens(
    val isDark: Boolean,
    // Surfaces, deepest to highest.
    val voidSurface: Color,
    val slab: Color,
    val island: Color,
    val raised: Color,
    val recess: Color,
    // Ink.
    val inkPrimary: Color,
    val inkSecondary: Color,
    val inkSlate: Color,
    // Clause hues.
    val whenTeal: Color,
    val ifSlate: Color,
    val doYellow: Color,
    val untilOrange: Color,
    val restoreGhost: Color,
    // Status.
    val go: Color,
    val warn: Color,
    val stop: Color,
    val unknown: Color,
    // Edges.
    val hairline: Color,
    val activeEdge: Color,
    val dangerEdge: Color,
) {
    val goBg: Color get() = go.copy(alpha = 0.14f)
    val warnBg: Color get() = warn.copy(alpha = 0.14f)
    val stopBg: Color get() = stop.copy(alpha = 0.14f)
    val unknownBg: Color get() = unknown.copy(alpha = 0.14f)

    val whenBg: Color get() = whenTeal.copy(alpha = 0.10f)
    val ifBg: Color get() = ifSlate.copy(alpha = 0.10f)
    val doBg: Color get() = doYellow.copy(alpha = if (isDark) 1f else 0.12f)
    val untilBg: Color get() = untilOrange.copy(alpha = 0.10f)
    val restoreBg: Color get() = restoreGhost.copy(alpha = 0.10f)
}

val DarkCuesColors = CuesColorTokens(
    isDark = true,
    voidSurface = CuesPalette.voidDark,
    slab = CuesPalette.slabDark,
    island = CuesPalette.islandDark,
    raised = CuesPalette.raisedDark,
    recess = CuesPalette.recessDark,
    inkPrimary = CuesPalette.inkPrimaryDark,
    inkSecondary = CuesPalette.inkSecondaryDark,
    inkSlate = CuesPalette.inkSlateDark,
    whenTeal = CuesPalette.whenTealDark,
    ifSlate = CuesPalette.ifSlateDark,
    doYellow = CuesPalette.doYellowDark,
    untilOrange = CuesPalette.untilOrangeDark,
    restoreGhost = CuesPalette.restoreGhostDark.copy(alpha = 0.4f),
    go = CuesPalette.goDark,
    warn = CuesPalette.warnDark,
    stop = CuesPalette.stopDark,
    unknown = CuesPalette.unknownDark,
    hairline = CuesPalette.hairlineDark,
    activeEdge = CuesPalette.activeEdgeDark,
    dangerEdge = CuesPalette.dangerEdgeDark,
)

val LightCuesColors = CuesColorTokens(
    isDark = false,
    voidSurface = CuesPalette.canvasLight,
    slab = CuesPalette.tileLight,
    island = CuesPalette.tileLight,
    raised = CuesPalette.wellLight,
    recess = CuesPalette.wellLight,
    inkPrimary = CuesPalette.inkPrimaryLight,
    inkSecondary = CuesPalette.inkSecondaryLight,
    inkSlate = CuesPalette.inkSlateLight,
    whenTeal = CuesPalette.whenTealLight,
    ifSlate = CuesPalette.ifSlateLight,
    doYellow = CuesPalette.doYellowLight,
    untilOrange = CuesPalette.untilOrangeLight,
    restoreGhost = CuesPalette.restoreGhostLight,
    go = CuesPalette.goLight,
    warn = CuesPalette.warnLight,
    stop = CuesPalette.stopLight,
    unknown = CuesPalette.unknownLight,
    hairline = CuesPalette.hairlineLight,
    activeEdge = CuesPalette.activeEdgeLight,
    dangerEdge = CuesPalette.dangerEdgeLight,
)

val LocalCuesColors = staticCompositionLocalOf { DarkCuesColors }

/** 4dp-base spacing scale, per the redesign plan §4. */
object CuesSpacing {
    val xs = androidx.compose.ui.unit.Dp(4f)
    val sm = androidx.compose.ui.unit.Dp(8f)
    val md = androidx.compose.ui.unit.Dp(14f)
    val lg = androidx.compose.ui.unit.Dp(20f)
    val xl = androidx.compose.ui.unit.Dp(28f)
    val xxl = androidx.compose.ui.unit.Dp(40f)

    val screenMargin = androidx.compose.ui.unit.Dp(16f)
    val gutter = androidx.compose.ui.unit.Dp(12f)

    /** Width at which Now/Receipts switch to a two-pane layout (tablet/foldable-unfolded). */
    val twoPaneBreakpoint = androidx.compose.ui.unit.Dp(600f)
}
