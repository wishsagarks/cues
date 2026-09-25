package com.cues.app.coach

import com.cues.core.coach.EvidenceLine
import com.cues.core.coach.Suggestion
import com.cues.core.drafting.LlmSession
import com.cues.core.drafting.UnconfiguredLlmSession

/**
 * C2: purely cosmetic narration for a coach [Suggestion] — never a second
 * decision-maker. [Suggestion]'s structured fields (kind, evidence, proposal)
 * are already computed by [com.cues.core.coach.Detectors] with no model
 * involved; this only asks the model to restate those *already-decided*
 * numbers as one readable sentence for display under the existing card.
 *
 * Crosses no trust boundary at all: unlike [com.cues.core.assistant.OnDeviceRefinePhraser]
 * or [com.cues.core.drafting.OnDeviceLlmDrafter], this never produces a
 * [com.cues.core.model.Routine] or a [com.cues.core.assistant.RefineOperation] —
 * nothing here is ever handed to [com.cues.core.compile.Validator]. It lives
 * in `:app`, not `:core`, because it is presentation, not drafting: on
 * failure or when the model isn't enabled, [narrate] returns `null` and the
 * caller shows no narration line at all — an honest absence, never a
 * fabricated sentence describing evidence that wasn't there.
 *
 * Not yet wired into a screen: the coach-suggestion card this would sit
 * under was not carried over into the `NowScreen` redesign (CL-33) — see
 * `tasks/todo.md`'s Task 6 note. Reconnecting the card itself is that
 * redesign's job, not this class's; wiring this in is a one-line addition
 * once it exists (call [narrate] with the card's own [Suggestion] and render
 * the result as small "AI-generated summary — informational only" copy).
 */
class SuggestionNarrator(private val session: LlmSession = UnconfiguredLlmSession()) {
    suspend fun narrate(suggestion: Suggestion): String? = try {
        session.generate(prompt(suggestion)).text.trim().takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    }

    private fun prompt(suggestion: Suggestion): String {
        val evidence = suggestion.evidence.joinToString("; ") { it.describe() }
        return "In one plain sentence, describe this observation to the person it's about. " +
            "Do not suggest anything new or add numbers not given. " +
            "Kind: ${suggestion.kind.name}. Evidence: $evidence. Proposed edit: ${suggestion.proposal}."
    }

    private fun EvidenceLine.describe(): String = "$text ($count)"
}
