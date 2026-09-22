package com.cues.core

import com.cues.core.approval.ArmResult
import com.cues.core.approval.Approvals
import com.cues.core.approval.DeleteResult
import com.cues.core.context.SnapshotBuilder
import com.cues.core.drafting.CompositeDrafter
import com.cues.core.drafting.DraftResult
import com.cues.core.model.DraftSourceId
import com.cues.core.drafting.RoutineDrafter
import com.cues.core.eval.ReasonCode
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
     */
    fun onDeviceEvent(
        event: TriggerEvent,
        charging: ContextValue<Boolean> = unread(UnknownReason.NEVER_OBSERVED),
        connectedDeviceIds: ContextValue<Set<String>> = unread(UnknownReason.NEVER_OBSERVED),
    ): List<EngineResult> {
        val snapshot = SnapshotBuilder.build(event.atMillis, zoneId(), charging, connectedDeviceIds)

        return routines.armed().flatMap { routine ->
            listOf(
                engine.onExitEvent(routine, event),
                engine.onTriggerEvent(routine, event, snapshot),
            ).filter { it.isMeaningfulFor(routine) }.onEach { recordReceipt(routine, it) }
        }
    }

    /** The exact-alarm callback for one session's deadline. */
    fun onDeadline(routineId: String, sessionId: String): EngineResult? =
        exitFor(routineId, sessionId, EventKind.DEADLINE_REACHED)

    /** The reconnect grace window for one session has elapsed without a reconnect. */
    fun onGraceElapsed(routineId: String, sessionId: String): EngineResult? {
        val routine = routines.findRoutine(routineId) ?: return null
        return engine.onGraceElapsed(routine, sessionId).also { recordReceipt(routine, it) }
    }

    /** The user's own stop control for a running session. */
    fun onManualStop(routineId: String, sessionId: String): EngineResult? =
        exitFor(routineId, sessionId, EventKind.MANUAL_STOP)

    private fun exitFor(routineId: String, sessionId: String, kind: EventKind): EngineResult? {
        val routine = routines.findRoutine(routineId) ?: return null
        val event = TriggerEvent(kind, clock.nowMillis())
        return engine.onExitEvent(routine, event, sessionId).also { recordReceipt(routine, it) }
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

    /**
     * False for the two outcomes that are pure noise from trying every armed
     * routine against every event: "wrong device" and "wrong event kind".
     * Everything else — a real skip reason, a start, an end, a scheduled or
     * cancelled exit — is worth a receipt.
     */
    private fun EngineResult.isMeaningfulFor(routine: Routine): Boolean = when (this) {
        EngineResult.Ignored -> false
        is EngineResult.Skipped -> reasons.size != 1 || reasons.single().code !in TRIGGER_MISMATCH_CODES
        else -> true
    }

    private fun <T> unread(reason: UnknownReason): ContextValue<T> =
        ContextValue.Unknown(reason, com.cues.core.model.ContextSource.USER)

    private companion object {
        val TRIGGER_MISMATCH_CODES = setOf(ReasonCode.TRIGGER_KIND_MISMATCH, ReasonCode.TRIGGER_DEVICE_MISMATCH)
    }
}
