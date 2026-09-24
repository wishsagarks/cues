package com.cues.core.export

import com.cues.core.coach.LedgerEvent
import com.cues.core.coach.Suggestion
import com.cues.core.inference.InferenceReport
import com.cues.core.model.Routine
import com.cues.core.review.ForecastItem
import com.cues.core.review.ReviewCopy
import com.cues.core.store.ReceiptEntry
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The one-way export that feeds the Cue Console: everything a laptop screen
 * needs to *show*, nothing it needs to *trust*.
 *
 * Every field here is read, rendered text — the same [ReviewCopy] the phone
 * itself shows, the same receipt/forecast/ledger records already written
 * through this phone's own ports. There is no path back from the Console
 * into a routine, a session or an approval; it is a read-only mirror, built
 * for a laptop screen during a demo or a desk review, never a second place
 * Cues' own decisions could be made or changed.
 */
object CuesExporter {
    const val CURRENT_EXPORT_SCHEMA = 1

    private val json = Json { prettyPrint = true }

    fun export(
        routines: List<Routine>,
        receipts: List<ReceiptEntry>,
        forecast: List<ForecastItem>,
        coachSuggestions: List<Suggestion>,
        ledgerEvents: List<LedgerEvent>,
        primaryDrafter: String,
        lastFallbackReason: String?,
        lastInferenceReport: InferenceReport?,
        generatedAtMillis: Long,
    ): JsonObject = buildJsonObject {
        put("exportSchemaVersion", CURRENT_EXPORT_SCHEMA)
        put("generatedAtMillis", generatedAtMillis)

        putJsonArray("routines") {
            routines.forEach { routine ->
                addJsonObject {
                    put("id", routine.id)
                    put("title", routine.title)
                    put("status", routine.status.name)
                    put("draftedBy", routine.draftedBy?.name)
                    put("when", ReviewCopy.whenText(routine))
                    put("if", ReviewCopy.ifText(routine))
                    put("do", ReviewCopy.doText(routine))
                    put("until", ReviewCopy.untilText(routine))
                    put("restore", ReviewCopy.restoreText(routine))
                }
            }
        }

        putJsonArray("receipts") {
            receipts.forEach { receipt ->
                addJsonObject {
                    put("sessionId", receipt.sessionId)
                    put("atMillis", receipt.atMillis)
                    put("text", receipt.text)
                }
            }
        }

        putJsonArray("forecast") {
            forecast.forEach { item ->
                addJsonObject {
                    put("routineId", item.routineId)
                    put("window", item.window)
                    put("status", item.status.name)
                    putJsonArray("reasons") { item.reasons.forEach { add(it) } }
                }
            }
        }

        putJsonArray("coach") {
            coachSuggestions.forEach { suggestion ->
                addJsonObject {
                    put("patternKey", suggestion.patternKey)
                    put("kind", suggestion.kind.name)
                    put("proposal", suggestion.proposal)
                    putJsonArray("evidence") { suggestion.evidence.forEach { add(it.text) } }
                }
            }
        }

        // Already a closed, @Serializable sealed type — encoded as-is rather
        // than hand-rebuilt field by field.
        put("ledger", json.encodeToJsonElement(ListSerializer(LedgerEvent.serializer()), ledgerEvents))

        putJsonObject("diagnostics") {
            put("primaryDrafter", primaryDrafter)
            put("lastFallbackReason", lastFallbackReason)
            lastInferenceReport?.let { report ->
                put("inferenceBackend", report.backend.name)
                put("loadMs", report.loadMs)
                put("generationMs", report.generationMs)
                put("tokensPerSecond", report.tokensPerSecond)
            }
        }
    }

    fun exportText(
        routines: List<Routine>,
        receipts: List<ReceiptEntry>,
        forecast: List<ForecastItem>,
        coachSuggestions: List<Suggestion>,
        ledgerEvents: List<LedgerEvent>,
        primaryDrafter: String,
        lastFallbackReason: String?,
        lastInferenceReport: InferenceReport?,
        generatedAtMillis: Long,
    ): String = json.encodeToString(
        JsonObject.serializer(),
        export(
            routines, receipts, forecast, coachSuggestions, ledgerEvents,
            primaryDrafter, lastFallbackReason, lastInferenceReport, generatedAtMillis,
        ),
    )
}
