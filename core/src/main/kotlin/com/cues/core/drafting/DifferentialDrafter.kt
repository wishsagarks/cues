package com.cues.core.drafting

import com.cues.core.compile.Normalizer
import com.cues.core.model.DraftSourceId
import com.cues.core.model.Routine

/** Compares two independent proposals; disagreement is a question, never a silent selection. */
class DifferentialDrafter(
    private val first: RoutineDrafter,
    private val second: RoutineDrafter,
) : RoutineDrafter {
    override val id: DraftSourceId = first.id

    override suspend fun draft(text: String): DraftResult {
        val a = first.draft(text)
        val b = second.draft(text)
        if (a is DraftResult.Drafted && b is DraftResult.Drafted) {
            if (Normalizer.digest(a.routine) == Normalizer.digest(b.routine)) {
                return a.copy(findings = a.findings + com.cues.core.compile.Finding(
                    com.cues.core.compile.Severity.WARNING, "draft", "Two independent drafters agree.",
                ))
            }
            return DraftResult.NeedsClarification(
                source = id,
                question = "The drafters disagree about ${firstDifference(a.routine, b.routine)}. Which is intended?",
                about = "draft.disagreement",
            )
        }
        return when {
            a is DraftResult.Drafted -> a
            b is DraftResult.Drafted -> b
            a is DraftResult.NeedsClarification -> a
            b is DraftResult.NeedsClarification -> b
            else -> DraftResult.Failed(id, "Neither drafter produced a usable cue.")
        }
    }

    private fun firstDifference(a: Routine, b: Routine): String = when {
        a.trigger != b.trigger -> "the trigger"
        a.conditions != b.conditions -> "the conditions"
        a.actions != b.actions -> "the action"
        a.endConditions != b.endConditions -> "the ending"
        else -> "the routine"
    }
}
