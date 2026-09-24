package com.cues.core.model

import kotlinx.serialization.Serializable

/**
 * One occurrence of a routine.
 *
 * A session owns resources and therefore owes obligations. It is persisted
 * *before* any side effect runs, so a crash between an effect and its record
 * still leaves evidence that something may need releasing.
 */
@Serializable
data class Session(
    val id: String,
    val routineId: String,
    /** The exact routine version that started this. Editing the routine never rewrites it. */
    val routineVersion: Int,
    val startedAtMillis: Long,
    /** Identity of the admitting event, used to collapse duplicate callbacks. */
    val admissionKey: String,
    val observedInputs: ContextSnapshot,
    val state: SessionState,
    /** Wall-clock deadline, persisted so it survives a reboot. Null when purely event-ended. */
    val deadlineMillis: Long? = null,
    val actions: List<ActionRecord> = emptyList(),
    val obligations: List<CleanupObligation> = emptyList(),
    /** Set when the user changed an owned effect by hand. Cleanup then stands down. */
    val userOverride: Boolean = false,
    val endedAtMillis: Long? = null,
    val endReason: EndReason? = null,
    /** Set when a disconnect is waiting out the reconnect grace period. */
    val pendingExitAtMillis: Long? = null,
)

@Serializable
enum class SessionState {
    STARTING,
    ACTIVE,
    /** A disconnect was seen and the grace window is running. Still ACTIVE in effect. */
    EXIT_PENDING,
    ENDING,
    COMPLETED,
    CANCELLED,
    /** Started, but at least one action did not succeed. Never reported as success. */
    PARTIAL,
    /** Ended, but at least one owned resource could not be released. Stays visible. */
    CLEANUP_PENDING,
    FAILED,
}

@Serializable
enum class EndReason {
    DEADLINE_REACHED,
    TRIGGER_REVERSED,
    MANUAL_STOP,
    ROUTINE_PAUSED,
    RECONCILED_EXPIRED,
    START_FAILED,
    /** The device was no longer connected on resume, but no disconnect was ever observed (R6). */
    COVERAGE_GAP,
}

/** The outcome of one action, recorded individually. A blocked action is not a success. */
@Serializable
data class ActionRecord(
    val actionId: ActionId,
    val state: ActionState,
    val detail: String? = null,
    /**
     * How [state] was confirmed. Defaults to [Verification.NONE], which is
     * also what every record written before this field existed decodes as —
     * the honest reading of a record that never said.
     */
    val verification: Verification = Verification.NONE,
)

/**
 * How Cues knows an action (or a release) did what it reports.
 *
 * "Done" means different things for different mechanisms, and the UI has to
 * be able to tell them apart: a ringer mode Cues read back is a fact, a macro
 * whose on-screen steps all landed is an assumption about a setting Cues
 * cannot read. The two must never look alike.
 */
@Serializable
enum class Verification {
    /** The platform state was re-read after the change — the ringer mode, the DND rule. */
    READ_BACK,

    /**
     * Only the macro's own on-screen postconditions held. The setting itself
     * was never read, so the result is shown as assumed, never as a plain ✓.
     */
    STEPS_CONFIRMED,

    /** No check was possible, or none was recorded. */
    NONE,
}

/**
 * The exact [ActionRecord.detail] a [ActionState.PENDING] action is stamped
 * with when the session ends before anyone became available to run it.
 *
 * A shared constant, not a re-typed string, so [com.cues.core.session.SessionEngine]
 * (which writes it) and [com.cues.core.receipt.Receipts] (which looks for it
 * to report genuinely new information in the Ended receipt) can never drift
 * apart.
 */
const val EXPIRED_WHILE_PENDING_DETAIL = "The session ended before you were available."

@Serializable
enum class ActionState {
    NOT_STARTED,
    IN_PROGRESS,
    SUCCEEDED,
    /** The OS refused, usually for want of a permission. An honest, visible outcome. */
    BLOCKED,
    FAILED,
    COMPENSATED,
    /** We could not undo something we did. The session stays visibly unresolved. */
    COMPENSATION_FAILED,
    /**
     * A [com.cues.core.registry.Presence.NEEDS_USER] action whose moment
     * arrived while nobody was at the phone. Never a terminal state: the
     * engine either succeeds it once the user is present, or converts it to
     * [BLOCKED] when the session ends still waiting — expiry is a block, not
     * a silent drop.
     */
    PENDING,
}

/**
 * A resource this session must release before it is finished.
 *
 * Obligations are recorded at acquisition, not at exit, so a process death in
 * between still leaves a durable trace of what is outstanding.
 */
@Serializable
data class CleanupObligation(
    val resource: OwnedResource,
    val acquiredAtMillis: Long,
    val released: Boolean = false,
    val failureDetail: String? = null,
    /**
     * The approved arguments of the action that acquired this resource,
     * persisted at acquisition so release can work out what to undo from the
     * session record alone.
     *
     * Why the session's pinned `routineVersion` is not enough on its own:
     * `RoutineStore` keeps only a routine's latest version, so an edit made
     * while this session ran — which the FDD allows, and which never rewrites
     * the running session — replaces the arguments it ran with. A deleted
     * routine takes them with it. And two `USE_UTILITY` actions in one cue
     * owe the same [OwnedResource], so the resource alone cannot say which
     * utility a given obligation is for. Keeping the arguments here is the
     * one field that closes all three.
     *
     * Null for an obligation written before this field existed. A release
     * that needs the arguments and finds null must report that it cannot
     * tell what to restore — never succeed by default.
     */
    val args: ActionArgs? = null,
    /** How the release was confirmed, once it has happened. */
    val releaseVerification: Verification = Verification.NONE,
)

/**
 * Something Cues itself created and may therefore undo.
 *
 * The enum is closed on purpose. There is no member for "the global Do Not
 * Disturb setting", because Cues did not set it and cannot know who did.
 */
@Serializable
enum class OwnedResource {
    FOCUS_TIMER,
    DND_CONTRIBUTION,
    PINNED_NOTE,
    /** The ringer mode Cues set. Restored on exit only if it is still the mode Cues left it in. */
    RINGER_MODE,

    /**
     * A utility toggle Cues turned on via [com.cues.core.model.ActionId.USE_UTILITY].
     * Restored on exit only if the toggle is still in the state Cues set it
     * to, the same rule [RINGER_MODE] already follows — see
     * `AndroidActionExecutor.releaseRingerMode`'s doc comment for why.
     */
    UTILITY_CONTRIBUTION,
}
