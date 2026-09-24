package com.cues.core.cli

import com.cues.core.CueService
import com.cues.core.approval.ArmResult
import com.cues.core.drafting.DraftResult
import com.cues.core.drafting.GrammarParser
import com.cues.core.drafting.PairedDevice
import com.cues.core.context.Remedy
import com.cues.core.insights.BlockCause
import com.cues.core.insights.InsightsReport
import com.cues.core.insights.InsightsWindow
import com.cues.core.model.*
import com.cues.core.ports.ActionExecutor
import com.cues.core.ports.ActionOutcome
import com.cues.core.ports.CapabilityProvider
import com.cues.core.ports.Clock
import com.cues.core.review.ReviewCopy.friendlyName
import com.cues.core.store.JsonFileStore
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.runBlocking

/**
 * `./dev insights`: a synthetic week driven through [CueService], then the
 * Insights report it produces, printed to fit a phone screen.
 *
 * Every event is invented here and every executor call is faked, so nothing
 * printed is a measurement of anything — the header says so. What it does
 * show is the real path: real drafting, real sessions, real structured
 * receipts, and the same [CueService.insights] the app calls.
 */

private val ZONE: ZoneId = ZoneId.of("Asia/Kolkata")
private val EARBUDS = PairedDevice("AA:BB:CC:DD:EE:FF", "TWS Air Pro", setOf("earbuds", "buds"))

fun main() = runBlocking {
    val root = createTempDirectory("cues-insights").toFile()
    try {
        run(root)
    } finally {
        root.deleteRecursively()
    }
}

private suspend fun run(root: File) {
    val clock = WeekClock(at(LocalDate.of(2026, 9, 21), 6, 0)) // a Monday
    val store = JsonFileStore(root, nowMillis = { clock.now })
    val executor = FixtureExecutor()
    val service = CueService(
        routines = store, sessions = store, receipts = store, executor = executor, clock = clock,
        capabilities = CapabilityProvider { Capability.entries.toSet() },
        drafter = GrammarParser(listOf(EARBUDS)), zoneId = { ZONE },
        usageLedger = store, receiptLog = store,
    )

    suspend fun arm(sentence: String): Routine {
        val drafted = (service.draft(sentence) as DraftResult.Drafted).routine
        return (service.approveAndArm(drafted) as ArmResult.Ok).routine
    }
    val hero = arm(
        "When my earbuds connect after 6 PM on weekdays, start a 45-minute focus timer and quiet notifications. " +
            "End it if I disconnect.",
    )
    arm("At 7:00, while the phone is charging, quiet notifications for an hour.")

    for (dayIndex in 0 until 7) {
        val day = LocalDate.of(2026, 9, 21).plusDays(dayIndex.toLong())

        // 07:00 — the charging-gated morning cue. Three mornings the battery
        // state could not be read at all: those must count as UNKNOWN. The
        // first falls before the 7-day window the report ends up reading
        // (Monday 09:00 onward), so the report counts two — by design.
        clock.now = at(day, 7, 0)
        val charging: ContextValue<Boolean> = if (dayIndex % 3 == 0) {
            ContextValue.Unknown(UnknownReason.PERMISSION_DENIED, ContextSource.BATTERY_MANAGER)
        } else {
            ContextValue.Known(true, ContextSource.BATTERY_MANAGER, clock.now)
        }
        service.onDeviceEvent(
            TriggerEvent(EventKind.TIME_REACHED, clock.now, localTime = LocalTimeOfDay(7, 0), zoneId = "system"),
            charging = charging,
        )
        clock.now += 60 * 60_000
        store.allUnfinished().forEach { service.onDeadline(it.id) }

        // Wednesday: a connection before 18:00 is a TIME skip.
        if (dayIndex == 2) {
            clock.now = at(day, 17, 15)
            service.onDeviceEvent(connect(clock.now, "early-$dayIndex"))
        }

        // 18:30 — the hero cue. Weekends skip on DAY; Friday's quiet rule is
        // refused; Thursday's quiet rule cannot be released.
        executor.blockDnd = dayIndex == 4
        executor.failDndRelease = dayIndex == 3
        clock.now = at(day, 18, 30)
        service.onDeviceEvent(connect(clock.now, "conn-$dayIndex"))
        val live = store.activeFor(hero.id).firstOrNull { it.endedAtMillis == null } ?: continue
        when (dayIndex) {
            1 -> { // disconnect after 20 minutes, grace elapses
                clock.now += 20 * 60_000
                service.onDeviceEvent(TriggerEvent(EventKind.BLUETOOTH_DISCONNECTED, clock.now, EARBUDS.id, "conn-$dayIndex"))
                clock.now += 21_000
                service.onGraceElapsed(live.id)
            }
            2 -> { clock.now += 30 * 60_000; service.onManualStop(live.id) }
            else -> { clock.now += 45 * 60_000; service.onDeadline(live.id) }
        }
        executor.blockDnd = false
        executor.failDndRelease = false
    }

    clock.now = at(LocalDate.of(2026, 9, 28), 9, 0)
    print(service.insights(InsightsWindow.LAST_7_DAYS))
}

private fun print(report: InsightsReport) {
    println("INSIGHTS · last ${report.window.days} days")
    println("SYNTHETIC CLI FIXTURE: invented events, a fake executor. Not device data.")
    if (!report.hasData) {
        println("Insights appear after your first session. Nothing here is estimated.")
        return
    }

    println()
    report.timeInCues?.let { time ->
        val earlier = time.deltaMillis?.let { "${sign(it)}${hm(kotlin.math.abs(it))} vs earlier" } ?: "no earlier data"
        println("Time in cues  ${hm(time.totalMillis)} over ${time.endedSessions} ended · $earlier")
        time.byEndReason.entries.sortedByDescending { it.value }
            .forEach { (reason, millis) -> println("  ${reason.friendlyName()}: ${hm(millis)}") }
    } ?: println("Time in cues  nothing has ended yet")

    report.counts?.let { c ->
        println()
        println("Started ${c.started} · Skipped ${c.skipped ?: "not recorded"} · Needs attention ${c.needsAttention}")
        println("  partial ${c.partial} · cleanup pending ${c.cleanupPending} · blocked actions ${c.blockedActions}")
    }

    if (report.skipReasons.isNotEmpty()) {
        println()
        println("Why skipped")
        report.skipReasons.forEach { group ->
            val name = if (group.family.name == "UNKNOWN") "UNKNOWN (couldn't read)" else group.family.name.lowercase()
            println("  $name ×${group.count}")
            group.unreadable.forEach { u ->
                println("    ${u.source.name.lowercase()}: ${u.reason.name.lowercase().replace('_', ' ')} ×${u.count} → ${remedyText(u.remedy)}")
            }
        }
    }

    report.cleanup?.let { cleanup ->
        println()
        println("Cleanup  released ${cleanup.released} · outstanding ${cleanup.outstanding.size} · held ${cleanup.heldByLiveSessions}")
        cleanup.outstanding.forEach { println("  ${it.resource.name.lowercase()}: ${it.failureDetail ?: "not released"}") }
    }

    if (report.blocked.isNotEmpty()) {
        println()
        println("Blocked")
        report.blocked.forEach { group ->
            val cause = when (val c = group.cause) {
                is BlockCause.NeedsCapabilities -> "needs one of ${c.required.joinToString { it.name.lowercase() }}"
                BlockCause.ExpiredWaitingForYou -> "expired waiting for you"
                BlockCause.Unattributed -> "no recorded cause"
            }
            println("  ${group.actionId.name.lowercase()} ×${group.count} — $cause")
        }
    }

    println()
    println("Per cue")
    report.perRoutine.forEach { r ->
        val median = r.medianDurationMillis?.let { hm(it) } ?: "—"
        val clean = when (r.lastEndedCleanly) { true -> "✓"; false -> "✗"; null -> "—" }
        println("  ${r.title ?: "(deleted cue)"} v${r.currentVersion ?: "?"}")
        println("    runs ${r.runs} · median $median · skips ${r.skips ?: "—"}" +
            (r.topSkipFamily?.let { " (mostly ${it.name.lowercase()})" } ?: "") +
            " · blocked ${r.blockedActions} · last ended cleanly $clean")
    }

    report.coverage?.let { cov ->
        println()
        println("Coverage  ${cov.sessionsEndedByGap} ended by a gap · ledger gap time " +
            (cov.ledgerGapMillis?.let { hm(it) } ?: "none recorded") +
            " · learning ${if (report.ledgerEnabled) "on" else "off"}")
    }
}

private fun remedyText(remedy: Remedy?): String = when (remedy) {
    is Remedy.GrantCapability -> "grant ${remedy.capability.friendlyName()}"
    Remedy.KeepCuesRunning -> "keep Cues running"
    is Remedy.TurnOnRadio -> "turn on ${remedy.radio.name.lowercase()}"
    Remedy.WaitForFirstReading -> "wait for a first reading"
    Remedy.OsWithholds -> "the system withholds this"
    null -> "no fix known"
}

private fun hm(millis: Long): String {
    val minutes = millis / 60_000
    return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
}

private fun sign(millis: Long) = if (millis >= 0) "+" else "−"

private fun at(day: LocalDate, hour: Int, minute: Int): Long =
    day.atTime(hour, minute).atZone(ZONE).toInstant().toEpochMilli()

private fun connect(atMillis: Long, connectionId: String) =
    TriggerEvent(EventKind.BLUETOOTH_CONNECTED, atMillis, deviceId = EARBUDS.id, connectionSessionId = connectionId)

private class WeekClock(var now: Long) : Clock {
    override fun nowMillis(): Long = now
}

/** Succeeds at everything, except the two refusals this fixture exists to show. */
private class FixtureExecutor : ActionExecutor {
    var blockDnd = false
    var failDndRelease = false

    override fun execute(actionId: ActionId, args: ActionArgs, sessionId: String): ActionOutcome {
        if (actionId == ActionId.REQUEST_DND && blockDnd) {
            return ActionOutcome(ActionState.BLOCKED, "Do Not Disturb access was not granted.")
        }
        return ActionOutcome(ActionState.SUCCEEDED, acquired = com.cues.core.registry.ActionRegistry.definition(actionId)?.owns)
    }

    override fun release(resource: OwnedResource, sessionId: String): ActionOutcome =
        if (resource == OwnedResource.DND_CONTRIBUTION && failDndRelease) {
            ActionOutcome(ActionState.COMPENSATION_FAILED, "The quiet rule could not be removed.")
        } else {
            ActionOutcome(ActionState.SUCCEEDED)
        }
}
