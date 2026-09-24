package com.cues.core.insights

import com.cues.core.Fixtures
import com.cues.core.coach.LedgerEvent
import com.cues.core.context.Remedy
import com.cues.core.eval.Reason
import com.cues.core.eval.ReasonCode
import com.cues.core.eval.Truth
import com.cues.core.model.*
import com.cues.core.receipt.ReceiptKind
import com.cues.core.receipt.ReceiptRecord
import com.cues.core.session.FakeClock
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

private const val MIN = 60_000L
private const val DAY = 86_400_000L

class InsightsTest {

    private val now = Fixtures.NOW
    private val clock = FakeClock(now)
    private val week = InsightsWindow.LAST_7_DAYS
    private val from = now - week.millis
    private val routine = Fixtures.heroRoutine()

    private fun session(
        id: String,
        startedAt: Long,
        minutes: Long? = 30,
        state: SessionState = if (minutes == null) SessionState.ACTIVE else SessionState.COMPLETED,
        endReason: EndReason? = minutes?.let { EndReason.DEADLINE_REACHED },
        actions: List<ActionRecord> = listOf(ActionRecord(ActionId.START_FOCUS_TIMER, ActionState.SUCCEEDED)),
        obligations: List<CleanupObligation> = listOf(
            CleanupObligation(OwnedResource.FOCUS_TIMER, startedAt, released = minutes != null),
        ),
        routineId: String = routine.id,
    ) = Session(
        id = id,
        routineId = routineId,
        routineVersion = 1,
        startedAtMillis = startedAt,
        admissionKey = "key-$id",
        observedInputs = Fixtures.snapshot(nowMillis = startedAt),
        state = state,
        actions = actions,
        obligations = obligations,
        endedAtMillis = minutes?.let { startedAt + it * MIN },
        endReason = endReason,
    )

    private fun skip(
        at: Long,
        vararg reasons: Reason,
        observed: ContextSnapshot? = null,
        provenance: EventProvenance = EventProvenance.PHYSICAL,
        routineId: String = routine.id,
    ) = ReceiptRecord(
        key = "skip-$routineId-$at",
        sessionId = null,
        routineId = routineId,
        routineVersion = 1,
        atMillis = at,
        kind = ReceiptKind.SKIPPED,
        provenance = provenance,
        reasons = listOf(Reason(ReasonCode.TRIGGER_MATCHED, Truth.MATCH, "matched")) + reasons,
        observedInputs = observed,
        headline = "Skipped",
        lines = emptyList(),
    )

    private fun reason(code: ReasonCode, truth: Truth) = Reason(code, truth, code.name)

    private fun compute(
        sessions: List<Session> = emptyList(),
        records: List<ReceiptRecord> = emptyList(),
        ledger: LedgerView? = null,
        routines: List<Routine> = listOf(routine),
        window: InsightsWindow = week,
    ) = Insights.compute(sessions, records, ledger, routines, window, clock, ZoneId.of("Asia/Kolkata"))

    // ------------------------------------------------------------ empty data

    @Test
    fun `no data gives an empty report with no zeros pretending to be facts`() {
        val report = compute(routines = listOf(routine, routine.copy(id = "routine-2")))

        assertFalse(report.hasData)
        assertNull(report.timeInCues)
        assertNull(report.counts, "no counts at all, rather than a row of zeros")
        assertNull(report.cleanup)
        assertNull(report.coverage)
        assertNull(report.ledgerEventsInWindow)
        assertTrue(report.skipReasons.isEmpty() && report.blocked.isEmpty() && report.perRoutine.isEmpty())
        assertTrue(report.spans.isEmpty() && report.skipMarks.isEmpty())
        assertFalse(report.ledgerEnabled)
    }

    @Test
    fun `sessions without any structured receipts leave skips uncounted, not zero`() {
        val report = compute(sessions = listOf(session("s1", now - DAY)))

        val counts = assertNotNull(report.counts)
        assertEquals(1, counts.started)
        assertNull(counts.skipped, "no receipt log in the window means skips were not being counted")
        assertNull(report.perRoutine.single().skips)
    }

    @Test
    fun `an earlier window with nothing ended gives no delta rather than a delta from zero`() {
        val time = assertNotNull(compute(sessions = listOf(session("s1", now - DAY))).timeInCues)

        assertEquals(30 * MIN, time.totalMillis)
        assertNull(time.previousWindowTotalMillis)
        assertNull(time.deltaMillis)
    }

    // ------------------------------------------------ UNKNOWN is not NO_MATCH

    @Test
    fun `a skip decided by an unreadable signal is UNKNOWN, never the signal's NO_MATCH family`() {
        val calendarDenied = Fixtures.snapshot().copy(
            calendarBusy = ContextValue.Unknown(UnknownReason.PERMISSION_DENIED, ContextSource.CALENDAR_PROVIDER),
        )
        val records = listOf(
            skip(now - 3 * DAY, reason(ReasonCode.CALENDAR_UNKNOWN, Truth.UNKNOWN), observed = calendarDenied),
            skip(now - 2 * DAY, reason(ReasonCode.CALENDAR_UNKNOWN, Truth.UNKNOWN), observed = calendarDenied),
            skip(now - DAY, reason(ReasonCode.DAY_NOT_IN_SET, Truth.NO_MATCH)),
            skip(now - DAY + 1, reason(ReasonCode.DAY_NOT_IN_SET, Truth.NO_MATCH)),
            skip(now - DAY + 2, reason(ReasonCode.DAY_NOT_IN_SET, Truth.NO_MATCH)),
        )

        val groups = compute(records = records).skipReasons

        assertEquals(listOf(SkipFamily.UNKNOWN, SkipFamily.DAY), groups.map { it.family }, "UNKNOWN is pinned first")
        assertEquals(2, groups[0].count)
        assertEquals(3, groups[1].count)
        assertTrue(groups.none { it.family == SkipFamily.CALENDAR }, "an unread calendar is not a busy calendar")
        val unreadable = groups[0].unreadable.single()
        assertEquals(ContextSource.CALENDAR_PROVIDER, unreadable.source)
        assertEquals(UnknownReason.PERMISSION_DENIED, unreadable.reason)
        assertEquals(2, unreadable.count)
        assertEquals(Remedy.GrantCapability(Capability.READ_CALENDAR), unreadable.remedy)
    }

    @Test
    fun `when a real NO_MATCH decided the skip, an UNKNOWN alongside it is not blamed`() {
        // Kleene conjunction: a false gate decides, whatever else was unreadable.
        val record = skip(
            now - DAY,
            reason(ReasonCode.DAY_NOT_IN_SET, Truth.NO_MATCH),
            reason(ReasonCode.CHARGING_UNKNOWN, Truth.UNKNOWN),
        )

        assertEquals(listOf(SkipFamily.DAY), compute(records = listOf(record)).skipReasons.map { it.family })
    }

    @Test
    fun `rehearsals and duplicate callbacks are not skips of anything that happened`() {
        val records = listOf(
            skip(now - DAY, reason(ReasonCode.DAY_NOT_IN_SET, Truth.NO_MATCH), provenance = EventProvenance.REHEARSAL),
            skip(now - DAY, reason(ReasonCode.DUPLICATE_EVENT_SAME_CONNECTION, Truth.NO_MATCH)),
            skip(now - DAY, reason(ReasonCode.TIME_OUTSIDE_WINDOW, Truth.NO_MATCH)),
        )

        val report = compute(records = records)

        assertEquals(1, report.counts?.skipped)
        assertEquals(listOf(SkipFamily.TIME), report.skipReasons.map { it.family })
    }

    // -------------------------------------------------------------- cleanup

    @Test
    fun `cleanup counts released in the window and never hides an old outstanding obligation`() {
        val clean = session(
            "clean", now - DAY,
            obligations = listOf(
                CleanupObligation(OwnedResource.FOCUS_TIMER, now - DAY, released = true),
                CleanupObligation(OwnedResource.DND_CONTRIBUTION, now - DAY, released = true),
            ),
        )
        // Twenty days old: outside the 7-day window, still owing Game Mode.
        val stuck = session(
            "stuck", now - 20 * DAY,
            state = SessionState.CLEANUP_PENDING,
            obligations = listOf(
                CleanupObligation(OwnedResource.FOCUS_TIMER, now - 20 * DAY, released = true),
                CleanupObligation(
                    OwnedResource.UTILITY_CONTRIBUTION, now - 20 * DAY, released = false,
                    failureDetail = "Can't tell what to restore.",
                    args = ActionArgs.UseUtility(UtilityId.GAME_MODE, UtilityState.ON),
                ),
            ),
        )
        val running = session("running", now - 10 * MIN, minutes = null)

        val report = compute(sessions = listOf(clean, stuck, running))

        val cleanup = assertNotNull(report.cleanup)
        assertEquals(2, cleanup.released, "only the window's sessions count as released")
        val owed = cleanup.outstanding.single()
        assertEquals("stuck", owed.sessionId)
        assertEquals(OwnedResource.UTILITY_CONTRIBUTION, owed.resource)
        assertEquals(ActionArgs.UseUtility(UtilityId.GAME_MODE, UtilityState.ON), owed.args)
        assertEquals(1, cleanup.heldByLiveSessions, "a running session's timer is held, not overdue")

        val counts = assertNotNull(report.counts)
        assertEquals(1, counts.cleanupPending)
        assertEquals(1, counts.needsAttention)
    }

    @Test
    fun `an outstanding obligation alone is enough for a report`() {
        val stuck = session("stuck", now - 25 * DAY, state = SessionState.CLEANUP_PENDING)
            .let { it.copy(obligations = it.obligations.map { o -> o.copy(released = false) }) }

        val report = compute(sessions = listOf(stuck))

        assertTrue(report.hasData)
        assertEquals(1, report.cleanup?.outstanding?.size)
    }

    @Test
    fun `needs attention counts a partial session once, however many things went wrong in it`() {
        val partial = session(
            "partial", now - DAY,
            actions = listOf(
                ActionRecord(ActionId.START_FOCUS_TIMER, ActionState.BLOCKED, "Exact alarms not granted."),
                ActionRecord(ActionId.REQUEST_DND, ActionState.BLOCKED, "No DND access."),
            ),
        )

        val counts = assertNotNull(compute(sessions = listOf(partial)).counts)

        assertEquals(1, counts.partial)
        assertEquals(2, counts.blockedActions)
        assertEquals(1, counts.needsAttention)
    }

    @Test
    fun `blocked actions group by action and by what the record can say about the cause`() {
        val sessions = listOf(
            session("a", now - DAY, actions = listOf(ActionRecord(ActionId.START_FOCUS_TIMER, ActionState.BLOCKED))),
            session("b", now - 2 * DAY, actions = listOf(ActionRecord(ActionId.START_FOCUS_TIMER, ActionState.BLOCKED))),
            session("c", now - 3 * DAY, actions = listOf(ActionRecord(ActionId.OPEN_LINK, ActionState.BLOCKED, EXPIRED_WHILE_PENDING_DETAIL))),
        )

        val blocked = compute(sessions = sessions).blocked

        assertEquals(2, blocked.size)
        assertEquals(ActionId.START_FOCUS_TIMER, blocked[0].actionId)
        assertEquals(2, blocked[0].count)
        assertEquals(BlockCause.NeedsCapabilities(setOf(Capability.EXACT_ALARM, Capability.POST_NOTIFICATIONS)), blocked[0].cause)
        assertEquals(BlockCause.ExpiredWaitingForYou, blocked[1].cause)
    }

    // --------------------------------------------------------------- windows

    @Test
    fun `the window includes its first instant and now, and excludes the instant before`() {
        val atStart = session("at-start", from, minutes = 10)
        val justBefore = session("just-before", from - 1, minutes = 20)
        val atNow = skip(now, reason(ReasonCode.TIME_OUTSIDE_WINDOW, Truth.NO_MATCH))
        val tooOld = skip(from - 1, reason(ReasonCode.DAY_NOT_IN_SET, Truth.NO_MATCH))

        val report = compute(sessions = listOf(atStart, justBefore), records = listOf(atNow, tooOld))

        assertEquals(listOf("at-start"), report.spans.map { it.sessionId })
        assertEquals(1, report.counts?.started)
        assertEquals(1, report.counts?.skipped)
        val time = assertNotNull(report.timeInCues)
        assertEquals(10 * MIN, time.totalMillis)
        assertEquals(20 * MIN, time.previousWindowTotalMillis, "the instant before the window belongs to the one before")
        assertEquals(-10 * MIN, time.deltaMillis)
    }

    @Test
    fun `a session older than two windows feeds neither the counts nor the delta`() {
        val ancient = session("ancient", now - 2 * week.millis - 1)

        val report = compute(sessions = listOf(ancient))

        assertFalse(report.hasData)
    }

    @Test
    fun `time in cues splits by end reason`() {
        val sessions = listOf(
            session("deadline", now - DAY, minutes = 45, endReason = EndReason.DEADLINE_REACHED),
            session("unplugged", now - 2 * DAY, minutes = 12, endReason = EndReason.TRIGGER_REVERSED),
            session("stopped", now - 3 * DAY, minutes = 5, endReason = EndReason.MANUAL_STOP),
            session("running", now - 5 * MIN, minutes = null),
        )

        val time = assertNotNull(compute(sessions = sessions).timeInCues)

        assertEquals(62 * MIN, time.totalMillis, "a running session has no end yet and adds nothing")
        assertEquals(3, time.endedSessions)
        assertEquals(
            mapOf(EndReason.DEADLINE_REACHED to 45 * MIN, EndReason.TRIGGER_REVERSED to 12 * MIN, EndReason.MANUAL_STOP to 5 * MIN),
            time.byEndReason,
        )
    }

    // ------------------------------------------------------------ per routine

    @Test
    fun `per-routine stats carry median, top skip and whether the last run ended cleanly`() {
        val sessions = listOf(
            session("r1", now - 3 * DAY, minutes = 10),
            session("r2", now - 2 * DAY, minutes = 30),
            session(
                "r3", now - DAY, minutes = 50, state = SessionState.CLEANUP_PENDING,
                obligations = listOf(CleanupObligation(OwnedResource.FOCUS_TIMER, now - DAY, released = false)),
            ),
            session("gone", now - DAY, routineId = "deleted-routine"),
        )
        val records = listOf(
            skip(now - DAY, reason(ReasonCode.TIME_OUTSIDE_WINDOW, Truth.NO_MATCH)),
            skip(now - DAY, reason(ReasonCode.TIME_OUTSIDE_WINDOW, Truth.NO_MATCH)),
            skip(now - DAY, reason(ReasonCode.DAY_NOT_IN_SET, Truth.NO_MATCH)),
        )

        val stats = compute(sessions = sessions, records = records).perRoutine

        val hero = stats.single { it.routineId == routine.id }
        assertEquals(routine.title, hero.title)
        assertEquals(3, hero.runs)
        assertEquals(30 * MIN, hero.medianDurationMillis)
        assertEquals(3, hero.skips)
        assertEquals(SkipFamily.TIME, hero.topSkipFamily)
        assertEquals(false, hero.lastEndedCleanly, "its latest run still owes a cleanup")

        val deleted = stats.single { it.routineId == "deleted-routine" }
        assertNull(deleted.title)
        assertNull(deleted.currentVersion)
    }

    @Test
    fun `spans and skip ticks carry local minute of day for the 24-hour chart`() {
        val zone = ZoneId.of("Asia/Kolkata")
        val start = java.time.ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(now - DAY), zone)
            .withHour(18).withMinute(2).withSecond(0).withNano(0).toInstant().toEpochMilli()

        val report = compute(
            sessions = listOf(session("s", start)),
            records = listOf(skip(start + 60 * MIN, reason(ReasonCode.DAY_NOT_IN_SET, Truth.NO_MATCH))),
        )

        assertEquals(18 * 60 + 2, report.spans.single().startMinuteOfDay)
        assertEquals(19 * 60 + 2, report.skipMarks.single().minuteOfDay)
        assertEquals(SkipFamily.DAY, report.skipMarks.single().family)
    }

    // --------------------------------------------------------------- ledger

    @Test
    fun `with no ledger at all, ledger-derived fields are null, not zero`() {
        val coverage = assertNotNull(compute(sessions = listOf(session("s", now - DAY))).coverage)

        assertEquals(0, coverage.sessionsEndedByGap, "sessions are recorded regardless, so this zero is real")
        assertNull(coverage.ledgerGapMillis)
        assertNull(coverage.coachConclusionsSuppressed)
    }

    @Test
    fun `a ledger with no gap events reports no gap time rather than zero gap time`() {
        val ledger = LedgerView(enabled = true, events = listOf(LedgerEvent.Skipped("DAY_NOT_IN_SET", atMillis = now - DAY)))

        val report = compute(sessions = listOf(session("s", now - DAY)), ledger = ledger)

        assertTrue(report.ledgerEnabled)
        assertEquals(1, report.ledgerEventsInWindow)
        assertNull(report.coverage?.ledgerGapMillis)
        assertEquals(false, report.coverage?.coachConclusionsSuppressed)
    }

    @Test
    fun `recorded gaps over 30 percent of the coach window are reported as the coach suppressing conclusions`() {
        val gap = LedgerEvent.CoverageGap(fromMillis = now - 6 * DAY, toMillis = now - DAY, why = "process not running")
        val ledger = LedgerView(enabled = false, events = listOf(gap))

        val coverage = assertNotNull(compute(sessions = listOf(session("s", now - DAY)), ledger = ledger).coverage)

        assertEquals(5 * DAY, coverage.ledgerGapMillis)
        assertEquals(true, coverage.coachConclusionsSuppressed)
    }
}
