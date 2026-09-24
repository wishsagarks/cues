package com.cues.core.context

import com.cues.core.Fixtures
import com.cues.core.eval.ReasonCode
import com.cues.core.model.Capability
import com.cues.core.model.ContextSource
import com.cues.core.model.ContextSource.*
import com.cues.core.model.ContextValue
import com.cues.core.model.UnknownReason
import com.cues.core.model.UnknownReason.*
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class UnknownRemedyTest {

    private fun remedy(source: ContextSource, reason: UnknownReason) = UnknownRemedy.forUnknown(source, reason)

    // Every (source, reason) pair the codebase constructs today, with where it
    // comes from. A new pair appearing in an adapter should add a line here.

    @Test
    fun `nothing observed yet is a wait, for every source that snapshots default to`() {
        // SnapshotBuilder's unread* defaults, and CueService.unread (USER).
        listOf(BATTERY_MANAGER, BLUETOOTH_ADAPTER, WIFI_MANAGER, AUDIO_MANAGER, LOCATION_MANAGER, CALENDAR_PROVIDER, USER)
            .forEach { assertEquals(Remedy.WaitForFirstReading, remedy(it, NEVER_OBSERVED), "$it") }
    }

    @Test
    fun `a stale reading from a background listener asks to keep Cues running`() {
        // Evaluator.freshened demotes charging, connected devices, Wi-Fi and battery percent.
        listOf(BATTERY_MANAGER, BLUETOOTH_ADAPTER, WIFI_MANAGER)
            .forEach { assertEquals(Remedy.KeepCuesRunning, remedy(it, STALE), "$it") }
    }

    @Test
    fun `a denied permission names the grant that fixes it`() {
        // CalendarReadings (READ_CALENDAR missing), WifiReadings (SecurityException),
        // Rehearsal's unknownContext scenario (battery).
        assertEquals(Remedy.GrantCapability(Capability.READ_CALENDAR), remedy(CALENDAR_PROVIDER, PERMISSION_DENIED))
        assertEquals(Remedy.GrantCapability(Capability.NETWORK_STATE), remedy(WIFI_MANAGER, PERMISSION_DENIED))
        assertEquals(Remedy.GrantCapability(Capability.BATTERY_STATE), remedy(BATTERY_MANAGER, PERMISSION_DENIED))
        assertEquals(Remedy.GrantCapability(Capability.BLUETOOTH_CONNECT), remedy(BLUETOOTH_ADAPTER, PERMISSION_DENIED))
        assertEquals(Remedy.GrantCapability(Capability.LOCATION_FOREGROUND), remedy(LOCATION_MANAGER, PERMISSION_DENIED))
    }

    @Test
    fun `an unavailable radio asks to turn it on, and a quiet listener asks to keep Cues running`() {
        // WifiReadings: no ConnectivityManager, or no capabilities to read.
        assertEquals(Remedy.TurnOnRadio(Radio.WIFI), remedy(WIFI_MANAGER, ADAPTER_UNAVAILABLE))
        assertEquals(Remedy.TurnOnRadio(Radio.BLUETOOTH), remedy(BLUETOOTH_ADAPTER, ADAPTER_UNAVAILABLE))
        assertEquals(Remedy.TurnOnRadio(Radio.LOCATION), remedy(LOCATION_MANAGER, ADAPTER_UNAVAILABLE))
        // Readings (battery sticky intent missing) and AudioOutputAdapter (no AudioManager).
        assertEquals(Remedy.KeepCuesRunning, remedy(BATTERY_MANAGER, ADAPTER_UNAVAILABLE))
        assertEquals(Remedy.KeepCuesRunning, remedy(AUDIO_MANAGER, ADAPTER_UNAVAILABLE))
    }

    @Test
    fun `a calendar provider that did not answer gets no invented fix`() {
        // CalendarReadings: the provider query returned no cursor. It is not a
        // background listener and has no radio; there is nothing to send anyone to.
        assertNull(remedy(CALENDAR_PROVIDER, ADAPTER_UNAVAILABLE))
    }

    @Test
    fun `a name the system withheld is honestly out of Cues' hands`() {
        // WifiConnectedKit's WIFI_NETWORK_UNKNOWN, via UnreadableInputs.
        assertEquals(Remedy.OsWithholds, remedy(WIFI_MANAGER, REDACTED_BY_OS))
    }

    @Test
    fun `the mapping is total, and a rehearsal placeholder never gets a remedy`() {
        ContextSource.entries.forEach { source ->
            UnknownReason.entries.forEach { reason ->
                val result = remedy(source, reason) // must not throw for any pair
                if (source == REHEARSAL) assertNull(result, "rehearsal $reason")
                if (reason == PERMISSION_DENIED) {
                    assertTrue(result == null || result is Remedy.GrantCapability, "$source $reason -> $result")
                }
            }
        }
    }

    // --------------------------------------------------------- UnreadableInputs

    @Test
    fun `an unreadable input is reported as observed`() {
        val snapshot = Fixtures.snapshot().copy(
            calendarBusy = ContextValue.Unknown(PERMISSION_DENIED, CALENDAR_PROVIDER),
        )

        assertEquals(
            ContextValue.Unknown(PERMISSION_DENIED, CALENDAR_PROVIDER),
            UnreadableInputs.behind(ReasonCode.CALENDAR_UNKNOWN, snapshot),
        )
    }

    @Test
    fun `a known reading behind an UNKNOWN verdict aged out, and is reported as stale from its own source`() {
        val snapshot = Fixtures.snapshot(charging = Fixtures.known(true, source = BATTERY_MANAGER))

        assertEquals(
            ContextValue.Unknown(STALE, BATTERY_MANAGER),
            UnreadableInputs.behind(ReasonCode.CHARGING_UNKNOWN, snapshot),
        )
    }

    @Test
    fun `a withheld network name, a composite context and a matching code have no single input`() {
        val snapshot = Fixtures.snapshot()

        assertEquals(ContextValue.Unknown(REDACTED_BY_OS, WIFI_MANAGER), UnreadableInputs.behind(ReasonCode.WIFI_NETWORK_UNKNOWN, snapshot))
        assertNull(UnreadableInputs.behind(ReasonCode.CONTEXT_UNKNOWN, snapshot))
        assertNull(UnreadableInputs.behind(ReasonCode.CHARGING_AS_REQUIRED, snapshot))
        // Day is never freshened, so a Known day cannot explain an UNKNOWN verdict.
        assertNull(UnreadableInputs.behind(ReasonCode.DAY_UNKNOWN, snapshot))
    }
}
