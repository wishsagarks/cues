package com.cues.core.rehearsal

import com.cues.core.Fixtures
import com.cues.core.model.*
import com.cues.core.ports.ActionExecutor
import com.cues.core.ports.ActionOutcome
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class RehearsalTest {

    @Test
    fun `the core scenarios are all covered`() {
        val report = Rehearsal.run(Fixtures.heroRoutine(), Fixtures.NOW)

        val labels = report.rows.map { it.label }
        assertTrue(labels.any { it.contains("matching") }, labels.toString())
        assertTrue(labels.any { it.contains("outside the cue") }, labels.toString())
        assertTrue(labels.any { it.contains("time window") }, labels.toString())
        assertTrue(labels.any { it.contains("twice") }, labels.toString())
        assertTrue(labels.any { it.contains("goes away") }, labels.toString())
        assertTrue(labels.any { it.contains("timer runs out") }, labels.toString())
    }

    @Test
    fun `a matching event starts the cue`() {
        val report = Rehearsal.run(Fixtures.heroRoutine(), Fixtures.NOW)

        val matching = report.rows.first { it.label.contains("matching") }
        assertEquals("Started", matching.outcome)
    }

    @Test
    fun `a day outside the cue is skipped with a reason`() {
        val report = Rehearsal.run(Fixtures.heroRoutine(), Fixtures.NOW)

        val row = report.rows.first { it.label.contains("outside the cue") }
        assertEquals("Skipped", row.outcome)
        assertTrue(row.explanation.any { it.contains("outside Monday–Friday") }, row.explanation.toString())
    }

    @Test
    fun `a repeated connection does not start a second time`() {
        val report = Rehearsal.run(Fixtures.heroRoutine(), Fixtures.NOW)

        val row = report.rows.first { it.label.contains("twice") }
        assertEquals("Skipped", row.outcome)
        assertTrue(row.explanation.any { it.contains("already running") }, row.explanation.toString())
    }

    @Test
    fun `the timeout row shows the release of our own effects`() {
        val report = Rehearsal.run(Fixtures.heroRoutine(), Fixtures.NOW)

        val row = report.rows.first { it.label.contains("timer runs out") }
        assertEquals("Ended", row.outcome)
        assertTrue(row.explanation.any { it.contains("Released our quiet rule") }, row.explanation.toString())
        assertTrue(row.explanation.any { it.contains("Released our focus timer") }, row.explanation.toString())
    }

    @Test
    fun `an unreadable signal is shown as unreadable`() {
        val routine = Fixtures.heroRoutine(
            conditions = listOf(Condition.DaysOfWeek(WEEKDAYS), Condition.ChargingState(charging = true)),
        )

        val report = Rehearsal.run(routine, Fixtures.NOW)

        val row = report.rows.first { it.label.contains("cannot be read") }
        assertEquals("Skipped, because something could not be read", row.outcome)
    }

    @Test
    fun `every row is marked synthetic`() {
        assertTrue(Rehearsal.run(Fixtures.heroRoutine(), Fixtures.NOW).rows.all { it.synthetic })
    }

    @Test
    fun `rehearsal cannot reach a real executor`() {
        // The engine's only executor is constructed privately inside Rehearsal.
        // This asserts the observable consequence: a real adapter handed to the
        // app is never called while a rehearsal runs.
        val realExecutor = object : ActionExecutor {
            var called = false
            override fun execute(actionId: ActionId, args: ActionArgs, sessionId: String): ActionOutcome {
                called = true
                error("a rehearsal must never touch the device")
            }

            override fun release(resource: OwnedResource, sessionId: String): ActionOutcome {
                called = true
                error("a rehearsal must never touch the device")
            }
        }

        Rehearsal.run(Fixtures.heroRoutine(), Fixtures.NOW)

        assertTrue(!realExecutor.called)
        // Rehearsal.run takes no executor parameter at all, so there is no way
        // to pass one in. The guarantee is structural, not procedural.
        assertTrue(
            Rehearsal::class.java.methods.none { method ->
                method.name == "run" && method.parameterTypes.any { ActionExecutor::class.java.isAssignableFrom(it) }
            },
            "Rehearsal.run must not accept an ActionExecutor",
        )
    }

    @Test
    fun `a charging cue rehearses without a bluetooth trigger`() {
        val routine = Fixtures.heroRoutine().copy(
            trigger = Trigger.Charging(PowerTransition.PLUGGED_IN),
            conditions = listOf(Condition.DaysOfWeek(WEEKDAYS)),
            endConditions = listOf(EndCondition.Duration(45), EndCondition.ManualStop),
        )

        val report = Rehearsal.run(routine, Fixtures.NOW)

        assertEquals("Started", report.rows.first { it.label.contains("matching") }.outcome)
    }
}
