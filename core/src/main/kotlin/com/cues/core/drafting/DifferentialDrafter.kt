package com.cues.core.drafting

import com.cues.core.compile.Normalizer
import com.cues.core.model.DraftSourceId
import com.cues.core.model.Routine
import com.cues.core.ports.ModelAvailability
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
 *
 * Sprint 8 (CLEANUP.md CL-18/CL-34) added [DraftTrace]: every call now
 * records what *each* attempt did, not only the one whose output won. Before
 * this, an agreed, disagreed, invalid or timed-out model attempt discarded
 * its own [com.cues.core.inference.InferenceReport] entirely — the model ran,
 * cost load and generation time, and left no trace anywhere. See [id]'s own
 * doc for why the drafter's reported identity is a separate, narrower claim
 * than the trace.
 */
class DifferentialDrafter(
    private val first: RoutineDrafter,
    private val second: RoutineDrafter,
    /**
     * Whether [first] — conventionally the model-backed drafter — is even
     * worth asking right now. Checked once per call, before any timeout is
     * spent: [ModelAvailability.NOT_INSTALLED] or [ModelAvailability.INSTALLED_OFF]
     * skip [first] outright, recorded as [AttemptOutcome.SKIPPED_UNAVAILABLE]
     * rather than an attempt that was made and failed.
     */
    private val firstAvailability: () -> ModelAvailability = { ModelAvailability.READY },
    private val timeoutMillis: Long = 4_000,
    /** Recorded on the resulting [DraftTrace] — identifies which prompt template, if any, backed this call. */
    private val promptId: String? = null,
) : RoutineDrafter {
    /**
     * The identity this drafter reports to callers that only look at
     * [RoutineDrafter.id] — kept as [first]'s id for source compatibility.
     * This is *not* the same claim as [DraftTrace.verdict]: `:app` must read
     * the trace (or [ModelAvailability]) to know whether a model actually
     * ran, never this property alone. See CLEANUP.md CL-18 item "the drafter
     * label is always ON-DEVICE MODEL."
     */
    override val id: DraftSourceId = first.id

    override suspend fun draft(text: String): DraftResult {
        val (aResult, aAttempt) = when (val availability = firstAvailability()) {
            ModelAvailability.NOT_INSTALLED -> skipped(first, DraftReason.MODEL_UNAVAILABLE_NOT_INSTALLED)
            ModelAvailability.INSTALLED_OFF -> skipped(first, DraftReason.MODEL_UNAVAILABLE_OFF)
            ModelAvailability.READY -> guarded(first, text)
        }
        val (bResult, bAttempt) = guarded(second, text)
        val attempts = listOf(aAttempt, bAttempt)

        if (aResult is DraftResult.Drafted && bResult is DraftResult.Drafted) {
            if (Normalizer.digest(aResult.routine) == Normalizer.digest(bResult.routine)) {
                // Prefer the deterministic parser's own span data when it's
                // part of the agreement — it's the one source CueService
                // trusts without recomputing, so keeping it here is strictly
                // more auditable than picking the model's copy instead. No
                // WARNING finding is added any more: the trace's own AGREED
                // verdict is what the UI now reads to say the two agreed.
                val chosen = if (bResult.source == DraftSourceId.GRAMMAR_PARSER) bResult else aResult
                return chosen.copy(trace = DraftTrace(attempts, DraftVerdict.AGREED, promptId))
            }
            val clause = firstDifference(aResult.routine, bResult.routine)
            return DraftResult.NeedsClarification(
                source = id,
                question = "The drafters disagree about ${clauseLabel(clause)}. Which is intended?",
                about = "draft.disagreement",
                candidates = listOf(
                    DraftCandidate(aResult.source, aResult.routine),
                    DraftCandidate(bResult.source, bResult.routine),
                ),
                differingClause = clause,
                trace = DraftTrace(attempts, DraftVerdict.DISAGREED, promptId),
            )
        }
        return when {
            aResult is DraftResult.Drafted -> aResult.copy(trace = DraftTrace(attempts, DraftVerdict.MODEL_ONLY, promptId))
            bResult is DraftResult.Drafted -> bResult.copy(trace = DraftTrace(attempts, DraftVerdict.PARSER_ONLY, promptId))
            // A clarifying question from either side still surfaces as one —
            // unchanged behaviour from before this trace existed — now
            // carrying the trace too.
            aResult is DraftResult.NeedsClarification -> aResult.copy(trace = DraftTrace(attempts, DraftVerdict.NEITHER, promptId))
            bResult is DraftResult.NeedsClarification -> bResult.copy(trace = DraftTrace(attempts, DraftVerdict.NEITHER, promptId))
            else -> DraftResult.Failed(
                id,
                "Neither drafter produced a usable cue.",
                trace = DraftTrace(attempts, DraftVerdict.NEITHER, promptId),
            )
        }
    }

    /** [first] was never asked — no timeout spent, no [com.cues.core.inference.InferenceReport] to lose. */
    private fun skipped(drafter: RoutineDrafter, reason: String): Pair<DraftResult, DrafterAttempt> {
        val failed = DraftResult.Failed(drafter.id, "The on-device model is not available right now.")
        return failed to DrafterAttempt(drafter.id, AttemptOutcome.SKIPPED_UNAVAILABLE, reason, elapsedMillis = 0)
    }

    /**
     * Runs one drafter under a timeout and independent validation, and
     * — unlike the pre-Sprint-8 version of this class — always returns the
     * [DrafterAttempt] alongside the [DraftResult], so a caller that only
     * looks at the winning [DraftResult] can still be told the loser ran.
     */
    private suspend fun guarded(drafter: RoutineDrafter, text: String): Pair<DraftResult, DrafterAttempt> {
        val startedAt = System.nanoTime()
        val raw: DraftResult? = try {
            withTimeoutOrNull(timeoutMillis) { drafter.draft(text) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DraftResult.Failed(drafter.id, e.message ?: e::class.simpleName ?: DraftReason.CRASHED)
        }
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000

        if (raw == null) {
            val failed = DraftResult.Failed(drafter.id, "Did not answer within ${timeoutMillis}ms.", elapsedMillis)
            return failed to DrafterAttempt(drafter.id, AttemptOutcome.TIMED_OUT, DraftReason.TIMEOUT_GENERATE, elapsedMillis)
        }

        val unvalidatedModelDraft = raw is DraftResult.Drafted && ModelDraftGuard.rejects(raw)
        if (unvalidatedModelDraft) {
            // The model does not get to vouch for its own output just
            // because it produced *something* — but its InferenceReport is
            // still kept: the model ran, and that cost is real even though
            // the output isn't trusted. This is exactly the report Sprint 8
            // stopped discarding.
            val report = (raw as DraftResult.Drafted).inferenceReport
            val failed = DraftResult.Failed(
                drafter.id,
                "Produced a cue that did not pass independent validation.",
                elapsedMillis,
                report,
            )
            return failed to DrafterAttempt(drafter.id, AttemptOutcome.INVALID, DraftReason.VALIDATOR_REJECTED, elapsedMillis, report)
        }

        val attempt = when (raw) {
            is DraftResult.Drafted -> DrafterAttempt(drafter.id, AttemptOutcome.DRAFTED, null, elapsedMillis, raw.inferenceReport)
            is DraftResult.NeedsClarification -> DrafterAttempt(drafter.id, AttemptOutcome.CLARIFY, null, elapsedMillis, raw.inferenceReport)
            is DraftResult.Failed -> DrafterAttempt(drafter.id, AttemptOutcome.FAILED, raw.reason, elapsedMillis, raw.inferenceReport)
        }
        return raw to attempt
    }

    private fun firstDifference(a: Routine, b: Routine): DifferingClause = when {
        a.trigger != b.trigger -> DifferingClause.TRIGGER
        a.conditions != b.conditions -> DifferingClause.CONDITIONS
        a.actions != b.actions -> DifferingClause.ACTIONS
        a.endConditions != b.endConditions -> DifferingClause.ENDING
        else -> DifferingClause.OTHER
    }

    private fun clauseLabel(clause: DifferingClause): String = when (clause) {
        DifferingClause.TRIGGER -> "the trigger"
        DifferingClause.CONDITIONS -> "the conditions"
        DifferingClause.ACTIONS -> "the action"
        DifferingClause.ENDING -> "the ending"
        DifferingClause.OTHER -> "the routine"
    }
}
