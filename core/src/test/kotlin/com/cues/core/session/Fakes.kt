package com.cues.core.session

import com.cues.core.model.*
import com.cues.core.ports.ActionExecutor
import com.cues.core.ports.ActionOutcome
import com.cues.core.ports.Clock
import com.cues.core.ports.DeviceAttention
import com.cues.core.ports.SessionStore
import com.cues.core.ports.SignalAdapter
import com.cues.core.ports.ListenerHealth

/** A [DeviceAttention] a test can flip mid-scenario, unlike a fixed lambda. */
class ToggleAttention(var present: Boolean = true) : DeviceAttention {
    override fun isUserPresent(): Boolean = present
}

/** A clock the test drives by hand, so lifecycle tests never sleep. */
class FakeClock(var now: Long) : Clock {
    override fun nowMillis(): Long = now
    fun advanceSeconds(seconds: Long) { now += seconds * 1_000 }
    fun advanceMinutes(minutes: Long) = advanceSeconds(minutes * 60)
}

class InMemorySessionStore : SessionStore {
    private val sessions = linkedMapOf<String, Session>()

    override fun save(session: Session) { sessions[session.id] = session }
    override fun find(sessionId: String): Session? = sessions[sessionId]
    override fun activeFor(routineId: String): List<Session> = sessions.values.filter { it.routineId == routineId }
    override fun allUnfinished(): List<Session> = sessions.values.filter {
        it.state != SessionState.COMPLETED && it.state != SessionState.CANCELLED
    }
    override fun recent(sinceMillis: Long): List<Session> =
        sessions.values.filter { (it.endedAtMillis ?: Long.MAX_VALUE) >= sinceMillis }

    val all: List<Session> get() = sessions.values.toList()
}

class FakeSignalAdapter(override val key: String) : SignalAdapter {
    var startedWith: List<Routine> = emptyList()
        private set
    var running: Boolean = false
        private set

    override fun start(armed: List<Routine>) {
        startedWith = armed
        running = true
    }

    override fun stop() { running = false }

    override fun health() = ListenerHealth(key, running)
}

/**
 * Records every call, and can be told to refuse specific ones.
 *
 * Refusal rather than exception is the common case on Android: the OS declines
 * because a permission is missing, and the honest outcome is BLOCKED.
 */
class RecordingExecutor(
    private val blocked: Set<ActionId> = emptySet(),
    private val unreleasable: Set<OwnedResource> = emptySet(),
    private val throwOnRelease: Set<OwnedResource> = emptySet(),
) : ActionExecutor {

    val executed = mutableListOf<ActionId>()
    val released = mutableListOf<OwnedResource>()

    override fun execute(actionId: ActionId, args: ActionArgs, sessionId: String): ActionOutcome {
        executed += actionId
        if (actionId in blocked) {
            return ActionOutcome(ActionState.BLOCKED, "Permission not granted.")
        }
        val owns = com.cues.core.registry.ActionRegistry.definition(actionId)?.owns
        return ActionOutcome(ActionState.SUCCEEDED, acquired = owns)
    }

    override fun release(resource: OwnedResource, sessionId: String): ActionOutcome {
        released += resource
        if (resource in throwOnRelease) error("release crashed for $resource")
        if (resource in unreleasable) {
            return ActionOutcome(ActionState.COMPENSATION_FAILED, "Could not release $resource.")
        }
        return ActionOutcome(ActionState.SUCCEEDED)
    }
}
