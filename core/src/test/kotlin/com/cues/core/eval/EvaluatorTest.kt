package com.cues.core.eval

import com.cues.core.Fixtures
import com.cues.core.model.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class EvaluatorTest {

    @Test
    fun `eligible weekday evening connection matches`() {
        val decision = Evaluator.evaluate(
            Fixtures.heroRoutine(),
            Fixtures.connect(),
            Fixtures.snapshot(day = Fixtures.known(Day.MON), time = Fixtures.known(LocalTimeOfDay(18, 30))),
        )

        assertEquals(Truth.MATCH, decision.truth)
        assertTrue(decision.shouldStart)
    }

    @Test
    fun `saturday is reported as outside monday to friday`() {
        val decision = Evaluator.evaluate(
            Fixtures.heroRoutine(),
            Fixtures.connect(),
            Fixtures.snapshot(day = Fixtures.known(Day.SAT)),
        )

        assertEquals(Truth.NO_MATCH, decision.truth)
        val reason = decision.reasons.single { it.code == ReasonCode.DAY_NOT_IN_SET }
        assertEquals("Saturday is outside Monday–Friday.", reason.detail)
    }

    @Test
    fun `a connection before the window does not start the session`() {
        val decision = Evaluator.evaluate(
            Fixtures.heroRoutine(),
            Fixtures.connect(),
            Fixtures.snapshot(time = Fixtures.known(LocalTimeOfDay(9, 15))),
        )

        assertEquals(Truth.NO_MATCH, decision.truth)
        assertEquals(
            "09:15 is outside at or after 18:00.",
            decision.reasons.single { it.code == ReasonCode.TIME_OUTSIDE_WINDOW }.detail,
        )
    }

    // The central safety property of the whole system.
    @Test
    fun `unknown context never grants permission to act`() {
        val routine = Fixtures.heroRoutine(
            conditions = listOf(Condition.DaysOfWeek(WEEKDAYS), Condition.ChargingState(charging = true)),
        )

        val decision = Evaluator.evaluate(
            routine,
            Fixtures.connect(),
            Fixtures.snapshot(charging = Fixtures.unknown(UnknownReason.PERMISSION_DENIED)),
        )

        assertEquals(Truth.UNKNOWN, decision.truth)
        assertFalse(decision.shouldStart, "UNKNOWN must not start a session")
    }

    @Test
    fun `unknown is reported as unknown rather than silently read as false`() {
        val routine = Fixtures.heroRoutine(conditions = listOf(Condition.ChargingState(charging = false)))

        val decision = Evaluator.evaluate(
            routine,
            Fixtures.connect(),
            Fixtures.snapshot(charging = Fixtures.unknown(UnknownReason.ADAPTER_UNAVAILABLE)),
        )

        // Wanting "not charging" with an unreadable battery must not become a match.
        assertEquals(Truth.UNKNOWN, decision.truth)
        assertEquals(
            "Charging state could not be read (the adapter was unavailable).",
            decision.reasons.single { it.code == ReasonCode.CHARGING_UNKNOWN }.detail,
        )
    }

    @Test
    fun `a definite non-match wins over an unknown`() {
        val routine = Fixtures.heroRoutine(
            conditions = listOf(Condition.DaysOfWeek(WEEKDAYS), Condition.ChargingState(charging = true)),
        )

        val decision = Evaluator.evaluate(
            routine,
            Fixtures.connect(),
            Fixtures.snapshot(day = Fixtures.known(Day.SUN), charging = Fixtures.unknown()),
        )

        // "Sunday is outside Monday–Friday" is more useful than "something was unreadable".
        assertEquals(Truth.NO_MATCH, decision.truth)
    }

    @Test
    fun `a stale reading becomes unknown rather than being believed`() {
        val routine = Fixtures.heroRoutine(conditions = listOf(Condition.ChargingState(charging = true)))
        val twoHoursAgo = Fixtures.NOW - 2 * 60 * 60 * 1000

        val decision = Evaluator.evaluate(
            routine,
            Fixtures.connect(),
            Fixtures.snapshot(charging = Fixtures.known(true, atMillis = twoHoursAgo)),
            FreshnessPolicy(chargingMaxAgeMillis = 60_000),
        )

        assertEquals(Truth.UNKNOWN, decision.truth)
        assertEquals(
            "Charging state could not be read (the reading was too old to trust).",
            decision.reasons.single { it.code == ReasonCode.CHARGING_UNKNOWN }.detail,
        )
    }

    @Test
    fun `another device connecting does not trigger this cue`() {
        val decision = Evaluator.evaluate(
            Fixtures.heroRoutine(),
            Fixtures.connect(deviceId = "11:22:33:44:55:66"),
            Fixtures.snapshot(),
        )

        assertEquals(Truth.NO_MATCH, decision.truth)
        assertEquals(ReasonCode.TRIGGER_DEVICE_MISMATCH, decision.reasons.single().code)
    }

    @Test
    fun `a disconnect does not satisfy a connect trigger`() {
        val decision = Evaluator.evaluate(Fixtures.heroRoutine(), Fixtures.disconnect(), Fixtures.snapshot())

        assertEquals(Truth.NO_MATCH, decision.truth)
        assertEquals(ReasonCode.TRIGGER_KIND_MISMATCH, decision.reasons.single().code)
    }

    @Test
    fun `conditions are not evaluated for an event that is not ours`() {
        val decision = Evaluator.evaluate(
            Fixtures.heroRoutine(),
            Fixtures.connect(deviceId = "11:22:33:44:55:66"),
            Fixtures.snapshot(day = Fixtures.known(Day.SAT)),
        )

        // One reason, about the device. Weekday noise would only obscure it.
        assertEquals(1, decision.reasons.size)
    }

    @Test
    fun `an overnight window wraps past midnight`() {
        val routine = Fixtures.heroRoutine(
            conditions = listOf(Condition.TimeWindow(LocalTimeOfDay(22, 0), LocalTimeOfDay(6, 0))),
        )

        fun truthAt(h: Int, m: Int) = Evaluator.evaluate(
            routine,
            Fixtures.connect(),
            Fixtures.snapshot(time = Fixtures.known(LocalTimeOfDay(h, m))),
        ).truth

        assertEquals(Truth.MATCH, truthAt(23, 30), "23:30 is inside 22:00–06:00")
        assertEquals(Truth.MATCH, truthAt(2, 0), "02:00 is inside 22:00–06:00")
        assertEquals(Truth.NO_MATCH, truthAt(12, 0), "midday is not")
        assertEquals(Truth.NO_MATCH, truthAt(6, 0), "the end of the window is exclusive")
    }
}

class TruthTest {

    @Test
    fun `an empty condition list matches`() {
        assertEquals(Truth.MATCH, emptyList<Truth>().conjoin())
    }

    @Test
    fun `conjunction follows Kleene logic`() {
        assertEquals(Truth.MATCH, listOf(Truth.MATCH, Truth.MATCH).conjoin())
        assertEquals(Truth.NO_MATCH, listOf(Truth.MATCH, Truth.NO_MATCH).conjoin())
        assertEquals(Truth.UNKNOWN, listOf(Truth.MATCH, Truth.UNKNOWN).conjoin())
        assertEquals(Truth.NO_MATCH, listOf(Truth.UNKNOWN, Truth.NO_MATCH).conjoin())
    }
}
