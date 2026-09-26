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
            addAll(actionLines(session))
            session.deadlineMillis?.let { add("Ends at the ${routine.timerMinutes()}-minute mark unless stopped sooner.") }
            if (EndCondition.TriggerReversed in routine.endConditions) {
                add("Also ends if ${routine.triggerNoun()} goes away for more than ${routine.rearmPolicy.reconnectGraceSeconds} seconds.")
            }
        }

        val headline = when {
            session.state != SessionState.PARTIAL -> "Started"
            session.actions.any { it.state == ActionState.BLOCKED || it.state == ActionState.FAILED } ->
                "Started, with something blocked"
            session.actions.any { it.state == ActionState.PENDING } -> "Started, waiting on you for one step"
            else -> "Started, with something blocked"
        }
        return Receipt(headline, lines)
    }

    /**
     * A step that was [ActionState.PENDING] because [Presence.NEEDS_USER]
     * found nobody at the phone, now attempted because someone is. Not part
     * of [EngineResult] — nothing decided whether to admit a session here,
     * only whether to retry what was already waiting — so this is called
     * straight from [com.cues.core.CueService.retryPendingActions], not
     * through [forResult].
     */
    fun resumed(session: Session): Receipt {
        val headline = when {
            session.actions.any { it.state == ActionState.BLOCKED || it.state == ActionState.FAILED } ->
                "Resumed, with something blocked"
            else -> "Resumed"
        }
        return Receipt(headline, actionLines(session))
    }

    private fun actionLines(session: Session): List<String> = session.actions.map { record ->
        when (record.state) {
            // A macro whose on-screen steps landed is an assumption about a
            // setting Cues never read, and the receipt says so every time.
            ActionState.SUCCEEDED -> if (record.verification == Verification.STEPS_CONFIRMED) {
                "${record.actionId.friendly()}: done, assumed. $STEPS_ONLY_CAVEAT"
            } else {
                "${record.actionId.friendly()}: done.${record.actionId.unownedCaveat()}"
            }
            // Named as refused, not folded into a general success.
            ActionState.BLOCKED -> "${record.actionId.friendly()}: blocked. ${record.detail.orEmpty()}".trim()
            ActionState.FAILED -> "${record.actionId.friendly()}: failed. ${record.detail.orEmpty()}".trim()
            ActionState.PENDING ->
                "${record.actionId.friendly()}: waiting for you. ${record.detail.orEmpty()}".trim()
            else -> "${record.actionId.friendly()}: ${record.state.name.lowercase()}."
        }
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
        if (deciding == null) return Receipt(headline, emptyList())
        val otherFailures = reasons.filter { it.truth != Truth.MATCH && it !== deciding }
        val disclosure = if (otherFailures.isEmpty()) {
            "Everything else matched."
        } else {
            "${otherFailures.size} other condition${if (otherFailures.size == 1) "" else "s"} also did not match."
        }
        return Receipt(headline, listOf(deciding.detail, disclosure))
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
                    EndReason.COVERAGE_GAP ->
                        "A coverage gap: the device was no longer connected when Cues checked, " +
                            "but no disconnect was ever observed — most likely while the app was not running."
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
                            obligation.releaseVerification == Verification.STEPS_CONFIRMED ->
                                "Released our ${obligation.resource.friendly()}, assumed. $STEPS_ONLY_CAVEAT"
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

            // New information relative to the Started receipt: a step that was
            // still waiting on you when the session ended is expired, not
            // dropped silently — see SessionEngine's PENDING -> BLOCKED
            // conversion at exit, tagged with this exact detail text.
            session.actions.filter { it.detail == EXPIRED_WHILE_PENDING_DETAIL }.forEach { record ->
                add("${record.actionId.friendly()}: never happened. ${record.detail}")
            }
        }

        val headline = if (session.state == SessionState.CLEANUP_PENDING) "Ended, with cleanup outstanding" else "Ended"
        return Receipt(headline, lines)
    }
}

/** The one sentence every [Verification.STEPS_CONFIRMED] result carries, so it can never read as a checked fact. */
internal const val STEPS_ONLY_CAVEAT = "Its on-screen steps were confirmed; Cues can't read the setting itself."

internal fun ActionId.friendly(): String = when (this) {
    ActionId.START_FOCUS_TIMER -> "Focus timer"
    ActionId.REQUEST_DND -> "Quiet notifications"
    ActionId.NOTIFY_RESULT -> "Result note"
    ActionId.PINNED_NOTE -> "Pinned note"
    ActionId.OPEN_APP -> "Open app"
    ActionId.COMPOSE_MESSAGE -> "Pre-filled message"
    ActionId.COMPOSE_WHATSAPP -> "WhatsApp draft"
    ActionId.ADD_CALENDAR_EVENT -> "Calendar event"
    ActionId.SET_ALARM -> "Alarm"
    ActionId.MEDIA_CONTROL -> "Media control"
    ActionId.RINGER_MODE -> "Ringer"
    ActionId.OPEN_LINK -> "Link"
    ActionId.USE_UTILITY -> "Utility toggle"
}

/** " You finish this." or " Cues cannot undo this." — only for the two risk classes that need the caveat. */
internal fun ActionId.unownedCaveat(): String = when (com.cues.core.registry.ActionRegistry.definition(this)?.risk) {
    com.cues.core.registry.ActionRisk.HANDOFF -> " You finish this."
    com.cues.core.registry.ActionRisk.EXTERNAL_UNOWNED -> " Cues cannot undo this."
    else -> ""
}

internal fun OwnedResource.friendly(): String = when (this) {
    OwnedResource.FOCUS_TIMER -> "focus timer"
    OwnedResource.DND_CONTRIBUTION -> "quiet rule"
    OwnedResource.PINNED_NOTE -> "pinned note"
    OwnedResource.RINGER_MODE -> "ringer mode"
    OwnedResource.UTILITY_CONTRIBUTION -> "utility toggle"
}

internal fun Routine.timerMinutes(): Int? = actions
    .firstOrNull { it.actionId == ActionId.START_FOCUS_TIMER }
    ?.let { (it.args as? ActionArgs.FocusTimer)?.durationMinutes }

internal fun Routine.triggerNoun(): String = SignalRegistry.noun(trigger)
