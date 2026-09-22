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
}

/** The outcome of one action, recorded individually. A blocked action is not a success. */
@Serializable
data class ActionRecord(
    val actionId: ActionId,
    val state: ActionState,
    val detail: String? = null,
)

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
}
