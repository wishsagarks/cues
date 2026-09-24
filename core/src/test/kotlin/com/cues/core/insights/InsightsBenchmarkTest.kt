package com.cues.core.insights

import com.cues.core.Fixtures
import com.cues.core.coach.LedgerEvent
import com.cues.core.eval.Reason
import com.cues.core.eval.ReasonCode
import com.cues.core.eval.Truth
import com.cues.core.model.*
import com.cues.core.receipt.ReceiptKind
import com.cues.core.receipt.ReceiptRecord
import com.cues.core.session.FakeClock
import java.time.ZoneId
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

private const val DAY = 86_400_000L
private const val MIN = 60_000L

/**
 * Plan §10.2's synthetic worst case for [Insights.compute]:
 * 14 days x 1,000 ledger events a day, 30 days x 40 sessions a day, and the
 * full 200 structured receipts, over the 30-day window.
 *
 * This measures the pure computation on the JVM only. It says nothing about
 * a phone, and nothing about the file reads that gather these inputs — those
 * are what §10.2 expects to dominate on the device, and the figure that
 * matters is the debug "Insights computed in N ms" line in Checks, recorded in
 * docs/MEASUREMENTS.md (CLEANUP.md CL-34).
 *
 * Budget: 150 ms, the plan's starting point.
 * Measured: median 2–3 ms (min 1–2 ms, max 3–4 ms) over 7 timed runs after
 * 5 warm-ups, three separate runs — 25 Sep 2026, Gradle `:core:test` on the
 * development Mac (Apple M4, OpenJDK 21.0.11). A JVM figure from one laptop,
 * not a device number.
 */
class InsightsBenchmarkTest {

    private val now = Fixtures.NOW
    private val clock = FakeClock(now)

    private val routines = (1..8).map { i ->
        Fixtures.heroRoutine().copy(id = "routine-$i", title = "Cue $i")
    }

    private val calendarDenied = Fixtures.snapshot().copy(
        calendarBusy = ContextValue.Unknown(UnknownReason.PERMISSION_DENIED, ContextSource.CALENDAR_PROVIDER),
    )

    /** 30 days x 40 sessions: most clean, some partial, a few still owing cleanup, a few still running. */
    private val sessions: List<Session> = (0 until 30).flatMap { day ->
        (0 until 40).map { n ->
            val startedAt = now - day * DAY - n * 30 * MIN - 1
            val minutes = 5L + (n * 7) % 60
            val blocked = n % 9 == 0
            val stuck = n % 97 == 0
            val live = day == 0 && n < 2
            Session(
                id = "s-$day-$n",
                routineId = routines[n % routines.size].id,
                routineVersion = 1,
                startedAtMillis = startedAt,
                admissionKey = "k-$day-$n",
                observedInputs = Fixtures.snapshot(nowMillis = startedAt),
                state = when {
                    live -> SessionState.ACTIVE
                    stuck -> SessionState.CLEANUP_PENDING
                    else -> SessionState.COMPLETED
                },
                actions = listOf(
                    ActionRecord(ActionId.START_FOCUS_TIMER, if (blocked) ActionState.BLOCKED else ActionState.SUCCEEDED),
                    ActionRecord(ActionId.REQUEST_DND, ActionState.SUCCEEDED, verification = Verification.READ_BACK),
                ),
                obligations = listOf(
                    CleanupObligation(OwnedResource.DND_CONTRIBUTION, startedAt, released = !live && !stuck),
                ),
                endedAtMillis = if (live) null else startedAt + minutes * MIN,
                endReason = if (live) null else EndReason.entries[n % EndReason.entries.size],
            )
        }
    }

    /** 200 receipts, the log's cap: starts and skips, a third of the skips on an unreadable calendar. */
    private val receipts: List<ReceiptRecord> = (0 until 200).map { i ->
        val at = now - i * 3 * 60 * MIN
        val skip = i % 2 == 0
        val unknown = i % 3 == 0
        ReceiptRecord(
            key = if (skip) "skip-routine-$i-$at" else "s-$i",
            sessionId = if (skip) null else "s-$i",
            routineId = routines[i % routines.size].id,
            routineVersion = 1,
            atMillis = at,
            kind = if (skip) ReceiptKind.SKIPPED else ReceiptKind.STARTED,
            provenance = EventProvenance.PHYSICAL,
            reasons = listOf(
                Reason(ReasonCode.TRIGGER_MATCHED, Truth.MATCH, "matched"),
                when {
                    !skip -> Reason(ReasonCode.DAY_IN_SET, Truth.MATCH, "in set")
                    unknown -> Reason(ReasonCode.CALENDAR_UNKNOWN, Truth.UNKNOWN, "unread")
                    else -> Reason(ReasonCode.TIME_OUTSIDE_WINDOW, Truth.NO_MATCH, "outside")
                },
            ),
            observedInputs = calendarDenied,
            headline = if (skip) "Skipped" else "Started",
            lines = listOf("line"),
        )
    }

    /** 14 days x 1,000 ledger events: mostly signal observations, with the other kinds mixed in. */
    private val ledger = LedgerView(
        enabled = true,
        events = (0 until 14).flatMap { day ->
            (0 until 1_000).map { n ->
                val at = now - day * DAY - n * 80_000L
                when (n % 10) {
                    0 -> LedgerEvent.SessionEnded("routine-${n % 8}", 45, 30, "DEADLINE_REACHED", at)
                    1 -> LedgerEvent.Skipped("DAY_NOT_IN_SET", weekday = Day.MON, atMillis = at)
                    2 -> LedgerEvent.CoverageGap(at - 10 * MIN, at, "process not running")
                    else -> LedgerEvent.SignalObserved("bluetooth_connected", "AA:BB", n % 1_440, Day.TUE, at)
                }
            }
        },
    )

    private fun run() = Insights.compute(
        sessions, receipts, ledger, routines, InsightsWindow.LAST_30_DAYS, clock, ZoneId.of("Asia/Kolkata"),
    )

    @Test
    fun `the worst case computes inside its JVM budget`() {
        check(sessions.size == 1_200 && receipts.size == 200 && ledger.events.size == 14_000)

        repeat(5) { run() } // JIT warm-up; the first calls measure class loading, not Insights.
        val timings = (1..7).map {
            val start = System.nanoTime()
            val report = run()
            val elapsed = (System.nanoTime() - start) / 1_000_000
            check(report.hasData) // keep the work observable so it cannot be optimised away
            elapsed
        }.sorted()
        val median = timings[timings.size / 2]

        println("InsightsBenchmark: median ${median} ms, min ${timings.first()} ms, max ${timings.last()} ms " +
            "(1200 sessions, 200 receipts, 14000 ledger events)")
        assertTrue(median < BUDGET_MILLIS, "Insights.compute took a median $median ms; budget is $BUDGET_MILLIS ms")
    }

    private companion object {
        const val BUDGET_MILLIS = 150L
    }
}
