package com.cues.core.model

import kotlinx.serialization.Serializable

/** A place is only ever one the user explicitly saved. */
@Serializable
data class Place(
    val id: String,
    val label: String,
    val version: Int,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
) {
    init { require(radiusMeters in 100..1000) { "Place radius must be between 100 and 1000 metres." } }
}
