package com.cues.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Corner radii, redesign plan §4: cards 16dp, nested pills 8dp, clause badges 4dp, capsules/sheets rounder. */
object CuesShape {
    val card = RoundedCornerShape(16.dp)
    val pill = RoundedCornerShape(8.dp)
    val badge = RoundedCornerShape(4.dp)
    val capsule = RoundedCornerShape(50)
    val sheetTop = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
}

val CuesShapes = Shapes(
    extraSmall = CuesShape.badge,
    small = CuesShape.pill,
    medium = CuesShape.card,
    large = RoundedCornerShape(20.dp),
    extraLarge = CuesShape.sheetTop,
)
