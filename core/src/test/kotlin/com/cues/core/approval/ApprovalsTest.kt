package com.cues.core.approval

import com.cues.core.Fixtures
import com.cues.core.model.*
import com.cues.core.session.InMemorySessionStore
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

private val ALL_GRANTED = com.cues.core.ports.CapabilityProvider {
    setOf(
        Capability.BLUETOOTH_CONNECT,
        Capability.NOTIFICATION_POLICY_ACCESS,
        Capability.POST_NOTIFICATIONS,
        Capability.EXACT_ALARM,
        Capability.BATTERY_STATE,
    )
}

class ApprovalsTest {

    @Test
    fun `review normalizes and validates without changing status`() {
        val draft = Fixtures.heroRoutine(status = RoutineStatus.DRAFT)

        val result = Approvals.review(draft)

        assertTrue(result.validation.isValid)
        assertEquals(RoutineStatus.DRAFT, draft.status, "review must not mutate the caller's routine")
    }

    @Test
    fun `review reports an invalid routine without throwing`() {
        val result = Approvals.review(Fixtures.heroRoutine().copy(actions = emptyList()))

        assertTrue(result.validation.isValid.not())
    }

    @Test
    fun `approving a valid routine binds the digest and moves to reviewable`() {
        val draft = Fixtures.heroRoutine(status = RoutineStatus.DRAFT).copy(approvedDigest = null)

        val result = assertIs<ArmResult.Ok<Routine>>(Approvals.approve(draft))

        assertEquals(RoutineStatus.REVIEWABLE, result.routine.status)
        assertEquals(com.cues.core.compile.Normalizer.digest(result.routine), result.routine.approvedDigest)
    }

    @Test
    fun `approving an invalid routine is refused`() {
        val invalid = Fixtures.heroRoutine().copy(endConditions = emptyList())

        val result = Approvals.approve(invalid)

        assertIs<ArmResult.Invalid>(result)
    }

    @Test
    fun `arming an approved routine with every capability granted succeeds`() {
        val approved = assertIs<ArmResult.Ok<Routine>>(
            Approvals.approve(Fixtures.heroRoutine(status = RoutineStatus.DRAFT)),
        ).routine

        val result = assertIs<ArmResult.Ok<Routine>>(Approvals.arm(approved, ALL_GRANTED))

        assertEquals(RoutineStatus.ARMED, result.routine.status)
    }

    @Test
    fun `arming refuses and names a missing capability`() {
        val approved = assertIs<ArmResult.Ok<Routine>>(
            Approvals.approve(Fixtures.heroRoutine(status = RoutineStatus.DRAFT)),
        ).routine
        val onlyBluetooth = com.cues.core.ports.CapabilityProvider { setOf(Capability.BLUETOOTH_CONNECT) }

        val result = assertIs<ArmResult.MissingCapabilities>(Approvals.arm(approved, onlyBluetooth))

        assertEquals(
            setOf(Capability.NOTIFICATION_POLICY_ACCESS, Capability.EXACT_ALARM, Capability.POST_NOTIFICATIONS),
            result.missing,
        )
    }

    @Test
    fun `a capability revoked after approval is caught at arm time, not assumed from review`() {
        val approved = assertIs<ArmResult.Ok<Routine>>(
            Approvals.approve(Fixtures.heroRoutine(status = RoutineStatus.DRAFT)),
        ).routine
        var revoked = false
        val flakyProvider = com.cues.core.ports.CapabilityProvider {
            if (revoked) emptySet() else ALL_GRANTED.granted()
        }

        assertIs<ArmResult.Ok<Routine>>(Approvals.arm(approved, flakyProvider))
        revoked = true
        val result = Approvals.arm(approved, flakyProvider)

        assertIs<ArmResult.MissingCapabilities>(result)
    }

    @Test
    fun `arming an unapproved routine is refused`() {
        val neverApproved = Fixtures.heroRoutine().copy(approvedDigest = null)

        val result = Approvals.arm(neverApproved, ALL_GRANTED)

        assertEquals(ArmResult.NotApproved, result)
    }

    @Test
    fun `editing an approved routine invalidates the approval and blocks arming`() {
        val approved = assertIs<ArmResult.Ok<Routine>>(
            Approvals.approve(Fixtures.heroRoutine(status = RoutineStatus.DRAFT)),
        ).routine

        // A valid edit — changing the reconnect grace is real, visible behaviour
        // and stays independently valid — so arm() must fail on the digest
        // mismatch specifically, not on validity.
        val edited = approved.copy(rearmPolicy = RearmPolicy(reconnectGraceSeconds = 120))

        val result = Approvals.arm(edited, ALL_GRANTED)

        assertEquals(ArmResult.NotApproved, result)
    }

    @Test
    fun `arming an invalid routine is refused even if it carries an old approved digest`() {
        val approved = assertIs<ArmResult.Ok<Routine>>(
            Approvals.approve(Fixtures.heroRoutine(status = RoutineStatus.DRAFT)),
        ).routine
        val brokenButStaleApproval = approved.copy(endConditions = emptyList())

        val result = Approvals.arm(brokenButStaleApproval, ALL_GRANTED)

        assertIs<ArmResult.Invalid>(result)
    }

    @Test
    fun `pause prevents future arming but is itself unconditional`() {
        val armed = Fixtures.heroRoutine(status = RoutineStatus.ARMED)

        val paused = Approvals.pause(armed)

        assertEquals(RoutineStatus.PAUSED, paused.status)
    }

    @Test
    fun `resume re-arms a paused routine without re-running preflight`() {
        val paused = Fixtures.heroRoutine(status = RoutineStatus.PAUSED)

        val result = assertIs<ArmResult.Ok<Routine>>(Approvals.resume(paused))

        assertEquals(RoutineStatus.ARMED, result.routine.status)
    }

    @Test
    fun `resuming a routine that was not paused is refused`() {
        val result = Approvals.resume(Fixtures.heroRoutine(status = RoutineStatus.DRAFT))

        assertEquals(ArmResult.NotPaused, result)
    }

    @Test
    fun `disable is unconditional`() {
        assertEquals(RoutineStatus.DISABLED, Approvals.disable(Fixtures.heroRoutine(status = RoutineStatus.ARMED)).status)
    }

    @Test
    fun `delete is allowed when no session owes a cleanup`() {
        val store = InMemorySessionStore()
        val routine = Fixtures.heroRoutine()
        store.save(
            Session(
                id = "s1", routineId = routine.id, routineVersion = 1, startedAtMillis = 0,
                admissionKey = "k", observedInputs = Fixtures.snapshot(), state = SessionState.COMPLETED,
                obligations = listOf(CleanupObligation(OwnedResource.FOCUS_TIMER, 0, released = true)),
            ),
        )

        assertEquals(DeleteResult.Ok, Approvals.delete(routine, store))
    }

    @Test
    fun `delete is refused while a session still owes a cleanup`() {
        val store = InMemorySessionStore()
        val routine = Fixtures.heroRoutine()
        val stuck = Session(
            id = "s1", routineId = routine.id, routineVersion = 1, startedAtMillis = 0,
            admissionKey = "k", observedInputs = Fixtures.snapshot(), state = SessionState.CLEANUP_PENDING,
            obligations = listOf(CleanupObligation(OwnedResource.DND_CONTRIBUTION, 0, released = false)),
        )
        store.save(stuck)

        val result = assertIs<DeleteResult.Blocked>(Approvals.delete(routine, store))

        assertEquals(listOf("s1"), result.sessionsWithObligations.map { it.id })
    }

    @Test
    fun `delete is unaffected by a different routine's stuck session`() {
        val store = InMemorySessionStore()
        val routine = Fixtures.heroRoutine()
        store.save(
            Session(
                id = "other", routineId = "some-other-routine", routineVersion = 1, startedAtMillis = 0,
                admissionKey = "k", observedInputs = Fixtures.snapshot(), state = SessionState.CLEANUP_PENDING,
                obligations = listOf(CleanupObligation(OwnedResource.FOCUS_TIMER, 0, released = false)),
            ),
        )

        assertEquals(DeleteResult.Ok, Approvals.delete(routine, store))
    }
}
