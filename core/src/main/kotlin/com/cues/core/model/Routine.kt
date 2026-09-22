package com.cues.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Schema revision of the persisted routine format. Bump on any breaking change. */
const val SCHEMA_VERSION: Int = 1

/**
 * A routine is a persistent policy. A [Session] is one occurrence of it.
 *
 * The two are kept strictly apart: finishing today's focus session must never
 * disable tomorrow's routine.
 *
 * Every field here is produced by the deterministic compiler, never taken on
 * trust from a drafting model. [requiredCapabilities] in particular is derived
 * from [actions] and [trigger]; a model claiming a routine needs no permissions
 * does not make it so.
 */
@Serializable
data class Routine(
    val id: String,
    val version: Int,
    val schemaVersion: Int = SCHEMA_VERSION,
    /** The original transcript or typed request, kept correctable and never executed. */
    val sourceText: String,
    val title: String,
    val trigger: Trigger,
    val conditions: List<Condition>,
    val actions: List<ActionSpec>,
    val endConditions: List<EndCondition>,
    val cleanupPolicy: CleanupPolicy,
    val rearmPolicy: RearmPolicy,
    val requiredCapabilities: Set<Capability>,
    /** Digest of the normalized executable semantics the user approved. Null until approval. */
    val approvedDigest: String? = null,
    val status: RoutineStatus = RoutineStatus.DRAFT,
)

@Serializable
enum class RoutineStatus { DRAFT, INVALID, REVIEWABLE, ARMED, PAUSED, DISABLED }

// ---------------------------------------------------------------- triggers

/**
 * What begins a session. Exactly one per routine in the core.
 *
 * A trigger is an *event*, not a state. This distinction carries real weight:
 * a day condition becoming true while earbuds are already connected is not a
 * connection event and must not silently start a session.
 */
@Serializable
sealed interface Trigger {
    @Serializable
    @SerialName("bluetooth")
    data class BluetoothConnection(
        /** Stable address of a specific paired device. "My earbuds" is resolved before this point. */
        val deviceId: String,
        val deviceLabel: String,
        val transition: DeviceTransition,
    ) : Trigger

    @Serializable
    @SerialName("charging")
    data class Charging(val transition: PowerTransition) : Trigger

    /** An explicit user-initiated test run. Recorded with its own provenance. */
    @Serializable
    @SerialName("manual")
    data object Manual : Trigger
}

@Serializable
enum class DeviceTransition { CONNECTED, DISCONNECTED }

@Serializable
enum class PowerTransition { PLUGGED_IN, UNPLUGGED }

// -------------------------------------------------------------- conditions

/**
 * A gate evaluated against a context snapshot at trigger time.
 *
 * Every condition can return UNKNOWN as well as true or false. That third
 * outcome is the point: a condition whose input is missing or stale must not
 * quietly read as false, and must never read as true.
 */
@Serializable
sealed interface Condition {
    @Serializable
    @SerialName("daysOfWeek")
    data class DaysOfWeek(val days: Set<Day>) : Condition

    /**
     * A local wall-clock window, inclusive of [startInclusive] and exclusive of
     * [endExclusive]. When end is not after start the window wraps midnight —
     * "22:00 to 06:00" is a window the user can genuinely mean.
     */
    @Serializable
    @SerialName("timeWindow")
    data class TimeWindow(
        val startInclusive: LocalTimeOfDay,
        val endExclusive: LocalTimeOfDay,
    ) : Condition

    @Serializable
    @SerialName("chargingState")
    data class ChargingState(val charging: Boolean) : Condition
}

@Serializable
enum class Day { MON, TUE, WED, THU, FRI, SAT, SUN }

val WEEKDAYS: Set<Day> = setOf(Day.MON, Day.TUE, Day.WED, Day.THU, Day.FRI)
val WEEKEND: Set<Day> = setOf(Day.SAT, Day.SUN)

/** Minute-resolution local time. Seconds are deliberately absent, and the review says so. */
@Serializable
data class LocalTimeOfDay(val hour: Int, val minute: Int) : Comparable<LocalTimeOfDay> {
    init {
        require(hour in 0..23) { "hour out of range: $hour" }
        require(minute in 0..59) { "minute out of range: $minute" }
    }

    val minutesOfDay: Int get() = hour * 60 + minute

    override fun compareTo(other: LocalTimeOfDay): Int = minutesOfDay - other.minutesOfDay

    override fun toString(): String = "%02d:%02d".format(hour, minute)
}

// ----------------------------------------------------------------- actions

/**
 * A reference to an entry in the closed action registry, with validated
 * arguments. The identifier is an allowlist key, never a command, an intent or
 * anything else a model could widen.
 */
@Serializable
data class ActionSpec(
    val actionId: ActionId,
    val args: ActionArgs,
)

@Serializable
enum class ActionId {
    /** Start the app's own countdown. Owned by Cues, so Cues can cancel it. */
    START_FOCUS_TIMER,

    /** Add this session's quiet contribution. Released, not globally reset, on exit. */
    REQUEST_DND,

    /** Record a local, user-visible note of the outcome. */
    NOTIFY_RESULT,
}

@Serializable
sealed interface ActionArgs {
    @Serializable
    @SerialName("focusTimer")
    data class FocusTimer(val durationMinutes: Int) : ActionArgs

    @Serializable
    @SerialName("dnd")
    data class Dnd(val allowPriority: Boolean = true) : ActionArgs

    @Serializable
    @SerialName("notify")
    data class Notify(val message: String) : ActionArgs

    @Serializable
    @SerialName("none")
    data object None : ActionArgs
}

// ------------------------------------------------------------ end and exit

/** How a session is allowed to end. A routine with no end condition is invalid. */
@Serializable
sealed interface EndCondition {
    /** The trigger device goes away. Subject to [RearmPolicy.reconnectGraceSeconds]. */
    @Serializable
    @SerialName("triggerReversed")
    data object TriggerReversed : EndCondition

    @Serializable
    @SerialName("duration")
    data class Duration(val minutes: Int) : EndCondition

    /** Always present in practice: the user can always stop a session by hand. */
    @Serializable
    @SerialName("manualStop")
    data object ManualStop : EndCondition
}

/**
 * What cleanup is permitted to touch.
 *
 * [releaseOwnedEffectsOnly] is fixed true in the core and exists to make the
 * boundary legible in the review and in the persisted record. Cues restores
 * what Cues owns. It does not reset global state it did not set, because it
 * cannot know who else wanted that state.
 */
@Serializable
data class CleanupPolicy(
    val releaseOwnedEffectsOnly: Boolean = true,
    /** An explicit user change during a session wins over our scheduled release. */
    val respectUserOverride: Boolean = true,
)

/**
 * When a routine may start another session.
 *
 * [reconnectGraceSeconds] covers signal flap: earbuds that drop for three
 * seconds should not end the session and then start a fresh one with a full
 * new timer. The grace period is shown in the review, because a user who
 * cannot see it cannot predict the behaviour.
 */
@Serializable
data class RearmPolicy(
    val reconnectGraceSeconds: Int = 20,
    val cooldownSeconds: Int = 0,
    val maxConcurrentSessions: Int = 1,
)

// ------------------------------------------------------------ capabilities

/** An OS permission or access grant, derived deterministically from the routine. */
@Serializable
enum class Capability {
    BLUETOOTH_CONNECT,
    NOTIFICATION_POLICY_ACCESS,
    POST_NOTIFICATIONS,
    EXACT_ALARM,
    BATTERY_STATE,
}
