package com.cues.core.cli

import com.cues.core.CueService
import com.cues.core.approval.ArmResult
import com.cues.core.drafting.DraftResult
import com.cues.core.drafting.GrammarParser
import com.cues.core.drafting.PairedDevice
import com.cues.core.model.ActionArgs
import com.cues.core.model.ActionId
import com.cues.core.model.ActionState
import com.cues.core.model.Capability
import com.cues.core.model.EventKind
import com.cues.core.model.OwnedResource
import com.cues.core.model.Routine
import com.cues.core.model.TriggerEvent
import com.cues.core.ports.ActionExecutor
import com.cues.core.ports.ActionOutcome
import com.cues.core.ports.CapabilityProvider
import com.cues.core.ports.Clock
import com.cues.core.receipt.Receipts
import com.cues.core.session.EngineResult
import com.cues.core.store.JsonFileStore
import java.io.File
import java.time.ZoneId
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.runBlocking

/**
 * Drives a full cue lifecycle through [CueService] — draft, approve, arm, a
 * duplicate callback, a reconnect inside the grace window, and a timeout —
 * printing the receipt of each step.
 *
 * `./dev d` proves the decision path in a terminal. This proves the
 * *lifecycle* path the same way: no device, no emulator, nothing but a JDK.
 * Between the two, most of Red Light needs no deploy at all.
 *
 * The store lives in a temp directory deleted on exit, so running this twice
 * in a row starts clean both times.
 */

private val EARBUDS = PairedDevice(
    id = "AA:BB:CC:DD:EE:FF",
    label = "TWS Air Pro",
    aliases = setOf("earbuds", "ear buds", "buds"),
)

private const val HERO = "When my earbuds connect after 6 PM on weekdays, start a 45-minute " +
    "focus timer and quiet notifications. End it if I disconnect."

fun main() = runBlocking {
    val root = createTempDirectory("cues-sim").toFile()
    try {
        run(root)
    } finally {
        root.deleteRecursively()
    }
}

private suspend fun run(root: File) {
    val store = JsonFileStore(root)
    val clock = SimClock(baseMillisAt(hour = 18, minute = 0))
    val executor = LoggingExecutor()
    val service = CueService(
        routines = store,
        sessions = store,
        receipts = store,
        executor = executor,
        clock = clock,
        capabilities = CapabilityProvider {
            setOf(
                Capability.BLUETOOTH_CONNECT, Capability.NOTIFICATION_POLICY_ACCESS,
                Capability.POST_NOTIFICATIONS, Capability.EXACT_ALARM,
            )
        },
        drafter = GrammarParser(listOf(EARBUDS)),
        zoneId = { ZoneId.of("Asia/Kolkata") },
    )

    heading("1. Draft")
    val drafted = (service.draft(HERO) as? DraftResult.Drafted)?.routine
        ?: error("expected a draft from the hero sentence")
    println("   drafted by ${drafted.draftedBy}")

    heading("2. Approve & arm")
    val armed = (service.approveAndArm(drafted) as? ArmResult.Ok)?.routine
        ?: error("expected the hero routine to arm cleanly")
    println("   status: ${armed.status}")

    heading("3. Earbuds connect at 18:30 — should start")
    report(armed, service.onDeviceEvent(connect(clock.at(18, 30), "conn-1")))

    heading("4. The same connection reported twice — should be skipped, no duplicate timer")
    report(armed, service.onDeviceEvent(connect(clock.at(18, 30), "conn-1")))

    val sessionId = store.activeFor(armed.id).single().id

    heading("5. Earbuds disconnect — exit scheduled, grace window opens")
    report(armed, service.onDeviceEvent(disconnect(clock.now, "conn-1")))

    heading("6. Reconnect 5s later, inside the 20s grace — resumed, timer NOT restarted")
    clock.advanceSeconds(5)
    report(armed, service.onDeviceEvent(connect(clock.now, "conn-2")))

    heading("7. The 45-minute timer runs out — ends, releases its own effects")
    clock.advanceMinutes(45)
    report(armed, listOfNotNull(service.onDeadline(sessionId)))

    println()
    println("executor log:")
    executor.log.forEach { println("  $it") }
}

private fun heading(title: String) {
    println()
    println("── $title ".padEnd(70, '─'))
}

private fun report(routine: Routine, results: List<EngineResult>) {
    if (results.isEmpty()) {
        println("   (no armed routine responded to this event)")
        return
    }
    results.forEach { result ->
        val receipt = Receipts.forResult(routine, result)
        println("   ${receipt.headline}")
        receipt.lines.forEach { println("     $it") }
    }
}

private fun connect(atMillis: Long, connectionId: String) = TriggerEvent(
    EventKind.BLUETOOTH_CONNECTED, atMillis, deviceId = EARBUDS.id, connectionSessionId = connectionId,
)

private fun disconnect(atMillis: Long, connectionId: String) = TriggerEvent(
    EventKind.BLUETOOTH_DISCONNECTED, atMillis, deviceId = EARBUDS.id, connectionSessionId = connectionId,
)

private fun baseMillisAt(hour: Int, minute: Int): Long {
    // A fixed, known Monday so the hero routine's weekday condition matches.
    val monday = java.time.LocalDate.of(2026, 9, 28)
    return monday.atTime(hour, minute).atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()
}

/** A clock the sim drives by hand, with a helper to express "today at HH:mm". */
private class SimClock(startMillis: Long) : Clock {
    var now = startMillis
        private set

    override fun nowMillis(): Long = now
    fun advanceSeconds(seconds: Long) { now += seconds * 1_000 }
    fun advanceMinutes(minutes: Long) { now += minutes * 60_000 }
    fun at(hour: Int, minute: Int): Long = baseMillisAt(hour, minute)
}

/** Logs every call instead of touching real hardware — there is none here. */
private class LoggingExecutor : ActionExecutor {
    val log = mutableListOf<String>()

    override fun execute(actionId: ActionId, args: ActionArgs, sessionId: String): ActionOutcome {
        log += "execute $actionId for session $sessionId"
        val owns = when (actionId) {
            ActionId.START_FOCUS_TIMER -> OwnedResource.FOCUS_TIMER
            ActionId.REQUEST_DND -> OwnedResource.DND_CONTRIBUTION
            ActionId.NOTIFY_RESULT -> null
        }
        return ActionOutcome(ActionState.SUCCEEDED, acquired = owns)
    }

    override fun release(resource: OwnedResource, sessionId: String): ActionOutcome {
        log += "release $resource for session $sessionId"
        return ActionOutcome(ActionState.SUCCEEDED)
    }
}
