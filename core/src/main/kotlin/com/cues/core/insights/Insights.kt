package com.cues.core.insights

import com.cues.core.coach.Detectors
import com.cues.core.coach.LedgerEvent
import com.cues.core.context.Remedy
import com.cues.core.context.UnknownRemedy
import com.cues.core.context.UnreadableInputs
import com.cues.core.eval.Reason
import com.cues.core.eval.ReasonCode
import com.cues.core.eval.Truth
import com.cues.core.model.ActionArgs
import com.cues.core.model.ActionId
import com.cues.core.model.ActionState
import com.cues.core.model.Capability
import com.cues.core.model.ContextSource
import com.cues.core.model.EXPIRED_WHILE_PENDING_DETAIL
import com.cues.core.model.EndReason
import com.cues.core.model.EventProvenance
import com.cues.core.model.OwnedResource
import com.cues.core.model.Routine
import com.cues.core.model.Session
import com.cues.core.model.SessionState
import com.cues.core.model.UnknownReason
import com.cues.core.ports.Clock
import com.cues.core.receipt.ReceiptKind
import com.cues.core.receipt.ReceiptRecord
import com.cues.core.registry.ActionRegistry
import com.cues.core.session.isLive
import java.time.Instant
import java.time.ZoneId

private const val DAY_MILLIS = 86_400_000L

/** How far back Insights looks. Thirty days is also how long finished sessions are kept. */
enum class InsightsWindow(val days: Int) {
    LAST_7_DAYS(7),
    LAST_30_DAYS(30),
    ;

    val millis: Long get() = days * DAY_MILLIS
}

/**
 * What the usage ledger contributes, read once by the caller.
 *
 * Passed as data rather than the [com.cues.core.coach.UsageLedger] port so
 * [Insights.compute] never does I/O of its own. `null` in place of a whole
 * view means there is no ledger at all, which is different from one that is
 * switched off.
 */
data class LedgerView(
    /** Whether signal learning is switched on — the opt-in, not whether a ledger exists. */
    val enabled: Boolean,
    val events: List<LedgerEvent>,
)

/**
 * Everything the Insights tab shows, computed from records and nothing else.
 *
 * The rule throughout: a number exists only when there is data behind it. A
 * nullable field is null — not zero — when its source recorded nothing in the
 * window, so the screen shows "No earlier data" or its empty state instead of
 * zeros that look like facts.
 */
data class InsightsReport(
    val window: InsightsWindow,
    val fromMillis: Long,
    val toMillis: Long,
    /** False when no session and no receipt fell in the window. The screen shows its empty state. */
    val hasData: Boolean,
    val timeInCues: TimeInCues?,
    val counts: InsightCounts?,
    /** Deciding reasons of the window's skips, grouped. UNKNOWN is its own group, pinned first. */
    val skipReasons: List<SkipGroup>,
    val cleanup: CleanupLedger?,
    val blocked: List<BlockedGroup>,
    val perRoutine: List<RoutineStats>,
    /** Sessions started in the window, for the 24-hour chart. */
    val spans: List<SessionSpan>,
    /** Skips in the window, as ticks on the same chart. */
    val skipMarks: List<SkipMark>,
    val coverage: Coverage?,
    /** Whether signal learning is on. False, not null, when there is no ledger at all. */
    val ledgerEnabled: Boolean,
    /**
     * Ledger events held in the window. Null only when there is no ledger at
     * all. Session, skip and block events are kept whether or not learning is
     * on; only signal observations need the opt-in.
     */
    val ledgerEventsInWindow: Int?,
) {
    companion object {
        fun empty(window: InsightsWindow, fromMillis: Long, toMillis: Long, ledgerEnabled: Boolean) = InsightsReport(
            window, fromMillis, toMillis, hasData = false, timeInCues = null, counts = null,
            skipReasons = emptyList(), cleanup = null, blocked = emptyList(), perRoutine = emptyList(),
            spans = emptyList(), skipMarks = emptyList(), coverage = null,
            ledgerEnabled = ledgerEnabled, ledgerEventsInWindow = null,
        )
    }
}

/** Summed `endedAt - startedAt` of the window's ended sessions. Null in the report when none ended. */
data class TimeInCues(
    val totalMillis: Long,
    val byEndReason: Map<EndReason, Long>,
    val endedSessions: Int,
    /** The same sum for the window before. Null when that window has no ended session: "No earlier data". */
    val previousWindowTotalMillis: Long?,
) {
    val deltaMillis: Long? get() = previousWindowTotalMillis?.let { totalMillis - it }
}

data class InsightCounts(
    val started: Int,
    /** Null when no structured receipt was recorded in the window: skips were not being counted. */
    val skipped: Int?,
    /** Sessions in the window where not every action happened. */
    val partial: Int,
    /** Sessions, of any age, still owing a cleanup. Never windowed: an outstanding obligation is never hidden. */
    val cleanupPending: Int,
    /** Individual BLOCKED actions in the window's sessions. */
    val blockedActions: Int,
    /**
     * Distinct sessions a person should look at: partial in the window, or
     * owing a cleanup at any age. A distinct count, so one session with a
     * blocked action that is also PARTIAL is one thing to look at, not two.
     */
    val needsAttention: Int,
)

/** The condition family a skip's deciding reason belongs to. */
enum class SkipFamily {
    /** Could not be read. Kept apart from every NO_MATCH family, always. */
    UNKNOWN,
    DAY, TIME, CHARGING, BATTERY, DEVICE, WIFI, AUDIO, PLACE, CALENDAR, CONTEXT,
    /** The user's own skip-today or pause-until. */
    PATCH,
    /** Already running, or cooling down between runs. */
    ALREADY_RUNNING,
    NOT_ARMED,
    OTHER,
}

data class SkipGroup(
    val family: SkipFamily,
    val count: Int,
    /** Keys of the receipts behind this bar, oldest first, so the bar can expand into them. */
    val receiptKeys: List<String>,
    /** For [SkipFamily.UNKNOWN] only: which readings could not be taken, most frequent first. */
    val unreadable: List<UnreadableCount> = emptyList(),
)

/** One unreadable input behind some number of UNKNOWN skips, with what would fix it. */
data class UnreadableCount(
    val source: ContextSource,
    val reason: UnknownReason,
    val count: Int,
    val remedy: Remedy?,
)

data class CleanupLedger(
    /** Obligations released by sessions started in the window. */
    val released: Int,
    /** Unreleased obligations of finished sessions, at any age — the list behind "Retry cleanup". */
    val outstanding: List<OutstandingObligation>,
    /** Obligations still held by running sessions. Owed later, not overdue. */
    val heldByLiveSessions: Int,
)

data class OutstandingObligation(
    val sessionId: String,
    val routineId: String,
    val resource: OwnedResource,
    /** The approved action that acquired it — e.g. which utility may still be on. Null for a record that predates it. */
    val args: ActionArgs?,
    val acquiredAtMillis: Long,
    val failureDetail: String?,
)

/** Why an action was blocked, as far as the records can say without guessing. */
sealed interface BlockCause {
    /**
     * The action needs these grants. Which one was missing at the time is not
     * recorded; the Fix button asks the live capability provider.
     */
    data class NeedsCapabilities(val required: Set<Capability>) : BlockCause

    /** A step that needed someone at the phone, and the session ended before anyone was. */
    data object ExpiredWaitingForYou : BlockCause

    /** Blocked, and the action needs no grant — the records hold no cause beyond the executor's detail. */
    data object Unattributed : BlockCause
}

data class BlockedGroup(val actionId: ActionId, val cause: BlockCause, val count: Int, val routineIds: Set<String>)

data class RoutineStats(
    val routineId: String,
    /** Null when the routine has since been deleted. */
    val title: String?,
    /** The routine's current version. Null when it has been deleted. */
    val currentVersion: Int?,
    val runs: Int,
    /** Null when none of the window's runs has ended. */
    val medianDurationMillis: Long?,
    /** Null when no structured receipt was recorded in the window. */
    val skips: Int?,
    val topSkipFamily: SkipFamily?,
    val blockedActions: Int,
    /** Whether its most recent ended run finished with everything released. Null when none has ended. */
    val lastEndedCleanly: Boolean?,
)

data class SessionSpan(
    val sessionId: String,
    val routineId: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long?,
    val endReason: EndReason?,
    /** Minutes since local midnight at the start, in the zone Insights was computed for. */
    val startMinuteOfDay: Int,
)

data class SkipMark(val atMillis: Long, val minuteOfDay: Int, val family: SkipFamily)

data class Coverage(
    /** Sessions in the window ended because a disconnect was inferred, never observed. */
    val sessionsEndedByGap: Int,
    /** Gap time the ledger recorded within the window. Null when there is no ledger, or it holds no gap events. */
    val ledgerGapMillis: Long?,
    /**
     * True when the coach is holding back its conclusions because gaps cover
     * over 30% of its window — the coach's own rule. Null with no ledger.
     */
    val coachConclusionsSuppressed: Boolean?,
)

/**
 * Computes Insights from what the phone recorded, and nothing else.
 *
 * Pure: no clock of its own beyond [Clock], no I/O, no model. Every number
 * is a count or a sum over sessions, structured receipts or ledger events;
 * none is estimated, projected or smoothed. Where a source recorded nothing,
 * the matching field is null rather than zero.
 *
 * Window membership is by start time for sessions (a session belongs to the
 * window it started in) and by record time for receipts and ledger events.
 * The window is `[now - window, now]`; the previous window, used only for the
 * time-in-cues delta, is `[now - 2 * window, now - window)`, so the boundary
 * instant belongs to the current window and never to both.
 */
object Insights {

    fun compute(
        sessions: List<Session>,
        receiptRecords: List<ReceiptRecord>,
        ledger: LedgerView?,
        routines: List<Routine>,
        window: InsightsWindow,
        clock: Clock,
        /** Local time for the 24-hour chart. The zone is not part of any count. */
        zone: ZoneId = ZoneId.systemDefault(),
    ): InsightsReport {
        val now = clock.nowMillis()
        val from = now - window.millis
        val previousFrom = from - window.millis
        fun inWindow(t: Long) = t in from..now

        val windowSessions = sessions.filter { inWindow(it.startedAtMillis) }.sortedBy { it.startedAtMillis }
        // A rehearsal is never something that happened; a duplicate callback
        // is one occurrence reported twice, not a second skipped occurrence.
        val windowRecords = receiptRecords.filter { inWindow(it.atMillis) && it.provenance != EventProvenance.REHEARSAL }
        val skips = windowRecords
            .filter { it.kind == ReceiptKind.SKIPPED }
            .filterNot { record -> record.reasons.any { it.code == ReasonCode.DUPLICATE_EVENT_SAME_CONNECTION } }
        val ledgerEnabled = ledger?.enabled == true
        val outstanding = outstandingObligations(sessions)
        val attentionIds = sessions.filter { it.owesCleanup() }.map { it.id }.toSet()

        if (windowSessions.isEmpty() && windowRecords.isEmpty() && outstanding.isEmpty()) {
            return InsightsReport.empty(window, from, now, ledgerEnabled)
        }

        val receiptsRecorded = windowRecords.isNotEmpty()
        val decided = skips.map { it to skipFamily(it.reasons) }

        return InsightsReport(
            window = window,
            fromMillis = from,
            toMillis = now,
            hasData = true,
            timeInCues = timeInCues(windowSessions, sessions.filter { it.startedAtMillis in previousFrom until from }),
            counts = InsightCounts(
                started = windowSessions.size,
                skipped = if (receiptsRecorded) skips.size else null,
                partial = windowSessions.count { it.isPartial() },
                cleanupPending = sessions.count { it.state == SessionState.CLEANUP_PENDING },
                blockedActions = windowSessions.sumOf { s -> s.actions.count { it.state == ActionState.BLOCKED } },
                needsAttention = (windowSessions.filter { it.isPartial() }.map { it.id }.toSet() + attentionIds).size,
            ),
            skipReasons = skipGroups(decided),
            cleanup = cleanupLedger(windowSessions, sessions, outstanding),
            blocked = blockedGroups(windowSessions),
            perRoutine = perRoutine(windowSessions, decided, routines, receiptsRecorded),
            spans = windowSessions.map { s ->
                SessionSpan(s.id, s.routineId, s.startedAtMillis, s.endedAtMillis, s.endReason, minuteOfDay(s.startedAtMillis, zone))
            },
            skipMarks = decided.map { (record, family) -> SkipMark(record.atMillis, minuteOfDay(record.atMillis, zone), family) },
            coverage = Coverage(
                sessionsEndedByGap = windowSessions.count { it.endReason == EndReason.COVERAGE_GAP },
                ledgerGapMillis = ledger?.let { ledgerGapMillis(it.events, from, now) },
                coachConclusionsSuppressed = ledger?.let { Detectors.conclusionsSuppressed(it.events, now) },
            ),
            ledgerEnabled = ledgerEnabled,
            ledgerEventsInWindow = ledger?.events?.count { inWindow(it.atMillis) },
        )
    }

    // ---------------------------------------------------------- time in cues

    private fun timeInCues(current: List<Session>, previous: List<Session>): TimeInCues? {
        val ended = current.filter { it.endedAtMillis != null }
        if (ended.isEmpty()) return null
        val previousEnded = previous.filter { it.endedAtMillis != null }
        return TimeInCues(
            totalMillis = ended.sumOf { it.durationMillis() },
            byEndReason = ended.groupBy { it.endReason }
                .filterKeys { it != null }
                .map { (reason, group) -> reason!! to group.sumOf { it.durationMillis() } }
                .toMap(),
            endedSessions = ended.size,
            previousWindowTotalMillis = previousEnded.takeIf { it.isNotEmpty() }?.sumOf { it.durationMillis() },
        )
    }

    private fun Session.durationMillis(): Long = ((endedAtMillis ?: startedAtMillis) - startedAtMillis).coerceAtLeast(0)

    // ------------------------------------------------------------ skip reasons

    /**
     * The reason that decided a skip — the rule [com.cues.core.receipt.Receipts]
     * uses to word it: the first NO_MATCH, else the first UNKNOWN. A skip
     * decided by an UNKNOWN is always [SkipFamily.UNKNOWN], whatever signal it
     * was about; it is never filed under DAY or CALENDAR as though the signal
     * had been read and failed.
     */
    fun decidingReason(reasons: List<Reason>): Reason? =
        reasons.firstOrNull { it.truth == Truth.NO_MATCH } ?: reasons.firstOrNull { it.truth == Truth.UNKNOWN }

    fun skipFamily(reasons: List<Reason>): SkipFamily {
        val deciding = decidingReason(reasons) ?: return SkipFamily.OTHER
        if (deciding.truth == Truth.UNKNOWN) return SkipFamily.UNKNOWN
        return when (deciding.code) {
            ReasonCode.DAY_NOT_IN_SET, ReasonCode.TIME_TRIGGER_DAY_MISMATCH -> SkipFamily.DAY
            ReasonCode.TIME_OUTSIDE_WINDOW -> SkipFamily.TIME
            ReasonCode.CHARGING_NOT_AS_REQUIRED -> SkipFamily.CHARGING
            ReasonCode.BATTERY_NOT_AS_REQUIRED -> SkipFamily.BATTERY
            ReasonCode.DEVICE_NOT_CONNECTED -> SkipFamily.DEVICE
            ReasonCode.WIFI_NOT_CONNECTED -> SkipFamily.WIFI
            ReasonCode.AUDIO_OUTPUT_INACTIVE -> SkipFamily.AUDIO
            ReasonCode.PLACE_NOT_INSIDE -> SkipFamily.PLACE
            ReasonCode.CALENDAR_NOT_AS_REQUIRED -> SkipFamily.CALENDAR
            ReasonCode.CONTEXT_NOT_MATCHED -> SkipFamily.CONTEXT
            ReasonCode.PATCH_SKIPPED_TODAY, ReasonCode.PATCH_SKIPPED_UNTIL -> SkipFamily.PATCH
            ReasonCode.SESSION_ALREADY_ACTIVE, ReasonCode.COOLDOWN_ACTIVE,
            ReasonCode.DUPLICATE_EVENT_SAME_CONNECTION,
            -> SkipFamily.ALREADY_RUNNING
            ReasonCode.ROUTINE_NOT_ARMED -> SkipFamily.NOT_ARMED
            else -> SkipFamily.OTHER
        }
    }

    private fun skipGroups(decided: List<Pair<ReceiptRecord, SkipFamily>>): List<SkipGroup> =
        decided.groupBy({ it.second }, { it.first }).map { (family, records) ->
            SkipGroup(
                family = family,
                count = records.size,
                receiptKeys = records.map { it.key },
                unreadable = if (family == SkipFamily.UNKNOWN) unreadableCounts(records) else emptyList(),
            )
        }.sortedWith(
            // UNKNOWN is pinned first: it is the one group with something to fix.
            compareBy<SkipGroup> { it.family != SkipFamily.UNKNOWN }
                .thenByDescending { it.count }
                .thenBy { it.family.ordinal },
        )

    private fun unreadableCounts(records: List<ReceiptRecord>): List<UnreadableCount> =
        records.mapNotNull { record ->
            val deciding = decidingReason(record.reasons) ?: return@mapNotNull null
            val observed = record.observedInputs ?: return@mapNotNull null
            UnreadableInputs.behind(deciding.code, observed)
        }
            .groupingBy { it.source to it.reason }
            .eachCount()
            .map { (key, count) ->
                UnreadableCount(key.first, key.second, count, UnknownRemedy.forUnknown(key.first, key.second))
            }
            .sortedWith(compareByDescending<UnreadableCount> { it.count }.thenBy { it.source.ordinal }.thenBy { it.reason.ordinal })

    // ---------------------------------------------------------------- cleanup

    private fun outstandingObligations(sessions: List<Session>): List<OutstandingObligation> =
        sessions.filter { !it.state.isLive() }.flatMap { session ->
            session.obligations.filter { !it.released }.map {
                OutstandingObligation(session.id, session.routineId, it.resource, it.args, it.acquiredAtMillis, it.failureDetail)
            }
        }.sortedBy { it.acquiredAtMillis }

    private fun cleanupLedger(
        windowSessions: List<Session>,
        allSessions: List<Session>,
        outstanding: List<OutstandingObligation>,
    ): CleanupLedger? {
        val released = windowSessions.sumOf { s -> s.obligations.count { it.released } }
        val held = allSessions.filter { it.state.isLive() }.sumOf { s -> s.obligations.count { !it.released } }
        // "Released 0 of 0" is not a fact worth a card.
        if (released == 0 && outstanding.isEmpty() && held == 0) return null
        return CleanupLedger(released, outstanding, held)
    }

    private fun Session.owesCleanup(): Boolean =
        state == SessionState.CLEANUP_PENDING || (!state.isLive() && obligations.any { !it.released })

    private fun Session.isPartial(): Boolean =
        state == SessionState.PARTIAL || actions.any {
            it.state == ActionState.BLOCKED || it.state == ActionState.FAILED || it.state == ActionState.COMPENSATION_FAILED
        }

    // ---------------------------------------------------------------- blocked

    private fun blockedGroups(windowSessions: List<Session>): List<BlockedGroup> =
        windowSessions.flatMap { session ->
            session.actions.filter { it.state == ActionState.BLOCKED }.map { record ->
                val cause = when {
                    record.detail == EXPIRED_WHILE_PENDING_DETAIL -> BlockCause.ExpiredWaitingForYou
                    else -> ActionRegistry.definition(record.actionId)?.requiredCapabilities
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { BlockCause.NeedsCapabilities(it) }
                        ?: BlockCause.Unattributed
                }
                Triple(record.actionId, cause, session.routineId)
            }
        }
            .groupBy { it.first to it.second }
            .map { (key, hits) -> BlockedGroup(key.first, key.second, hits.size, hits.map { it.third }.toSet()) }
            .sortedWith(compareByDescending<BlockedGroup> { it.count }.thenBy { it.actionId.ordinal })

    // ------------------------------------------------------------ per routine

    private fun perRoutine(
        windowSessions: List<Session>,
        decided: List<Pair<ReceiptRecord, SkipFamily>>,
        routines: List<Routine>,
        receiptsRecorded: Boolean,
    ): List<RoutineStats> {
        val byId = routines.associateBy { it.id }
        val sessionsByRoutine = windowSessions.groupBy { it.routineId }
        val skipsByRoutine = decided.groupBy { it.first.routineId }
        val active = (sessionsByRoutine.keys + skipsByRoutine.keys)

        return active.map { id ->
            val runs = sessionsByRoutine[id].orEmpty()
            val ended = runs.filter { it.endedAtMillis != null }
            val skipFamilies = skipsByRoutine[id].orEmpty().map { it.second }
            RoutineStats(
                routineId = id,
                title = byId[id]?.title,
                currentVersion = byId[id]?.version,
                runs = runs.size,
                medianDurationMillis = median(ended.map { it.durationMillis() }),
                skips = if (receiptsRecorded) skipFamilies.size else null,
                topSkipFamily = skipFamilies.groupingBy { it }.eachCount()
                    .entries.sortedWith(compareByDescending<Map.Entry<SkipFamily, Int>> { it.value }.thenBy { it.key.ordinal })
                    .firstOrNull()?.key,
                blockedActions = runs.sumOf { s -> s.actions.count { it.state == ActionState.BLOCKED } },
                lastEndedCleanly = ended.maxByOrNull { it.endedAtMillis!! }?.let { last ->
                    last.state == SessionState.COMPLETED && last.obligations.all { it.released }
                },
            )
        }.sortedWith(compareByDescending<RoutineStats> { it.runs }.thenBy { it.routineId })
    }

    private fun median(values: List<Long>): Long? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Null, not zero, when the ledger holds no gap events at all: as of this
     * writing nothing in the app appends `LedgerEvent.CoverageGap`, and "0 ms
     * of gaps" from a recorder that never records would be a zero pretending
     * to be a measurement.
     */
    private fun ledgerGapMillis(events: List<LedgerEvent>, from: Long, to: Long): Long? {
        val gaps = events.filterIsInstance<LedgerEvent.CoverageGap>()
        if (gaps.isEmpty()) return null
        return gaps.sumOf { gap -> (minOf(to, gap.toMillis) - maxOf(from, gap.fromMillis)).coerceAtLeast(0) }
    }

    private fun minuteOfDay(atMillis: Long, zone: ZoneId): Int =
        Instant.ofEpochMilli(atMillis).atZone(zone).let { it.hour * 60 + it.minute }
}
