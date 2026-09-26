package com.cues.core.review

import com.cues.core.drafting.AttemptOutcome
import com.cues.core.drafting.DraftReason
import com.cues.core.drafting.DraftTrace
import com.cues.core.drafting.DraftVerdict
import com.cues.core.drafting.DrafterAttempt
import com.cues.core.inference.InferenceBackend
import com.cues.core.model.DraftSourceId

/**
 * What CLAUDE.md's "every draft names the drafter that produced it" means in
 * practice: one rendering, shared by every screen and by receipts, so
 * "grammar parser" vs "on-device model" is never spelled two different ways
 * (or, before Sprint 8, spelled the *same* way regardless of whether a model
 * ran at all — CLEANUP.md CL-18).
 *
 * [primary] is what actually produced the routine on screen — the routine's
 * own [com.cues.core.model.Routine.draftedBy]. [confirmedBy] and [modelNote]
 * say what happened to the *other* attempt, from the [DraftTrace] the
 * drafter that produced this result carried. [backend] is set only from a
 * real [com.cues.core.inference.InferenceReport], never from eligibility.
 */
data class DraftCredit(
    val primary: DraftSourceId,
    val confirmedBy: DraftSourceId?,
    val modelNote: ModelNote,
    val backend: InferenceBackend?,
) {
    /** The one place this credit becomes text — CLI, Review, receipts and every badge render this, never their own wording. */
    fun label(): String {
        val base = sourceLabel(primary)
        val modelBacked = primary == DraftSourceId.ON_DEVICE_LLM || primary == DraftSourceId.SARVAM_CLOUD
        return when (modelNote) {
            ModelNote.NONE -> when {
                confirmedBy != null -> "$base · confirmed by ${sourceLabel(confirmedBy)}"
                modelBacked -> "$base · independently validated"
                else -> base
            }
            ModelNote.MODEL_OFF -> "$base · model off"
            ModelNote.MODEL_NOT_INSTALLED -> "$base · no model installed"
            ModelNote.MODEL_TIMED_OUT -> "$base · model timed out"
            ModelNote.MODEL_INVALID -> "$base · model's draft didn't validate"
            ModelNote.MODEL_FAILED -> "$base · model failed"
            ModelNote.CHOSEN_FROM_DISAGREEMENT -> "$base · chosen by you from a disagreement"
        }
    }

    companion object {
        private fun sourceLabel(source: DraftSourceId): String = when (source) {
            DraftSourceId.GRAMMAR_PARSER -> "grammar parser"
            DraftSourceId.ON_DEVICE_LLM -> "on-device model"
            DraftSourceId.SARVAM_CLOUD -> "cloud assist"
            DraftSourceId.IMPORTED_CARD -> "imported card"
            // Neither ever backs a routine's own DraftCredit — both are
            // completion-only hub/system-agent calls (CLEANUP.md CL-38),
            // never a source `CueService.draft` can attach to a Routine —
            // but the `when` above is exhaustive, so a label still exists
            // rather than a crash if one is ever passed here by mistake.
            DraftSourceId.EXTERNAL_GEMMA_CALL -> "local model hub"
            DraftSourceId.SYSTEM_AGENT_CALL -> "system agent"
        }

        /**
         * Builds the credit for a routine whose [primary] source is
         * [draftedBy], from the [trace] the drafter attached — `null` when
         * there was only ever one drafter to ask (no [DraftTrace] at all;
         * verdict [DraftVerdict.SINGLE] in spirit, though [DifferentialDrafter]
         * never literally emits that value itself).
         *
         * [chosenFromDisagreement] is set by the caller — never inferred from
         * the trace — when this particular routine is one side of a
         * [DraftVerdict.DISAGREED] result that the user picked explicitly;
         * see the Ask disagreement card.
         */
        fun credit(
            trace: DraftTrace?,
            draftedBy: DraftSourceId,
            chosenFromDisagreement: Boolean = false,
        ): DraftCredit {
            val modelAttempt = trace?.attempts?.firstOrNull { it.source != DraftSourceId.GRAMMAR_PARSER }
            val backend = modelAttempt?.inferenceReport?.backend

            if (chosenFromDisagreement) {
                return DraftCredit(draftedBy, null, ModelNote.CHOSEN_FROM_DISAGREEMENT, backend)
            }
            if (trace == null) {
                return DraftCredit(draftedBy, null, ModelNote.NONE, null)
            }
            return when (trace.verdict) {
                DraftVerdict.AGREED -> DraftCredit(draftedBy, modelAttempt?.source, ModelNote.NONE, backend)
                DraftVerdict.MODEL_ONLY -> DraftCredit(draftedBy, null, ModelNote.NONE, backend)
                DraftVerdict.PARSER_ONLY -> DraftCredit(draftedBy, null, noteFor(modelAttempt), null)
                // A disagreement never reaches a Drafted routine on its own —
                // the user must pick a side first (see [chosenFromDisagreement]) —
                // but this is still exhaustive rather than throwing.
                DraftVerdict.DISAGREED -> DraftCredit(draftedBy, null, ModelNote.NONE, backend)
                DraftVerdict.NEITHER -> DraftCredit(draftedBy, null, ModelNote.NONE, null)
                // A single-attempt trace (CueService synthesizes one for any
                // unwrapped drafter that still carried its own report, e.g. a
                // raw cloud call) — the one attempt's own backend is exactly
                // what ran, so it is shown, not withheld.
                DraftVerdict.SINGLE -> DraftCredit(draftedBy, null, ModelNote.NONE, backend)
            }
        }

        private fun noteFor(attempt: DrafterAttempt?): ModelNote = when (attempt?.outcome) {
            null -> ModelNote.NONE
            AttemptOutcome.SKIPPED_UNAVAILABLE -> when (attempt.reasonCode) {
                DraftReason.MODEL_UNAVAILABLE_NOT_INSTALLED -> ModelNote.MODEL_NOT_INSTALLED
                else -> ModelNote.MODEL_OFF
            }
            AttemptOutcome.TIMED_OUT -> ModelNote.MODEL_TIMED_OUT
            AttemptOutcome.INVALID -> ModelNote.MODEL_INVALID
            AttemptOutcome.FAILED, AttemptOutcome.CANCELLED -> ModelNote.MODEL_FAILED
            AttemptOutcome.DRAFTED, AttemptOutcome.CLARIFY -> ModelNote.NONE
        }
    }
}

/** What, if anything, is worth saying about the *other* attempt — never prose, always one of these. */
enum class ModelNote {
    NONE,
    MODEL_OFF,
    MODEL_NOT_INSTALLED,
    MODEL_TIMED_OUT,
    MODEL_INVALID,
    MODEL_FAILED,
    CHOSEN_FROM_DISAGREEMENT,
}
