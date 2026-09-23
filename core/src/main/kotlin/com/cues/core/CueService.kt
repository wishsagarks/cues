package com.cues.core

import com.cues.core.approval.ArmResult
import com.cues.core.approval.Approvals
import com.cues.core.approval.DeleteResult
import com.cues.core.context.SnapshotBuilder
import com.cues.core.drafting.CompositeDrafter
import com.cues.core.drafting.DraftResult
import com.cues.core.model.DraftSourceId
import com.cues.core.drafting.RoutineDrafter
import com.cues.core.model.ContextValue
import com.cues.core.model.EventKind
import com.cues.core.model.Routine
import com.cues.core.model.Session
import com.cues.core.model.TriggerEvent
import com.cues.core.model.UnknownReason
import com.cues.core.ports.ActionExecutor
import com.cues.core.ports.CapabilityProvider
import com.cues.core.ports.Clock
import com.cues.core.ports.ReceiptSink
import com.cues.core.ports.RoutineStore
import com.cues.core.ports.SessionStore
import com.cues.core.receipt.Receipts
import com.cues.core.session.EngineResult
import com.cues.core.session.SessionEngine
import com.cues.core.signals.SignalRegistry
import java.time.ZoneId

/**
 * The one class Android calls for everything. Android supplies ports and a
 * UI; every decision — what a request means, whether a cue may arm, what an
 * event does — happens on this side of the line.
 *
 * Nothing here talks to a device directly. It orchestrates the pieces that
 * already do: [RoutineDrafter] proposes, [Approvals] gates, [SessionEngine]
 * decides and acts through [ActionExecutor], [SnapshotBuilder] turns raw
 * readings into what the evaluator consumes, and every outcome is written
 * through [ReceiptSink] before it is handed back.
 */
class CueService(
    private val routines: RoutineStore,
    private val sessions: SessionStore,
    private val receipts: ReceiptSink,
    executor: ActionExecutor,
    private val clock: Clock,
    private val capabilities: CapabilityProvider,
    private val drafter: RoutineDrafter,
    private val zoneId: () -> ZoneId = { ZoneId.systemDefault() },
) {

    private val engine = SessionEngine(sessions, executor, clock)

    // ------------------------------------------------------------ authoring

    /**
     * Proposes a routine from a spoken or typed request.
     *
     * Stamps the accepted result with which drafter actually produced it —
     * [DraftResult.source] already carries that, this just also puts it on
     * the routine itself so it survives into storage and the review screen.
     * Nothing is persisted yet; a draft is not reviewable until [review] and
     * not armed until [approveAndArm].
     */
    suspend fun draft(text: String): DraftResult = when (val result = drafter.draft(text)) {
        is DraftResult.Drafted -> result.copy(routine = result.routine.copy(draftedBy = result.source))
        is DraftResult.NeedsClarification, is DraftResult.Failed -> result
    }

    /** Normalizes and validates without changing status. Safe to call repeatedly while editing. */
    fun review(routine: Routine): Approvals.ReviewResult = Approvals.review(routine)

    /**
     * Approves the routine's current form, then attempts to arm it.
     *
     * The approved version is persisted even if arming then fails on a
     * missing capability: the user can grant that permission and retry
     * without re-approving, because nothing about what they agreed to has
     * changed.
     */
    fun approveAndArm(routine: Routine): ArmResult<Routine> {
        val approved = when (val result = Approvals.approve(routine)) {
            is ArmResult.Ok -> result.routine
            is ArmResult.Invalid -> return result
            else -> error("Approvals.approve never returns $result")
        }
        routines.save(approved)

        return when (val armed = Approvals.arm(approved, capabilities)) {
            is ArmResult.Ok -> {
                routines.save(armed.routine)
                armed
            }

            else -> armed
        }
    }

    fun list(): List<Routine> = routines.all()

    fun pause(routineId: String): Routine? {
        val routine = routines.findRoutine(routineId) ?: return null
        return Approvals.pause(routine).also { routines.save(it) }
    }

    fun resume(routineId: String): ArmResult<Routine>? {
        val routine = routines.findRoutine(routineId) ?: return null
        return when (val result = Approvals.resume(routine)) {
            is ArmResult.Ok -> {
                routines.save(result.routine)
                result
            }

            else -> result
        }
    }

    /** No-op, successfully, on an id that is already gone — deletion is idempotent. */
    fun delete(routineId: String): DeleteResult {
        val routine = routines.findRoutine(routineId) ?: return DeleteResult.Ok
        return when (val result = Approvals.delete(routine, sessions)) {
            DeleteResult.Ok -> {
                routines.delete(routineId)
                DeleteResult.Ok
            }

            is DeleteResult.Blocked -> result
        }
    }

    // -------------------------------------------------------------- runtime

    /**
     * Handles a Bluetooth or power transition observed by an adapter.
     *
     * Tries every armed routine against the event: [SessionEngine.onExitEvent]
     * first (does this end an already-running session?), then
     * [SessionEngine.onTriggerEvent] (does this start a new one?). A routine
     * whose trigger has nothing to do with this event resolves as a harmless
     * mismatch in both calls, so the caller never has to pre-filter which
     * routines might care — that filtering already lives in the evaluator.
     *
     * Readings the caller could not take are passed through as
     * [ContextValue.Unknown] and stay that way all the way to the receipt;
     * this never fills one in on the caller's behalf.
     *
     * The snapshot describes the moment the event happened — [TriggerEvent.atMillis]
     * — not the moment this method got around to running. A broadcast that was
     * queued behind other work must still be judged against the conditions
     * that held when the earbuds actually connected, not a later "now".
     *
     * [SessionEngine.onTriggerEvent] is only asked about a routine whose
     * trigger could plausibly be this event — right kind, right device.
     * Its own admission checks (duplicate, already-active) run before trigger
     * matching, so asking it about an event that was never this routine's
     * business would report "already running" instead of the truth, which
     * is that the event had nothing to do with this routine at all. Filtering
     * here means every result that does come back is worth a receipt.
     */
    fun onDeviceEvent(
        event: TriggerEvent,
        charging: ContextValue<Boolean> = unread(UnknownReason.NEVER_OBSERVED),
        connectedDeviceIds: ContextValue<Set<String>> = unread(UnknownReason.NEVER_OBSERVED),
        wifi: ContextValue<com.cues.core.model.WifiState> = unread(UnknownReason.NEVER_OBSERVED),
    ): List<EngineResult> {
        val snapshot = SnapshotBuilder.build(
            nowMillis = event.atMillis,
            zoneId = zoneId(),
            charging = charging,
            connectedDeviceIds = connectedDeviceIds,
            wifi = wifi,
        )

        return routines.armed().flatMap { routine ->
            val results = mutableListOf<EngineResult>()

            val exit = engine.onExitEvent(routine, event)
            if (exit !is EngineResult.Ignored) {
                recordReceipt(routine, exit)
                results += exit
            }

            if (event.couldStart(routine)) {
                val start = engine.onTriggerEvent(routine, event, snapshot)
                recordReceipt(routine, start)
                results += start
            }

            results
        }
    }

    /** True when [routine]'s trigger is even the right shape for this event — same kind, same device. */
    private fun TriggerEvent.couldStart(routine: Routine): Boolean =
        SignalRegistry.listensFor(routine.trigger, kind) &&
            SignalRegistry.match(routine.trigger, this).truth == com.cues.core.eval.Truth.MATCH

    /**
     * The exact-alarm callback for one session's deadline.
     *
     * Takes only the session id, not a routine id — that is deliberately all
     * the alarm's own PendingIntent has to carry. The routine is looked up
     * from the session record itself, which is also simpler at every real
     * call site: an alarm, a grace-window timer and a manual stop button all
     * naturally know which session they're about, never which routine.
     */
    fun onDeadline(sessionId: String): EngineResult? = exitFor(sessionId, EventKind.DEADLINE_REACHED)

    /** The reconnect grace window for one session has elapsed without a reconnect. */
    fun onGraceElapsed(sessionId: String): EngineResult? {
        val routine = routineForSession(sessionId) ?: return null
        return engine.onGraceElapsed(routine, sessionId).also { recordReceipt(routine, it) }
    }

    /** The user's own stop control for a running session. */
    fun onManualStop(sessionId: String): EngineResult? = exitFor(sessionId, EventKind.MANUAL_STOP)

    private fun exitFor(sessionId: String, kind: EventKind): EngineResult? {
        val routine = routineForSession(sessionId) ?: return null
        val event = TriggerEvent(kind, clock.nowMillis())
        return engine.onExitEvent(routine, event, sessionId).also { recordReceipt(routine, it) }
    }

    private fun routineForSession(sessionId: String): Routine? {
        val session = sessions.find(sessionId) ?: return null
        return routines.findRoutine(session.routineId)
    }

    /**
     * Reconciles state after a process restart: an expired session is cleaned
     * up, never replayed, per [SessionEngine.reconcile]. Also retries any
     * cleanup that was left outstanding from a previous run.
     */
    fun onBoot(): List<Session> {
        val byId = routines.all().associateBy { it.id }
        val reconciled = engine.reconcile(byId)
        val retried = engine.retryPendingCleanup(byId)

        (reconciled + retried).forEach { session ->
            byId[session.routineId]?.let { recordReceipt(it, EngineResult.Ended(session)) }
        }
        return reconciled + retried
    }

    /**
     * Checks for Bluetooth coverage gaps (R6, task 4.6): a live session whose
     * device Android now reports as not connected, with no disconnect ever
     * observed for it. [currentlyConnectedDeviceIds] is a live platform
     * reading the caller supplies — same discipline as [onDeviceEvent]'s
     * readings — never assumed here. Called on resume and after every
     * device event, per R6's method.
     */
    fun checkBluetoothCoverage(currentlyConnectedDeviceIds: Set<String>): List<Session> {
        val byId = routines.all().associateBy { it.id }
        val gaps = engine.checkBluetoothCoverage(byId, currentlyConnectedDeviceIds)
        gaps.forEach { session -> byId[session.routineId]?.let { recordReceipt(it, EngineResult.Ended(session)) } }
        return gaps
    }

    // ---------------------------------------------------------- diagnostics

    data class Diagnostics(
        /** The drafter this service is configured to try first. */
        val primaryDrafter: DraftSourceId,
        /** Why the most recent draft fell back, if it did. Null when nothing has fallen back yet. */
        val lastFallbackReason: String?,
    )

    /**
     * Names the actual drafting path, for [com.cues.core.drafting.DraftSourceId]'s
     * whole reason for existing: a canonical parser must never be presented as
     * language understanding, on screen or in a slide.
     */
    fun diagnostics(): Diagnostics {
        val composite = drafter as? CompositeDrafter
        return Diagnostics(
            primaryDrafter = drafter.id,
            lastFallbackReason = composite?.lastFallbackReason,
        )
    }

    // ------------------------------------------------------------- receipts

    private fun recordReceipt(routine: Routine, result: EngineResult) {
        val receipt = Receipts.forResult(routine, result)
        receipts.record(sessionIdFor(routine, result), listOf(receipt.headline) + receipt.lines)
    }

    private fun sessionIdFor(routine: Routine, result: EngineResult): String = when (result) {
        is EngineResult.Started -> result.session.id
        is EngineResult.Ended -> result.session.id
        is EngineResult.ExitScheduled -> result.session.id
        is EngineResult.ExitCancelled -> result.session.id
        // No session exists yet for these — a stable, readable id still lets a
        // UI group "everything that happened to this routine" together.
        is EngineResult.Skipped -> "skip-${routine.id}-${clock.nowMillis()}"
        EngineResult.Ignored -> "ignored-${routine.id}-${clock.nowMillis()}"
    }

    private fun <T> unread(reason: UnknownReason): ContextValue<T> =
        ContextValue.Unknown(reason, com.cues.core.model.ContextSource.USER)

}
