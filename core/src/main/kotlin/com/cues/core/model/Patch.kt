package com.cues.core.model

import kotlinx.serialization.Serializable

/** A visible, temporary override. It deliberately does not alter approval semantics. */
@Serializable
data class Patch(
    val routineId: String,
    val baseVersion: Int,
    val kind: PatchKind,
    val createdAt: Long,
)

@Serializable
sealed interface PatchKind {
    @Serializable data class SkipUntil(val epochMillis: Long) : PatchKind
    /** ISO local date, kept timezone-neutral until it is evaluated. */
    @Serializable data class SkipOccurrence(val date: String) : PatchKind
}
