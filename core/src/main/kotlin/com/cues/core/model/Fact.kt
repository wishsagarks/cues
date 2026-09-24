package com.cues.core.model

import kotlinx.serialization.Serializable

/** A piece of personal context the user explicitly declared or reviewed. */
@Serializable
data class Fact(
    val id: String,
    val version: Int,
    val kind: FactKind,
    val label: String,
    val value: String,
    val source: FactSource,
    val createdAt: Long,
)

@Serializable
enum class FactKind { DATE, DAYS, PLACE_ALIAS, DEVICE_ALIAS, TEXT }

@Serializable
enum class FactSource { SAID, CAMERA, SHARED, OFFICE_KIT }

/** Versioned dependency captured in an approved cue's semantics. */
@Serializable
data class FactReference(val id: String, val version: Int, val label: String)
