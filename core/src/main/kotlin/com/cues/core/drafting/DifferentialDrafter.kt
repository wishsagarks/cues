package com.cues.core.drafting

import com.cues.core.compile.Finding
import com.cues.core.compile.Normalizer
import com.cues.core.compile.Severity
import com.cues.core.compile.Validator
import com.cues.core.model.DraftSourceId
import com.cues.core.model.Routine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Runs both drafters to completion and compares them, rather than racing one
 * against a fallback the way [CompositeDrafter] does. Disagreement is a
 * question, never a silent selection.
 *
 * Each call is protected the same way [CompositeDrafter] protects its
 * primary: a timeout turns a hang into an honest [DraftResult.Failed], an
 * exception never escapes uncaught, and a routine from anything other than
 * the deterministic parser is only trusted once the independent validator
 * accepts it — the model does not get to vouch for its own output, whether
 * or not the parser agrees with it.
 */
class DifferentialDrafter(
    private val first: RoutineDrafter,
    private val second: RoutineDrafter,
    private val timeoutMillis: Long = 4_000,
) : RoutineDrafter {
    override val id: DraftSourceId = first.id

    override suspend fun draft(text: String): DraftResult {
        val a = guarded(first, text)
        val b = guarded(second, text)
        if (a is DraftResult.Drafted && b is DraftResult.Drafted) {
            if (Normalizer.digest(a.routine) == Normalizer.digest(b.routine)) {
                // Prefer the deterministic parser's own span data when it's
                // part of the agreement — it's the one source CueService
                // trusts without recomputing, so keeping it here is strictly
                // more auditable than picking the model's copy instead.
                val chosen = if (b.source == DraftSourceId.GRAMMAR_PARSER) b else a
                return chosen.copy(
                    findings = chosen.findings + Finding(Severity.WARNING, "draft", "Two independent drafters agree."),
                )
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

    private suspend fun guarded(drafter: RoutineDrafter, text: String): DraftResult {
        val result = try {
            withTimeoutOrNull(timeoutMillis) { drafter.draft(text) }
                ?: DraftResult.Failed(drafter.id, "Did not answer within ${timeoutMillis}ms.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DraftResult.Failed(drafter.id, e.message ?: e::class.simpleName ?: "crashed")
        }
        val unvalidatedModelDraft = result is DraftResult.Drafted &&
            result.source != DraftSourceId.GRAMMAR_PARSER &&
            !Validator.validate(result.routine).isValid
        return if (unvalidatedModelDraft) {
            DraftResult.Failed(drafter.id, "Produced a cue that did not pass independent validation.")
        } else {
            result
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
