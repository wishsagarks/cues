package com.cues.core.store

import com.cues.core.Fixtures
import com.cues.core.model.*
import com.cues.core.session.InMemorySessionStore
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val DAY = 86_400_000L

/** `SessionStore.recent` (a bounded window) and session pruning (bounded history). */
class SessionWindowTest {

    private lateinit var root: File
    private var now = Fixtures.NOW
    private lateinit var store: JsonFileStore

    @BeforeEach
    fun setUp() {
        root = createTempDirectory("session-window-test").toFile()
        now = Fixtures.NOW
        store = JsonFileStore(root, nowMillis = { now })
    }

    @AfterEach
    fun tearDown() { root.deleteRecursively() }

    private fun session(
        id: String,
        startedAt: Long,
        endedAt: Long? = null,
        state: SessionState = if (endedAt == null) SessionState.ACTIVE else SessionState.COMPLETED,
        obligations: List<CleanupObligation> = listOf(CleanupObligation(OwnedResource.FOCUS_TIMER, startedAt, released = true)),
    ) = Session(
        id = id,
        routineId = "routine-1",
        routineVersion = 1,
        startedAtMillis = startedAt,
        admissionKey = "key-$id",
        observedInputs = Fixtures.snapshot(nowMillis = startedAt),
        state = state,
        obligations = obligations,
        endedAtMillis = endedAt,
        endReason = endedAt?.let { EndReason.MANUAL_STOP },
    )

    /** Saves [session] as the store would have at [at], so its file carries that stamp. */
    private fun saveAt(at: Long, session: Session) {
        now = at
        store.save(session)
    }

    // ------------------------------------------------------------- recent

    @Test
    fun `recent returns sessions that ended in the window or are still running, and nothing older`() {
        saveAt(Fixtures.NOW - 10 * DAY, session("old", Fixtures.NOW - 10 * DAY - 3_600_000, Fixtures.NOW - 10 * DAY))
        saveAt(Fixtures.NOW - DAY, session("live", Fixtures.NOW - DAY))
        saveAt(Fixtures.NOW - 3_600_000, session("fresh", Fixtures.NOW - 7_200_000, Fixtures.NOW - 3_600_000))
        now = Fixtures.NOW

        val ids = store.recent(Fixtures.NOW - 7 * DAY).map { it.id }.toSet()

        assertEquals(setOf("live", "fresh"), ids)
    }

    @Test
    fun `a session that began before the window but ended inside it is in the window`() {
        val straddling = session("straddle", startedAt = Fixtures.NOW - 7 * DAY - 600_000, endedAt = Fixtures.NOW - 7 * DAY + 600_000)
        saveAt(straddling.endedAtMillis!!, straddling)

        assertEquals(listOf("straddle"), store.recent(Fixtures.NOW - 7 * DAY).map { it.id })
    }

    @Test
    fun `recent never decodes a file last written before the window`() {
        // A file too old to be in the window, and unreadable. If recent()
        // decoded it, readOrQuarantine would move it to quarantine/.
        val stale = File(root, "sessions/garbage.json").apply { writeText("{ not json") }
        stale.setLastModified(Fixtures.NOW - 20 * DAY)

        assertTrue(store.recent(Fixtures.NOW - 7 * DAY).isEmpty())
        assertTrue(stale.exists(), "an out-of-window file must be skipped by its stamp, not opened")
        assertTrue(File(root, "quarantine").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `the in-memory fake follows the same window rule`() {
        val fake = InMemorySessionStore()
        fake.save(session("old", Fixtures.NOW - 10 * DAY, Fixtures.NOW - 9 * DAY))
        fake.save(session("live", Fixtures.NOW - 20 * DAY))
        fake.save(session("fresh", Fixtures.NOW - DAY, Fixtures.NOW - DAY + 60_000))

        assertEquals(setOf("live", "fresh"), fake.recent(Fixtures.NOW - 7 * DAY).map { it.id }.toSet())
    }

    // ------------------------------------------------------------ pruning

    @Test
    fun `a released session that ended over thirty days ago is pruned on the next ended save`() {
        saveAt(Fixtures.NOW - 31 * DAY, session("ancient", Fixtures.NOW - 31 * DAY - 60_000, Fixtures.NOW - 31 * DAY))
        saveAt(Fixtures.NOW - 29 * DAY, session("recentish", Fixtures.NOW - 29 * DAY - 60_000, Fixtures.NOW - 29 * DAY))

        saveAt(Fixtures.NOW, session("today", Fixtures.NOW - 60_000, Fixtures.NOW))

        assertNull(store.find("ancient"), "finished, released and past retention: gone")
        assertNotNull(store.find("recentish"), "inside the thirty days: kept")
        assertNotNull(store.find("today"))
    }

    @Test
    fun `a session with any unreleased obligation is never pruned, however old`() {
        val owing = session(
            "owing",
            startedAt = Fixtures.NOW - 90 * DAY,
            endedAt = Fixtures.NOW - 90 * DAY + 60_000,
            state = SessionState.CLEANUP_PENDING,
            obligations = listOf(
                CleanupObligation(OwnedResource.FOCUS_TIMER, Fixtures.NOW - 90 * DAY, released = true),
                CleanupObligation(OwnedResource.UTILITY_CONTRIBUTION, Fixtures.NOW - 90 * DAY, released = false, failureDetail = "Can't tell what to restore"),
            ),
        )
        saveAt(owing.endedAtMillis!!, owing)
        now = Fixtures.NOW

        assertEquals(0, store.pruneSessions())
        assertEquals(owing, store.find("owing"))
    }

    @Test
    fun `a session with no recorded end is never pruned, however old`() {
        // What a crash between persisting STARTING and the first effect leaves behind.
        val orphan = session("orphan", Fixtures.NOW - 60 * DAY, state = SessionState.STARTING)
        saveAt(orphan.startedAtMillis, orphan)
        now = Fixtures.NOW

        assertEquals(0, store.pruneSessions())
        assertNotNull(store.find("orphan"))
    }

    @Test
    fun `a completed session whose obligations were somehow left unreleased is kept`() {
        // COMPLETED with an unreleased obligation should not exist, but if a
        // bug ever produced one, pruning must not be what hides it.
        val odd = session(
            "odd", Fixtures.NOW - 45 * DAY, Fixtures.NOW - 45 * DAY + 1,
            obligations = listOf(CleanupObligation(OwnedResource.DND_CONTRIBUTION, Fixtures.NOW - 45 * DAY, released = false)),
        )
        saveAt(odd.endedAtMillis!!, odd)
        now = Fixtures.NOW

        assertEquals(0, store.pruneSessions())
        assertNotNull(store.find("odd"))
    }
}
