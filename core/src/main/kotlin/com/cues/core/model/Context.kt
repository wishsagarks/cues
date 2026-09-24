package com.cues.core.model

import kotlinx.serialization.Serializable

/**
 * One observed fact, carrying where it came from and when it was seen.
 *
 * Cues never stores a bare value. "Charging: false" and "charging: we could
 * not tell" are different facts that lead to different behaviour, and a plain
 * Boolean cannot hold that difference. Absent readings stay [Unknown] all the
 * way through evaluation and into the receipt.
 */
@Serializable
sealed interface ContextValue<out T> {
    @Serializable
    data class Known<out T>(
        val value: T,
        val source: ContextSource,
        /** Epoch millis at observation. Compared against each adapter's freshness rule. */
        val observedAtMillis: Long,
    ) : ContextValue<T>

    @Serializable
    data class Unknown(val reason: UnknownReason, val source: ContextSource) : ContextValue<Nothing>
}

@Serializable
enum class ContextSource {
    BLUETOOTH_ADAPTER,
    BATTERY_MANAGER,
    WIFI_MANAGER,
    AUDIO_MANAGER,
    LOCATION_MANAGER,
    SYSTEM_CLOCK,
    USER,
    REHEARSAL,
    CALENDAR_PROVIDER,
}

@Serializable
enum class UnknownReason {
    PERMISSION_DENIED,
    ADAPTER_UNAVAILABLE,
    NEVER_OBSERVED,
    STALE,
    REDACTED_BY_OS,
}

/**
 * Everything the evaluator is allowed to look at, gathered at trigger time.
 *
 * The snapshot is the enforcement point for "context is declared, not
 * inferred": if a signal is not in here, no condition can reach it. There is
 * no escape hatch to the live device from inside evaluation.
 */
@Serializable
data class ContextSnapshot(
    val nowMillis: Long,
    val localDay: ContextValue<Day>,
    val localTime: ContextValue<LocalTimeOfDay>,
    val zoneId: String,
    val charging: ContextValue<Boolean> = ContextValue.Unknown(
        UnknownReason.NEVER_OBSERVED,
        ContextSource.BATTERY_MANAGER,
    ),
    val connectedDeviceIds: ContextValue<Set<String>> = ContextValue.Unknown(
        UnknownReason.NEVER_OBSERVED,
        ContextSource.BLUETOOTH_ADAPTER,
    ),
    val wifi: ContextValue<WifiState> = ContextValue.Unknown(
        UnknownReason.NEVER_OBSERVED,
        ContextSource.WIFI_MANAGER,
    ),
    val audioOutputs: ContextValue<Set<AudioKind>> = ContextValue.Unknown(
        UnknownReason.NEVER_OBSERVED, ContextSource.AUDIO_MANAGER,
    ),
    val batteryPercent: ContextValue<Int> = ContextValue.Unknown(
        UnknownReason.NEVER_OBSERVED, ContextSource.BATTERY_MANAGER,
    ),
    val insidePlaces: ContextValue<Set<String>> = ContextValue.Unknown(
        UnknownReason.NEVER_OBSERVED, ContextSource.LOCATION_MANAGER,
    ),
    /**
     * Whether the calendar shows a busy event covering [nowMillis]. Missing
     * `READ_CALENDAR` reads as [ContextValue.Unknown] with
     * [UnknownReason.PERMISSION_DENIED] — never as "not busy". See
     * `signals/CalendarKit.kt`.
     */
    val calendarBusy: ContextValue<Boolean> = ContextValue.Unknown(
        UnknownReason.NEVER_OBSERVED, ContextSource.CALENDAR_PROVIDER,
    ),
)

/**
 * A real or rehearsed occurrence that may start or end a session.
 *
 * [connectionSessionId] identifies one unbroken connection as the OS sees it.
 * Keying admission on it, rather than on a clock bucket, is what makes
 * duplicate callbacks for the same physical connection collapse into one
 * session while a genuine reconnect still counts as new.
 */
@Serializable
data class TriggerEvent(
    val kind: EventKind,
    val atMillis: Long,
    val deviceId: String? = null,
    val connectionSessionId: String? = null,
    /** Supplied by time adapters when a trigger is scheduled in a visible zone. */
    val localTime: LocalTimeOfDay? = null,
    val zoneId: String? = null,
    val networkLabel: String? = null,
    val provenance: EventProvenance = EventProvenance.PHYSICAL,
    /**
     * Which routine a manual run (`EventKind.MANUAL_RUN`) targets. Every
     * other trigger kind identifies its target through its own fields
     * (`deviceId`, `networkLabel`, ...); a manual run has none of its own,
     * so without this, one `MANUAL_RUN` event would start every armed
     * `Trigger.Manual` routine at once — see `CueService.couldStart` and
     * CLEANUP.md CL-28.
     */
    val routineId: String? = null,
)

@Serializable
enum class EventKind {
    BLUETOOTH_CONNECTED,
    BLUETOOTH_DISCONNECTED,
    POWER_CONNECTED,
    POWER_DISCONNECTED,
    MANUAL_RUN,
    MANUAL_STOP,
    DEADLINE_REACHED,
    WIFI_CONNECTED,
    WIFI_DISCONNECTED,
    TIME_REACHED,
    AUDIO_OUTPUT_ADDED,
    AUDIO_OUTPUT_REMOVED,
    PLACE_ENTERED,
    PLACE_EXITED,
}

/** Kept distinct so a receipt can never present a rehearsal as something that happened. */
@Serializable
enum class EventProvenance { PHYSICAL, MANUAL, REHEARSAL }

/** A redacted SSID is represented by the outer ContextValue.Unknown instead. */
@Serializable
data class WifiState(
    val connected: Boolean,
    /** Null is valid for the any-network signal; named matching needs a value. */
    val networkLabel: String? = null,
)
