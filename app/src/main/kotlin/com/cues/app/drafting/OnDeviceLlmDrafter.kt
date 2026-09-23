package com.cues.app.drafting

import com.cues.core.drafting.DraftResult
import com.cues.core.drafting.RoutineDrafter
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
class OnDeviceLlmDrafter : RoutineDrafter {

    override val id: DraftSourceId = DraftSourceId.ON_DEVICE_LLM

    override suspend fun draft(text: String): DraftResult =
        DraftResult.Failed(id, "The on-device model is not wired up yet.")
}
