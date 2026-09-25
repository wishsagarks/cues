package com.cues.core.export

import com.cues.core.Fixtures
import com.cues.core.coach.LedgerEvent
import com.cues.core.coach.Suggestion
import com.cues.core.coach.SuggestionKind
import com.cues.core.coach.EvidenceLine
import com.cues.core.inference.InferenceBackend
import com.cues.core.inference.InferenceReport
import com.cues.core.model.RoutineStatus
import com.cues.core.review.ForecastItem
import com.cues.core.review.ForecastStatus
import com.cues.core.store.ReceiptEntry
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class CuesExporterTest {

    private fun sampleExport(
        routine: com.cues.core.model.Routine = Fixtures.heroRoutine(),
        lastInferenceReport: InferenceReport? = null,
    ) = CuesExporter.export(
        routines = listOf(routine),
        receipts = listOf(ReceiptEntry("session-1", 1_000L, "Started\n  Focus timer: done.")),
        forecast = listOf(ForecastItem(routine.id, "at 18:00", ForecastStatus.WILL_ARM, listOf("reason one"))),
        coachSuggestions = listOf(
            Suggestion("early-stop:study", SuggestionKind.SHORTER_DURATION, listOf(EvidenceLine("Stopped early 4 of 5 times.", 4)), "make study 22 minutes"),
        ),
        ledgerEvents = listOf(LedgerEvent.ManualStart(540, com.cues.core.model.Day.MON, 2_000L)),
        primaryDrafter = "GRAMMAR_PARSER",
        lastFallbackReason = null,
        lastInferenceReport = lastInferenceReport,
        generatedAtMillis = 5_000L,
    )

    @Test
    fun `every section is present and reflects the same data the phone itself already shows`() {
        val routine = Fixtures.heroRoutine()
        val export = sampleExport(routine)

        assertEquals(CuesExporter.CURRENT_EXPORT_SCHEMA, export["exportSchemaVersion"]!!.jsonPrimitive.int)
        assertEquals(5_000L, export["generatedAtMillis"]!!.jsonPrimitive.long)

        val routineJson = export["routines"]!!.jsonArray.single().jsonObject
        assertEquals(routine.title, routineJson["title"]!!.jsonPrimitive.content)
        assertEquals(
            com.cues.core.review.ReviewCopy.doText(routine),
            routineJson["do"]!!.jsonPrimitive.content,
            "the Console must show the exact same review text the phone's own Review screen renders, never a re-derived paraphrase",
        )

        val receiptJson = export["receipts"]!!.jsonArray.single().jsonObject
        assertEquals("session-1", receiptJson["sessionId"]!!.jsonPrimitive.content)

        val forecastJson = export["forecast"]!!.jsonArray.single().jsonObject
        assertEquals("WILL_ARM", forecastJson["status"]!!.jsonPrimitive.content)

        val coachJson = export["coach"]!!.jsonArray.single().jsonObject
        assertEquals("make study 22 minutes", coachJson["proposal"]!!.jsonPrimitive.content)
        assertEquals("Stopped early 4 of 5 times.", coachJson["evidence"]!!.jsonArray.single().jsonPrimitive.content)

        assertEquals(1, export["ledger"]!!.jsonArray.size)
    }

    @Test
    fun `an armed routine's status and approval text are exactly what they are, never re-approved by exporting`() {
        val routine = Fixtures.heroRoutine(status = RoutineStatus.ARMED)

        val export = sampleExport(routine)

        val routineJson = export["routines"]!!.jsonArray.single().jsonObject
        assertEquals("ARMED", routineJson["status"]!!.jsonPrimitive.content)
    }

    @Test
    fun `no inference report yet is shown as absent, never a fabricated backend`() {
        val export = sampleExport(lastInferenceReport = null)

        val diagnostics = export["diagnostics"]!!.jsonObject
        assertTrue("inferenceBackend" !in diagnostics)
    }

    @Test
    fun `an inference report's backend, timings and rate all travel through untouched`() {
        val report = InferenceReport(InferenceBackend.NPU, loadMs = 900, generationMs = 400, estimatedTokens = 5)

        val export = sampleExport(lastInferenceReport = report)

        val diagnostics = export["diagnostics"]!!.jsonObject
        assertEquals("NPU", diagnostics["inferenceBackend"]!!.jsonPrimitive.content)
        assertEquals(900L, diagnostics["loadMs"]!!.jsonPrimitive.long)
        assertEquals(12.5, diagnostics["tokensPerSecond"]!!.jsonPrimitive.double)
    }

    @Test
    fun `exportText produces parseable JSON with the same content as export`() {
        val text = CuesExporter.exportText(
            routines = emptyList(), receipts = emptyList(), forecast = emptyList(),
            coachSuggestions = emptyList(), ledgerEvents = emptyList(),
            primaryDrafter = "GRAMMAR_PARSER", lastFallbackReason = null,
            lastInferenceReport = null, generatedAtMillis = 1L,
        )

        assertTrue(text.contains("\"exportSchemaVersion\""))
        val reparsed = kotlinx.serialization.json.Json.parseToJsonElement(text).jsonObject
        assertEquals(1L, reparsed["generatedAtMillis"]!!.jsonPrimitive.long)
    }

    @Test
    fun `an empty phone exports empty sections, not an error`() {
        val export = CuesExporter.export(
            routines = emptyList(), receipts = emptyList(), forecast = emptyList(),
            coachSuggestions = emptyList(), ledgerEvents = emptyList(),
            primaryDrafter = "GRAMMAR_PARSER", lastFallbackReason = null,
            lastInferenceReport = null, generatedAtMillis = 1L,
        )

        assertTrue(export["routines"]!!.jsonArray.isEmpty())
        assertTrue(export["receipts"]!!.jsonArray.isEmpty())
        assertNull(export["diagnostics"]!!.jsonObject["inferenceBackend"])
    }
}
