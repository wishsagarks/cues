package com.cues.core.context

import com.cues.core.model.ContextValue
import com.cues.core.model.Day
import com.cues.core.model.LocalTimeOfDay
import com.cues.core.model.UnknownReason
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.jupiter.api.Test

class SnapshotBuilderTest {

    private fun millisAt(iso: String, zone: String) =
        ZonedDateTime.parse("$iso[$zone]", java.time.format.DateTimeFormatter.ISO_ZONED_DATE_TIME)
            .toInstant().toEpochMilli()

    @Test
    fun `local day and time are derived from the clock and zone`() {
        val zone = ZoneId.of("Asia/Kolkata")
        // A known Monday, 18:30 IST.
        val now = millisAt("2026-09-28T18:30:00+05:30", zone.id)

        val snapshot = SnapshotBuilder.build(now, zone)

        assertEquals(Day.MON, (snapshot.localDay as ContextValue.Known).value)
        assertEquals(LocalTimeOfDay(18, 30), (snapshot.localTime as ContextValue.Known).value)
        assertEquals("Asia/Kolkata", snapshot.zoneId)
    }

    @Test
    fun `unsupplied readings stay unknown rather than defaulting`() {
        val snapshot = SnapshotBuilder.build(System.currentTimeMillis(), ZoneId.of("UTC"))

        val charging = assertIs<ContextValue.Unknown>(snapshot.charging)
        assertEquals(UnknownReason.NEVER_OBSERVED, charging.reason)
        assertIs<ContextValue.Unknown>(snapshot.connectedDeviceIds)
    }

    @Test
    fun `a supplied unknown reading passes through unchanged`() {
        val supplied = ContextValue.Unknown(
            UnknownReason.PERMISSION_DENIED,
            com.cues.core.model.ContextSource.BATTERY_MANAGER,
        )

        val snapshot = SnapshotBuilder.build(System.currentTimeMillis(), ZoneId.of("UTC"), charging = supplied)

        assertEquals(supplied, snapshot.charging)
    }

    @Test
    fun `a supplied known reading passes through unchanged`() {
        val supplied = ContextValue.Known(true, com.cues.core.model.ContextSource.BATTERY_MANAGER, 1234L)

        val snapshot = SnapshotBuilder.build(System.currentTimeMillis(), ZoneId.of("UTC"), charging = supplied)

        assertEquals(supplied, snapshot.charging)
    }

    @Test
    fun `crossing a DST spring-forward boundary still derives a valid local time`() {
        // US DST 2026 spring-forward is 2026-03-08 02:00 -> 03:00 in America/New_York.
        val zone = ZoneId.of("America/New_York")
        val justAfter = millisAt("2026-03-08T03:30:00-04:00", zone.id)

        val snapshot = SnapshotBuilder.build(justAfter, zone)

        val time = (snapshot.localTime as ContextValue.Known).value
        assertEquals(LocalTimeOfDay(3, 30), time)
        assertEquals(Day.SUN, (snapshot.localDay as ContextValue.Known).value)
    }

    @Test
    fun `crossing a DST fall-back boundary still derives a valid local time`() {
        // US DST 2026 fall-back is 2026-11-01 02:00 -> 01:00 in America/New_York.
        val zone = ZoneId.of("America/New_York")
        val duringFallBack = millisAt("2026-11-01T01:30:00-05:00", zone.id)

        val snapshot = SnapshotBuilder.build(duringFallBack, zone)

        val time = (snapshot.localTime as ContextValue.Known).value
        assertEquals(LocalTimeOfDay(1, 30), time)
    }

    @Test
    fun `different zones for the same instant produce different local readings`() {
        val instant = millisAt("2026-09-28T23:30:00+05:30", "Asia/Kolkata")

        val kolkata = SnapshotBuilder.build(instant, ZoneId.of("Asia/Kolkata"))
        val utc = SnapshotBuilder.build(instant, ZoneId.of("UTC"))

        assertEquals(LocalTimeOfDay(23, 30), (kolkata.localTime as ContextValue.Known).value)
        assertEquals(LocalTimeOfDay(18, 0), (utc.localTime as ContextValue.Known).value)
    }

    @Test
    fun `the observation timestamp matches the moment supplied, not wall-clock now`() {
        val now = millisAt("2026-09-28T18:30:00+05:30", "Asia/Kolkata")

        val snapshot = SnapshotBuilder.build(now, ZoneId.of("Asia/Kolkata"))

        assertEquals(now, (snapshot.localDay as ContextValue.Known).observedAtMillis)
        assertEquals(now, snapshot.nowMillis)
    }
}
