package com.cues.app.drafting

import com.cues.core.drafting.DraftResult
import com.cues.core.drafting.RoutineDrafter
import com.cues.core.drafting.GrammarParser
import com.cues.core.inference.InferenceBackend
import com.cues.core.inference.InferenceReport
import com.cues.core.model.DraftSourceId

/** What one call to the model produced, alongside what actually ran it — see [InferenceReport]. */
data class InferenceOutput(val text: String, val report: InferenceReport)

/**
 * A local-model text generator, kept behind this port so [OnDeviceLlmDrafter]
 * — and everything above it — never depends on which runtime is behind it.
 *
 * Suspend rather than a plain call: [LiteRtLmSession]'s real implementation
 * can take several seconds to load a model, and `OnDeviceLlmDrafter.draft`
 * is already suspend precisely so a slow drafter never has to fake being
 * synchronous.
 */
fun interface LlmSession {
    suspend fun generate(prompt: String): InferenceOutput
}

/**
 * The honest default for anyone running Cues without a side-loaded model.
 * No asset, no attempt, no fabricated answer — this is what
 * [DifferentialDrafter]/[CompositeDrafter] fall all the way back to the
 * parser from.
 */
class UnconfiguredLlmSession : LlmSession {
    override suspend fun generate(prompt: String): InferenceOutput =
        throw IllegalStateException("No side-loaded model is configured.")
}

/** For bake-offs and tests: a fixed answer and an optional, explicit report. */
class FakeLlmSession(
    private val answer: String,
    private val report: InferenceReport = InferenceReport(InferenceBackend.CPU, loadMs = 0, generationMs = 0, tokensPerSecond = 0.0),
) : LlmSession {
    override suspend fun generate(prompt: String): InferenceOutput = InferenceOutput(answer, report)
}

/**
 * Proposes a routine by asking a local model for one sentence in the closed
 * grammar, then handing that sentence to [GrammarParser] — the model's own
 * words are never trusted as structure, only as candidate prose for the same
 * deterministic compiler every other draft goes through.
 *
 * [session] does the actual generation; see [LiteRtLmSession] for the real
 * on-device runtime and [docs/API_VERIFICATION.md] for what backends it can
 * reach. This class stays runtime-agnostic on purpose: everything here would
 * be identical against llama.cpp or MediaPipe if either replaced LiteRT-LM.
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
        when (val parsed = parser.parse(output.text)) {
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
        "Return one Cues request using only supported trigger, condition, action and ending vocabulary. Request: $text"
}
