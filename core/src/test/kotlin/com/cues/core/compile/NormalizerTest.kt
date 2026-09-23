package com.cues.core.compile

import com.cues.core.Fixtures
import com.cues.core.model.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class NormalizerTest {

    @Test
    fun `reordering conditions does not change the digest`() {
        val a = Fixtures.heroRoutine(
            conditions = listOf(
                Condition.DaysOfWeek(WEEKDAYS),
                Condition.TimeWindow(LocalTimeOfDay(18, 0), LocalTimeOfDay(0, 0)),
            ),
        )
        val b = a.copy(conditions = a.conditions.reversed())

        // Editing the order of the review rows is not a behaviour change and
        // must not force the user to approve the cue again.
        assertEquals(Normalizer.digest(a), Normalizer.digest(b))
    }

    @Test
    fun `correcting the transcript does not change the digest`() {
        val a = Fixtures.heroRoutine()
        val b = a.copy(sourceText = "when my ear buds connect after 6pm on weekdays ...")

        assertEquals(Normalizer.digest(a), Normalizer.digest(b))
    }

    @Test
    fun `renaming a cue does not change the digest`() {
        val a = Fixtures.heroRoutine()
        assertEquals(Normalizer.digest(a), Normalizer.digest(a.copy(title = "Evening focus")))
    }

    @Test
    fun `moving the time window changes the digest`() {
        val a = Fixtures.heroRoutine()
        val b = Fixtures.heroRoutine(
            conditions = listOf(
                Condition.DaysOfWeek(WEEKDAYS),
                Condition.TimeWindow(LocalTimeOfDay(17, 0), LocalTimeOfDay(0, 0)),
            ),
        )

        assertNotEquals(Normalizer.digest(a), Normalizer.digest(b))
    }

    @Test
    fun `changing the timer duration changes the digest`() {
        val a = Fixtures.heroRoutine()
        val b = a.copy(
            actions = listOf(
                ActionSpec(ActionId.START_FOCUS_TIMER, ActionArgs.FocusTimer(25)),
                ActionSpec(ActionId.REQUEST_DND, ActionArgs.Dnd()),
            ),
        )

        assertNotEquals(Normalizer.digest(a), Normalizer.digest(b))
    }

    @Test
    fun `changing the reconnect grace changes the digest`() {
        val a = Fixtures.heroRoutine()
        val b = Fixtures.heroRoutine(rearmPolicy = RearmPolicy(reconnectGraceSeconds = 120))

        // The grace period is visible behaviour: it decides whether a short
        // dropout ends the session. It belongs inside what was approved.
        assertNotEquals(Normalizer.digest(a), Normalizer.digest(b))
    }

    @Test
    fun `changing the trigger device changes the digest`() {
        val a = Fixtures.heroRoutine()
        val b = a.copy(
            trigger = Trigger.BluetoothConnection("99:88:77:66:55:44", "Car stereo", DeviceTransition.CONNECTED),
        )

        assertNotEquals(Normalizer.digest(a), Normalizer.digest(b))
    }

    @Test
    fun `approval holds for the routine that was approved`() {
        val routine = Fixtures.heroRoutine()
        val approved = routine.copy(approvedDigest = Normalizer.digest(routine))

        assertTrue(Normalizer.matchesApproval(approved))
    }

    @Test
    fun `approval does not carry over to an edited routine`() {
        val routine = Fixtures.heroRoutine()
        val approved = routine.copy(approvedDigest = Normalizer.digest(routine))
        val edited = approved.copy(
            actions = listOf(
                ActionSpec(ActionId.START_FOCUS_TIMER, ActionArgs.FocusTimer(90)),
                ActionSpec(ActionId.REQUEST_DND, ActionArgs.Dnd()),
            ),
        )

        assertFalse(Normalizer.matchesApproval(edited), "an edited cue must be re-approved")
    }

    @Test
    fun `an unapproved routine never matches approval`() {
        assertFalse(Normalizer.matchesApproval(Fixtures.heroRoutine().copy(approvedDigest = null)))
    }

    @Test
    fun `capabilities are recomputed rather than trusted`() {
        // A draft claiming it needs nothing is corrected by the registry.
        val lying = Fixtures.heroRoutine().copy(requiredCapabilities = emptySet())

        val normalized = Normalizer.normalize(lying)

        assertEquals(
            setOf(
                Capability.BLUETOOTH_CONNECT,
                Capability.EXACT_ALARM,
                Capability.POST_NOTIFICATIONS,
                Capability.NOTIFICATION_POLICY_ACCESS,
            ),
            normalized.requiredCapabilities,
        )
    }

    @Test
    fun `which drafter produced a routine does not affect its digest`() {
        val a = Fixtures.heroRoutine().copy(draftedBy = com.cues.core.model.DraftSourceId.GRAMMAR_PARSER)
        val b = Fixtures.heroRoutine().copy(draftedBy = com.cues.core.model.DraftSourceId.ON_DEVICE_LLM)
        val c = Fixtures.heroRoutine().copy(draftedBy = null)

        // Which drafter wrote a cue says nothing about what the cue does, and
        // must never be able to invalidate an existing approval.
        assertEquals(Normalizer.digest(a), Normalizer.digest(b))
        assertEquals(Normalizer.digest(a), Normalizer.digest(c))
    }

    @Test
    fun `normalization is idempotent`() {
        val once = Normalizer.normalize(Fixtures.heroRoutine())
        assertEquals(once, Normalizer.normalize(once))
    }
}
