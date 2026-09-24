package com.cues.core.session

import com.cues.core.eval.Decision
import com.cues.core.eval.Evaluator
import com.cues.core.eval.FreshnessPolicy
import com.cues.core.eval.Reason
import com.cues.core.eval.ReasonCode
import com.cues.core.eval.Truth
import com.cues.core.model.*
import com.cues.core.ports.ActionExecutor
import com.cues.core.ports.Clock
import com.cues.core.ports.DeviceAttention
import com.cues.core.ports.SessionStore
import com.cues.core.registry.ActionRegistry
import com.cues.core.registry.Presence
import com.cues.core.signals.SignalRegistry

/** What the engine did, and why, in a form a receipt can render without interpreting. */
sealed interface EngineResult {
    data class Started(val session: Session, val decision: Decision) : EngineResult
    data class Skipped(val reasons: List<Reason>) : EngineResult
    data class Ended(val session: Session) : EngineResult
    data class ExitScheduled(val session: Session, val atMillis: Long) : EngineResult
    data class ExitCancelled(val session: Session) : EngineResult
    data object Ignored : EngineResult
}

/**
 * Runs the lifecycle of a cue: admission, effects, exit, cleanup.
 *
 * Everything here is deterministic. No model is consulted at any point past
 * approval — the engine reads an approved routine, an observed event and a
 * context snapshot, and follows rules. That is what makes the behaviour
 * predictable enough to leave running unattended for months.
 *
 * The engine holds no state of its own. Everything that must survive a process
 * death lives in [SessionStore], which is why crash recovery is a matter of
 * re-reading the store rather than of hoping.
 */
class SessionEngine(
    private val store: SessionStore,
    private val executor: ActionExecutor,
    private val clock: Clock,
    private val freshness: FreshnessPolicy = FreshnessPolicy(),
    private val idGenerator: () -> String = { "session-" + java.util.UUID.randomUUID() },
    /** Defaults to "always present", which is exactly today's behaviour for every action that predates this. */
    private val attention: DeviceAttention = DeviceAttention { true },
) {

    // ----------------------------------------------------------- admission

    /**
     * Considers starting a session.
     *
     * The order of checks matters. Arming, duplicates and cooldown are settled
     * before conditions are evaluated, because "this is the same connection you
     * already told me about" is a better explanation than a weekday check that
     * was never the reason.
     */
    fun onTriggerEvent(routine: Routine, event: TriggerEvent, context: ContextSnapshot): EngineResult {
        if (routine.status != RoutineStatus.ARMED) {
            return EngineResult.Skipped(
                listOf(
                    Reason(
                        ReasonCode.ROUTINE_NOT_ARMED,
                        Truth.NO_MATCH,
                        "This cue is ${routine.status.name.lowercase()}, so it did not run.",
                    ),
                ),
            )
        }

        val active = store.activeFor(routine.id)
        val admissionKey = admissionKey(routine, event)

        // A reconnect inside the grace window resumes the session that was on
        // its way out. Crucially it does not restart the timer: the user asked
        // for forty-five minutes of focus, not forty-five more.
        active.firstOrNull { it.state == SessionState.EXIT_PENDING }?.let { pending ->
            if (isReversal(routine, event).not() && withinGrace(routine, pending, event)) {
                val resumed = pending.copy(state = SessionState.ACTIVE, pendingExitAtMillis = null)
                store.save(resumed)
                return EngineResult.ExitCancelled(resumed)
            }
        }

        // The same physical connection reported twice. Android does this.
        active.firstOrNull { it.admissionKey == admissionKey }?.let {
            return EngineResult.Skipped(
                listOf(
                    Reason(
                        ReasonCode.DUPLICATE_EVENT_SAME_CONNECTION,
                        Truth.NO_MATCH,
                        "This cue is already running for this connection.",
                    ),
                ),
            )
        }

        if (active.any { it.state.isLive() }) {
            return EngineResult.Skipped(
                listOf(
                    Reason(
                        ReasonCode.SESSION_ALREADY_ACTIVE,
                        Truth.NO_MATCH,
                        "This cue is already running.",
                    ),
                ),
            )
        }

        cooldownReason(routine, event)?.let { return EngineResult.Skipped(listOf(it)) }

        val decision = Evaluator.evaluate(routine, event, context, freshness)
        if (!decision.shouldStart) return EngineResult.Skipped(decision.reasons)

        return EngineResult.Started(start(routine, event, context, admissionKey), decision)
    }

    private fun start(
        routine: Routine,
        event: TriggerEvent,
        context: ContextSnapshot,
        admissionKey: String,
    ): Session {
        val now = clock.nowMillis()
        // Persisted before a single side effect runs. If the process dies in
        // the middle of starting, the record of what might need releasing
        // already exists.
        var session = Session(
            id = idGenerator(),
            routineId = routine.id,
            routineVersion = routine.version,
            startedAtMillis = now,
            admissionKey = admissionKey,
            observedInputs = context,
            state = SessionState.STARTING,
            deadlineMillis = null,
            actions = routine.actions.map { ActionRecord(it.actionId, ActionState.NOT_STARTED) },
        )
        session = session.copy(
            deadlineMillis = routine.endConditions.mapNotNull { SignalRegistry.schedule(it, session) }.minOrNull(),
        )
        store.save(session)

        val records = mutableListOf<ActionRecord>()
        val obligations = mutableListOf<CleanupObligation>()

        routine.actions.forEach { spec ->
            val definition = ActionRegistry.definition(spec.actionId)
            if (definition?.presence == Presence.NEEDS_USER && !attention.isUserPresent()) {
                // Never attempted: a NEEDS_USER action with nobody at the
                // phone is not a failed attempt, it is a deferred one. It
                // waits here, unattempted, until retryPendingActions is
                // called once someone is present, or the session ends still
                // waiting — see the PENDING -> BLOCKED conversion below.
                records += ActionRecord(spec.actionId, ActionState.PENDING, "Ready when you are.")
                return@forEach
            }
            val outcome = executor.execute(spec.actionId, spec.args, session.id)
            records += ActionRecord(spec.actionId, outcome.state, outcome.detail)
            // Ownership is recorded the moment it is acquired, not at exit.
            outcome.acquired?.let { obligations += CleanupObligation(it, clock.nowMillis()) }
        }

        // A blocked action is not a success. A session where the timer ran but
        // the quiet rule was refused is PARTIAL, and says so — it still owns
        // the timer, so it is still live and still owes a cleanup.
        val allSucceeded = records.all { it.state == ActionState.SUCCEEDED }

        session = session.copy(
            state = if (allSucceeded) SessionState.ACTIVE else SessionState.PARTIAL,
            actions = records,
            obligations = obligations,
        )
        store.save(session)
        return session
    }

    // ---------------------------------------------------------------- exit

    /**
     * Handles an event that may end a running session.
     *
     * A disconnect does not end anything immediately when a grace period is
     * configured; it schedules an exit. Short dropouts are extremely common
     * with earbuds, and ending plus restarting on every one of them is the
     * behaviour users describe as the routine "going haywire".
     */
    fun onExitEvent(routine: Routine, event: TriggerEvent, sessionId: String? = null): EngineResult {
        val session = sessionId?.let { store.find(it) }
            ?: store.activeFor(routine.id).firstOrNull { it.state.isLive() }
            ?: return EngineResult.Ignored

        return when (event.kind) {
            EventKind.MANUAL_STOP -> EngineResult.Ended(end(routine, session, EndReason.MANUAL_STOP))

            EventKind.DEADLINE_REACHED -> {
                val deadline = session.deadlineMillis
                if (deadline == null || clock.nowMillis() < deadline) {
                    // An alarm that fired early, or for a session whose deadline
                    // moved. Completing the timer here would shorten it.
                    EngineResult.Ignored
                } else {
                    EngineResult.Ended(end(routine, session, EndReason.DEADLINE_REACHED))
                }
            }

            else -> {
                if (!isReversal(routine, event)) return EngineResult.Ignored
                if (EndCondition.TriggerReversed !in routine.endConditions) return EngineResult.Ignored

                val graceMillis = routine.rearmPolicy.reconnectGraceSeconds * 1_000L
                if (graceMillis <= 0) {
                    EngineResult.Ended(end(routine, session, EndReason.TRIGGER_REVERSED))
                } else {
                    val exitAt = clock.nowMillis() + graceMillis
                    val pending = session.copy(state = SessionState.EXIT_PENDING, pendingExitAtMillis = exitAt)
                    store.save(pending)
                    EngineResult.ExitScheduled(pending, exitAt)
                }
            }
        }
    }

    /** Called when a scheduled exit's grace period has elapsed without a reconnect. */
    fun onGraceElapsed(routine: Routine, sessionId: String): EngineResult {
        val session = store.find(sessionId) ?: return EngineResult.Ignored
        if (session.state != SessionState.EXIT_PENDING) return EngineResult.Ignored
        val exitAt = session.pendingExitAtMillis ?: return EngineResult.Ignored
        if (clock.nowMillis() < exitAt) return EngineResult.Ignored

        return EngineResult.Ended(end(routine, session, EndReason.TRIGGER_REVERSED))
    }

    /**
     * Ends a session and releases what it owns.
     *
     * Idempotent: ending an already-ended session returns it unchanged, because
     * a deadline alarm and a disconnect can easily arrive within milliseconds
     * of each other and both are entitled to try.
     */
    fun end(routine: Routine, session: Session, reason: EndReason): Session {
        if (!session.state.isLive()) return session
        return releaseAndFinalize(routine, session, reason)
    }

    /**
     * Releases a session's owned resources and files the outcome.
     *
     * Separate from [end] so that a retry of a CLEANUP_PENDING session can
     * re-enter the release path. That session is deliberately not "live" — it
     * cannot start anything or end again — but it still owns something, and a
     * resource we failed to release once is exactly the thing worth trying
     * again when the user grants the permission.
     */
    private fun releaseAndFinalize(routine: Routine, session: Session, reason: EndReason): Session {
        // Expiry is a block, not a silent drop: a NEEDS_USER action that never
        // got its moment while someone was present is stamped BLOCKED here,
        // with a detail Receipts recognizes as new information worth telling
        // the user again in the Ended receipt, not the Started one.
        val settledActions = session.actions.map { record ->
            if (record.state == ActionState.PENDING) {
                ActionRecord(record.actionId, ActionState.BLOCKED, EXPIRED_WHILE_PENDING_DETAIL)
            } else {
                record
            }
        }
        val ending = session.copy(state = SessionState.ENDING, actions = settledActions)
        store.save(ending)

        val released = mutableListOf<CleanupObligation>()
        var anyFailure = false

        ending.obligations.forEach { obligation ->
            if (obligation.released) {
                released += obligation
                return@forEach
            }

            // The user changed our effect by hand during the session. Their
            // choice is more recent and better informed than our schedule.
            if (routine.cleanupPolicy.respectUserOverride && ending.userOverride) {
                released += obligation.copy(released = true, failureDetail = "Left as you set it.")
                return@forEach
            }

            // Each obligation is attempted independently. One failed release
            // must not strand another resource — a timer we cannot cancel is
            // no reason to leave the phone silent as well.
            val outcome = runCatching { executor.release(obligation.resource, ending.id) }
                .getOrElse { com.cues.core.ports.ActionOutcome(ActionState.COMPENSATION_FAILED, it.message) }

            if (outcome.state == ActionState.SUCCEEDED || outcome.state == ActionState.COMPENSATED) {
                released += obligation.copy(released = true)
            } else {
                anyFailure = true
                released += obligation.copy(released = false, failureDetail = outcome.detail ?: "Release failed.")
            }
        }

        val ended = ending.copy(
            // An unreleased resource keeps the session visibly unfinished
            // rather than quietly filed as done. A terminal SessionState has
            // no PARTIAL-but-finished member — PARTIAL is deliberately one of
            // the *live* states (isLive() relies on that for admission: a
            // still-running session with one blocked action must still block
            // a duplicate start) — so an action that never succeeded, expired
            // PENDING included, stays visible in [ActionRecord] and in the
            // Ended receipt (see EXPIRED_WHILE_PENDING_DETAIL) rather than by
            // repurposing the terminal state itself.
            state = if (anyFailure) SessionState.CLEANUP_PENDING else SessionState.COMPLETED,
            obligations = released,
            // Preserved across a cleanup retry: the session ended when it
            // ended, whatever time we finally managed to let go of things.
            endedAtMillis = session.endedAtMillis ?: clock.nowMillis(),
            endReason = reason,
            pendingExitAtMillis = null,
        )
        store.save(ended)
        return ended
    }

    /** Records that the user changed one of our effects themselves. */
    fun markUserOverride(sessionId: String) {
        store.find(sessionId)?.let { store.save(it.copy(userOverride = true)) }
    }

    /**
     * Called once someone is confirmed present — a tap on the "Ready when
     * you are" notification, or the app resuming to a live session. Attempts
     * every [ActionState.PENDING] action now, exactly as [start] would have
     * attempted it at the time.
     *
     * Takes [routine], the same way [onGraceElapsed] and [onExitEvent] do,
     * rather than caching a [com.cues.core.model.ActionSpec.args] copy on the
     * session: the routine is already the durable source of an action's
     * arguments, and re-reading it here means a process restart between
     * "waiting" and "retried" needs no state of its own to survive.
     *
     * A no-op, safely, on a session that has since ended or holds nothing
     * pending — the caller does not have to know which is true before asking.
     */
    fun retryPendingActions(routine: Routine, sessionId: String): Session? {
        val session = store.find(sessionId) ?: return null
        if (!session.state.isLive()) return null
        if (session.actions.none { it.state == ActionState.PENDING }) return session

        val argsByAction = routine.actions.associate { it.actionId to it.args }
        val obligations = session.obligations.toMutableList()
        val actions = session.actions.map { record ->
            if (record.state != ActionState.PENDING) return@map record
            val args = argsByAction[record.actionId] ?: ActionArgs.None
            val outcome = executor.execute(record.actionId, args, session.id)
            outcome.acquired?.let { obligations += CleanupObligation(it, clock.nowMillis()) }
            ActionRecord(record.actionId, outcome.state, outcome.detail)
        }

        val updated = session.copy(
            actions = actions,
            obligations = obligations,
            state = if (actions.all { it.state == ActionState.SUCCEEDED }) SessionState.ACTIVE else SessionState.PARTIAL,
        )
        store.save(updated)
        return updated
    }

    // ------------------------------------------------------------ recovery

    /**
     * Brings persisted sessions back in line with reality after a restart.
     *
     * The rule that matters: an expired session is *cleaned up*, never replayed.
     * Waking to find a session whose deadline passed while the process was dead
     * means releasing what it still holds — not starting its actions again and
     * silently granting the user another forty-five minutes of silence.
     */
    fun reconcile(routines: Map<String, Routine>): List<Session> {
        val now = clock.nowMillis()

        return store.allUnfinished().mapNotNull { session ->
            val routine = routines[session.routineId] ?: return@mapNotNull null

            val expired = session.deadlineMillis?.let { it <= now } == true
            val graceElapsed = session.pendingExitAtMillis?.let { it <= now } == true

            when {
                expired -> end(routine, session, EndReason.DEADLINE_REACHED)
                graceElapsed -> end(routine, session, EndReason.TRIGGER_REVERSED)
                // Still legitimately running. Left alone.
                else -> null
            }
        }
    }

    /**
     * Detects and closes Bluetooth coverage gaps (R6, task 4.6).
     *
     * A live session admitted by a [Trigger.BluetoothConnection] whose device
     * is not in [currentlyConnectedDeviceIds] means the device disconnected
     * without Cues ever observing the event — most plausibly while the
     * process was dead, since a live process would have seen the
     * `ACL_DISCONNECTED` broadcast and ended the session the ordinary way.
     * Ends it, distinctly from [EndReason.TRIGGER_REVERSED], so the receipt
     * says a gap was detected rather than claiming a clean, observed exit.
     * Never restarts anything — the same rule [reconcile] follows for an
     * expired session.
     */
    fun checkBluetoothCoverage(routines: Map<String, Routine>, currentlyConnectedDeviceIds: Set<String>): List<Session> =
        store.allUnfinished().mapNotNull { session ->
            if (!session.state.isLive()) return@mapNotNull null
            val routine = routines[session.routineId] ?: return@mapNotNull null
            val trigger = routine.trigger as? Trigger.BluetoothConnection ?: return@mapNotNull null
            if (trigger.transition != DeviceTransition.CONNECTED) return@mapNotNull null
            if (trigger.deviceId in currentlyConnectedDeviceIds) return@mapNotNull null
            end(routine, session, EndReason.COVERAGE_GAP)
        }

    /** Retries releases for sessions that ended owing something. */
    fun retryPendingCleanup(routines: Map<String, Routine>): List<Session> =
        store.allUnfinished()
            .filter { it.state == SessionState.CLEANUP_PENDING }
            .mapNotNull { session ->
                val routine = routines[session.routineId] ?: return@mapNotNull null
                releaseAndFinalize(routine, session, session.endReason ?: EndReason.RECONCILED_EXPIRED)
            }

    // ------------------------------------------------------------- helpers

    /**
     * Identity of the connection that admitted a session.
     *
     * Keyed on the OS connection session where one exists, rather than on a
     * time bucket. Two callbacks for one connection collapse; a genuine
     * disconnect and reconnect do not.
     */
    private fun admissionKey(routine: Routine, event: TriggerEvent): String {
        val connection = event.connectionSessionId ?: event.deviceId ?: event.atMillis.toString()
        return "${routine.id}:v${routine.version}:${event.kind}:$connection"
    }

    private fun isReversal(routine: Routine, event: TriggerEvent): Boolean =
        SignalRegistry.reverses(routine.trigger, event)

    private fun withinGrace(routine: Routine, session: Session, event: TriggerEvent): Boolean {
        val exitAt = session.pendingExitAtMillis ?: return false
        return event.atMillis <= exitAt && routine.rearmPolicy.reconnectGraceSeconds > 0
    }

    private fun cooldownReason(routine: Routine, event: TriggerEvent): Reason? {
        val cooldownMillis = routine.rearmPolicy.cooldownSeconds * 1_000L
        if (cooldownMillis <= 0) return null

        val lastEnd = store.activeFor(routine.id).mapNotNull { it.endedAtMillis }.maxOrNull() ?: return null
        val since = event.atMillis - lastEnd
        if (since >= cooldownMillis) return null

        return Reason(
            ReasonCode.COOLDOWN_ACTIVE,
            Truth.NO_MATCH,
            "This cue ran ${since / 1000} seconds ago and waits " +
                "${routine.rearmPolicy.cooldownSeconds} seconds between runs.",
        )
    }
}

/** True while a session still owns resources or could still end. */
fun SessionState.isLive(): Boolean = when (this) {
    SessionState.STARTING, SessionState.ACTIVE, SessionState.EXIT_PENDING, SessionState.PARTIAL -> true
    SessionState.ENDING, SessionState.COMPLETED, SessionState.CANCELLED,
    SessionState.CLEANUP_PENDING, SessionState.FAILED,
    -> false
}
