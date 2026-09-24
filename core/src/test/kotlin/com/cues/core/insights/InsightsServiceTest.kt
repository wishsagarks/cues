package com.cues.core.insights

import com.cues.core.CueService
import com.cues.core.Fixtures
import com.cues.core.drafting.GrammarParser
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** `CueService.insights` end to end: real store, real receipts, one report. */
class InsightsServiceTest {

    private lateinit var root: File
    private val clock = FakeClock(Fixtures.NOW)
    private lateinit var store: JsonFileStore
    private lateinit var service: CueService

    @BeforeEach
    fun setUp() {
        root = createTempDirectory("insights-service-test").toFile()
        store = JsonFileStore(root, nowMillis = { clock.now })
        service = CueService(
            routines = store, sessions = store, receipts = store, executor = RecordingExecutor(),
            clock = clock, capabilities = CapabilityProvider { Capability.entries.toSet() },
            drafter = GrammarParser(), zoneId = { ZoneId.of("Asia/Kolkata") },
            usageLedger = store, receiptLog = store,
        )
        store.save(Fixtures.heroRoutine(conditions = listOf(Condition.ChargingState(true))))
    }

    @AfterEach
    fun tearDown() { root.deleteRecursively() }

    @Test
    fun `a fresh install has an empty report`() {
        assertFalse(service.insights(InsightsWindow.LAST_7_DAYS).hasData)
    }

    @Test
    fun `a run and an unreadable skip show up as counted, timed and explained`() {
        val started = service.onDeviceEvent(Fixtures.connect(), charging = Fixtures.known(true))
            .filterIsInstance<EngineResult.Started>().single()
        clock.advanceMinutes(12)
        service.onManualStop(started.session.id)
        clock.advanceMinutes(60)
        service.onDeviceEvent(
            Fixtures.connect(atMillis = clock.now, connectionSessionId = "conn-2"),
            charging = ContextValue.Unknown(UnknownReason.PERMISSION_DENIED, ContextSource.BATTERY_MANAGER),
        )

        val report = service.insights(InsightsWindow.LAST_7_DAYS)

        val counts = assertNotNull(report.counts)
        assertEquals(1, counts.started)
        assertEquals(1, counts.skipped)
        assertEquals(12 * 60_000L, report.timeInCues?.totalMillis)
        val unknown = report.skipReasons.single()
        assertEquals(SkipFamily.UNKNOWN, unknown.family)
        assertEquals(ContextSource.BATTERY_MANAGER, unknown.unreadable.single().source)
        assertEquals(2, report.cleanup?.released, "the hero cue's timer and quiet rule")
        assertEquals(false, report.ledgerEnabled, "learning was never switched on")
    }
}
