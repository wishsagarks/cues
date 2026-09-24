package com.cues.core.receipt

import com.cues.core.eval.Reason
import com.cues.core.model.ActionRecord
import com.cues.core.model.CleanupObligation
import com.cues.core.model.ContextSnapshot
import com.cues.core.model.EndReason
import com.cues.core.model.EventProvenance
import com.cues.core.model.Routine
import com.cues.core.model.Session
import com.cues.core.model.SessionState
import com.cues.core.session.EngineResult
import kotlinx.serialization.Serializable

/** Which lifecycle moment a receipt records. One member per [EngineResult] variant, plus a resume. */
@Serializable
enum class ReceiptKind {
    STARTED,
    SKIPPED,
    ENDED,
    EXIT_SCHEDULED,
    EXIT_CANCELLED,
    /** Nothing happened. Recorded anyway, because the text receipt is, and the two must pair up. */
    IGNORED,
    /** A [com.cues.core.model.ActionState.PENDING] step was retried once someone was present. */
    RESUMED,
}

/**
 * The structured twin of a text [Receipt]: the same event, as data a screen
 * can filter, count and chart without parsing prose.
 *
 * Everything here is copied from an [EngineResult] or the [Session] it
 * carries: reason codes, action records, obligations. [headline] and [lines]
 * are the text receipt's own headline and lines, taken from the very same
 * [Receipt] object that is written to the text log, so the two cannot say
 * different things. Nothing here is generated prose, and nothing is inferred
 * after the fact.
 */
@Serializable
data class ReceiptRecord(
    /**
     * The key the paired text receipt was filed under: a session id, or the
     * synthetic `skip-…`/`ignored-…` id used when no session exists.
     */
    val key: String,
    /** The real session, when there is one. Null for a skip or an ignored event. */
    val sessionId: String?,
    val routineId: String,
    /** The routine version that decided this — the session's pinned version, when a session exists. */
    val routineVersion: Int,
    val atMillis: Long,
    val kind: ReceiptKind,
    /**
     * Where the deciding event came from. Null when no event drove this
     * receipt at all: reconciliation after a restart, or a coverage check.
     * Kept so a rehearsal can never be counted as something that happened.
     */
    val provenance: EventProvenance?,
    /** The evaluator's verdicts, exactly as produced. Empty for receipts no evaluation stood behind. */
    val reasons: List<Reason> = emptyList(),
    val actions: List<ActionRecord> = emptyList(),
    val obligations: List<CleanupObligation> = emptyList(),
    val sessionState: SessionState? = null,
    val endReason: EndReason? = null,
    /**
     * What the evaluator was shown, for a start or a skip. Lets a later
     * reader say *which* signal could not be read and why, without the
     * receipt having to spell it out in prose.
     */
    val observedInputs: ContextSnapshot? = null,
    val headline: String,
    val lines: List<String>,
) {
    /** The receipt text exactly as the text log stores it. */
    val textLines: List<String> get() = listOf(headline) + lines
}

/**
 * Builds [ReceiptRecord]s from the same inputs [Receipts] renders text from.
 *
 * The text is taken from [Receipts] rather than rendered a second time, so a
 * wording change there flows into the structured record automatically.
 */
object ReceiptRecords {

    fun forResult(
        routine: Routine,
        result: EngineResult,
        key: String,
        atMillis: Long,
        provenance: EventProvenance?,
        observedInputs: ContextSnapshot? = null,
    ): ReceiptRecord {
        val receipt = Receipts.forResult(routine, result)
        return when (result) {
            is EngineResult.Started -> fromSession(
                ReceiptKind.STARTED, routine, result.session, key, atMillis, provenance, receipt,
                reasons = result.decision.reasons,
                observedInputs = observedInputs ?: result.session.observedInputs,
            )
            is EngineResult.Ended -> fromSession(ReceiptKind.ENDED, routine, result.session, key, atMillis, provenance, receipt)
            is EngineResult.ExitScheduled ->
                fromSession(ReceiptKind.EXIT_SCHEDULED, routine, result.session, key, atMillis, provenance, receipt)
            is EngineResult.ExitCancelled ->
                fromSession(ReceiptKind.EXIT_CANCELLED, routine, result.session, key, atMillis, provenance, receipt)
            is EngineResult.Skipped -> ReceiptRecord(
                key = key,
                sessionId = null,
                routineId = routine.id,
                routineVersion = routine.version,
                atMillis = atMillis,
                kind = ReceiptKind.SKIPPED,
                provenance = provenance,
                reasons = result.reasons,
                observedInputs = observedInputs,
                headline = receipt.headline,
                lines = receipt.lines,
            )
            EngineResult.Ignored -> ReceiptRecord(
                key = key,
                sessionId = null,
                routineId = routine.id,
                routineVersion = routine.version,
                atMillis = atMillis,
                kind = ReceiptKind.IGNORED,
                provenance = provenance,
                headline = receipt.headline,
                lines = receipt.lines,
            )
        }
    }

    /** The structured twin of [Receipts.resumed]. */
    fun resumed(routine: Routine, session: Session, atMillis: Long): ReceiptRecord =
        fromSession(ReceiptKind.RESUMED, routine, session, session.id, atMillis, EventProvenance.MANUAL, Receipts.resumed(session))

    private fun fromSession(
        kind: ReceiptKind,
        routine: Routine,
        session: Session,
        key: String,
        atMillis: Long,
        provenance: EventProvenance?,
        receipt: Receipt,
        reasons: List<Reason> = emptyList(),
        observedInputs: ContextSnapshot? = null,
    ) = ReceiptRecord(
        key = key,
        sessionId = session.id,
        routineId = routine.id,
        // The session's pinned version, not the routine's current one: an
        // edit made while this session ran must not be credited with it.
        routineVersion = session.routineVersion,
        atMillis = atMillis,
        kind = kind,
        provenance = provenance,
        reasons = reasons,
        actions = session.actions,
        obligations = session.obligations,
        sessionState = session.state,
        endReason = session.endReason,
        observedInputs = observedInputs,
        headline = receipt.headline,
        lines = receipt.lines,
    )
}
