package com.cues.core.cli

import com.cues.core.drafting.ClauseAccounting
import com.cues.core.drafting.DifferentialDrafter
import com.cues.core.drafting.DraftResult
import com.cues.core.drafting.GrammarParser
import com.cues.core.drafting.OnDeviceLlmDrafter
import com.cues.core.drafting.PairedDevice
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

/**
 * `./dev llm "<sentence>"` — the same review/rehearsal `./dev d` prints, but
 * drafted through a local Ollama server (Gemma) cross-checked against the
 * grammar parser, exactly the way the phone cross-checks LiteRT-LM against
 * it in [DifferentialDrafter]. Laptop-only: no Android, no phone, no
 * side-loaded model required.
 *
 * If Ollama isn't running or the model isn't pulled,
 * [DifferentialDrafter.guarded] already turns that failure into an honest
 * fallback to the parser's own answer — this file adds no separate degrade
 * path, it inherits the one the phone already has.
 */
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

fun main(args: Array<String>) = runBlocking {
    val sentence = args.firstOrNull()?.takeIf { it.isNotBlank() } ?: HERO

    val drafter = DifferentialDrafter(
        first = OnDeviceLlmDrafter(session = OllamaLlmSession()),
        second = GrammarParser(SAMPLE_DEVICES),
    )

    println()
    println("You said")
    println("  \"$sentence\"")
    println()

    when (val result = drafter.draft(sentence)) {
        is DraftResult.NeedsClarification -> {
            println("Cues needs to ask")
            println("  ${result.question}")
            result.unsupported.drop(1).forEach { println("  - ${it.explanation}") }
            println()
            exitProcess(2)
        }

        is DraftResult.Failed -> {
            println("Could not draft this cue")
            println("  ${result.reason}")
            println()
            exitProcess(2)
        }

        is DraftResult.Drafted -> {
            val stamped = ClauseAccounting.stamp(sentence, result)
            printReview(stamped)
            printRehearsal(stamped.routine)
        }
    }
}
