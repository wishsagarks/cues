package com.cues.core

import com.cues.core.approval.ArmResult
import com.cues.core.approval.DeleteResult
import com.cues.core.drafting.DraftResult
import com.cues.core.model.DraftSourceId
import com.cues.core.drafting.GrammarParser
import com.cues.core.drafting.PairedDevice
import com.cues.core.model.*
import com.cues.core.ports.CapabilityProvider
import com.cues.core.session.EngineResult
import com.cues.core.session.FakeClock
import com.cues.core.session.RecordingExecutor
import com.cues.core.session.ToggleAttention
import com.cues.core.store.JsonFileStore
import java.io.File
import java.time.ZoneId
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val HERO = "When my earbuds connect after 6 PM on weekdays, start a 45-minute " +
    "focus timer and quiet notifications. End it if I disconnect."

private val PAIRED = listOf(PairedDevice(Fixtures.EARBUDS_ID, Fixtures.EARBUDS_LABEL, setOf("earbuds", "buds")))

private val ALL_GRANTED = CapabilityProvider {
    setOf(
        Capability.BLUETOOTH_CONNECT, Capability.NOTIFICATION_POLICY_ACCESS,
        Capability.POST_NOTIFICATIONS, Capability.EXACT_ALARM, Capability.BATTERY_STATE,
    )
}

class CueServiceTest {

    private lateinit var root: File
    private lateinit var store: JsonFileStore
    private lateinit var executor: RecordingExecutor
    private lateinit var clock: FakeClock
    private lateinit var service: CueService

    @BeforeEach
    fun setUp() {
        root = createTempDirectory("cue-service-test").toFile()
        store = JsonFileStore(root)
        executor = RecordingExecutor()
        clock = FakeClock(Fixtures.NOW)
        service = CueService(
            routines = store,
            sessions = store,
            receipts = store,
            executor = executor,
            clock = clock,
            capabilities = ALL_GRANTED,
            drafter = GrammarParser(PAIRED),
            zoneId = { ZoneId.of("Asia/Kolkata") },
        )
    }

    @AfterEach
    fun tearDown() { root.deleteRecursively() }

    @Test
    fun `draft stamps the routine with the drafter that produced it`() = runTest {
        val result = assertIs<DraftResult.Drafted>(service.draft(HERO))

        assertEquals(DraftSourceId.GRAMMAR_PARSER, result.source)
        assertEquals(DraftSourceId.GRAMMAR_PARSER, result.routine.draftedBy)
    }

    @Test
    fun `a non-parser drafter's own clause accounting is never trusted`() = runTest {
        // A model that claims (falsely) it accounted for everything, on a
        // routine that never mentions the "text Mum" clause at all. Only the
        // deterministic parser's own span data may be trusted; anything else
        // must be recomputed against the real request text.
        val dishonestModel = object : com.cues.core.drafting.RoutineDrafter {
            override val id = DraftSourceId.ON_DEVICE_LLM
            override suspend fun draft(text: String) = DraftResult.Drafted(
                source = DraftSourceId.ON_DEVICE_LLM,
                routine = Fixtures.heroRoutine(),
                clauses = listOf(
                    com.cues.core.drafting.ClauseSpan(text, text.indices, com.cues.core.drafting.ClauseKind.MAPPED),
                ),
            )
        }
        val serviceWithLyingModel = CueService(
            routines = store, sessions = store, receipts = store, executor = executor,
            clock = clock, capabilities = ALL_GRANTED, drafter = dishonestModel,
            zoneId = { ZoneId.of("Asia/Kolkata") },
        )

        val result = assertIs<DraftResult.Drafted>(serviceWithLyingModel.draft("when earbuds connect text Mum"))

        assertTrue(
            result.routine.unaccountedClauses.isNotEmpty(),
            "a model-sourced draft's self-reported 'nothing unaccounted' must be recomputed, not trusted",
        )
    }

    @Test
    fun `the full lifecycle - draft, approve, arm, connect, end - works through the facade`() = runTest {
        val drafted = assertIs<DraftResult.Drafted>(service.draft(HERO)).routine

        val armed = assertIs<ArmResult.Ok<Routine>>(service.approveAndArm(drafted)).routine
        assertEquals(RoutineStatus.ARMED, armed.status)
        assertEquals(listOf(armed.id), service.list().map { it.id }, "arming must persist")

        val results = service.onDeviceEvent(
            event = TriggerEvent(
                EventKind.BLUETOOTH_CONNECTED,
                atMillis = millisAt(18, 30),
                deviceId = Fixtures.EARBUDS_ID,
                connectionSessionId = "conn-1",
            ),
            charging = Fixtures.known(true),
        )

        val started = results.filterIsInstance<EngineResult.Started>().single()
        assertEquals(SessionState.ACTIVE, started.session.state)

        clock.advanceMinutes(45)
        val ended = service.onDeadline(started.session.id)
        assertEquals(SessionState.COMPLETED, assertIs<EngineResult.Ended>(ended).session.state)
    }

    @Test
    fun `a manual run only starts the routine it names, never every armed manual cue`() = runTest {
        val targeted = Fixtures.heroRoutine(conditions = emptyList()).copy(
            id = "manual-a", trigger = Trigger.Manual, endConditions = listOf(EndCondition.ManualStop),
        )
        val other = targeted.copy(id = "manual-b")
        store.save(targeted)
        store.save(other)

        val results = service.onDeviceEvent(TriggerEvent(EventKind.MANUAL_RUN, Fixtures.NOW, routineId = "manual-a"))

        val started = results.filterIsInstance<EngineResult.Started>()
        assertEquals(1, started.size, "CL-28: an untargeted manual run must not start every armed manual cue")
        assertEquals("manual-a", started.single().session.routineId)
        assertTrue(store.activeFor("manual-b").isEmpty(), "the un-named routine must not have started at all")
    }

    @Test
    fun `an unscoped manual run starts nothing rather than guessing`() = runTest {
        val routine = Fixtures.heroRoutine(conditions = emptyList()).copy(
            id = "manual-a", trigger = Trigger.Manual, endConditions = listOf(EndCondition.ManualStop),
        )
        store.save(routine)

        val results = service.onDeviceEvent(TriggerEvent(EventKind.MANUAL_RUN, Fixtures.NOW))

        assertTrue(results.isEmpty(), "no routineId names no target, so nothing may start")
    }

    @Test
    fun `a NEEDS_USER action waits, and CueService retryPendingActions runs it once someone is present`() = runTest {
        val attention = ToggleAttention(present = false)
        val serviceWithAttention = CueService(
            routines = store, sessions = store, receipts = store, executor = executor,
            clock = clock, capabilities = ALL_GRANTED, drafter = GrammarParser(PAIRED),
            zoneId = { ZoneId.of("Asia/Kolkata") }, attention = attention,
        )
        val routine = Fixtures.heroRoutine(conditions = emptyList()).copy(
            actions = listOf(ActionSpec(ActionId.OPEN_APP, ActionArgs.OpenApp("com.example.app", "Example"))),
            endConditions = listOf(EndCondition.ManualStop),
        )
        store.save(routine)

        val started = assertIs<EngineResult.Started>(
            serviceWithAttention.onDeviceEvent(Fixtures.connect()).single(),
        ).session
        assertEquals(SessionState.PARTIAL, started.state)
        assertEquals(ActionState.PENDING, started.actions.single().state)
        assertTrue(executor.executed.isEmpty(), "a PENDING action must never be attempted while nobody is present")

        // Nothing pending yet to retry — must not falsely report progress.
        assertTrue(serviceWithAttention.retryPendingActions().isEmpty())

        attention.present = true
        val retried = serviceWithAttention.retryPendingActions()

        assertEquals(listOf(ActionId.OPEN_APP), executor.executed)
        val resumedSession = retried.single()
        assertEquals(ActionState.SUCCEEDED, resumedSession.actions.single().state)
        assertEquals(SessionState.ACTIVE, resumedSession.state)

        // A second call after everything already ran must be a no-op, not a re-execution.
        assertTrue(serviceWithAttention.retryPendingActions().isEmpty())
        assertEquals(1, executor.executed.size)
    }

    @Test
    fun `acceptSuggestion applies the structured operation and leaves the routine unapproved`() = runTest {
        val drafted = assertIs<DraftResult.Drafted>(service.draft(HERO)).routine
        val armed = assertIs<ArmResult.Ok<Routine>>(service.approveAndArm(drafted)).routine

        val suggestion = com.cues.core.coach.Suggestion(
            "early-stop:${armed.id}", com.cues.core.coach.SuggestionKind.SHORTER_DURATION,
            listOf(com.cues.core.coach.EvidenceLine("Stopped early 3 of 5 times.", 3)),
            "make ${armed.id} 20 minutes",
            routineId = armed.id,
            operation = com.cues.core.assistant.RefineOperation.SetDuration(20),
        )

        val refined = service.acceptSuggestion(suggestion)

        assertNotNull(refined)
        assertEquals(20, (refined.actions.single { it.actionId == ActionId.START_FOCUS_TIMER }.args as ActionArgs.FocusTimer).durationMinutes)
        assertEquals(RoutineStatus.REVIEWABLE, refined.status, "an accepted edit needs review again, exactly like any other refinement")
        assertNull(refined.approvedDigest, "accepting must clear approval, the same as Refiner.apply always does")
        // Nothing is persisted or armed on the caller's behalf — same contract as draft().
        assertEquals(RoutineStatus.ARMED, service.list().single { it.id == armed.id }.status)
    }

    @Test
    fun `acceptSuggestion refuses rather than guessing when there is nothing to act on`() = runTest {
        val drafted = assertIs<DraftResult.Drafted>(service.draft(HERO)).routine
        val armed = assertIs<ArmResult.Ok<Routine>>(service.approveAndArm(drafted)).routine

        val noOperation = com.cues.core.coach.Suggestion(
            "unknown:BLUETOOTH_CONNECT", com.cues.core.coach.SuggestionKind.FIX_PERMISSION,
            listOf(com.cues.core.coach.EvidenceLine("unreadable 2 times", 2)), "check access",
        )
        assertNull(service.acceptSuggestion(noOperation), "FIX_PERMISSION names no routine to edit")

        val deletedRoutine = com.cues.core.coach.Suggestion(
            "early-stop:gone", com.cues.core.coach.SuggestionKind.SHORTER_DURATION,
            listOf(com.cues.core.coach.EvidenceLine("x", 1)), "make gone 20 minutes",
            routineId = "gone", operation = com.cues.core.assistant.RefineOperation.SetDuration(20),
        )
        assertNull(service.acceptSuggestion(deletedRoutine), "a routine id that no longer exists must not be guessed at")
    }

    @Test
    fun `arming without a required capability is refused and names it`() = runTest {
        val drafted = assertIs<DraftResult.Drafted>(service.draft(HERO)).routine
        val limited = CueService(
            store, store, store, executor, clock,
            capabilities = CapabilityProvider { setOf(Capability.BLUETOOTH_CONNECT) },
            drafter = GrammarParser(PAIRED),
        )

        val result = assertIs<ArmResult.MissingCapabilities>(limited.approveAndArm(drafted))

        assertTrue(Capability.EXACT_ALARM in result.missing)
        // The approved-but-not-armed version is still persisted, so granting
        // the permission and retrying does not require re-approval.
        assertEquals(RoutineStatus.REVIEWABLE, limited.list().single().status)
    }

    @Test
    fun `an irrelevant device connecting produces no receipts`() = runTest {
        val drafted = assertIs<DraftResult.Drafted>(service.draft(HERO)).routine
        service.approveAndArm(drafted)

        val results = service.onDeviceEvent(
            TriggerEvent(EventKind.BLUETOOTH_CONNECTED, clock.now, deviceId = "unrelated-device"),
        )

        assertTrue(results.isEmpty(), "a mismatched device must not even surface as a skip")
        assertTrue(store.receiptFiles().isEmpty())
    }

    @Test
    fun `a connection outside the time window is a meaningful skip with a receipt`() = runTest {
        val drafted = assertIs<DraftResult.Drafted>(service.draft(HERO)).routine
        val armed = assertIs<ArmResult.Ok<Routine>>(service.approveAndArm(drafted)).routine

        val results = service.onDeviceEvent(
            TriggerEvent(EventKind.BLUETOOTH_CONNECTED, millisAt(9, 0), deviceId = Fixtures.EARBUDS_ID),
        )

        val skipped = assertIs<EngineResult.Skipped>(results.single())
        assertTrue(skipped.reasons.any { it.detail.contains("outside") })
        assertTrue(store.receiptFiles().isNotEmpty(), "a real skip reason is worth a receipt")
    }

    @Test
    fun `pause then resume round-trips through the store`() = runTest {
        val drafted = assertIs<DraftResult.Drafted>(service.draft(HERO)).routine
        val armed = assertIs<ArmResult.Ok<Routine>>(service.approveAndArm(drafted)).routine

        val paused = assertNotNull(service.pause(armed.id))
        assertEquals(RoutineStatus.PAUSED, paused.status)
        assertEquals(RoutineStatus.PAUSED, store.findRoutine(armed.id)?.status)

        val resumed = assertIs<ArmResult.Ok<Routine>>(service.resume(armed.id))
        assertEquals(RoutineStatus.ARMED, resumed.routine.status)
    }

    @Test
    fun `delete is refused while a session owes a cleanup, then succeeds once resolved`() = runTest {
        val drafted = assertIs<DraftResult.Drafted>(service.draft(HERO)).routine
        val armed = assertIs<ArmResult.Ok<Routine>>(service.approveAndArm(drafted)).routine

        service.onDeviceEvent(
            TriggerEvent(
                EventKind.BLUETOOTH_CONNECTED, millisAt(18, 30),
                deviceId = Fixtures.EARBUDS_ID, connectionSessionId = "conn-1",
            ),
            charging = Fixtures.known(true),
        )

        assertIs<DeleteResult.Blocked>(service.delete(armed.id))

        val session = store.activeFor(armed.id).single()
        service.onManualStop(session.id)

        assertEquals(DeleteResult.Ok, service.delete(armed.id))
        assertNull(store.findRoutine(armed.id))
    }

    @Test
    fun `onBoot reconciles an expired session without replaying its actions`() = runTest {
        val drafted = assertIs<DraftResult.Drafted>(service.draft(HERO)).routine
        val armed = assertIs<ArmResult.Ok<Routine>>(service.approveAndArm(drafted)).routine

        service.onDeviceEvent(
            TriggerEvent(
                EventKind.BLUETOOTH_CONNECTED, millisAt(18, 30),
                deviceId = Fixtures.EARBUDS_ID, connectionSessionId = "conn-1",
            ),
            charging = Fixtures.known(true),
        )
        val actionsBefore = executor.executed.size

        clock.advanceMinutes(120)
        val reconciled = service.onBoot()

        assertEquals(1, reconciled.size)
        assertEquals(EndReason.DEADLINE_REACHED, reconciled.single().endReason)
        assertEquals(actionsBefore, executor.executed.size, "reconciliation must not replay actions")
    }

    @Test
    fun `diagnostics honestly reports no model when none is configured`() {
        // No ModelAvailabilityProbe was wired for this fixture's CueService —
        // NOT_INSTALLED is the honest default, never a claim that a model is
        // ready just because a drafter happens to be present (CLEANUP.md CL-18).
        assertEquals(com.cues.core.ports.ModelAvailability.NOT_INSTALLED, service.diagnostics().setup.model)
        assertNull(service.diagnostics().lastTrace, "a plain grammar-parser draft carries no trace")
    }

    private fun millisAt(hour: Int, minute: Int): Long {
        val base = java.time.ZonedDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(Fixtures.NOW), ZoneId.of("Asia/Kolkata"),
        )
        return base.withHour(hour).withMinute(minute).withSecond(0).withNano(0).toInstant().toEpochMilli()
    }
}
