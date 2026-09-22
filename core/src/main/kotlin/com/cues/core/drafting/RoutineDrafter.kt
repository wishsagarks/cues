package com.cues.core.drafting

import com.cues.core.compile.Finding
import com.cues.core.model.Routine

/**
 * Which component actually produced a draft.
 *
 * Recorded on every result and surfaced in diagnostics. PRS requirement IN-03
 * exists because a canonical parser presented as "AI understanding" is a lie
 * told to a judge, and an on-device model that silently fell back to a parser
 * is the same lie told by accident. Neither is acceptable, so the answer is
 * carried in the data rather than asserted in a slide.
 */
enum class DraftSourceId {
    /** A deterministic phrase grammar. Fast, offline, and honest about its limits. */
    GRAMMAR_PARSER,

    /** A small language model running on the phone. */
    ON_DEVICE_LLM,
}

/** What a drafter could not handle, kept rather than discarded. */
data class Unsupported(
    val fragment: String,
    val explanation: String,
)

sealed interface DraftResult {

    /** The drafter that produced this result, whatever the outcome. */
    val source: DraftSourceId

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
        val elapsedMillis: Long = 0,
    ) : DraftResult

    /** The request was understood well enough to know a question is needed. */
    data class NeedsClarification(
        override val source: DraftSourceId,
        val question: String,
        val about: String,
        /**
         * Limitations that explain the question, when there are any.
         *
         * Carried as data rather than folded into [question] so that callers
         * can render them properly and tests can assert on them without
         * matching prose.
         */
        val unsupported: List<Unsupported> = emptyList(),
        val elapsedMillis: Long = 0,
    ) : DraftResult

    /** Nothing usable came back. */
    data class Failed(
        override val source: DraftSourceId,
        val reason: String,
        val elapsedMillis: Long = 0,
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
