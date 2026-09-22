package com.cues.core.review

import com.cues.core.Fixtures
import com.cues.core.compile.Normalizer
import com.cues.core.model.*
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class ReviewCopyTest {

    @Test
    fun `the hero routine renders the reviewed table from PRS`() {
        // ReviewCopy renders whatever order it's given; sorting endConditions
        // into a stable order is Normalizer's job, and every real caller
        // (the CLI, CueService.review) normalizes first.
        val routine = Normalizer.normalize(Fixtures.heroRoutine())

        assertEquals("TWS Air Pro connects", ReviewCopy.whenText(routine))
        assertEquals("Monday to Friday, at or after 18:00 local time", ReviewCopy.ifText(routine))
        assertEquals(
            "start a 45-minute focus timer, request our quiet-notifications rule",
            ReviewCopy.doText(routine),
        )
        assertEquals("45 minutes have passed, you stop it, TWS Air Pro goes away", ReviewCopy.untilText(routine))
        assertEquals(
            "end our timer, release our quiet rule — and nothing else. Another quiet mode stays as it is.",
            ReviewCopy.restoreText(routine),
        )
    }

    @Test
    fun `restore names nothing to release for a routine with no owned effects`() {
        val routine = Fixtures.heroRoutine().copy(
            actions = listOf(ActionSpec(ActionId.NOTIFY_RESULT, ActionArgs.Notify("done"))),
        )

        assertEquals("nothing to release", ReviewCopy.restoreText(routine))
    }

    @Test
    fun `a charging trigger renders as the charger, not a device label`() {
        val routine = Fixtures.heroRoutine().copy(trigger = Trigger.Charging(PowerTransition.PLUGGED_IN))

        assertEquals("the charger is plugged in", ReviewCopy.whenText(routine))
        assertEquals("the charger", ReviewCopy.triggerNounFor(routine))
    }

    @Test
    fun `access lists the routine's required capabilities by name`() {
        // Fixtures.heroRoutine declares its own capability set rather than
        // deriving one, so this checks against what it actually declares.
        val routine = Fixtures.heroRoutine()

        assertEquals("Bluetooth, Do Not Disturb access, exact alarms", ReviewCopy.accessText(routine))
    }

    @Test
    fun `access includes notifications when the derived capabilities call for it`() {
        val derived = Normalizer.normalize(Fixtures.heroRoutine())

        assertEquals(
            "exact alarms, notifications, Do Not Disturb access",
            ReviewCopy.accessText(derived),
        )
    }

    @Test
    fun `access reads none for a routine that needs nothing`() {
        val routine = Fixtures.heroRoutine().copy(requiredCapabilities = emptySet())

        assertEquals("none", ReviewCopy.accessText(routine))
    }

    @Test
    fun `repeat names the grace and cooldown only when they are set`() {
        val bare = Fixtures.heroRoutine(rearmPolicy = RearmPolicy(reconnectGraceSeconds = 0, cooldownSeconds = 0))
        assertEquals("one session at a time", ReviewCopy.repeatText(bare))

        val withBoth = Fixtures.heroRoutine(
            rearmPolicy = RearmPolicy(reconnectGraceSeconds = 20, cooldownSeconds = 60),
        )
        assertEquals(
            "one session at a time; a dropout shorter than 20s does not end it; waits 60s between runs",
            ReviewCopy.repeatText(withBoth),
        )
    }

    @Test
    fun `always is shown for a routine with no conditions`() {
        assertEquals("always", ReviewCopy.ifText(Fixtures.heroRoutine(conditions = emptyList())))
    }
}
