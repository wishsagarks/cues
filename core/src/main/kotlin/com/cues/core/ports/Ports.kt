package com.cues.core.ports

import com.cues.core.model.ActionId
import com.cues.core.model.ActionArgs
import com.cues.core.model.ActionState
import com.cues.core.model.OwnedResource
import com.cues.core.model.Session

/**
 * The seams between decision-making and the world.
 *
 * Everything Cues *decides* lives in :core and is pure. Everything Cues *does*
 * arrives through one of these interfaces, implemented by Android in :app and
 * by fakes in tests. That split is what lets the lifecycle rules — duplicate
 * suppression, reconnect grace, cleanup ordering, crash reconciliation — be
 * tested exhaustively in milliseconds, on any machine, with no phone attached.
 */

/** Wall-clock time, injectable so tests can move time without sleeping. */
fun interface Clock {
    fun nowMillis(): Long
}

interface SessionStore {
    fun save(session: Session)
    fun find(sessionId: String): Session?
    fun activeFor(routineId: String): List<Session>
    fun allUnfinished(): List<Session>
}

/** The result of asking the registry to perform one action. */
data class ActionOutcome(
    val state: ActionState,
    val detail: String? = null,
    /** What this action now makes the session responsible for releasing. */
    val acquired: OwnedResource? = null,
)

/**
 * Performs allowlisted actions. The only path from a routine to the device.
 *
 * Rehearsal is handed a mock implementation of this interface and nothing
 * else, which is why a rehearsal structurally cannot change phone state.
 */
interface ActionExecutor {
    fun execute(actionId: ActionId, args: ActionArgs, sessionId: String): ActionOutcome

    /**
     * Releases one owned resource. Must be idempotent: releasing something
     * already released is a success, not an error, because recovery paths will
     * legitimately try twice.
     */
    fun release(resource: OwnedResource, sessionId: String): ActionOutcome
}

interface ReceiptSink {
    fun record(sessionId: String, lines: List<String>)
}
