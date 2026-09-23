package com.cues.core.model

import kotlinx.serialization.Serializable

/** A user-named, visible conjunction of existing context predicates. */
@Serializable
data class NamedContext(
    val id: String,
    val label: String,
    val version: Int,
    val predicates: List<Condition>,
)
