package com.cues.core.session

import com.cues.core.Fixtures
import com.cues.core.model.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class SessionEngineTest {

    private val clock = FakeClock(Fixtures.NOW)
    private val store = InMemorySessionStore()

    private fun engine(executor: RecordingExecutor = RecordingExecutor()) =
        SessionEngine(store, executor, clock, idGenerator = { "session-${store.all.size + 1}" })

    private fun routines(routine: Routine) = mapOf(routine.id to routine)

    // ------------------------------------------------------------ starting

    @Test
    fun `an eligible connection starts one session and runs its actions`() {
        val executor = RecordingExecutor()
        val result = engine(executor).onTriggerEvent(Fixtures.heroRoutine(), Fixtures.connect(), Fixtures.snapshot())

        val started = assertIs<EngineResult.Started>(result)
        assertEquals(SessionState.ACTIVE, started.session.state)
        assertEquals(listOf(ActionId.START_FOCUS_TIMER, ActionId.REQUEST_DND), executor.executed)
        assertEquals(
            setOf(OwnedResource.FOCUS_TIMER, OwnedResource.DND_CONTRIBUTION),
            started.session.obligations.map { it.resource }.toSet(),
        )
    }

    @Test
    fun `a paused cue does not run`() {
        val result = engine().onTriggerEvent(
            Fixtures.heroRoutine(status = RoutineStatus.PAUSED),
            Fixtures.connect(),
            Fixtures.snapshot(),
        )

        assertIs<EngineResult.Skipped>(result)
        assertTrue(store.all.isEmpty(), "a paused cue must not create a session")
    }

    @Test
    fun `a duplicate callback for the same connection does not start a second session`() {
        val executor = RecordingExecutor()
        val engine = engine(executor)
        val routine = Fixtures.heroRoutine()

        engine.onTriggerEvent(routine, Fixtures.connect(connectionSessionId = "conn-1"), Fixtures.snapshot())
        // Android delivers the same connection more than once. It must not
        // produce a second timer.
        val second = engine.onTriggerEvent(routine, Fixtures.connect(connectionSessionId = "conn-1"), Fixtures.snapshot())

        assertIs<EngineResult.Skipped>(second)
        assertEquals(1, store.all.size)
        assertEquals(2, executor.executed.size, "actions must not run a second time")
    }

    @Test
    fun `a blocked action produces a partial session rather than a reported success`() {
        val executor = RecordingExecutor(blocked = setOf(ActionId.REQUEST_DND))

        val result = engine(executor).onTriggerEvent(Fixtures.heroRoutine(), Fixtures.connect(), Fixtures.snapshot())

        val session = assertIs<EngineResult.Started>(result).session
        assertEquals(SessionState.PARTIAL, session.state)
        assertEquals(
            ActionState.BLOCKED,
            session.actions.single { it.actionId == ActionId.REQUEST_DND }.state,
        )
        // The timer did start, so it is still owed a cleanup.
        assertEquals(listOf(OwnedResource.FOCUS_TIMER), session.obligations.map { it.resource })
    }

    private fun handoffRoutine() = Fixtures.heroRoutine(conditions = emptyList()).copy(
        actions = listOf(ActionSpec(ActionId.OPEN_APP, ActionArgs.OpenApp("com.example.app", "Example"))),
        endConditions = listOf(EndCondition.ManualStop),
    )

    @Test
    fun `a NEEDS_USER action waits rather than attempting when nobody is present`() {
        val executor = RecordingExecutor()
        val engine = SessionEngine(store, executor, clock, attention = ToggleAttention(present = false))

        val session = assertIs<EngineResult.Started>(
            engine.onTriggerEvent(handoffRoutine(), Fixtures.connect(), Fixtures.snapshot()),
        ).session

        assertEquals(SessionState.PARTIAL, session.state)
        assertEquals(ActionState.PENDING, session.actions.single().state)
        assertTrue(executor.executed.isEmpty(), "a PENDING action must never be attempted")
        assertTrue(session.obligations.isEmpty(), "nothing was acquired yet")
    }

    @Test
    fun `retryPendingActions runs a pending action once someone is present`() {
        val attention = ToggleAttention(present = false)
        val executor = RecordingExecutor()
        val engine = SessionEngine(store, executor, clock, attention = attention)
        val routine = handoffRoutine()

        val session = assertIs<EngineResult.Started>(
            engine.onTriggerEvent(routine, Fixtures.connect(), Fixtures.snapshot()),
        ).session

        attention.present = true
        val retried = engine.retryPendingActions(routine, session.id)

        assertEquals(listOf(ActionId.OPEN_APP), executor.executed, "retry is the first real attempt")
        assertEquals(ActionState.SUCCEEDED, retried?.actions?.single()?.state)
        assertEquals(SessionState.ACTIVE, retried?.state)
    }

    @Test
    fun `an unattended NEEDS_USER action is blocked, not dropped, when the session ends`() {
        val executor = RecordingExecutor()
        val engine = SessionEngine(store, executor, clock, attention = ToggleAttention(present = false))
        val routine = handoffRoutine()

        val session = assertIs<EngineResult.Started>(
            engine.onTriggerEvent(routine, Fixtures.connect(), Fixtures.snapshot()),
        ).session

        val ended = engine.end(routine, session, EndReason.MANUAL_STOP)

        val record = ended.actions.single()
        assertEquals(ActionState.BLOCKED, record.state)
        assertEquals(EXPIRED_WHILE_PENDING_DETAIL, record.detail)
        assertTrue(executor.executed.isEmpty(), "expiry is not an attempt")
    }

    @Test
    fun `a session is persisted before any side effect runs`() {
        val routine = Fixtures.heroRoutine()
        var stateAtFirstAction: SessionState? = null

        val executor = object : com.cues.core.ports.ActionExecutor {
            override fun execute(actionId: ActionId, args: ActionArgs, sessionId: String) =
                com.cues.core.ports.ActionOutcome(ActionState.SUCCEEDED).also {
                    stateAtFirstAction = stateAtFirstAction ?: store.find(sessionId)?.state
                }

            override fun release(resource: OwnedResource, sessionId: String) =
                com.cues.core.ports.ActionOutcome(ActionState.SUCCEEDED)
        }

        SessionEngine(store, executor, clock).onTriggerEvent(routine, Fixtures.connect(), Fixtures.snapshot())

        // A crash between the effect and its record still leaves a trace.
        assertEquals(SessionState.STARTING, stateAtFirstAction)
    }

    // ---------------------------------------------------- reconnect grace

    @Test
    fun `a disconnect schedules an exit rather than ending immediately`() {
        val engine = engine()
        val routine = Fixtures.heroRoutine()
        engine.onTriggerEvent(routine, Fixtures.connect(), Fixtures.snapshot())

        clock.advanceMinutes(5)
        val result = engine.onExitEvent(routine, Fixtures.disconnect(atMillis = clock.now))

        val scheduled = assertIs<EngineResult.ExitScheduled>(result)
        assertEquals(SessionState.EXIT_PENDING, scheduled.session.state)
        assertEquals(clock.now + 20_000, scheduled.atMillis)
    }

    @Test
    fun `a reconnect inside the grace window resumes without restarting the timer`() {
        val executor = RecordingExecutor()
        val engine = engine(executor)
        val routine = Fixtures.heroRoutine()

        val started = assertIs<EngineResult.Started>(
            engine.onTriggerEvent(routine, Fixtures.connect(connectionSessionId = "conn-1"), Fixtures.snapshot()),
        ).session
        val originalDeadline = started.deadlineMillis

        clock.advanceMinutes(5)
        engine.onExitEvent(routine, Fixtures.disconnect(atMillis = clock.now))

        clock.advanceSeconds(5)
        val resumed = engine.onTriggerEvent(
            routine,
            Fixtures.connect(atMillis = clock.now, connectionSessionId = "conn-2"),
            Fixtures.snapshot(),
        )

        val session = assertIs<EngineResult.ExitCancelled>(resumed).session
        assertEquals(SessionState.ACTIVE, session.state)
        assertNull(session.pendingExitAtMillis)
        // The user asked for 45 minutes of focus, not 45 more.
        assertEquals(originalDeadline, session.deadlineMillis)
        assertEquals(1, store.all.size)
        assertEquals(2, executor.executed.size, "a flap must not re-run the actions")
        assertTrue(executor.released.isEmpty(), "a flap must not release anything")
    }

    @Test
    fun `a disconnect past the grace window ends the session`() {
        val executor = RecordingExecutor()
        val engine = engine(executor)
        val routine = Fixtures.heroRoutine()
        val started = assertIs<EngineResult.Started>(
            engine.onTriggerEvent(routine, Fixtures.connect(), Fixtures.snapshot()),
        ).session

        engine.onExitEvent(routine, Fixtures.disconnect(atMillis = clock.now))
        clock.advanceSeconds(25)
        val result = engine.onGraceElapsed(routine, started.id)

        val ended = assertIs<EngineResult.Ended>(result).session
        assertEquals(SessionState.COMPLETED, ended.state)
        assertEquals(EndReason.TRIGGER_REVERSED, ended.endReason)
        assertEquals(
            setOf(OwnedResource.FOCUS_TIMER, OwnedResource.DND_CONTRIBUTION),
            executor.released.toSet(),
        )
    }

    @Test
    fun `the grace period does not elapse early`() {
        val engine = engine()
        val routine = Fixtures.heroRoutine()
        val started = assertIs<EngineResult.Started>(
            engine.onTriggerEvent(routine, Fixtures.connect(), Fixtures.snapshot()),
        ).session

        engine.onExitEvent(routine, Fixtures.disconnect(atMillis = clock.now))
        clock.advanceSeconds(5)

        assertIs<EngineResult.Ignored>(engine.onGraceElapsed(routine, started.id))
    }

    @Test
    fun `a zero grace period ends on disconnect immediately`() {
        val routine = Fixtures.heroRoutine(rearmPolicy = RearmPolicy(reconnectGraceSeconds = 0))
        val engine = engine()
        engine.onTriggerEvent(routine, Fixtures.connect(), Fixtures.snapshot())

        val result = engine.onExitEvent(routine, Fixtures.disconnect(atMillis = clock.now))

        assertEquals(SessionState.COMPLETED, assertIs<EngineResult.Ended>(result).session.state)
    }

    // -------------------------------------------------------------- exits

    @Test
    fun `the deadline ends the session and releases what it owns`() {
        val executor = RecordingExecutor()
        val engine = engine(executor)
        val routine = Fixtures.heroRoutine()
        engine.onTriggerEvent(routine, Fixtures.connect(), Fixtures.snapshot())

        clock.advanceMinutes(45)
        val result = engine.onExitEvent(
            routine,
            TriggerEvent(EventKind.DEADLINE_REACHED, clock.now),
        )

        val ended = assertIs<EngineResult.Ended>(result).session
        assertEquals(EndReason.DEADLINE_REACHED, ended.endReason)
        assertTrue(ended.obligations.all { it.released })
    }

    @Test
    fun `an alarm that fires early does not shorten the timer`() {
        val engine = engine()
        val routine = Fixtures.heroRoutine()
        engine.onTriggerEvent(routine, Fixtures.connect(), Fixtures.snapshot())

        clock.advanceMinutes(10)
        val result = engine.onExitEvent(routine, TriggerEvent(EventKind.DEADLINE_REACHED, clock.now))

        assertIs<EngineResult.Ignored>(result)
    }

    @Test
    fun `a manual stop ends the session`() {
        val engine = engine()
        val routine = Fixtures.heroRoutine()
        engine.onTriggerEvent(routine, Fixtures.connect(), Fixtures.snapshot())

        val result = engine.onExitEvent(routine, TriggerEvent(EventKind.MANUAL_STOP, clock.now))

        assertEquals(EndReason.MANUAL_STOP, assertIs<EngineResult.Ended>(result).session.endReason)
    }

    @Test
    fun `ending twice is idempotent`() {
        val executor = RecordingExecutor()
        val engine = engine(executor)
        val routine = Fixtures.heroRoutine()
        val session = assertIs<EngineResult.Started>(
            engine.onTriggerEvent(routine, Fixtures.connect(), Fixtures.snapshot()),
        ).session

        val first = engine.end(routine, session, EndReason.MANUAL_STOP)
        val second = engine.end(routine, first, EndReason.DEADLINE_REACHED)

        // A deadline alarm and a disconnect can arrive milliseconds apart.
        assertEquals(first, second)
        assertEquals(2, executor.released.size, "resources must be released once, not twice")
    }

    // ----------------------------------------------------------- cleanup

    @Test
    fun `one failed release does not strand the other resource`() {
        val executor = RecordingExecutor(unreleasable = setOf(OwnedResource.DND_CONTRIBUTION))
        val engine = engine(executor)
        val routine = Fixtures.heroRoutine()
        val session = assertIs<EngineResult.Started>(
            engine.onTriggerEvent(routine, Fixtures.connect(), Fixtures.snapshot()),
        ).session

        val ended = engine.end(routine, session, EndReason.MANUAL_STOP)

        // A quiet rule we cannot release is no reason to leave the timer running.
        assertTrue(OwnedResource.FOCUS_TIMER in executor.released)
        assertTrue(OwnedResource.DND_CONTRIBUTION in executor.released)
        assertTrue(ended.obligations.single { it.resource == OwnedResource.FOCUS_TIMER }.released)
        assertFalse(ended.obligations.single { it.resource == OwnedResource.DND_CONTRIBUTION }.released)
        assertEquals(SessionState.CLEANUP_PENDING, ended.state, "an unreleased resource stays visible")
    }

    @Test
    fun `a release that throws does not abort the remaining cleanup`() {
        val executor = RecordingExecutor(throwOnRelease = setOf(OwnedResource.FOCUS_TIMER))
        val engine = engine(executor)
        val routine = Fixtures.heroRoutine()
        val session = assertIs<EngineResult.Started>(
            engine.onTriggerEvent(routine, Fixtures.connect(), Fixtures.snapshot()),
        ).session

        val ended = engine.end(routine, session, EndReason.MANUAL_STOP)

        assertTrue(OwnedResource.DND_CONTRIBUTION in executor.released)
        assertEquals(SessionState.CLEANUP_PENDING, ended.state)
    }

    @Test
    fun `a user override stands down cleanup rather than overriding the user`() {
        val executor = RecordingExecutor()
        val engine = engine(executor)
        val routine = Fixtures.heroRoutine()
        val session = assertIs<EngineResult.Started>(
            engine.onTriggerEvent(routine, Fixtures.connect(), Fixtures.snapshot()),
        ).session

        engine.markUserOverride(session.id)
        val ended = engine.end(routine, store.find(session.id)!!, EndReason.MANUAL_STOP)

        assertTrue(executor.released.isEmpty(), "the user's later choice wins over our schedule")
        assertEquals(SessionState.COMPLETED, ended.state)
        assertTrue(ended.obligations.all { it.released })
    }

    @Test
    fun `pending cleanup can be retried`() {
        val executor = RecordingExecutor(unreleasable = setOf(OwnedResource.DND_CONTRIBUTION))
        val routine = Fixtures.heroRoutine()
        val failing = SessionEngine(store, executor, clock, idGenerator = { "session-1" })
        val session = assertIs<EngineResult.Started>(
            failing.onTriggerEvent(routine, Fixtures.connect(), Fixtures.snapshot()),
        ).session
        failing.end(routine, session, EndReason.MANUAL_STOP)

        // The next attempt succeeds, e.g. after the user granted the permission.
        val recovering = SessionEngine(store, RecordingExecutor(), clock)
        val retried = recovering.retryPendingCleanup(routines(routine))

        assertEquals(1, retried.size)
        assertEquals(SessionState.COMPLETED, retried.single().state)
    }

    // ---------------------------------------------------------- recovery

    @Test
    fun `an expired session is cleaned up and never replayed`() {
        val executor = RecordingExecutor()
        val routine = Fixtures.heroRoutine()
        val engine = engine(executor)
        engine.onTriggerEvent(routine, Fixtures.connect(), Fixtures.snapshot())
        val actionsBefore = executor.executed.size

        // The process died; we wake up long after the deadline passed.
        clock.advanceMinutes(120)
        val reconciled = SessionEngine(store, executor, clock).reconcile(routines(routine))

        assertEquals(1, reconciled.size)
        assertEquals(EndReason.DEADLINE_REACHED, reconciled.single().endReason)
        assertEquals(actionsBefore, executor.executed.size, "recovery must not re-run actions")
        assertEquals(
            setOf(OwnedResource.FOCUS_TIMER, OwnedResource.DND_CONTRIBUTION),
            executor.released.toSet(),
        )
    }

    @Test
    fun `a session still within its deadline survives a restart`() {
        val routine = Fixtures.heroRoutine()
        engine().onTriggerEvent(routine, Fixtures.connect(), Fixtures.snapshot())

        clock.advanceMinutes(10)
        val reconciled = SessionEngine(store, RecordingExecutor(), clock).reconcile(routines(routine))

        assertTrue(reconciled.isEmpty(), "a live session must be left alone")
        assertEquals(SessionState.ACTIVE, store.all.single().state)
    }

    @Test
    fun `a grace window that elapsed while the process was dead ends the session`() {
        val routine = Fixtures.heroRoutine()
        val engine = engine()
        engine.onTriggerEvent(routine, Fixtures.connect(), Fixtures.snapshot())
        engine.onExitEvent(routine, Fixtures.disconnect(atMillis = clock.now))

        clock.advanceSeconds(60)
        val reconciled = SessionEngine(store, RecordingExecutor(), clock).reconcile(routines(routine))

        assertEquals(EndReason.TRIGGER_REVERSED, reconciled.single().endReason)
    }

    @Test
    fun `a session whose routine is gone is left alone rather than guessed at`() {
        val routine = Fixtures.heroRoutine()
        engine().onTriggerEvent(routine, Fixtures.connect(), Fixtures.snapshot())

        clock.advanceMinutes(120)
        val reconciled = SessionEngine(store, RecordingExecutor(), clock).reconcile(emptyMap())

        // Without the routine we do not know its cleanup policy, so acting
        // would be guessing.
        assertTrue(reconciled.isEmpty())
    }

    // -------------------------------------------------------- coverage gaps

    @Test
    fun `a live session whose device is no longer connected is closed as a coverage gap`() {
        val routine = Fixtures.heroRoutine()
        val engine = engine()
        engine.onTriggerEvent(routine, Fixtures.connect(), Fixtures.snapshot())

        val gaps = engine.checkBluetoothCoverage(routines(routine), currentlyConnectedDeviceIds = emptySet())

        assertEquals(1, gaps.size)
        assertEquals(EndReason.COVERAGE_GAP, gaps.single().endReason)
        assertEquals(SessionState.COMPLETED, gaps.single().state)
    }

    @Test
    fun `a session whose device is still reported connected is left alone`() {
        val routine = Fixtures.heroRoutine()
        val engine = engine()
        engine.onTriggerEvent(routine, Fixtures.connect(), Fixtures.snapshot())

        val gaps = engine.checkBluetoothCoverage(routines(routine), currentlyConnectedDeviceIds = setOf(Fixtures.EARBUDS_ID))

        assertTrue(gaps.isEmpty())
        assertEquals(SessionState.ACTIVE, store.all.single().state)
    }

    @Test
    fun `coverage checking releases what the session owned`() {
        val executor = RecordingExecutor()
        val routine = Fixtures.heroRoutine()
        val engine = engine(executor)
        engine.onTriggerEvent(routine, Fixtures.connect(), Fixtures.snapshot())

        engine.checkBluetoothCoverage(routines(routine), currentlyConnectedDeviceIds = emptySet())

        assertEquals(
            setOf(OwnedResource.FOCUS_TIMER, OwnedResource.DND_CONTRIBUTION),
            executor.released.toSet(),
        )
    }

    // ---------------------------------------------------------- rearming

    @Test
    fun `a new connection after a confirmed exit starts a new session`() {
        val routine = Fixtures.heroRoutine()
        val engine = engine()
        val first = assertIs<EngineResult.Started>(
            engine.onTriggerEvent(routine, Fixtures.connect(connectionSessionId = "conn-1"), Fixtures.snapshot()),
        ).session

        engine.onExitEvent(routine, Fixtures.disconnect(atMillis = clock.now))
        clock.advanceSeconds(25)
        engine.onGraceElapsed(routine, first.id)

        clock.advanceMinutes(30)
        val second = engine.onTriggerEvent(
            routine,
            Fixtures.connect(atMillis = clock.now, connectionSessionId = "conn-2"),
            Fixtures.snapshot(),
        )

        assertIs<EngineResult.Started>(second)
        assertEquals(2, store.all.size)
    }

    @Test
    fun `a cooldown suppresses an immediate restart`() {
        val routine = Fixtures.heroRoutine(
            rearmPolicy = RearmPolicy(reconnectGraceSeconds = 0, cooldownSeconds = 300),
        )
        val engine = engine()
        engine.onTriggerEvent(routine, Fixtures.connect(connectionSessionId = "conn-1"), Fixtures.snapshot())
        engine.onExitEvent(routine, Fixtures.disconnect(atMillis = clock.now))

        clock.advanceSeconds(30)
        val result = engine.onTriggerEvent(
            routine,
            Fixtures.connect(atMillis = clock.now, connectionSessionId = "conn-2"),
            Fixtures.snapshot(),
        )

        assertIs<EngineResult.Skipped>(result)
    }

    @Test
    fun `a skipped condition is explained rather than silently ignored`() {
        val result = engine().onTriggerEvent(
            Fixtures.heroRoutine(),
            Fixtures.connect(),
            Fixtures.snapshot(day = Fixtures.known(Day.SAT)),
        )

        val skipped = assertIs<EngineResult.Skipped>(result)
        assertTrue(skipped.reasons.any { it.detail == "Saturday is outside Monday–Friday." })
        assertTrue(store.all.isEmpty())
    }
}
