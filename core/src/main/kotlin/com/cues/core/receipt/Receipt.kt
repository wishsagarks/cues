package com.cues.core.receipt

import com.cues.core.eval.Decision
import com.cues.core.eval.Reason
import com.cues.core.eval.Truth
import com.cues.core.model.*
import com.cues.core.session.EngineResult
import com.cues.core.signals.SignalRegistry

/**
 * A plain account of one run: what was observed, what was decided, what
 * happened and what was released.
 *
 * Every line is assembled from reason codes and observed values. Nothing here
 * is generated text. A model asked to explain a decision after the fact
 * produces fluent prose that may not describe what the code did, and the whole
 * purpose of a receipt is to be the thing you can trust when the app and your
 * memory disagree.
 */
data class Receipt(
    val headline: String,
    val lines: List<String>,
) {
    fun render(): String = (listOf(headline) + lines.map { "  $it" }).joinToString("\n")
}

object Receipts {

    fun forResult(routine: Routine, result: EngineResult): Receipt = when (result) {
        is EngineResult.Started -> started(routine, result.session, result.decision)
        is EngineResult.Skipped -> skipped(result.reasons)
        is EngineResult.Ended -> ended(result.session)
        is EngineResult.ExitScheduled -> exitScheduled(routine, result.session)
        is EngineResult.ExitCancelled -> Receipt(
            "Kept going",
            listOf("The connection came back within the grace period, so the session continued."),
        )

        EngineResult.Ignored -> Receipt("Nothing to do", listOf("This event did not affect any running session."))
    }

    private fun started(routine: Routine, session: Session, decision: Decision): Receipt {
        val lines = buildList {
            decision.reasons.filter { it.truth == Truth.MATCH }.forEach { add(it.detail) }

            session.actions.forEach { record ->
                add(
                    when (record.state) {
                        ActionState.SUCCEEDED -> "${record.actionId.friendly()}: done."
                        // Named as refused, not folded into a general success.
                        ActionState.BLOCKED -> "${record.actionId.friendly()}: blocked. ${record.detail.orEmpty()}".trim()
                        ActionState.FAILED -> "${record.actionId.friendly()}: failed. ${record.detail.orEmpty()}".trim()
                        else -> "${record.actionId.friendly()}: ${record.state.name.lowercase()}."
                    },
                )
            }

            session.deadlineMillis?.let { add("Ends at the ${routine.timerMinutes()}-minute mark unless stopped sooner.") }
            if (EndCondition.TriggerReversed in routine.endConditions) {
                add("Also ends if ${routine.triggerNoun()} goes away for more than ${routine.rearmPolicy.reconnectGraceSeconds} seconds.")
            }
        }

        val headline = if (session.state == SessionState.PARTIAL) "Started, with something blocked" else "Started"
        return Receipt(headline, lines)
    }

    private fun skipped(reasons: List<Reason>): Receipt {
        val deciding = reasons.firstOrNull { it.truth == Truth.NO_MATCH }
            ?: reasons.firstOrNull { it.truth == Truth.UNKNOWN }

        val headline = when (deciding?.truth) {
            // The distinction the user needs: "this did not apply" versus
            // "we could not tell, so we did nothing".
            Truth.UNKNOWN -> "Skipped, because something could not be read"
            else -> "Skipped"
        }
        return Receipt(headline, reasons.filter { it.truth != Truth.MATCH }.map { it.detail })
    }

    private fun exitScheduled(routine: Routine, session: Session): Receipt = Receipt(
        "Ending shortly",
        listOf(
            "${routine.triggerNoun().replaceFirstChar { it.uppercase() }} disconnected.",
            "Waiting ${routine.rearmPolicy.reconnectGraceSeconds} seconds in case it comes back.",
        ),
    )

    private fun ended(session: Session): Receipt {
        val lines = buildList {
            add(
                when (session.endReason) {
                    EndReason.DEADLINE_REACHED -> "The timer finished."
                    EndReason.TRIGGER_REVERSED -> "The trigger went away."
                    EndReason.MANUAL_STOP -> "You stopped it."
                    EndReason.ROUTINE_PAUSED -> "The cue was paused."
                    EndReason.RECONCILED_EXPIRED -> "It had already finished while the app was not running."
                    EndReason.START_FAILED -> "It could not start."
                    null -> "It ended."
                },
            )

            session.obligations.forEach { obligation ->
                add(
                    if (obligation.released) {
                        when {
                            obligation.failureDetail != null ->
                                "${obligation.resource.friendly()}: ${obligation.failureDetail}"
                            // Deliberately "our", and deliberately not a claim
                            // about the phone's overall state. Another mode may
                            // still want quiet, and we cannot see that.
                            else -> "Released our ${obligation.resource.friendly()}."
                        }
                    } else {
                        "Could not release our ${obligation.resource.friendly()}. " +
                            (obligation.failureDetail ?: "This is still outstanding.")
                    },
                )
            }

            if (session.state == SessionState.CLEANUP_PENDING) {
                add("This session is not finished cleaning up. You can retry it from the cue's page.")
            }
        }

        val headline = if (session.state == SessionState.CLEANUP_PENDING) "Ended, with cleanup outstanding" else "Ended"
        return Receipt(headline, lines)
    }
}

internal fun ActionId.friendly(): String = when (this) {
    ActionId.START_FOCUS_TIMER -> "Focus timer"
    ActionId.REQUEST_DND -> "Quiet notifications"
    ActionId.NOTIFY_RESULT -> "Result note"
    ActionId.PINNED_NOTE -> "Pinned note"
}

internal fun OwnedResource.friendly(): String = when (this) {
    OwnedResource.FOCUS_TIMER -> "focus timer"
    OwnedResource.DND_CONTRIBUTION -> "quiet rule"
    OwnedResource.PINNED_NOTE -> "pinned note"
}

internal fun Routine.timerMinutes(): Int? = actions
    .firstOrNull { it.actionId == ActionId.START_FOCUS_TIMER }
    ?.let { (it.args as? ActionArgs.FocusTimer)?.durationMinutes }

internal fun Routine.triggerNoun(): String = SignalRegistry.noun(trigger)
