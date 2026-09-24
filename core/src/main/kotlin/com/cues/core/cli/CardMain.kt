package com.cues.core.cli

import com.cues.core.drafting.GrammarParser
import com.cues.core.drafting.PairedDevice
import com.cues.core.drafting.DraftResult
import com.cues.core.share.CardDecodeResult
import com.cues.core.share.CueCards
import com.cues.core.share.ReimportResult
import kotlinx.coroutines.runBlocking

/**
 * A self-contained Cue Card round trip, phone-friendly: draft a sample cue,
 * export it, tamper with a copy, then reimport both against a *different*
 * paired-device list — standing in for a second phone — so the demo shows
 * the id/version rebinding, not a same-phone no-op.
 */
object CardMain {
    @JvmStatic
    fun main(args: Array<String>) {
        runBlocking {
            val senderDevice = PairedDevice("AA:BB:CC:DD:EE:FF", "TWS Air Pro", setOf("earbuds", "buds"))
            val drafted = GrammarParser(listOf(senderDevice)).draft(
                "when my earbuds connect after 6pm on weekdays, start a 45 minute focus timer and quiet notifications",
            )
            val routine = (drafted as? DraftResult.Drafted)?.routine
                ?: error("Fixture sentence failed to draft: $drafted")

            val card = CueCards.from(routine)
            val encoded = CueCards.encode(card)
            println("Card (${encoded.length} chars):")
            println(encoded)

            println()
            println("Decoding on a second phone, which paired the same earbuds under a new address:")
            val receiverDevice = PairedDevice("11:22:33:44:55:66", "TWS Air Pro", setOf("earbuds", "buds"))
            when (val result = CueCards.reimport(encoded, listOf(receiverDevice), emptyList(), emptyList())) {
                is ReimportResult.Ready -> {
                    println("  drafted by: ${result.routine.draftedBy}")
                    println("  status: ${result.routine.status}")
                    println("  rebinds to device id: ${(result.routine.trigger as com.cues.core.model.Trigger.BluetoothConnection).deviceId}")
                    println("  re-derived capabilities: ${result.routine.requiredCapabilities}")
                }
                else -> error("Expected a successful reimport, got $result")
            }

            println()
            println("A tampered copy of the same card:")
            val tampered = encoded.replace(routine.title, "Something else entirely")
            when (CueCards.decode(tampered)) {
                is CardDecodeResult.Tampered -> println("  refused: digest does not match — exactly as intended.")
                else -> error("Expected tampering to be caught")
            }
        }
    }
}
