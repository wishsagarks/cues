package com.cues.core.approval

import com.cues.core.compile.Normalizer
import com.cues.core.compile.ValidationResult
import com.cues.core.compile.Validator
import com.cues.core.model.Capability
import com.cues.core.model.Routine
import com.cues.core.model.RoutineStatus
import com.cues.core.model.Session
import com.cues.core.ports.CapabilityProvider
import com.cues.core.ports.SessionStore

/**
 * Drives [RoutineStatus] through the lifecycle it has been describing since
 * the routine model was written: DRAFT/INVALID → REVIEWABLE → ARMED, with
 * PAUSED and DISABLED as user-initiated exits.
 *
 * Nothing here evaluates a routine against an event — that stays
 * [com.cues.core.session.SessionEngine]'s job. This is the gate in front of
 * it: a routine only reaches ARMED, and therefore only becomes eligible for
 * [com.cues.core.session.SessionEngine.onTriggerEvent], once it has passed
 * validation, been explicitly approved at its current normalized form, and
 * cleared a live permission check.
 */
object Approvals {

    /** What review produced: the normalized routine plus everything a UI needs to show. */
    data class ReviewResult(
        val normalized: Routine,
        val validation: ValidationResult,
        val requiredCapabilities: Set<Capability>,
    )

    /** Normalizes and validates, without changing status or approval. Safe to call repeatedly while editing. */
    fun review(routine: Routine): ReviewResult {
        val normalized = Normalizer.normalize(routine)
        return ReviewResult(
            normalized = normalized,
            validation = Validator.validate(normalized),
            requiredCapabilities = normalized.requiredCapabilities,
        )
    }

    /**
     * Records approval of the routine's *current* normalized form.
     *
     * Binds [Routine.approvedDigest] to exactly this version. Any later edit
     * changes the digest and [Normalizer.matchesApproval] stops holding, which
     * is what forces re-approval rather than letting an edited rule keep
     * running on stale consent.
     *
     * Refuses an invalid routine: approving something that cannot be armed
     * would only move the failure to a later, more confusing point.
     */
    fun approve(routine: Routine): ArmResult<Routine> {
        val reviewed = review(routine)
        if (!reviewed.validation.isValid) {
            return ArmResult.Invalid(reviewed.validation)
        }

        val approved = reviewed.normalized.copy(
            approvedDigest = Normalizer.digest(reviewed.normalized),
            status = RoutineStatus.REVIEWABLE,
        )
        return ArmResult.Ok(approved)
    }

    /**
     * Moves an approved routine to ARMED, the only status
     * [com.cues.core.session.SessionEngine] will act on.
     *
     * Three independent gates, checked in the order a user would want to hear
     * about them: validity, that the exact approved version is what's being
     * armed, then live permissions. A capability that was granted at review
     * time and revoked since is caught here, not assumed from the review.
     */
    fun arm(routine: Routine, capabilities: CapabilityProvider): ArmResult<Routine> {
        val validation = Validator.validate(routine)
        if (!validation.isValid) return ArmResult.Invalid(validation)

        if (!Normalizer.matchesApproval(routine)) {
            return ArmResult.NotApproved
        }

        val granted = capabilities.granted()
        val missing = routine.requiredCapabilities - granted
        if (missing.isNotEmpty()) {
            return ArmResult.MissingCapabilities(missing)
        }

        return ArmResult.Ok(routine.copy(status = RoutineStatus.ARMED))
    }

    /** Prevents new sessions. Never touches a session already running. */
    fun pause(routine: Routine): Routine = routine.copy(status = RoutineStatus.PAUSED)

    /**
     * Re-arms a paused routine without re-running preflight.
     *
     * A missing capability can only have gotten worse while paused, never
     * better on its own — if it matters, the next real arm attempt (or the
     * app's own periodic capability check) will catch it. Resume exists for
     * the ordinary "I paused this, now I want it back" path.
     */
    fun resume(routine: Routine): ArmResult<Routine> {
        if (routine.status != RoutineStatus.PAUSED) return ArmResult.NotPaused
        return ArmResult.Ok(routine.copy(status = RoutineStatus.ARMED))
    }

    fun disable(routine: Routine): Routine = routine.copy(status = RoutineStatus.DISABLED)

    /**
     * Refuses to delete a routine with an outstanding cleanup obligation.
     *
     * Deleting the record of something the phone is still doing — an owned
     * timer, an owned DND contribution — is how a user ends up stuck with a
     * side effect nothing remembers how to release. The routine has to be
     * disabled and its sessions resolved first.
     */
    fun delete(routine: Routine, sessions: SessionStore): DeleteResult {
        val outstanding = sessions.activeFor(routine.id)
            .filter { session -> session.obligations.any { !it.released } }

        if (outstanding.isNotEmpty()) {
            return DeleteResult.Blocked(outstanding)
        }
        return DeleteResult.Ok
    }
}

sealed interface ArmResult<out T> {
    data class Ok<T>(val routine: T) : ArmResult<T>
    data class Invalid(val validation: ValidationResult) : ArmResult<Nothing>
    data class MissingCapabilities(val missing: Set<Capability>) : ArmResult<Nothing>
    /** The routine was edited since it was approved, or was never approved at all. */
    data object NotApproved : ArmResult<Nothing>
    data object NotPaused : ArmResult<Nothing>
}

sealed interface DeleteResult {
    data object Ok : DeleteResult
    data class Blocked(val sessionsWithObligations: List<Session>) : DeleteResult
}
