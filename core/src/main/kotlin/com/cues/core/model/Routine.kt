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
    /**
     * Which drafter actually produced this routine — never which one was
     * hoped to. Surfaced verbatim in the review and diagnostics screens so a
     * canonical parser is never presented as language understanding, and a
     * model that silently fell back to the parser cannot go unnoticed either.
     *
     * Deliberately outside [Normalizer.digest]'s semantic form: which drafter
     * wrote a cue says nothing about what the cue does, so this field must
     * never be able to invalidate an existing approval.
     */
    val draftedBy: DraftSourceId? = null,
    /** Text the drafter could not map to the closed vocabulary. Approval blocks until it is resolved. */
    val unaccountedClauses: List<String> = emptyList(),
    /** Explicit facts this behavior was compiled against. A fact revision requires re-review. */
    val factDependencies: Set<FactReference> = emptySet(),
)

/**
 * Which component actually produced a draft.
 *
 * Recorded on every drafted [Routine] and surfaced in diagnostics. PRS
 * requirement IN-03 exists because a canonical parser presented as "AI
 * understanding" is a lie told to a judge, and an on-device model that
 * silently fell back to a parser is the same lie told by accident. Neither is
 * acceptable, so the answer travels with the data rather than being asserted
 * in a slide.
 *
 * Lives in `model`, not `drafting`, so [Routine] can reference it without a
 * backward dependency on the package that drafts routines.
 */
@Serializable
enum class DraftSourceId {
    /** A deterministic phrase grammar. Fast, offline, and honest about its limits. */
    GRAMMAR_PARSER,

    /** A small language model running on the phone. */
    ON_DEVICE_LLM,

    /**
     * Reconstructed from a [com.cues.core.share.CueCard] someone else
     * exported — structured, already-compiled data, not language
     * understanding of any kind. Every device, place and context reference
     * inside it is re-resolved against this phone's own stores before this
     * label is ever attached; see `CueCards.reimport`.
     */
    IMPORTED_CARD,

    /**
     * A cloud chat model (Sarvam), consulted only when the user has opted
     * into cloud assist and the offline drafters above disagreed or both
     * failed. Its prose is never trusted as structure — it is re-parsed by
     * the same [GrammarParser][com.cues.core.drafting.GrammarParser] and
     * independently validated exactly like [ON_DEVICE_LLM] — and it never
     * appears downstream of approval. See CLEANUP.md CL-35.
     */
    SARVAM_CLOUD,

    /** A bounded completion supplied to another installed app through Cues' local-model hub. */
    EXTERNAL_GEMMA_CALL,

    /** A bounded completion requested by the platform system-agent surface. */
    SYSTEM_AGENT_CALL,
}

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

    @Serializable
    @SerialName("wifiConnection")
    data class WifiConnection(
        val transition: DeviceTransition,
        val network: WifiNetwork = WifiNetwork.Any,
    ) : Trigger

    @Serializable
    @SerialName("atTime")
    data class AtTime(
        val time: LocalTimeOfDay,
        /** Empty means every local day. */
        val days: Set<Day> = emptySet(),
        /** The zone is part of the approved meaning, not ambient app state. */
        val zoneId: String = "system",
    ) : Trigger

    @Serializable
    @SerialName("audioOutput")
    data class AudioOutput(val transition: AudioTransition, val kind: AudioKind = AudioKind.ANY) : Trigger

    @Serializable
    @SerialName("placeTransition")
    data class PlaceTransition(
        val placeId: String,
        val placeVersion: Int,
        val label: String,
        val transition: PlaceTransitionKind,
    ) : Trigger
}

@Serializable
enum class DeviceTransition { CONNECTED, DISCONNECTED }

@Serializable
enum class PowerTransition { PLUGGED_IN, UNPLUGGED }

@Serializable
enum class AudioTransition { ADDED, REMOVED }

@Serializable
enum class AudioKind { ANY, WIRED, BLUETOOTH }

@Serializable
enum class PlaceTransitionKind { ENTER, EXIT }

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

    @Serializable
    @SerialName("deviceConnected")
    data class DeviceConnected(val deviceId: String, val deviceLabel: String = deviceId) : Condition

    @Serializable
    @SerialName("wifiConnected")
    data class WifiConnected(val network: WifiNetwork = WifiNetwork.Any) : Condition

    @Serializable
    @SerialName("inContext")
    data class InContext(val contextId: String, val contextVersion: Int, val label: String) : Condition

    @Serializable
    @SerialName("audioOutputActive")
    data class AudioOutputActive(val kind: AudioKind = AudioKind.ANY) : Condition

    @Serializable
    @SerialName("batteryBelow")
    data class BatteryBelow(val percent: Int) : Condition

    @Serializable
    @SerialName("batteryAtLeast")
    data class BatteryAtLeast(val percent: Int) : Condition

    @Serializable
    @SerialName("atPlace")
    data class AtPlace(val placeId: String, val placeVersion: Int, val label: String) : Condition

    /**
     * Whether the calendar shows a busy event right now. Missing
     * `READ_CALENDAR` reads as [ContextValue.Unknown], never as "not busy" —
     * see `signals/CalendarKit.kt`.
     */
    @Serializable
    @SerialName("calendarBusy")
    data object CalendarBusy : Condition

    @Serializable
    @SerialName("calendarNotBusy")
    data object CalendarNotBusy : Condition
}

@Serializable
sealed interface WifiNetwork {
    @Serializable
    @SerialName("any")
    data object Any : WifiNetwork

    @Serializable
    @SerialName("named")
    data class Named(val label: String) : WifiNetwork
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

    /** Keep bounded user text visible while the session is active. */
    PINNED_NOTE,

    /**
     * Opens another app, chosen by the user from an installed-app picker at
     * Review — never a package name a drafter supplied. Success means
     * "opened"; the user finishes the task themselves.
     */
    OPEN_APP,

    /** Pre-fills a message. Never sends one — see [ActionRisk.HANDOFF]. */
    COMPOSE_MESSAGE,

    /** Opens WhatsApp with a draft. The user chooses the chat and sends it. */
    COMPOSE_WHATSAPP,

    /** Opens the calendar app's own "add event" screen, pre-filled. The user saves it. */
    ADD_CALENDAR_EVENT,

    /** Asks the clock app to set an alarm. A state Cues did not own before and cannot release. */
    SET_ALARM,

    /** A media-key press: play, pause, next or previous, for whatever app currently holds focus. */
    MEDIA_CONTROL,

    /** Sets the ringer to silent or vibrate. Owned: restored only if nothing else has since changed it. */
    RINGER_MODE,

    /** Opens an https or tel link. The scheme is checked against a closed allowlist, never trusted from a draft. */
    OPEN_LINK,

    /**
     * Turns one cataloged iQOO utility on or off by replaying a taught
     * [UiMacro] through [com.cues.core.registry.UtilityCatalog]'s closed
     * list. The highest risk class in the registry — see
     * [com.cues.core.registry.ActionRisk.UI_AUTOMATION] — because it is the
     * one action whose mechanism can be GUI automation rather than a public
     * API.
     */
    USE_UTILITY,
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

    @Serializable
    @SerialName("pinnedNote")
    data class PinnedNote(val message: String) : ActionArgs

    /**
     * [packageName] and [label] are chosen from an installed-app picker in
     * Review, never accepted verbatim from a drafter — the picker is what
     * makes this a reference to something real on the phone rather than a
     * string a model made up.
     */
    @Serializable
    @SerialName("openApp")
    data class OpenApp(val packageName: String, val label: String) : ActionArgs

    /** [contactHint] is free text shown to the user in the share sheet, resolved by the OS, never by Cues. */
    @Serializable
    @SerialName("composeMessage")
    data class ComposeMessage(val contactHint: String? = null, val text: String) : ActionArgs

    /** A WhatsApp handoff. [contactHint] is only shown to the user; Cues never resolves contacts. */
    @Serializable
    @SerialName("composeWhatsapp")
    data class ComposeWhatsApp(val contactHint: String? = null, val text: String) : ActionArgs

    /**
     * No stored moment on purpose: unlike a fact-dated reminder (not yet
     * wired — see CLEANUP.md CL-16), this event begins when the *action*
     * runs, i.e. at session start, exactly the way every other action here
     * takes effect at its trigger moment rather than at whatever moment the
     * cue happened to be drafted or approved.
     */
    @Serializable
    @SerialName("calendarEvent")
    data class CalendarEvent(val title: String, val durationMinutes: Int) : ActionArgs

    @Serializable
    @SerialName("alarm")
    data class Alarm(val hour: Int, val minute: Int, val label: String? = null) : ActionArgs

    @Serializable
    @SerialName("mediaControl")
    data class MediaControl(val command: MediaCommand) : ActionArgs

    @Serializable
    @SerialName("ringerMode")
    data class RingerMode(val mode: RingerModeKind) : ActionArgs

    /** [url] is validated against a closed scheme allowlist by [com.cues.core.registry.ActionRegistry]. */
    @Serializable
    @SerialName("openLink")
    data class OpenLink(val url: String) : ActionArgs

    /**
     * [utilityId] must name an entry in [com.cues.core.registry.UtilityCatalog] —
     * a closed list, never a string a drafter invented. Which macro actually
     * runs is resolved at execution time from the taught [com.cues.core.model.UtilityBinding]
     * for this utility, not stored here.
     */
    @Serializable
    @SerialName("useUtility")
    data class UseUtility(val utilityId: UtilityId, val state: UtilityState) : ActionArgs
}

@Serializable
enum class MediaCommand { PLAY, PAUSE, NEXT, PREVIOUS }

@Serializable
enum class RingerModeKind { SILENT, VIBRATE }

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

    @Serializable
    @SerialName("atTime")
    data class AtTime(
        val time: LocalTimeOfDay,
        /** Empty means the next occurrence on any local day. */
        val days: Set<Day> = emptySet(),
        val zoneId: String = "system",
    ) : EndCondition
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
    /** Suppress later starts on the same local calendar date, even after the session ends. */
    val oncePerLocalDay: Boolean = false,
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
    NETWORK_STATE,
    LOCATION_FOR_WIFI_NAME,
    LOCATION_FOREGROUND,
    LOCATION_BACKGROUND,

    /**
     * "Cues: iQOO utility bindings" enabled in system Accessibility settings.
     * A live read of that grant, never cached — the same rule every other
     * member of this enum already follows.
     */
    ACCESSIBILITY_SERVICE,

    /** Reading whether the calendar shows a busy event right now — `Condition.CalendarBusy`/`CalendarNotBusy`. */
    READ_CALENDAR,
}
