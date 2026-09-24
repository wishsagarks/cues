package com.cues.core.context

import com.cues.core.eval.ReasonCode
import com.cues.core.model.Capability
import com.cues.core.model.ContextSnapshot
import com.cues.core.model.ContextSource
import com.cues.core.model.ContextValue
import com.cues.core.model.UnknownReason

/** A radio the user can switch on from Settings. */
enum class Radio { BLUETOOTH, WIFI, LOCATION }

/**
 * The one thing a person can do about a reading Cues could not take.
 *
 * "Couldn't read" on its own reads as "Cues is broken". Each remedy names an
 * action that genuinely addresses its cause — and only causes Cues can see:
 * none of these claims the OS killed the app, because that is not something
 * the app can observe.
 */
sealed interface Remedy {
    /** The OS refused for want of a grant. The UI runs that grant's own flow. */
    data class GrantCapability(val capability: Capability) : Remedy

    /**
     * A background listener went quiet: its reading aged out, or the listener
     * was not there to answer. The UI offers the battery-optimisation page.
     */
    data object KeepCuesRunning : Remedy

    /** The radio this signal comes from is off or unavailable. */
    data class TurnOnRadio(val radio: Radio) : Remedy

    /** Nothing has been observed yet since Cues started listening. Not a fault. */
    data object WaitForFirstReading : Remedy

    /**
     * The system deliberately withheld it — a redacted Wi-Fi name, say.
     * Nothing in the app can change that, and the copy says so plainly and
     * suggests a signal that does not depend on it.
     */
    data object OsWithholds : Remedy
}

/**
 * Maps an unreadable input to its remedy — plan §10.3's table, as code.
 *
 * Pure and total over every (source, reason) pair. `null` means Cues knows of
 * no action that would help — a rehearsal's own placeholder, a clock that
 * somehow could not be read — and the UI then shows the reason alone rather
 * than inventing a fix.
 */
object UnknownRemedy {

    fun forUnknown(unknown: ContextValue.Unknown): Remedy? = forUnknown(unknown.source, unknown.reason)

    fun forUnknown(source: ContextSource, reason: UnknownReason): Remedy? = when (reason) {
        UnknownReason.PERMISSION_DENIED -> capabilityFor(source)?.let { Remedy.GrantCapability(it) }

        // A reading that aged past its freshness rule: whatever delivers it
        // stopped reporting. For a background listener that is the one thing
        // the user can influence.
        UnknownReason.STALE -> if (source in BACKGROUND_LISTENERS) Remedy.KeepCuesRunning else null

        UnknownReason.ADAPTER_UNAVAILABLE -> radioFor(source)?.let { Remedy.TurnOnRadio(it) }
            ?: if (source in BACKGROUND_LISTENERS) Remedy.KeepCuesRunning else null

        UnknownReason.NEVER_OBSERVED -> if (source == ContextSource.REHEARSAL) null else Remedy.WaitForFirstReading

        UnknownReason.REDACTED_BY_OS -> if (source == ContextSource.REHEARSAL) null else Remedy.OsWithholds
    }

    /**
     * The grant whose absence makes this source unreadable. Null for a source
     * that has no runtime grant at all (audio routing, the clock) — a
     * PERMISSION_DENIED from one of those has no permission screen to send
     * anyone to.
     */
    private fun capabilityFor(source: ContextSource): Capability? = when (source) {
        ContextSource.BLUETOOTH_ADAPTER -> Capability.BLUETOOTH_CONNECT
        ContextSource.BATTERY_MANAGER -> Capability.BATTERY_STATE
        ContextSource.WIFI_MANAGER -> Capability.NETWORK_STATE
        ContextSource.LOCATION_MANAGER -> Capability.LOCATION_FOREGROUND
        ContextSource.CALENDAR_PROVIDER -> Capability.READ_CALENDAR
        ContextSource.AUDIO_MANAGER,
        ContextSource.SYSTEM_CLOCK,
        ContextSource.USER,
        ContextSource.REHEARSAL,
        -> null
    }

    private fun radioFor(source: ContextSource): Radio? = when (source) {
        ContextSource.BLUETOOTH_ADAPTER -> Radio.BLUETOOTH
        ContextSource.WIFI_MANAGER -> Radio.WIFI
        ContextSource.LOCATION_MANAGER -> Radio.LOCATION
        else -> null
    }

    /**
     * Sources delivered by a listener that has to keep running in the
     * background. The calendar is deliberately absent: it is queried once, at
     * trigger time, so there is no listener to keep alive.
     */
    private val BACKGROUND_LISTENERS = setOf(
        ContextSource.BLUETOOTH_ADAPTER,
        ContextSource.BATTERY_MANAGER,
        ContextSource.WIFI_MANAGER,
        ContextSource.AUDIO_MANAGER,
        ContextSource.LOCATION_MANAGER,
    )
}

/**
 * Which observed input stands behind an UNKNOWN verdict.
 *
 * Kits report *that* a gate could not be read, as a [ReasonCode]; this says
 * *which* reading it was and why, by looking at the same snapshot the kit was
 * given. It decides nothing — the verdict is already made — and holds no
 * condition logic, only which snapshot field each kit reads.
 */
object UnreadableInputs {

    /**
     * The unreadable input behind [code], as observed in [snapshot]. Null
     * when [code] is not an UNKNOWN code, or when no single input explains it
     * (a named context is a conjunction of several signals).
     */
    fun behind(code: ReasonCode, snapshot: ContextSnapshot): ContextValue.Unknown? = when (code) {
        ReasonCode.DAY_UNKNOWN -> snapshot.localDay.unreadable(ages = false)
        ReasonCode.TIME_UNKNOWN -> snapshot.localTime.unreadable(ages = false)
        ReasonCode.CHARGING_UNKNOWN -> snapshot.charging.unreadable(ages = true)
        ReasonCode.DEVICE_CONNECTED_UNKNOWN -> snapshot.connectedDeviceIds.unreadable(ages = true)
        ReasonCode.WIFI_UNKNOWN -> snapshot.wifi.unreadable(ages = true)
        // Connected, but the system would not say to which network.
        ReasonCode.WIFI_NETWORK_UNKNOWN -> ContextValue.Unknown(UnknownReason.REDACTED_BY_OS, ContextSource.WIFI_MANAGER)
        ReasonCode.AUDIO_OUTPUT_UNKNOWN -> snapshot.audioOutputs.unreadable(ages = false)
        ReasonCode.BATTERY_UNKNOWN -> snapshot.batteryPercent.unreadable(ages = true)
        ReasonCode.PLACE_UNKNOWN -> snapshot.insidePlaces.unreadable(ages = false)
        ReasonCode.CALENDAR_UNKNOWN -> snapshot.calendarBusy.unreadable(ages = false)
        else -> null
    }

    /**
     * An Unknown reading is its own answer. A Known one can only have
     * produced an UNKNOWN verdict by ageing past its freshness rule — the one
     * demotion the evaluator applies — so it is reported as STALE, from its
     * own source. A kit that applies no freshness rule cannot get here with a
     * Known value, and gets null rather than a guess.
     */
    private fun ContextValue<*>.unreadable(ages: Boolean): ContextValue.Unknown? = when (this) {
        is ContextValue.Unknown -> this
        is ContextValue.Known -> if (ages) ContextValue.Unknown(UnknownReason.STALE, source) else null
    }
}
