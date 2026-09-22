package com.cues.core.store

import com.cues.core.Fixtures
import com.cues.core.model.*
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JsonFileStoreTest {

    private lateinit var root: File
    private lateinit var store: JsonFileStore

    @BeforeEach
    fun setUp() {
        root = createTempDirectory("cues-store-test").toFile()
        store = JsonFileStore(root)
    }

    @AfterEach
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `a routine round-trips through save and find`() {
        val routine = Fixtures.heroRoutine()
        store.save(routine)

        assertEquals(routine, store.findRoutine(routine.id))
    }

    @Test
    fun `a session round-trips including an Unknown context value`() {
        val session = Session(
            id = "session-1",
            routineId = "routine-1",
            routineVersion = 1,
            startedAtMillis = Fixtures.NOW,
            admissionKey = "key",
            observedInputs = Fixtures.snapshot(charging = Fixtures.unknown(UnknownReason.STALE)),
            state = SessionState.ACTIVE,
        )

        store.save(session)
        val loaded = store.find(session.id)

        assertEquals(session, loaded)
        assertEquals(
            ContextValue.Unknown(UnknownReason.STALE, ContextSource.BATTERY_MANAGER),
            (loaded as Session).observedInputs.charging,
            "an Unknown reading must not silently round-trip as something else",
        )
    }

    @Test
    fun `armed only returns armed routines`() {
        store.save(Fixtures.heroRoutine().copy(id = "r-draft", status = RoutineStatus.DRAFT))
        store.save(Fixtures.heroRoutine().copy(id = "r-armed", status = RoutineStatus.ARMED))
        store.save(Fixtures.heroRoutine().copy(id = "r-paused", status = RoutineStatus.PAUSED))

        assertEquals(listOf("r-armed"), store.armed().map { it.id })
    }

    @Test
    fun `delete removes a routine`() {
        val routine = Fixtures.heroRoutine()
        store.save(routine)
        store.delete(routine.id)

        assertNull(store.findRoutine(routine.id))
    }

    @Test
    fun `a truncated file is quarantined and the rest of the store still loads`() {
        store.save(Fixtures.heroRoutine().copy(id = "good-1"))
        store.save(Fixtures.heroRoutine().copy(id = "good-2"))

        // Simulates a write that was killed halfway through: valid JSON became
        // a truncated fragment.
        File(root, "routines/good-1.json").writeText("{\"id\": \"good-1\", \"vers")

        val loaded = store.all()

        assertEquals(listOf("good-2"), loaded.map { it.id })
        assertTrue(File(root, "quarantine").listFiles()?.isNotEmpty() == true, "the bad file should be quarantined")
        assertTrue(File(root, "routines/good-1.json").exists().not(), "the bad file is moved out, not left in place")
    }

    @Test
    fun `writes are atomic - no tmp file survives a successful save`() {
        store.save(Fixtures.heroRoutine())

        val leftoverTmp = File(root, "routines").listFiles()?.filter { it.extension == "tmp" }
        assertTrue(leftoverTmp.isNullOrEmpty(), "a successful save must not leave a .tmp file behind")
    }

    @Test
    fun `receipts beyond the retention limit are pruned`() {
        val bounded = JsonFileStore(root, maxReceiptFiles = 3)
        repeat(5) { i -> bounded.record("session-$i", listOf("line $i")) }

        assertEquals(3, bounded.receiptFiles().size, "only the most recent receipts should remain")
    }

    @Test
    fun `receipts are kept oldest first`() {
        val bounded = JsonFileStore(root, maxReceiptFiles = 10)
        bounded.record("s1", listOf("first"))
        Thread.sleep(2)
        bounded.record("s2", listOf("second"))

        val files = bounded.receiptFiles()
        assertTrue(files.first().name.contains("s1"))
        assertTrue(files.last().name.contains("s2"))
    }

    @Test
    fun `unfinished sessions exclude completed and cancelled`() {
        val base = Session(
            id = "s", routineId = "r", routineVersion = 1, startedAtMillis = Fixtures.NOW,
            admissionKey = "k", observedInputs = Fixtures.snapshot(), state = SessionState.ACTIVE,
        )
        store.save(base.copy(id = "active"))
        store.save(base.copy(id = "completed", state = SessionState.COMPLETED))
        store.save(base.copy(id = "cancelled", state = SessionState.CANCELLED))
        store.save(base.copy(id = "cleanup-pending", state = SessionState.CLEANUP_PENDING))

        assertEquals(setOf("active", "cleanup-pending"), store.allUnfinished().map { it.id }.toSet())
    }
}
