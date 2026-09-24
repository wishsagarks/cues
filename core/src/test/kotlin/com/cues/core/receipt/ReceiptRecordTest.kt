package com.cues.core.receipt

import com.cues.core.CueService
import com.cues.core.Fixtures
import com.cues.core.drafting.GrammarParser
import com.cues.core.eval.ReasonCode
import com.cues.core.eval.Truth
import com.cues.core.model.*
import com.cues.core.ports.CapabilityProvider
import com.cues.core.session.EngineResult
import com.cues.core.session.FakeClock
import com.cues.core.session.RecordingExecutor
import com.cues.core.store.JsonFileStore
import java.io.File
import java.time.ZoneId
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ReceiptRecordTest {

    private lateinit var root: File
    private lateinit var store: JsonFileStore
    private lateinit var clock: FakeClock
    private lateinit var service: CueService

    @BeforeEach
    fun setUp() {
        root = createTempDirectory("receipt-record-test").toFile()
        clock = FakeClock(Fixtures.NOW)
        store = JsonFileStore(root, nowMillis = { clock.now })
        service = CueService(
            routines = store,
            sessions = store,
            receipts = store,
            executor = RecordingExecutor(),
            clock = clock,
            capabilities = CapabilityProvider { Capability.entries.toSet() },
            drafter = GrammarParser(),
            zoneId = { ZoneId.of("Asia/Kolkata") },
            receiptLog = store,
        )
    }

    @AfterEach
    fun tearDown() { root.deleteRecursively() }

    /** An armed cue with one charging gate and nothing else, so the verdict is easy to steer. */
    private fun chargingGatedCue() = Fixtures.heroRoutine(conditions = listOf(Condition.ChargingState(true)))
        .also { store.save(it) }

    /**
     * The text log names each file `<wall-clock ms>-<key>.txt`, so two
     * receipts for one session inside the same millisecond share a name and
     * the second overwrites the first. That never happens at human speed, but
     * it does in a test that ends a session a microsecond after starting it.
     */
    private fun textLogTick() = Thread.sleep(3)

    /** Every structured record has a text receipt filed under the same key with exactly its lines. */
    private fun assertPairedWithText(records: List<ReceiptRecord>) {
        val texts = store.receipts().map { it.sessionId to it.text }.toMutableList()
        records.forEach { record ->
            val pair = record.key to record.textLines.joinToString("\n")
            assertTrue(texts.remove(pair), "no text receipt matches ${record.kind} for ${record.key}")
        }
        assertTrue(texts.isEmpty(), "a text receipt has no structured twin: $texts")
    }

    @Test
    fun `a start and a manual stop each produce a record that says what its text says`() {
        chargingGatedCue()

        val started = service.onDeviceEvent(Fixtures.connect(), charging = Fixtures.known(true))
            .filterIsInstance<EngineResult.Started>().single()
        clock.advanceMinutes(10)
        textLogTick()
        service.onManualStop(started.session.id)

        val records = store.receiptRecords()
        assertEquals(listOf(ReceiptKind.STARTED, ReceiptKind.ENDED), records.map { it.kind })
        assertPairedWithText(records)

        val start = records[0]
        assertEquals(started.session.id, start.sessionId)
        assertEquals(EventProvenance.PHYSICAL, start.provenance)
        assertEquals(listOf(ReasonCode.TRIGGER_MATCHED, ReasonCode.CHARGING_AS_REQUIRED), start.reasons.map { it.code })
        assertEquals(1, start.routineVersion)

        val end = records[1]
        assertEquals(EventProvenance.MANUAL, end.provenance, "a stop button is the user's hand, not a physical event")
        assertEquals(EndReason.MANUAL_STOP, end.endReason)
        assertEquals(SessionState.COMPLETED, end.sessionState)
        assertTrue(end.obligations.isNotEmpty() && end.obligations.all { it.released })
        assertEquals(Fixtures.NOW + 10 * 60_000, end.atMillis, "stamped by the service clock, not the wall clock")
    }

    @Test
    fun `a skip on an unreadable signal keeps UNKNOWN and the unreadable input itself`() {
        chargingGatedCue()
        val unreadable = ContextValue.Unknown(UnknownReason.PERMISSION_DENIED, ContextSource.BATTERY_MANAGER)

        service.onDeviceEvent(Fixtures.connect(), charging = unreadable)

        val skip = store.receiptRecords().single()
        assertEquals(ReceiptKind.SKIPPED, skip.kind)
        assertNull(skip.sessionId, "a skip never had a session")
        assertTrue(skip.key.startsWith("skip-"))
        val gate = skip.reasons.single { it.code == ReasonCode.CHARGING_UNKNOWN }
        assertEquals(Truth.UNKNOWN, gate.truth, "UNKNOWN must survive into the record, not become NO_MATCH")
        assertEquals(unreadable, assertNotNull(skip.observedInputs).charging)
        assertPairedWithText(listOf(skip))
    }

    @Test
    fun `a record round-trips through the store including an Unknown observation`() {
        val record = ReceiptRecords.forResult(
            routine = Fixtures.heroRoutine(),
            result = EngineResult.Skipped(emptyList()),
            key = "skip-routine-1-1",
            atMillis = Fixtures.NOW,
            provenance = EventProvenance.REHEARSAL,
            observedInputs = Fixtures.snapshot(charging = Fixtures.unknown(UnknownReason.STALE)),
        )

        store.append(record)

        assertEquals(listOf(record), store.receiptRecords())
    }

    @Test
    fun `the structured log keeps the same cap as the text log, oldest dropped first`() {
        val capped = JsonFileStore(File(root, "capped"), maxReceiptFiles = 3)
        (1..5).forEach { i ->
            capped.append(
                ReceiptRecords.forResult(
                    Fixtures.heroRoutine(), EngineResult.Ignored, "ignored-$i", Fixtures.NOW + i, provenance = null,
                ),
            )
        }

        assertEquals(listOf("ignored-3", "ignored-4", "ignored-5"), capped.receiptRecords().map { it.key })
        assertEquals(listOf("ignored-5"), capped.receiptRecords(sinceMillis = Fixtures.NOW + 5).map { it.key })
    }

    @Test
    fun `a service with no receipt log still files every text receipt`() {
        val textOnly = CueService(
            routines = store, sessions = store, receipts = store, executor = RecordingExecutor(),
            clock = clock, capabilities = CapabilityProvider { Capability.entries.toSet() },
            drafter = GrammarParser(), zoneId = { ZoneId.of("Asia/Kolkata") },
        )
        chargingGatedCue()

        textOnly.onDeviceEvent(Fixtures.connect(), charging = Fixtures.known(true))

        assertEquals(1, store.receipts().size)
        assertTrue(store.receiptRecords().isEmpty())
    }

    @Test
    fun `a retried pending step is recorded as RESUMED with the same text`() {
        val attention = com.cues.core.session.ToggleAttention(present = false)
        val waiting = CueService(
            routines = store, sessions = store, receipts = store, executor = RecordingExecutor(),
            clock = clock, capabilities = CapabilityProvider { Capability.entries.toSet() },
            drafter = GrammarParser(), zoneId = { ZoneId.of("Asia/Kolkata") },
            attention = attention, receiptLog = store,
        )
        store.save(
            Fixtures.heroRoutine(conditions = emptyList()).copy(
                actions = listOf(ActionSpec(ActionId.OPEN_LINK, ActionArgs.OpenLink("https://example.com"))),
            ),
        )
        assertIs<EngineResult.Started>(waiting.onDeviceEvent(Fixtures.connect()).single())

        attention.present = true
        textLogTick()
        waiting.retryPendingActions()

        val records = store.receiptRecords()
        assertEquals(listOf(ReceiptKind.STARTED, ReceiptKind.RESUMED), records.map { it.kind })
        assertEquals(ActionState.SUCCEEDED, records[1].actions.single().state)
        assertPairedWithText(records)
    }
}
