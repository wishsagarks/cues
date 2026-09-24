package com.cues.core.review

import com.cues.core.Fixtures
import com.cues.core.context.Remedy
import com.cues.core.eval.Evaluator
import com.cues.core.eval.FreshnessPolicy
import com.cues.core.eval.ReasonCode
import com.cues.core.eval.Truth
import com.cues.core.model.*
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class GateReadoutTest {

    private val gated = Fixtures.heroRoutine(
        conditions = listOf(
            Condition.DaysOfWeek(WEEKDAYS),
            Condition.ChargingState(true),
            Condition.CalendarNotBusy,
        ),
    )

    @Test
    fun `a missing calendar permission reads UNKNOWN, never free, and offers the calendar grant`() {
        val snapshot = Fixtures.snapshot().copy(
            calendarBusy = ContextValue.Unknown(UnknownReason.PERMISSION_DENIED, ContextSource.CALENDAR_PROVIDER),
        )

        val calendar = GateReadout.forRoutine(gated, snapshot).single { it.reason.code.name.startsWith("CALENDAR") }

        assertEquals(Truth.UNKNOWN, calendar.truth)
        assertEquals(ReasonCode.CALENDAR_UNKNOWN, calendar.reason.code)
        assertEquals(ContextValue.Unknown(UnknownReason.PERMISSION_DENIED, ContextSource.CALENDAR_PROVIDER), calendar.unreadable)
        assertEquals(Remedy.GrantCapability(Capability.READ_CALENDAR), calendar.remedy)
    }

    @Test
    fun `a charging reading older than its freshness rule reads UNKNOWN, as stale`() {
        val fiveMinutesAgo = Fixtures.NOW - 5 * 60_000
        val snapshot = Fixtures.snapshot(
            charging = Fixtures.known(true, atMillis = fiveMinutesAgo, source = ContextSource.BATTERY_MANAGER),
        )

        val charging = GateReadout.forRoutine(gated, snapshot).single { it.reason.code.name.startsWith("CHARGING") }

        assertEquals(Truth.UNKNOWN, charging.truth, "a stale true is not a true")
        assertEquals(ContextValue.Unknown(UnknownReason.STALE, ContextSource.BATTERY_MANAGER), charging.unreadable)
        assertEquals(Remedy.KeepCuesRunning, charging.remedy)
    }

    @Test
    fun `the same stale reading is fine when the caller's freshness rule allows it`() {
        val snapshot = Fixtures.snapshot(charging = Fixtures.known(true, atMillis = Fixtures.NOW - 5 * 60_000))

        val lines = GateReadout.forRoutine(gated, snapshot, FreshnessPolicy.NONE)

        assertEquals(Truth.MATCH, lines.single { it.reason.code.name.startsWith("CHARGING") }.truth)
    }

    @Test
    fun `every verdict is the one the runtime evaluator reaches for the same inputs`() {
        val snapshot = Fixtures.snapshot(charging = Fixtures.unknown(UnknownReason.PERMISSION_DENIED)).copy(
            calendarBusy = Fixtures.known(true, source = ContextSource.CALENDAR_PROVIDER),
        )

        val readout = GateReadout.forRoutine(gated, snapshot).map { it.reason }
        // The trigger reason comes first in a Decision; the rest are the conditions, in order.
        val runtime = Evaluator.evaluate(gated, Fixtures.connect(), snapshot).reasons.drop(1)

        assertEquals(runtime, readout)
    }

    @Test
    fun `one line per condition, worded exactly as the review rows are`() {
        val lines = GateReadout.forRoutine(gated, Fixtures.snapshot())

        assertEquals(ReviewCopy.conditionLines(gated), lines.map { it.clauseText })
        assertTrue(lines.filter { it.truth != Truth.UNKNOWN }.all { it.unreadable == null && it.remedy == null })
    }

    @Test
    fun `a routine with no conditions has no gates`() {
        assertEquals(emptyList(), GateReadout.forRoutine(gated.copy(conditions = emptyList()), Fixtures.snapshot()))
    }

    @Test
    fun `a named context that cannot be read has no single input to blame`() {
        val routine = gated.copy(conditions = listOf(Condition.InContext("ctx-missing", 1, "Desk")))

        val line = GateReadout.forRoutine(routine, Fixtures.snapshot()).single()

        assertEquals(Truth.UNKNOWN, line.truth)
        assertNull(line.unreadable)
        assertNull(line.remedy)
    }
}
