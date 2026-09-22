package com.cues.core.context

import com.cues.core.model.ContextSnapshot
import com.cues.core.model.ContextSource
import com.cues.core.model.ContextValue
import com.cues.core.model.Day
import com.cues.core.model.LocalTimeOfDay
import com.cues.core.model.UnknownReason
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Turns a moment plus whatever readings are actually available into the
 * [ContextSnapshot] the evaluator consumes.
 *
 * Nothing else in `:core` builds one of these outside a test — every real
 * event needs to go through here first. Its only job is to not invent
 * anything: the local day and time are derived deterministically from the
 * clock and the zone, and every other reading is passed through exactly as
 * the caller supplied it. A caller that could not read the battery hands in
 * [ContextValue.Unknown] and gets [ContextValue.Unknown] back — this class
 * never manufactures a default in its place.
 */
object SnapshotBuilder {

    fun build(
        nowMillis: Long,
        zoneId: ZoneId,
        charging: ContextValue<Boolean> = unreadCharging(),
        connectedDeviceIds: ContextValue<Set<String>> = unreadDevices(),
    ): ContextSnapshot {
        val local = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zoneId)

        return ContextSnapshot(
            nowMillis = nowMillis,
            localDay = ContextValue.Known(local.dayOfWeek.toCuesDay(), ContextSource.SYSTEM_CLOCK, nowMillis),
            localTime = ContextValue.Known(
                LocalTimeOfDay(local.hour, local.minute),
                ContextSource.SYSTEM_CLOCK,
                nowMillis,
            ),
            zoneId = zoneId.id,
            charging = charging,
            connectedDeviceIds = connectedDeviceIds,
        )
    }

    /** The day and time are never in question; only the readings a caller has to supply can be. */
    private fun unreadCharging() = ContextValue.Unknown(UnknownReason.NEVER_OBSERVED, ContextSource.BATTERY_MANAGER)
    private fun unreadDevices() = ContextValue.Unknown(UnknownReason.NEVER_OBSERVED, ContextSource.BLUETOOTH_ADAPTER)

    private fun java.time.DayOfWeek.toCuesDay(): Day = when (this) {
        java.time.DayOfWeek.MONDAY -> Day.MON
        java.time.DayOfWeek.TUESDAY -> Day.TUE
        java.time.DayOfWeek.WEDNESDAY -> Day.WED
        java.time.DayOfWeek.THURSDAY -> Day.THU
        java.time.DayOfWeek.FRIDAY -> Day.FRI
        java.time.DayOfWeek.SATURDAY -> Day.SAT
        java.time.DayOfWeek.SUNDAY -> Day.SUN
    }
}
