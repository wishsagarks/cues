package com.cues.core.rehearsal

import com.cues.core.eval.Evaluator
import com.cues.core.eval.FreshnessPolicy
import com.cues.core.eval.Truth
import com.cues.core.model.*
import com.cues.core.ports.ActionExecutor
import com.cues.core.ports.ActionOutcome
import com.cues.core.ports.Clock
import com.cues.core.ports.SessionStore
import com.cues.core.receipt.Receipts
import com.cues.core.session.EngineResult
import com.cues.core.session.SessionEngine

/** One labelled scenario and what the cue did in it. */
data class RehearsalRow(
    val label: String,
    val outcome: String,
    val explanation: List<String>,
) {
    /** Every row is marked, so a screenshot can never be mistaken for a real run. */
    val synthetic: Boolean get() = true
}

data class RehearsalReport(val rows: List<RehearsalRow>)

/**
 * An executor that does nothing and owns nothing real.
 *
 * Private to this file and not configurable. The rehearsal engine is
 * constructed with this and only this, so a rehearsal has no reference to a
 * real adapter to call. RH-01's guarantee is structural rather than a promise
 * to be careful: there is no code path from this screen to the phone's state.
 */
private class MockExecutor : ActionExecutor {
    override fun execute(actionId: ActionId, args: ActionArgs, sessionId: String): ActionOutcome {
        val owns = when (actionId) {
            ActionId.START_FOCUS_TIMER -> OwnedResource.FOCUS_TIMER
            ActionId.REQUEST_DND -> OwnedResource.DND_CONTRIBUTION
            ActionId.NOTIFY_RESULT -> null
        }
        return ActionOutcome(ActionState.SUCCEEDED, detail = "sample", acquired = owns)
    }

    override fun release(resource: OwnedResource, sessionId: String) =
        ActionOutcome(ActionState.SUCCEEDED, detail = "sample")
}

/** An in-memory store discarded when the rehearsal ends. */
private class ScratchStore : SessionStore {
    private val sessions = linkedMapOf<String, Session>()
    override fun save(session: Session) { sessions[session.id] = session }
    override fun find(sessionId: String): Session? = sessions[sessionId]
    override fun activeFor(routineId: String) = sessions.values.filter { it.routineId == routineId }
    override fun allUnfinished() = sessions.values.toList()
}

/**
 * Shows what a cue would do, before it is armed.
 *
 * The scenarios run through [SessionEngine] and [Evaluator] — the same code the
 * live runtime uses. That shared path is the only thing that makes a rehearsal
 * worth watching: a preview built from separate demonstration logic would
 * predict the behaviour of the preview.
 *
 * What it cannot show is whether a future OS call will succeed. It demonstrates
 * the decisions, not the permissions, and the UI says so.
 */
object Rehearsal {

    fun run(routine: Routine, atMillis: Long = System.currentTimeMillis()): RehearsalReport {
        val armed = routine.copy(status = RoutineStatus.ARMED)

        return RehearsalReport(
            listOfNotNull(
                eligible(armed, atMillis),
                wrongDay(armed, atMillis),
                tooEarly(armed, atMillis),
                duplicate(armed, atMillis),
                disconnect(armed, atMillis),
                timeout(armed, atMillis),
                unknownContext(armed, atMillis),
            ),
        )
    }

    // ------------------------------------------------------------ scenarios

    private fun eligible(routine: Routine, now: Long): RehearsalRow {
        val (engine, _) = rig(now)
        val result = engine.onTriggerEvent(routine, connect(routine, now), matchingContext(routine, now))
        return row("A matching ${routine.triggerLabel()}", routine, result)
    }

    private fun wrongDay(routine: Routine, now: Long): RehearsalRow? {
        val days = routine.conditions.filterIsInstance<Condition.DaysOfWeek>().firstOrNull()?.days ?: return null
        val excluded = Day.entries.firstOrNull { it !in days } ?: return null

        val (engine, _) = rig(now)
        val context = matchingContext(routine, now).copy(
            localDay = ContextValue.Known(excluded, ContextSource.REHEARSAL, now),
        )
        return row("On a day outside the cue", routine, engine.onTriggerEvent(routine, connect(routine, now), context))
    }

    private fun tooEarly(routine: Routine, now: Long): RehearsalRow? {
        val window = routine.conditions.filterIsInstance<Condition.TimeWindow>().firstOrNull() ?: return null
        val before = LocalTimeOfDay(
            (window.startInclusive.hour - 2 + 24) % 24,
            window.startInclusive.minute,
        )

        val (engine, _) = rig(now)
        val context = matchingContext(routine, now).copy(
            localTime = ContextValue.Known(before, ContextSource.REHEARSAL, now),
        )
        return row("Outside the time window", routine, engine.onTriggerEvent(routine, connect(routine, now), context))
    }

    private fun duplicate(routine: Routine, now: Long): RehearsalRow {
        val (engine, _) = rig(now)
        val context = matchingContext(routine, now)
        engine.onTriggerEvent(routine, connect(routine, now), context)
        // The same connection reported twice, which real adapters do.
        val second = engine.onTriggerEvent(routine, connect(routine, now), context)
        return row("The same connection reported twice", routine, second)
    }

    private fun disconnect(routine: Routine, now: Long): RehearsalRow? {
        if (EndCondition.TriggerReversed !in routine.endConditions) return null

        val (engine, clock) = rig(now)
        engine.onTriggerEvent(routine, connect(routine, now), matchingContext(routine, now))
        clock.now = now + 5 * 60_000
        val result = engine.onExitEvent(routine, reverse(routine, clock.now))
        return row("The trigger goes away", routine, result)
    }

    private fun timeout(routine: Routine, now: Long): RehearsalRow? {
        val minutes = routine.endConditions.filterIsInstance<EndCondition.Duration>()
            .minByOrNull { it.minutes }?.minutes ?: return null

        val (engine, clock) = rig(now)
        engine.onTriggerEvent(routine, connect(routine, now), matchingContext(routine, now))
        clock.now = now + minutes * 60_000L
        val result = engine.onExitEvent(routine, TriggerEvent(EventKind.DEADLINE_REACHED, clock.now))
        return row("The timer runs out", routine, result)
    }

    private fun unknownContext(routine: Routine, now: Long): RehearsalRow? {
        if (routine.conditions.none { it is Condition.ChargingState }) return null

        val (engine, _) = rig(now)
        val context = matchingContext(routine, now).copy(
            charging = ContextValue.Unknown(UnknownReason.PERMISSION_DENIED, ContextSource.BATTERY_MANAGER),
        )
        return row("Something cannot be read", routine, engine.onTriggerEvent(routine, connect(routine, now), context))
    }

    // -------------------------------------------------------------- rigging

    private class MutableClock(var now: Long) : Clock {
        override fun nowMillis(): Long = now
    }

    /** A fresh engine per scenario, wired to nothing that can touch the phone. */
    private fun rig(now: Long): Pair<SessionEngine, MutableClock> {
        val clock = MutableClock(now)
        var counter = 0
        return SessionEngine(
            store = ScratchStore(),
            executor = MockExecutor(),
            clock = clock,
            // Rehearsal supplies its own timestamps, so ageing them out is noise.
            freshness = FreshnessPolicy.NONE,
            idGenerator = { "sample-${++counter}" },
        ) to clock
    }

    private fun row(label: String, routine: Routine, result: EngineResult): RehearsalRow {
        val receipt = Receipts.forResult(routine, result)
        return RehearsalRow(label, receipt.headline, receipt.lines)
    }

    /** A context in which every one of the cue's conditions holds. */
    private fun matchingContext(routine: Routine, now: Long): ContextSnapshot {
        val day = routine.conditions.filterIsInstance<Condition.DaysOfWeek>()
            .firstOrNull()?.days?.firstOrNull() ?: Day.MON

        val window = routine.conditions.filterIsInstance<Condition.TimeWindow>().firstOrNull()
        val time = window?.let {
            val minutes = (it.startInclusive.minutesOfDay + 30) % (24 * 60)
            LocalTimeOfDay(minutes / 60, minutes % 60)
        } ?: LocalTimeOfDay(18, 30)

        val charging = routine.conditions.filterIsInstance<Condition.ChargingState>()
            .firstOrNull()?.charging ?: true

        return ContextSnapshot(
            nowMillis = now,
            localDay = ContextValue.Known(day, ContextSource.REHEARSAL, now),
            localTime = ContextValue.Known(time, ContextSource.REHEARSAL, now),
            zoneId = "local",
            charging = ContextValue.Known(charging, ContextSource.REHEARSAL, now),
            connectedDeviceIds = ContextValue.Known(emptySet(), ContextSource.REHEARSAL, now),
        )
    }

    private fun Routine.triggerLabel(): String = when (val t = trigger) {
        is Trigger.BluetoothConnection -> "${t.deviceLabel} connection"
        is Trigger.Charging -> "charger connection"
        Trigger.Manual -> "manual run"
    }

    /**
     * The event that would start this cue, built from its own trigger.
     *
     * The device identity has to be the real one: an event that does not carry
     * it is rejected as "a different device", which would make every rehearsal
     * row read as a non-match for the wrong reason.
     */
    private fun connect(routine: Routine, now: Long): TriggerEvent = when (val t = routine.trigger) {
        is Trigger.BluetoothConnection -> TriggerEvent(
            kind = when (t.transition) {
                DeviceTransition.CONNECTED -> EventKind.BLUETOOTH_CONNECTED
                DeviceTransition.DISCONNECTED -> EventKind.BLUETOOTH_DISCONNECTED
            },
            atMillis = now,
            deviceId = t.deviceId,
            connectionSessionId = "sample-connection",
            provenance = EventProvenance.REHEARSAL,
        )

        is Trigger.Charging -> TriggerEvent(
            kind = when (t.transition) {
                PowerTransition.PLUGGED_IN -> EventKind.POWER_CONNECTED
                PowerTransition.UNPLUGGED -> EventKind.POWER_DISCONNECTED
            },
            atMillis = now,
            connectionSessionId = "sample-connection",
            provenance = EventProvenance.REHEARSAL,
        )

        Trigger.Manual -> TriggerEvent(EventKind.MANUAL_RUN, now, provenance = EventProvenance.REHEARSAL)
    }

    private fun reverse(routine: Routine, now: Long): TriggerEvent = when (val t = routine.trigger) {
        is Trigger.BluetoothConnection -> TriggerEvent(
            EventKind.BLUETOOTH_DISCONNECTED,
            now,
            deviceId = t.deviceId,
            connectionSessionId = "sample-connection",
            provenance = EventProvenance.REHEARSAL,
        )

        else -> TriggerEvent(EventKind.POWER_DISCONNECTED, now, provenance = EventProvenance.REHEARSAL)
    }
}
