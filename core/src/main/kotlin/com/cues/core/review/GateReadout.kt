package com.cues.core.review

import com.cues.core.context.Remedy
import com.cues.core.context.UnknownRemedy
import com.cues.core.context.UnreadableInputs
import com.cues.core.eval.FreshnessPolicy
import com.cues.core.eval.Reason
import com.cues.core.eval.Truth
import com.cues.core.model.ContextSnapshot
import com.cues.core.model.ContextValue
import com.cues.core.model.Routine
import com.cues.core.signals.SignalRegistry

/**
 * One condition's live verdict, as a gate chip or tile shows it.
 *
 * [clauseText] is the same wording [ReviewCopy.conditionLines] uses for this
 * condition, so the gate and the review row it sits beside name it alike.
 */
data class GateLine(
    val clauseText: String,
    val truth: Truth,
    /** The kit's own verdict, rendered from reason codes and observed values. */
    val reason: Reason,
    /** For an UNKNOWN gate: the reading that could not be taken. Null otherwise, or when no single input explains it. */
    val unreadable: ContextValue.Unknown? = null,
    /** What would fix [unreadable], when anything would. Drives the inline Fix button. */
    val remedy: Remedy? = null,
)

/**
 * Reads each of a routine's gates against a live snapshot, before anything fires.
 *
 * Every verdict comes from [SignalRegistry.evaluate] — the exact function the
 * runtime's [com.cues.core.eval.Evaluator] calls — with the same freshness
 * rule. A gate that looks open here is open to the runtime too; a second,
 * display-only copy of the condition logic is precisely how a preview ends up
 * predicting the preview instead of the phone.
 *
 * Deliberately per-condition, not a combined verdict: the trigger is an event
 * that has not happened yet, so "would this start?" has no honest answer
 * here. What does have one is "which gates are open, closed, or unreadable
 * right now".
 */
object GateReadout {

    fun forRoutine(
        routine: Routine,
        snapshot: ContextSnapshot,
        freshness: FreshnessPolicy = FreshnessPolicy(),
    ): List<GateLine> = routine.conditions.map { condition ->
        val reason = SignalRegistry.evaluate(condition, snapshot, freshness)
        val unreadable = if (reason.truth == Truth.UNKNOWN) UnreadableInputs.behind(reason.code, snapshot) else null
        GateLine(
            clauseText = SignalRegistry.reviewText(condition),
            truth = reason.truth,
            reason = reason,
            unreadable = unreadable,
            remedy = unreadable?.let { UnknownRemedy.forUnknown(it) },
        )
    }
}
