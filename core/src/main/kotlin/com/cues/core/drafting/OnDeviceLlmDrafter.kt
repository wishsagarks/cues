package com.cues.core.drafting

import com.cues.core.inference.InferenceBackend
import com.cues.core.inference.InferenceReport
import com.cues.core.model.DraftSourceId

/** What one call to the model produced, alongside what actually ran it — see [InferenceReport]. */
data class InferenceOutput(val text: String, val report: InferenceReport)

/**
 * A local-model text generator, kept behind this port so [OnDeviceLlmDrafter]
 * — and everything above it — never depends on which runtime is behind it.
 *
 * Suspend rather than a plain call: a real implementation (LiteRT-LM on the
 * phone, or a local Ollama server on a laptop — see `core/.../cli/OllamaLlmSession.kt`)
 * can take several seconds to load a model, and `OnDeviceLlmDrafter.draft` is
 * already suspend precisely so a slow drafter never has to fake being
 * synchronous.
 *
 * Lives in `:core`, not `:app`, on purpose: nothing here touches Android —
 * only the concrete LiteRT-LM/Android binding (`app/.../drafting/LiteRtLmSession.kt`)
 * does, which is why that one class stays in `:app` while this contract and
 * everything built on it moved here. That split is what lets a laptop-only
 * implementation (Ollama over plain JVM `HttpClient`) share this exact
 * drafter instead of duplicating it.
 */
fun interface LlmSession {
    suspend fun generate(prompt: String): InferenceOutput
}

/**
 * The honest default for anyone running Cues without a configured model —
 * no side-loaded phone model, no laptop server reachable. No asset, no
 * attempt, no fabricated answer — this is what [DifferentialDrafter]/[CompositeDrafter]
 * fall all the way back to the parser from.
 */
class UnconfiguredLlmSession : LlmSession {
    override suspend fun generate(prompt: String): InferenceOutput =
        throw IllegalStateException("No on-device or local model is configured.")
}

/** For bake-offs and tests: a fixed answer and an optional, explicit report. */
class FakeLlmSession(
    private val answer: String,
    private val report: InferenceReport = InferenceReport(InferenceBackend.CPU, loadMs = 0, generationMs = 0, estimatedTokens = 0),
) : LlmSession {
    override suspend fun generate(prompt: String): InferenceOutput = InferenceOutput(answer, report)
}

/**
 * Proposes a routine by asking a local model for one sentence in the closed
 * grammar, then handing that sentence to [GrammarParser] — the model's own
 * words are never trusted as structure, only as candidate prose for the same
 * deterministic compiler every other draft goes through.
 *
 * [session] does the actual generation; see `app/.../drafting/LiteRtLmSession.kt`
 * for the real on-device runtime and `core/.../cli/OllamaLlmSession.kt` for
 * the laptop-only one — this class stays runtime-agnostic on purpose.
 */
class OnDeviceLlmDrafter(
    private val session: LlmSession = UnconfiguredLlmSession(),
    private val parser: GrammarParser = GrammarParser(),
) : RoutineDrafter {

    override val id: DraftSourceId = DraftSourceId.ON_DEVICE_LLM

    override suspend fun draft(text: String): DraftResult = try {
        val output = session.generate(prompt(text))
        // The model proposes a sentence in the same closed grammar; parsing it
        // keeps capabilities and validation independent of model prose.
        val candidate = cleanCandidate(output.text)
        when (val parsed = parser.parse(candidate)) {
            is DraftResult.Drafted -> parsed.copy(
                source = id, consumed = emptyList(), clauses = emptyList(), inferenceReport = output.report,
            )
            is DraftResult.NeedsClarification -> parsed.copy(
                source = id, consumed = emptyList(), clauses = emptyList(), inferenceReport = output.report,
            )
            is DraftResult.Failed -> parsed.copy(source = id, inferenceReport = output.report)
        }
    } catch (e: Exception) {
        DraftResult.Failed(id, e.message ?: "The on-device model could not produce a draft.")
    }

    private fun prompt(text: String): String =
        """
            You are the Cues on-device cue normalizer. Convert the user's request into exactly one
            plain-English sentence that GrammarParser can understand. Preserve the meaning, but
            use only supported trigger, condition, action, and ending vocabulary.
            Preserve named devices exactly (earbuds, headphones, watch, charger, Wi-Fi); never
            replace a named device with the generic word "device" and never invent a device.
            Never return DSL, code, JSON, labels, explanations, markdown, or multiple alternatives.
            Good examples:
            - when my charger connects, silence notifications for 10 minutes
            - when my earbuds connect, start a focus timer for 25 minutes
            - when I enter the office, silence notifications until I leave
            - every weekday at 9 AM, remind me to start a focus timer for 25 minutes
            - when my phone connects to any Wi-Fi, start a focus timer for 25 minutes
            - while I am in the office, silence notifications until I leave
            User request: $text
        """.trimIndent()

    /** Keep harmless model wrappers from preventing the deterministic parser from seeing the sentence. */
    private fun cleanCandidate(raw: String): String = raw
        .replace("```", "")
        .trim()
        .removePrefix("Output:")
        .removePrefix("Cue:")
        .trim()
        .removeSurrounding("\"", "\"")
        .removeSurrounding("'", "'")
        .trim()
}
