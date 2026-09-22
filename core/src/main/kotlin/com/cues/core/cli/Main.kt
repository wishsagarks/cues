package com.cues.core.cli

import com.cues.core.compile.Normalizer
import com.cues.core.compile.Severity
import com.cues.core.compile.Validator
import com.cues.core.corpus.Corpus
import com.cues.core.corpus.CorpusReport
import com.cues.core.drafting.DraftResult
import com.cues.core.drafting.GrammarParser
import com.cues.core.drafting.PairedDevice
import com.cues.core.model.*
import com.cues.core.receipt.friendly
import com.cues.core.rehearsal.Rehearsal
import kotlin.system.exitProcess

/**
 * Compiles a spoken sentence and prints its review and rehearsal.
 *
 * This exists because for roughly ten and a half hours of the event there is no
 * comfortable screen: the laptop is driven through Office Kit from the phone.
 * A terminal that shows the whole decision path in under a second — with no
 * emulator, no install, no device attached — is the difference between
 * iterating on the cue logic during Red Light and not iterating at all.
 *
 * Everything printed here comes from the same code the app runs.
 */

/** Stand-ins for the phone's paired devices, so "my earbuds" resolves offline. */
private val SAMPLE_DEVICES = listOf(
    PairedDevice(
        id = "AA:BB:CC:DD:EE:FF",
        label = "TWS Air Pro",
        aliases = setOf("earbuds", "ear buds", "buds", "headphones", "headset", "airpods"),
    ),
)

private const val HERO =
    "When my earbuds connect after 6 PM on weekdays, start a 45-minute focus timer " +
        "and quiet notifications. End it if I disconnect."

fun main(args: Array<String>) {
    val sentence = args.firstOrNull()?.takeIf { it.isNotBlank() } ?: HERO

    if (sentence.trim() == "--corpus") {
        printCorpus()
        return
    }

    println()
    println(bold("You said"))
    println("  \"$sentence\"")
    println()

    when (val result = GrammarParser(SAMPLE_DEVICES).parse(sentence)) {
        is DraftResult.NeedsClarification -> {
            println(bold("Cues needs to ask"))
            println("  ${result.question}")
            // The leading limitation is already the question; only list the rest.
            result.unsupported.drop(1).forEach { println("  - ${it.explanation}") }
            println()
            exitProcess(2)
        }

        is DraftResult.Failed -> {
            println(bold("Could not draft this cue"))
            println("  ${result.reason}")
            println()
            exitProcess(2)
        }

        is DraftResult.Drafted -> {
            printReview(result)
            printRehearsal(result.routine)
        }
    }
}

private fun printReview(result: DraftResult.Drafted) {
    val routine = Normalizer.normalize(result.routine)

    println(bold("Review") + dim("  (drafted by ${result.source.name.lowercase().replace('_', ' ')})"))
    row("WHEN", whenText(routine))
    row("IF", ifText(routine))
    row("DO", doText(routine))
    row("UNTIL", untilText(routine))
    row("RESTORE", restoreText(routine))
    row("REPEAT", repeatText(routine))
    row("ACCESS", routine.requiredCapabilities.joinToString(", ") { it.friendlyName() }.ifEmpty { "none" })
    println()

    if (result.unsupported.isNotEmpty()) {
        println(bold("Not included"))
        result.unsupported.forEach {
            println("  \"${it.fragment}\"")
            println("    ${it.explanation}")
        }
        println()
    }

    val validation = Validator.validate(routine)
    if (validation.findings.isNotEmpty()) {
        println(bold("Checks"))
        validation.findings.forEach {
            val mark = if (it.severity == Severity.ERROR) "error" else "note"
            println("  [$mark] ${it.message}")
        }
        println()
    }

    println(dim("  digest ${Normalizer.digest(routine).take(16)}  ") + dim("approval is bound to this"))
    println()
}

private fun printRehearsal(routine: Routine) {
    println(bold("Rehearsal") + dim("  (sample events — nothing on the phone changes)"))

    Rehearsal.run(routine).rows.forEach { row ->
        println("  ${row.label}")
        println("    ${row.outcome}")
        row.explanation.forEach { println(dim("      $it")) }
    }
    println()
}

/** Scores the grammar parser over the shared corpus, as the model will be scored. */
private fun printCorpus() {
    val text = checkNotNull(object {}.javaClass.getResourceAsStream("/corpus/paraphrases.txt")) {
        "corpus/paraphrases.txt is missing from resources"
    }.bufferedReader().readText()

    val parser = GrammarParser(SAMPLE_DEVICES)
    val startedAt = System.currentTimeMillis()
    val results = Corpus.parse(text).map { Corpus.check(it, parser.parse(it.input)) }
    val report = CorpusReport(results, System.currentTimeMillis() - startedAt)

    println()
    println(bold(report.summary()))
    results.filterNot { it.passed }.forEach {
        println("  ${it.case.input}")
        it.failures.forEach { failure -> println("    $failure") }
    }
    println()
}

// ------------------------------------------------------------------ review copy

private fun whenText(routine: Routine): String = when (val t = routine.trigger) {
    is Trigger.BluetoothConnection -> when (t.transition) {
        DeviceTransition.CONNECTED -> "${t.deviceLabel} connects"
        DeviceTransition.DISCONNECTED -> "${t.deviceLabel} disconnects"
    }

    is Trigger.Charging -> when (t.transition) {
        PowerTransition.PLUGGED_IN -> "the charger is plugged in"
        PowerTransition.UNPLUGGED -> "the charger is unplugged"
    }

    Trigger.Manual -> "you run it by hand"
}

private fun ifText(routine: Routine): String {
    if (routine.conditions.isEmpty()) return "always"
    return routine.conditions.joinToString(", ") { condition ->
        when (condition) {
            is Condition.DaysOfWeek -> condition.days.describeDays()
            // The normalization is shown, never hidden: "after 6 PM" became a
            // window running to midnight, and the user gets to disagree.
            is Condition.TimeWindow -> if (condition.endExclusive.minutesOfDay == 0) {
                "at or after ${condition.startInclusive} local time"
            } else {
                "${condition.startInclusive} to ${condition.endExclusive} local time"
            }

            is Condition.ChargingState ->
                if (condition.charging) "the phone is charging" else "the phone is not charging"
        }
    }
}

private fun doText(routine: Routine): String = routine.actions.joinToString(", ") { spec ->
    when (val args = spec.args) {
        is ActionArgs.FocusTimer -> "start a ${args.durationMinutes}-minute focus timer"
        is ActionArgs.Dnd -> "request our quiet-notifications rule"
        is ActionArgs.Notify -> "show \"${args.message}\""
        ActionArgs.None -> spec.actionId.friendly().lowercase()
    }
}

private fun untilText(routine: Routine): String = routine.endConditions.joinToString(", ") { end ->
    when (end) {
        is EndCondition.Duration -> "${end.minutes} minutes have passed"
        EndCondition.TriggerReversed -> "${triggerNounFor(routine)} goes away"
        EndCondition.ManualStop -> "you stop it"
    }
}

private fun restoreText(routine: Routine): String {
    val owned = routine.actions.mapNotNull { spec ->
        when (spec.actionId) {
            ActionId.START_FOCUS_TIMER -> "end our timer"
            ActionId.REQUEST_DND -> "release our quiet rule"
            ActionId.NOTIFY_RESULT -> null
        }
    }
    if (owned.isEmpty()) return "nothing to release"
    // The caveat is part of the promise, not a footnote.
    return owned.joinToString(", ") + " — and nothing else. Another quiet mode stays as it is."
}

private fun repeatText(routine: Routine): String = buildString {
    append("one session at a time")
    if (routine.rearmPolicy.reconnectGraceSeconds > 0) {
        append("; a dropout shorter than ${routine.rearmPolicy.reconnectGraceSeconds}s does not end it")
    }
    if (routine.rearmPolicy.cooldownSeconds > 0) {
        append("; waits ${routine.rearmPolicy.cooldownSeconds}s between runs")
    }
}

private fun triggerNounFor(routine: Routine): String = when (val t = routine.trigger) {
    is Trigger.BluetoothConnection -> t.deviceLabel
    is Trigger.Charging -> "the charger"
    Trigger.Manual -> "the run"
}

private fun Set<Day>.describeDays(): String = when (this) {
    WEEKDAYS -> "Monday to Friday"
    WEEKEND -> "Saturday and Sunday"
    else -> Day.entries.filter { it in this }.joinToString(", ") { it.name.lowercase().replaceFirstChar(Char::uppercase) }
}

private fun Capability.friendlyName(): String = when (this) {
    Capability.BLUETOOTH_CONNECT -> "Bluetooth"
    Capability.NOTIFICATION_POLICY_ACCESS -> "Do Not Disturb access"
    Capability.POST_NOTIFICATIONS -> "notifications"
    Capability.EXACT_ALARM -> "exact alarms"
    Capability.BATTERY_STATE -> "battery state"
}

// ----------------------------------------------------------------- formatting

/**
 * ANSI only when the output is a terminal.
 *
 * Office Kit's mirrored terminal renders these fine, but piping the output into
 * a file or a chat window should not produce escape-code soup.
 */
private val colour: Boolean = System.console() != null

private fun bold(text: String) = if (colour) "\u001B[1m$text\u001B[0m" else text
private fun dim(text: String) = if (colour) "\u001B[2m$text\u001B[0m" else text

private fun row(label: String, value: String) = println("  ${label.padEnd(8)} $value")
