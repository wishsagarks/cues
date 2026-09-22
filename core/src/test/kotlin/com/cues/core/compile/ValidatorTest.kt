package com.cues.core.compile

import com.cues.core.Fixtures
import com.cues.core.model.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class ValidatorTest {

    @Test
    fun `the hero routine is valid`() {
        val result = Validator.validate(Fixtures.heroRoutine())
        assertTrue(result.isValid, "unexpected errors: ${result.errors.map { it.message }}")
    }

    @Test
    fun `an unresolved device blocks arming`() {
        val routine = Fixtures.heroRoutine().copy(
            trigger = Trigger.BluetoothConnection("", "my earbuds", DeviceTransition.CONNECTED),
        )

        val result = Validator.validate(routine)

        assertFalse(result.isValid)
        assertEquals("Which device? Pick one from your paired devices.", result.errors.single().message)
    }

    @Test
    fun `a cue with no ending is rejected`() {
        val result = Validator.validate(Fixtures.heroRoutine().copy(endConditions = emptyList()))

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.field == "endConditions" })
    }

    @Test
    fun `a cue that does nothing is rejected`() {
        val result = Validator.validate(Fixtures.heroRoutine().copy(actions = emptyList()))

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.field == "actions" })
    }

    @Test
    fun `a timer duration out of bounds is rejected`() {
        val routine = Fixtures.heroRoutine().copy(
            actions = listOf(ActionSpec(ActionId.START_FOCUS_TIMER, ActionArgs.FocusTimer(0))),
            endConditions = listOf(EndCondition.ManualStop),
        )

        val result = Validator.validate(routine)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("between 1 and 480 minutes") })
    }

    @Test
    fun `a timer that disagrees with the ending is caught`() {
        val routine = Fixtures.heroRoutine().copy(
            actions = listOf(ActionSpec(ActionId.START_FOCUS_TIMER, ActionArgs.FocusTimer(45))),
            endConditions = listOf(EndCondition.Duration(30)),
        )

        val result = Validator.validate(routine)

        // Structurally impeccable, semantically incoherent. Exactly the class
        // of mistake a schema check cannot see.
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("One of them is wrong") })
    }

    @Test
    fun `contradictory day conditions are caught`() {
        val routine = Fixtures.heroRoutine(
            conditions = listOf(Condition.DaysOfWeek(WEEKDAYS), Condition.DaysOfWeek(WEEKEND)),
        )

        val result = Validator.validate(routine)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("contradict") })
    }

    @Test
    fun `contradictory charging conditions are caught`() {
        val routine = Fixtures.heroRoutine(
            conditions = listOf(Condition.ChargingState(true), Condition.ChargingState(false)),
        )

        val result = Validator.validate(routine)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.field == "conditions.charging" })
    }

    @Test
    fun `an empty day set is rejected`() {
        val result = Validator.validate(Fixtures.heroRoutine(conditions = listOf(Condition.DaysOfWeek(emptySet()))))

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("never run") })
    }

    @Test
    fun `cleanup beyond owned effects is rejected`() {
        val routine = Fixtures.heroRoutine().copy(
            cleanupPolicy = CleanupPolicy(releaseOwnedEffectsOnly = false),
        )

        val result = Validator.validate(routine)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("only release changes it made itself") })
    }

    @Test
    fun `a long grace period warns but does not block`() {
        val routine = Fixtures.heroRoutine(rearmPolicy = RearmPolicy(reconnectGraceSeconds = 600))

        val result = Validator.validate(routine)

        assertTrue(result.isValid, "a long grace period is a choice, not an error")
        assertTrue(result.warnings.any { it.field == "rearmPolicy.grace" })
    }

    @Test
    fun `a routine from a future schema is rejected`() {
        val result = Validator.validate(Fixtures.heroRoutine().copy(schemaVersion = SCHEMA_VERSION + 1))

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.field == "schemaVersion" })
    }

    @Test
    fun `a duplicated action is rejected`() {
        val routine = Fixtures.heroRoutine().copy(
            actions = listOf(
                ActionSpec(ActionId.START_FOCUS_TIMER, ActionArgs.FocusTimer(45)),
                ActionSpec(ActionId.START_FOCUS_TIMER, ActionArgs.FocusTimer(45)),
            ),
        )

        val result = Validator.validate(routine)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("more than once") })
    }
}
