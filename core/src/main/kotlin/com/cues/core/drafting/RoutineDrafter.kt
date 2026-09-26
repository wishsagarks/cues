package com.cues.core.drafting

import com.cues.core.compile.Finding
import com.cues.core.inference.InferenceReport
import com.cues.core.model.DraftSourceId
import com.cues.core.model.Routine

/** What a drafter could not handle, kept rather than discarded. */
data class Unsupported(
    val fragment: String,
    val explanation: String,
)

enum class ClauseKind { MAPPED, FILLER, UNACCOUNTED }

data class ClauseSpan(val text: String, val range: IntRange, val kind: ClauseKind)

/** Which top-level clause two disagreeing drafts differ on — a closed code, never a sentence built from the routines. */
enum class DifferingClause { TRIGGER, CONDITIONS, ACTIONS, ENDING, OTHER }

/** One drafter's own validated routine, offered as a side of a [DraftResult.NeedsClarification] disagreement — never the model's prose, always something [com.cues.core.compile.Validator] already accepted. */
data class DraftCandidate(val source: DraftSourceId, val routine: Routine)

sealed interface DraftResult {

    /** The drafter that produced this result, whatever the outcome. */
    val source: DraftSourceId

    /** Every drafter that was actually asked, and what each one did — set only by [DifferentialDrafter]. Null for a single-drafter result. */
    val trace: DraftTrace?

    /**
     * A routine was produced. It is not yet approved, not yet validated and
     * not yet armed — only proposed.
     */
    data class Drafted(
        override val source: DraftSourceId,
        val routine: Routine,
        /**
         * Parts of the request that did not make it into the routine.
         *
         * Never empty-and-ignored: a clause that could not be honoured is
         * reported so the user can decide, because silently dropping "except
         * on Fridays" produces a rule that works perfectly and does the wrong
         * thing.
         */
        val unsupported: List<Unsupported> = emptyList(),
        val findings: List<Finding> = emptyList(),
        val consumed: List<IntRange> = emptyList(),
        val clauses: List<ClauseSpan> = emptyList(),
        val elapsedMillis: Long = 0,
        /** Set only by a drafter backed by a local model — null for the parser. */
        val inferenceReport: InferenceReport? = null,
        override val trace: DraftTrace? = null,
    ) : DraftResult

    /** The request was understood well enough to know a question is needed. */
    data class NeedsClarification(
        override val source: DraftSourceId,
        val question: String,
        val about: String,
        /** Paired-device choices when [about] is `trigger.device`. */
        val deviceCandidates: List<PairedDevice> = emptyList(),
        /**
         * Both sides of a disagreement, when [about] is `draft.disagreement` —
         * each already independently validated. The UI renders both rather
         * than the drafter's own prose about the difference; [differingClause]
         * says which top-level clause to highlight.
         */
        val candidates: List<DraftCandidate> = emptyList(),
        val differingClause: DifferingClause? = null,
        /**
         * The app name text named an "open X" request, when [about] is
         * `action.app`. Never a package name — the parser has no installed-app
         * list to resolve one from. The Android layer queries what's actually
         * installed, filters by this text, and lets the user confirm one; the
         * re-drafted text then carries the resolved choice back in, the same
         * pattern [deviceCandidates] already uses for a paired device.
         */
        val appQuery: String? = null,
        /**
         * Limitations that explain the question, when there are any.
         *
         * Carried as data rather than folded into [question] so that callers
         * can render them properly and tests can assert on them without
         * matching prose.
         */
        val unsupported: List<Unsupported> = emptyList(),
        val consumed: List<IntRange> = emptyList(),
        val clauses: List<ClauseSpan> = emptyList(),
        val elapsedMillis: Long = 0,
        val inferenceReport: InferenceReport? = null,
        override val trace: DraftTrace? = null,
    ) : DraftResult

    /** Nothing usable came back. */
    data class Failed(
        override val source: DraftSourceId,
        val reason: String,
        val elapsedMillis: Long = 0,
        val inferenceReport: InferenceReport? = null,
        override val trace: DraftTrace? = null,
    ) : DraftResult
}

/**
 * Turns a spoken or typed request into a proposed routine.
 *
 * Implementations propose typed data and nothing else. There is no path from
 * here to an executor: a drafter returns a [Routine], which is inert until it
 * has been validated and explicitly approved.
 */
interface RoutineDrafter {
    val id: DraftSourceId
    suspend fun draft(text: String): DraftResult
}
