package com.cues.core.assistant

import com.cues.core.Fixtures
import com.cues.core.model.ActionArgs
import com.cues.core.model.ActionId
import com.cues.core.model.EndCondition
import com.cues.core.model.RoutineStatus
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class RefinerTest {
    @Test
    fun `duration edit updates timer and ending then clears old approval`() {
        val result = Refiner.apply(Fixtures.heroRoutine(), RefineOperation.SetDuration(30))

        val routine = result.routine
        val timer = routine.actions.single { it.actionId == ActionId.START_FOCUS_TIMER }.args as ActionArgs.FocusTimer
        assertEquals(30, timer.durationMinutes)
        assertTrue(EndCondition.Duration(30) in routine.endConditions)
        assertTrue(EndCondition.Duration(45) !in routine.endConditions)
        assertNull(routine.approvedDigest)
        assertEquals(RoutineStatus.REVIEWABLE, routine.status)
        assertTrue(result.validation.isValid)
    }
}
