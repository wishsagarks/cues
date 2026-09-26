package com.cues.core.drafting

import com.cues.core.inference.InferenceReport
import com.cues.core.model.DraftSourceId

/**
 * What happened to one drafter's own attempt, independent of which attempt a
 * [DraftTrace] ultimately credits. Kept even for an attempt that lost, so a
 * model call that ran — and cost load time, generation time and tokens —
 * is never silently invisible just because its output was not the one used.
 *
 * See CLEANUP.md CL-18/CL-34: before this existed, [DifferentialDrafter]
 * kept an [InferenceReport] only when the model-backed attempt was the sole
 * result returned. Agreement, disagreement, an invalid model draft and a
 * timeout all discarded it — the exact honesty gap this type exists to close.
 */
enum class AttemptOutcome {
    /** Produced a routine. */
    DRAFTED,

    /** Asked a clarifying question rather than drafting. */
    CLARIFY,

    /** Ran, but produced nothing usable. */
    FAILED,

    /** Did not answer inside its budget. */
    TIMED_OUT,

    /** Cancelled by the user before it finished. */
    CANCELLED,

    /** Drafted something, but [com.cues.core.compile.Validator] rejected it — never trusted, even alone. */
    INVALID,

    /** Never attempted: the drafter was not available (not installed, or turned off). */
    SKIPPED_UNAVAILABLE,
}

/**
 * How a [DraftTrace] resolved once every attempt was in — see
 * [DifferentialDrafter] for how each value is reached.
 */
enum class DraftVerdict {
    /** Two attempts produced the same normalized meaning. */
    AGREED,

    /** Two attempts drafted, but disagreed on meaning — a question for the user, never a silent pick. */
    DISAGREED,

    /** Only the model-backed attempt drafted; the other did not. */
    MODEL_ONLY,

    /** Only the deterministic parser drafted; the model did not, or was unavailable. */
    PARSER_ONLY,

    /** Neither attempt produced anything usable. */
    NEITHER,

    /** There was only ever one drafter to ask — not a differential comparison at all. */
    SINGLE,
}

/**
 * A closed set of reason codes carried on a [DrafterAttempt], parallel to
 * [com.cues.core.eval.ReasonCode]'s own discipline: the trace carries a code,
 * never prose, so `:app` renders copy from a lookup table and a receipt or a
 * CLI can print the code itself without duplicating wording.
 */
object DraftReason {
    const val MODEL_UNAVAILABLE_NOT_INSTALLED = "model.unavailable.not_installed"
    const val MODEL_UNAVAILABLE_OFF = "model.unavailable.off"
    const val TIMEOUT_LOAD = "timeout.load"
    const val TIMEOUT_GENERATE = "timeout.generate"
    const val CANCELLED_USER = "cancelled.user"
    const val VALIDATOR_REJECTED = "validator.rejected"
    const val CRASHED = "crashed"
}

/** One drafter's own attempt within a [DraftTrace]. */
data class DrafterAttempt(
    val source: DraftSourceId,
    val outcome: AttemptOutcome,
    /** A [DraftReason] constant, or a caught exception's message when neither applies. Null when [outcome] is [AttemptOutcome.DRAFTED] or [AttemptOutcome.CLARIFY]. */
    val reasonCode: String? = null,
    val elapsedMillis: Long = 0,
    /** Kept whenever a model-backed attempt produced one, regardless of [outcome] — the whole point of this type. */
    val inferenceReport: InferenceReport? = null,
)

/**
 * A record of every drafter that was asked, and what each one did — not only
 * the one whose output was used. Attached to a [DraftResult] so the trace
 * that decided a credit label travels with the routine it produced, into
 * [com.cues.core.CueService]'s ledger bookkeeping and the review screen's
 * provenance badge.
 */
data class DraftTrace(
    val attempts: List<DrafterAttempt>,
    val verdict: DraftVerdict,
    /** Identifies the prompt template used, when a model attempt ran one — e.g. `"draft-v2"`. Null for a parser-only trace. */
    val promptId: String? = null,
)
