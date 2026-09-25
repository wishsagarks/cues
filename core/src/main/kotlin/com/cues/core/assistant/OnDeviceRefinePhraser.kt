package com.cues.core.assistant

import com.cues.core.drafting.InferenceOutput
import com.cues.core.drafting.LlmSession
import com.cues.core.drafting.UnconfiguredLlmSession

/** What one refine-phrasing call produced — `operation` is `null` exactly when the sentence didn't parse. */
data class RefinePhrasing(val operation: RefineOperation?, val report: InferenceOutput)

/**
 * A second, narrower use of the same on-device model
 * [OnDeviceLlmDrafter][com.cues.core.drafting.OnDeviceLlmDrafter] already
 * makes: instead of asking for a fresh cue, this asks the model to restate a
 * free-text edit ("make it half an hour") as one sentence in
 * [RefineGrammarParser]'s small closed edit-vocabulary, then parses that
 * sentence deterministically — the model's own words are never trusted as
 * the [RefineOperation] itself, only as candidate phrasing for the same
 * grammar every deterministic edit already goes through
 * ([IntentRouter]'s own `"make it 30 minutes"` pattern).
 *
 * [CueService][com.cues.core.CueService] only ever reaches for this when the
 * deterministic router found no match at all — this is a fallback, not a
 * replacement — and whatever [RefineOperation] comes back is still applied
 * through [Refiner.apply], which independently re-validates the edited
 * routine exactly as it already does for every other edit. The model does
 * not get to vouch for its own output here either.
 */
class OnDeviceRefinePhraser(
    private val session: LlmSession = UnconfiguredLlmSession(),
    private val parser: RefineGrammarParser = RefineGrammarParser(),
) {
    suspend fun phrase(text: String): RefinePhrasing? = try {
        val output = session.generate(prompt(text))
        RefinePhrasing(parser.parse(output.text), output)
    } catch (e: Exception) {
        null
    }

    private fun prompt(text: String): String =
        "Restate this cue edit request as one sentence using only: " +
            "\"set duration to <N> minutes\", \"add day <weekday>\", or \"remove day <weekday>\". " +
            "Request: $text"
}
