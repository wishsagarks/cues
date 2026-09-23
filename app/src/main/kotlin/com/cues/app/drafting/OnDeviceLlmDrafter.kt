package com.cues.app.drafting

import com.cues.core.drafting.DraftResult
import com.cues.core.drafting.RoutineDrafter
import com.cues.core.drafting.GrammarParser
import com.cues.core.model.DraftSourceId

/**
 * The on-device model path.
 *
 * NOT YET IMPLEMENTED, and carried at equal weight with the grammar parser
 * rather than as an afterthought. The intended shape:
 *
 *  1. MediaPipe LLM Inference (`com.google.mediapipe:tasks-genai`) with a
 *     Gemma-class int4 model pushed to the device, GPU delegate if the loaner
 *     supports it. The model file does not ship in the APK; it is side-loaded
 *     once and read from app storage.
 *  2. A prompt that asks for typed data over the closed vocabulary only, with
 *     constrained decoding where the runtime supports it.
 *  3. The output is parsed into a Routine and handed back. It is *not* trusted:
 *     CompositeDrafter runs the independent validator over it, and falls back
 *     to the grammar parser if it does not pass.
 *
 * llama.cpp with a GBNF grammar is the contingency. Its grammar constraint is
 * the closer match to what the FDD asks for, at the cost of an NDK build, and
 * the choice between them should be made from measured latency on the actual
 * phone rather than from this comment.
 *
 * Until it is implemented this reports a clean failure, so CompositeDrafter
 * falls back to the parser and the diagnostics screen says which path ran. A
 * stub that returned a plausible routine would be the one outcome worth
 * avoiding: it would make the model look like it was working.
 */
fun interface LlmSession { fun generate(prompt: String): String }

/**
 * Integration seam for the MediaPipe runtime. Asset provisioning is deliberately
 * explicit: no model is downloaded by the app and an absent asset is a failure,
 * not a fallback disguised as inference.
 */
class MediaPipeLlmSession(private val modelPath: String? = null) : LlmSession {
    override fun generate(prompt: String): String =
        throw IllegalStateException("No side-loaded MediaPipe model is configured.")
}

class FakeLlmSession(private val answer: String) : LlmSession {
    override fun generate(prompt: String): String = answer
}

class OnDeviceLlmDrafter(
    private val session: LlmSession = MediaPipeLlmSession(),
    private val parser: GrammarParser = GrammarParser(),
) : RoutineDrafter {

    override val id: DraftSourceId = DraftSourceId.ON_DEVICE_LLM

    override suspend fun draft(text: String): DraftResult = try {
        // The model proposes a sentence in the same closed grammar; parsing it
        // keeps capabilities and validation independent of model prose.
        when (val parsed = parser.parse(session.generate(prompt(text)))) {
            is DraftResult.Drafted -> parsed.copy(source = id, consumed = emptyList(), clauses = emptyList())
            is DraftResult.NeedsClarification -> parsed.copy(source = id, consumed = emptyList(), clauses = emptyList())
            is DraftResult.Failed -> parsed.copy(source = id)
        }
    } catch (e: Exception) {
        DraftResult.Failed(id, e.message ?: "The on-device model could not produce a draft.")
    }

    private fun prompt(text: String): String =
        "Return one Cues request using only supported trigger, condition, action and ending vocabulary. Request: $text"
}
