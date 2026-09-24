package com.cues.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Haptic vocabulary, matching the cues-android skill exactly: "light tap
 * haptics for ordinary actions; reserve LongPress feedback for destructive
 * actions." The [HapticFeedbackType] pinned by this project's Compose BOM
 * (2024.12.01, CL-04, unverified) exposes only [HapticFeedbackType.TextHandleMove]
 * and [HapticFeedbackType.LongPress] — no Confirm/Reject/SegmentTick, which
 * a newer BOM adds. Rather than bump that pin as a side effect of a UI
 * redesign, every verb here maps onto one of the two available types.
 */
class CuesHaptics(private val feedback: HapticFeedback) {
    /** Chip taps, tab switches, toggles, approve — the ordinary case. */
    fun tap() = feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)

    /** An action that commits or stops something: approve & arm, halt, delete. */
    fun longPress() = feedback.performHapticFeedback(HapticFeedbackType.LongPress)

    // Aliases so call sites can name intent (confirm/reject/tick) even
    // though today's API surface collapses them onto the same two types.
    fun confirm() = longPress()
    fun reject() = tap()
    fun tick() = tap()
}

@Composable
fun rememberCuesHaptics(): CuesHaptics {
    val feedback = LocalHapticFeedback.current
    return androidx.compose.runtime.remember(feedback) { CuesHaptics(feedback) }
}
