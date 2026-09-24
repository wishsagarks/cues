package com.cues.core.session

import com.cues.core.Fixtures
import com.cues.core.model.*
import com.cues.core.ports.ActionExecutor
import com.cues.core.ports.ActionOutcome
import com.cues.core.receipt.Receipts
import com.cues.core.store.JsonFileStore
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * An executor with no memory at all: everything it knows at release time, it
 * reads from the obligation and session it is handed. Constructing a second
 * instance is therefore an exact model of a process death between execute
 * and release — nothing the first one saw survives into the second.
 *
 * It releases the way plan §10.4 says `:app` must: the restore target comes
 * from the persisted [ActionArgs.UseUtility], and a record that lacks one is
 * [ActionState.COMPENSATION_FAILED], never a default success.
 */
private class StatelessUtilityExecutor : ActionExecutor {
    val restored = mutableListOf<Pair<UtilityId, UtilityState>>()

    override fun execute(actionId: ActionId, args: ActionArgs, sessionId: String): ActionOutcome =
        if (actionId == ActionId.USE_UTILITY) {
            ActionOutcome(ActionState.SUCCEEDED, acquired = OwnedResource.UTILITY_CONTRIBUTION, verification = Verification.STEPS_CONFIRMED)
        } else {
            ActionOutcome(ActionState.SUCCEEDED, acquired = com.cues.core.registry.ActionRegistry.definition(actionId)?.owns)
        }

    override fun release(resource: OwnedResource, sessionId: String): ActionOutcome =
        error("the engine must release through the obligation overload, not by resource alone")

    override fun release(obligation: CleanupObligation, session: Session): ActionOutcome {
        if (obligation.resource != OwnedResource.UTILITY_CONTRIBUTION) return ActionOutcome(ActionState.SUCCEEDED)
        val args = obligation.args as? ActionArgs.UseUtility
            ?: return ActionOutcome(ActionState.COMPENSATION_FAILED, "Can't tell what to restore.")
        val target = if (args.state == UtilityState.ON) UtilityState.OFF else UtilityState.ON
        restored += args.utilityId to target
        return ActionOutcome(ActionState.SUCCEEDED, verification = Verification.STEPS_CONFIRMED)
    }
}

class UtilityRestoreTest {

    private lateinit var root: File
    private val clock = FakeClock(Fixtures.NOW)

    @BeforeEach
    fun setUp() { root = createTempDirectory("utility-restore-test").toFile() }

    @AfterEach
    fun tearDown() { root.deleteRecursively() }

    private fun utilityCue(vararg uses: ActionArgs.UseUtility, version: Int = 1) =
        Fixtures.heroRoutine(conditions = emptyList()).copy(
            version = version,
            actions = uses.map { ActionSpec(ActionId.USE_UTILITY, it) },
        )

    private fun startWith(executor: ActionExecutor, routine: Routine): Session {
        val engine = SessionEngine(JsonFileStore(root), executor, clock)
        return assertIs<EngineResult.Started>(engine.onTriggerEvent(routine, Fixtures.connect(), Fixtures.snapshot())).session
    }

    @Test
    fun `after a normal start the persisted session alone says what to restore`() {
        val routine = utilityCue(ActionArgs.UseUtility(UtilityId.GAME_MODE, UtilityState.ON))
        val started = startWith(StatelessUtilityExecutor(), routine)

        // Read back through a fresh store: this is what is on disk, not in memory.
        val onDisk = assertNotNull(JsonFileStore(root).find(started.id))

        assertEquals(routine.version, onDisk.routineVersion)
        val action = onDisk.actions.single()
        assertEquals(ActionState.SUCCEEDED, action.state)
        assertEquals(Verification.STEPS_CONFIRMED, action.verification, "a macro result is assumed, not read back")
        val obligation = onDisk.obligations.single()
        assertEquals(OwnedResource.UTILITY_CONTRIBUTION, obligation.resource)
        assertEquals(ActionArgs.UseUtility(UtilityId.GAME_MODE, UtilityState.ON), obligation.args)
    }

    @Test
    fun `a process death between execute and release still restores the utility it turned on`() {
        val v1 = utilityCue(ActionArgs.UseUtility(UtilityId.GAME_MODE, UtilityState.ON))
        val started = startWith(StatelessUtilityExecutor(), v1)

        // The process dies. The user also edits the cue while it runs, which
        // replaces version 1 in the routine store — the arguments this session
        // actually ran with are no longer anywhere but the session itself.
        val v2 = utilityCue(ActionArgs.UseUtility(UtilityId.EYE_PROTECTION, UtilityState.ON), version = 2)
        val afterRestart = StatelessUtilityExecutor()
        val engine = SessionEngine(JsonFileStore(root), afterRestart, clock)
        val persisted = assertNotNull(JsonFileStore(root).find(started.id))

        val ended = engine.end(v2, persisted, EndReason.MANUAL_STOP)

        assertEquals(listOf(UtilityId.GAME_MODE to UtilityState.OFF), afterRestart.restored)
        assertEquals(SessionState.COMPLETED, ended.state)
        assertEquals(Verification.STEPS_CONFIRMED, ended.obligations.single().releaseVerification)
        val receipt = Receipts.forResult(v2, EngineResult.Ended(ended)).lines
        assertTrue(receipt.any { "assumed" in it }, "a restore Cues could not read back must say so: $receipt")
    }

    @Test
    fun `two utilities in one cue each restore their own`() {
        val routine = utilityCue(
            ActionArgs.UseUtility(UtilityId.GAME_MODE, UtilityState.ON),
            ActionArgs.UseUtility(UtilityId.EYE_PROTECTION, UtilityState.OFF),
        )
        val started = startWith(StatelessUtilityExecutor(), routine)
        val afterRestart = StatelessUtilityExecutor()

        SessionEngine(JsonFileStore(root), afterRestart, clock).end(routine, started, EndReason.MANUAL_STOP)

        assertEquals(
            setOf(UtilityId.GAME_MODE to UtilityState.OFF, UtilityId.EYE_PROTECTION to UtilityState.ON),
            afterRestart.restored.toSet(),
        )
    }

    @Test
    fun `an obligation recorded before args existed ends CLEANUP_PENDING, never COMPLETED`() {
        File(root, "sessions").mkdirs()
        File(root, "sessions/legacy-1.json").writeText(OLD_SHAPE_SESSION)
        val store = JsonFileStore(root)
        val legacy = assertNotNull(store.find("legacy-1"))

        val ended = SessionEngine(store, StatelessUtilityExecutor(), clock)
            .end(utilityCue(ActionArgs.UseUtility(UtilityId.GAME_MODE, UtilityState.ON)), legacy, EndReason.MANUAL_STOP)

        assertEquals(SessionState.CLEANUP_PENDING, ended.state, "not knowing what to restore is not a success")
        val obligation = ended.obligations.single()
        assertEquals(false, obligation.released)
        assertEquals("Can't tell what to restore.", obligation.failureDetail)
    }

    @Test
    fun `an old-shape session with none of the new fields still decodes, as NONE and null`() {
        File(root, "sessions").mkdirs()
        File(root, "sessions/legacy-1.json").writeText(OLD_SHAPE_SESSION)

        val legacy = assertNotNull(JsonFileStore(root).find("legacy-1"))

        assertEquals(Verification.NONE, legacy.actions.single().verification)
        assertNull(legacy.obligations.single().args)
        assertEquals(Verification.NONE, legacy.obligations.single().releaseVerification)
        assertTrue(File(root, "quarantine").listFiles().orEmpty().isEmpty(), "an old record is not corrupt")
    }

    @Test
    fun `an executor that never adopted the obligation overload releases exactly as before`() {
        // RecordingExecutor implements only release(resource, sessionId); the
        // interface default must route to it unchanged.
        val executor = RecordingExecutor()
        val routine = Fixtures.heroRoutine()
        val engine = SessionEngine(InMemorySessionStore(), executor, clock)
        val started = assertIs<EngineResult.Started>(engine.onTriggerEvent(routine, Fixtures.connect(), Fixtures.snapshot())).session

        val ended = engine.end(routine, started, EndReason.MANUAL_STOP)

        assertEquals(listOf(OwnedResource.FOCUS_TIMER, OwnedResource.DND_CONTRIBUTION), executor.released)
        assertEquals(SessionState.COMPLETED, ended.state)
        assertTrue(ended.obligations.all { it.releaseVerification == Verification.NONE })
    }

    private companion object {
        /**
         * A session exactly as a build before `verification`, `args` and
         * `releaseVerification` existed wrote it: a USE_UTILITY action that
         * succeeded and the obligation it acquired, with no restore intent.
         */
        val OLD_SHAPE_SESSION = """
            {
              "id": "legacy-1",
              "routineId": "routine-1",
              "routineVersion": 1,
              "startedAtMillis": ${Fixtures.NOW},
              "admissionKey": "routine-1:v1:BLUETOOTH_CONNECTED:conn-1",
              "observedInputs": {
                "nowMillis": ${Fixtures.NOW},
                "localDay": {
                  "type": "com.cues.core.model.ContextValue.Known",
                  "value": "MON",
                  "source": "SYSTEM_CLOCK",
                  "observedAtMillis": ${Fixtures.NOW}
                },
                "localTime": {
                  "type": "com.cues.core.model.ContextValue.Known",
                  "value": { "hour": 18, "minute": 30 },
                  "source": "SYSTEM_CLOCK",
                  "observedAtMillis": ${Fixtures.NOW}
                },
                "zoneId": "Asia/Kolkata"
              },
              "state": "ACTIVE",
              "actions": [
                { "actionId": "USE_UTILITY", "state": "SUCCEEDED" }
              ],
              "obligations": [
                { "resource": "UTILITY_CONTRIBUTION", "acquiredAtMillis": ${Fixtures.NOW} }
              ]
            }
        """.trimIndent()
    }
}
