package com.cues.core.cli

import com.cues.core.coach.LedgerEvent
import com.cues.core.drafting.GrammarParser
import com.cues.core.drafting.PairedDevice
import com.cues.core.drafting.DraftResult
import com.cues.core.export.CuesExporter
import com.cues.core.inference.InferenceBackend
import com.cues.core.inference.InferenceReport
import com.cues.core.model.RoutineStatus
import com.cues.core.review.ForecastStatus
import com.cues.core.review.ForecastItem
import com.cues.core.coach.Detectors
import com.cues.core.store.ReceiptEntry
import java.io.File
import kotlinx.coroutines.runBlocking

/**
 * Builds a single, self-contained `console.html` from a fixture dataset —
 * phone-friendly, in the same spirit as `./dev today`/`./dev coach`: no
 * device, no server, and the output is exactly what a real export would
 * produce, just with sample data standing in for a live phone's own store.
 *
 * The real, device-fed export is `app/.../bridge/ExportImport.kt`'s job,
 * sharing the same `console/template.html` resource and the same
 * `CuesExporter`.
 */
object ConsoleMain {
    private const val SAMPLE_BANNER =
        "<div role=\"note\" style=\"background:#fde68a;color:#1f2937;padding:10px 16px;font:600 14px system-ui,sans-serif\">" +
            "Sample data from ./dev console. Not a phone export: every cue, receipt and model figure below is a fixture.</div>"

    @JvmStatic
    fun main(args: Array<String>) {
        runBlocking {
            val device = PairedDevice("AA:BB:CC:DD:EE:FF", "TWS Air Pro", setOf("earbuds"))
            val drafted = GrammarParser(listOf(device)).draft(
                "when my earbuds connect after 6pm on weekdays, start a 45 minute focus timer and quiet notifications",
            )
            val routine = ((drafted as? DraftResult.Drafted)?.routine ?: error("Fixture sentence failed to draft"))
                .copy(
                    status = RoutineStatus.ARMED,
                    approvedDigest = "fixture-digest",
                    // CueService.draft() stamps this in the real app; calling
                    // GrammarParser directly here skips that, so it's stamped
                    // by hand for a demo that reads the same way the real one does.
                    draftedBy = com.cues.core.model.DraftSourceId.GRAMMAR_PARSER,
                )

            val now = System.currentTimeMillis()
            val day = 86_400_000L

            val rendered = ConsoleHtml.render(
                CuesExporter.exportText(
                    routines = listOf(routine),
                    receipts = listOf(
                        ReceiptEntry("session-1", now - 2 * 3_600_000L, "Started\n  Earbuds connected.\n  Focus timer: done.\n  Quiet notifications: done."),
                        ReceiptEntry("session-1", now - 3_600_000L, "Ended\n  You stopped it.\n  Released our focus timer.\n  Released our quiet rule."),
                    ),
                    forecast = listOf(
                        ForecastItem(routine.id, "if TWS Air Pro connects today", ForecastStatus.WILL_ARM, listOf("It can run if that event happens and these observed conditions still hold.")),
                    ),
                    coachSuggestions = Detectors.all(
                        listOf(
                            LedgerEvent.SessionEnded(routine.id, 45, 22, "MANUAL_STOP", now - day),
                            LedgerEvent.SessionEnded(routine.id, 45, 25, "MANUAL_STOP", now - 2 * day),
                            LedgerEvent.SessionEnded(routine.id, 45, 20, "MANUAL_STOP", now - 3 * day),
                            LedgerEvent.SessionEnded(routine.id, 45, 44, "DEADLINE_REACHED", now - 4 * day),
                            LedgerEvent.SessionEnded(routine.id, 45, 45, "DEADLINE_REACHED", now - 5 * day),
                        ),
                        now,
                    ),
                    ledgerEvents = listOf(
                        LedgerEvent.SessionEnded(routine.id, 45, 22, "MANUAL_STOP", now - day),
                        LedgerEvent.ManualStart(9 * 60, com.cues.core.model.Day.MON, now - day),
                    ),
                    primaryDrafter = "GRAMMAR_PARSER",
                    lastFallbackReason = null,
                    lastInferenceReport = InferenceReport(InferenceBackend.CPU, loadMs = 812, generationMs = 340, estimatedTokens = 5),
                    generatedAtMillis = now,
                ),
            )
            // The fixture's inference figures would otherwise read as a phone measurement.
            require("<body>" in rendered) { "Console template has no <body> to label." }
            val html = rendered.replaceFirst("<body>", "<body>\n$SAMPLE_BANNER")

            val outPath = args.firstOrNull() ?: "build/console.html"
            val outFile = File(outPath)
            outFile.parentFile?.mkdirs()
            outFile.writeText(html)
            println("Wrote $outPath (${html.length} chars). Open it directly in a browser — no server needed.")
        }
    }
}

/** Substitutes an export's JSON into the checked-in static template — the one place both the CLI and the app read the resource from. */
object ConsoleHtml {
    private const val RESOURCE = "/console/template.html"
    private const val START_MARKER = "/*__CUES_EXPORT__*/"
    private const val END_MARKER = "/*__END_CUES_EXPORT__*/"

    fun render(exportJson: String): String {
        val stream = ConsoleHtml::class.java.getResourceAsStream(RESOURCE)
            ?: error("$RESOURCE missing from resources")
        val template = stream.bufferedReader().readText()
        val startIndex = template.indexOf(START_MARKER)
        val endIndex = template.indexOf(END_MARKER)
        require(startIndex >= 0 && endIndex > startIndex) { "Console template is missing its data markers." }
        return template.substring(0, startIndex + START_MARKER.length) +
            exportJson +
            template.substring(endIndex)
    }
}
