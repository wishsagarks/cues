package com.cues.core.signals

import com.cues.core.Fixtures
import com.cues.core.eval.FreshnessPolicy
import com.cues.core.eval.ReasonCode
import com.cues.core.eval.Truth
import com.cues.core.model.Capability
import com.cues.core.model.Condition
import com.cues.core.model.ContextValue
import com.cues.core.model.UnknownReason
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class CalendarKitTest {

    @Test
    fun `calendar busy matches only when busy`() {
        val busy = Fixtures.snapshot().copy(calendarBusy = Fixtures.known(true))
        val free = Fixtures.snapshot().copy(calendarBusy = Fixtures.known(false))

        assertEquals(Truth.MATCH, CalendarBusyKit.evaluate(Condition.CalendarBusy, busy, FreshnessPolicy.NONE).truth)
        assertEquals(Truth.NO_MATCH, CalendarBusyKit.evaluate(Condition.CalendarBusy, free, FreshnessPolicy.NONE).truth)
    }

    @Test
    fun `calendar not busy is the exact inverse`() {
        val busy = Fixtures.snapshot().copy(calendarBusy = Fixtures.known(true))
        val free = Fixtures.snapshot().copy(calendarBusy = Fixtures.known(false))

        assertEquals(Truth.NO_MATCH, CalendarNotBusyKit.evaluate(Condition.CalendarNotBusy, busy, FreshnessPolicy.NONE).truth)
        assertEquals(Truth.MATCH, CalendarNotBusyKit.evaluate(Condition.CalendarNotBusy, free, FreshnessPolicy.NONE).truth)
    }

    @Test
    fun `a missing READ_CALENDAR permission is unknown, never read as free`() {
        val denied = Fixtures.snapshot().copy(
            calendarBusy = Fixtures.unknown(UnknownReason.PERMISSION_DENIED, com.cues.core.model.ContextSource.CALENDAR_PROVIDER),
        )
        val busyResult = CalendarBusyKit.evaluate(Condition.CalendarBusy, denied, FreshnessPolicy.NONE)
        val notBusyResult = CalendarNotBusyKit.evaluate(Condition.CalendarNotBusy, denied, FreshnessPolicy.NONE)

        assertEquals(Truth.UNKNOWN, busyResult.truth)
        assertEquals(ReasonCode.CALENDAR_UNKNOWN, busyResult.code)
        assertEquals(Truth.UNKNOWN, notBusyResult.truth)
        assertEquals(ReasonCode.CALENDAR_UNKNOWN, notBusyResult.code)
    }

    @Test
    fun `both conditions derive READ_CALENDAR and nothing else`() {
        assertEquals(setOf(Capability.READ_CALENDAR), CalendarBusyKit.capabilities(Condition.CalendarBusy))
        assertEquals(setOf(Capability.READ_CALENDAR), CalendarNotBusyKit.capabilities(Condition.CalendarNotBusy))
    }

    @Test
    fun `registered in SignalRegistry and reachable through the generic dispatch`() {
        val busy = Fixtures.snapshot().copy(calendarBusy = Fixtures.known(true))
        assertEquals(
            Truth.MATCH,
            SignalRegistry.evaluate(Condition.CalendarBusy, busy, FreshnessPolicy.NONE).truth,
        )
        assertEquals("calendarBusy", SignalRegistry.semanticForm(Condition.CalendarBusy))
        assertEquals("calendarNotBusy", SignalRegistry.semanticForm(Condition.CalendarNotBusy))
    }
}
