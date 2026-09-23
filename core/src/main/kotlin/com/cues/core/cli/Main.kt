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
import com.cues.core.rehearsal.Rehearsal
import com.cues.core.review.ReviewCopy
import com.cues.core.review.forecastToday
import com.cues.core.review.FORECAST_LABEL
import com.cues.core.signals.ContextualStores
import com.cues.core.ports.NamedContextStore
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
    ContextualStores.contexts = CliContexts
    val sentence = args.firstOrNull()?.takeIf { it.isNotBlank() } ?: HERO

    if (sentence == "--today") {
        println(FORECAST_LABEL)
        forecastToday(listOf(FixturesForCli.routine()), emptyList(), FixturesForCli.snapshot(), java.time.ZoneId.systemDefault()).forEach {
            println("- ${it.window}: ${it.status.name.lowercase().replace('_', ' ')}")
            it.reasons.forEach { reason -> println("  $reason") }
        }
        return
    }

    if (sentence.trim() == "--corpus") {
        printCorpus()
        return
    }

    println()
    println(bold("You said"))
    println("  \"$sentence\"")
    println()

    when (val result = GrammarParser(SAMPLE_DEVICES, contextsProvider = { CliContexts.allContexts() }).parse(sentence)) {
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

private object FixturesForCli {
    fun routine() = Routine("today-fixture", 1, sourceText = HERO, title = "Focus", trigger = Trigger.BluetoothConnection("AA:BB", "earbuds", DeviceTransition.CONNECTED), conditions = emptyList(), actions = listOf(ActionSpec(ActionId.NOTIFY_RESULT, ActionArgs.Notify("Ready"))), endConditions = listOf(EndCondition.ManualStop), cleanupPolicy = CleanupPolicy(), rearmPolicy = RearmPolicy(), requiredCapabilities = emptySet())
    fun snapshot() = com.cues.core.context.SnapshotBuilder.build(System.currentTimeMillis(), java.time.ZoneId.systemDefault())
}

private fun printReview(result: DraftResult.Drafted) {
    val routine = Normalizer.normalize(result.routine)

    println(bold("Review") + dim("  (drafted by ${result.source.name.lowercase().replace('_', ' ')})"))
    row("WHEN", ReviewCopy.whenText(routine))
    row("IF", ReviewCopy.ifText(routine))
    row("DO", ReviewCopy.doText(routine))
    row("UNTIL", ReviewCopy.untilText(routine))
    row("RESTORE", ReviewCopy.restoreText(routine))
    row("REPEAT", ReviewCopy.repeatText(routine))
    row("ACCESS", ReviewCopy.accessText(routine))
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

    val parser = GrammarParser(SAMPLE_DEVICES, contextsProvider = { CliContexts.allContexts() })
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

private object CliContexts : NamedContextStore {
    private val desk = NamedContext("desk", "Desk", 1, listOf(Condition.ChargingState(true)))
    override fun findContext(id: String) = desk.takeIf { it.id == id }
    override fun allContexts() = listOf(desk)
    override fun saveContext(context: NamedContext) = Unit
    override fun deleteContext(id: String) = Unit
}
