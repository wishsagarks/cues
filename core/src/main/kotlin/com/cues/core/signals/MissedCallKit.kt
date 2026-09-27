package com.cues.core.signals

import com.cues.core.eval.Reason
import com.cues.core.eval.ReasonCode
import com.cues.core.eval.Truth
import com.cues.core.model.Capability
import com.cues.core.model.EventKind
import com.cues.core.model.EventProvenance
import com.cues.core.model.Trigger
import com.cues.core.model.TriggerEvent
import kotlin.reflect.KClass

/**
 * A missed call was reported.
 *
 * [adapterKey] is deliberately null: there is no [com.cues.core.ports.SignalAdapter]
 * for this trigger. Real detection needs `READ_CALL_LOG`/`READ_PHONE_STATE`,
 * which this app does not request — on-device this fires only from an
 * explicit "Simulate missed call" developer control, the same direct-dispatch
 * shape [ManualKit] already uses. [capabilities] is empty on purpose: nothing
 * about this trigger should ever cause the app to ask for a telephony
 * permission it cannot back up with a real listener.
 */
object MissedCallKit : TriggerKit<Trigger.MissedCall> {
    override val type: KClass<Trigger.MissedCall> = Trigger.MissedCall::class
    override val adapterKey: String? = null
    override val eventKinds = setOf(EventKind.MISSED_CALL)

    // Per-routine scoping (so one "Simulate missed call" tap does not start
    // every armed missed-call cue at once) is enforced in
    // CueService.couldStart via TriggerEvent.routineId, the same place
    // Trigger.Manual's scoping already lives — this kit only reports whether
    // the event kind matches, exactly like ManualKit.
    override fun match(trigger: Trigger.MissedCall, event: TriggerEvent): Reason =
        if (event.kind == EventKind.MISSED_CALL) {
            Reason(ReasonCode.MISSED_CALL_MATCHED, Truth.MATCH, "A missed call was reported.")
        } else {
            Reason(
                ReasonCode.MISSED_CALL_KIND_MISMATCH, Truth.NO_MATCH,
                "Event was ${event.kind}, this cue only runs on a reported missed call.",
            )
        }

    override fun reverses(trigger: Trigger.MissedCall, event: TriggerEvent) = false
    override fun semanticForm(trigger: Trigger.MissedCall) = "missedCall:${trigger.callerHint.orEmpty()}"
    override fun validate(trigger: Trigger.MissedCall) = emptyList<com.cues.core.compile.Finding>()
    override fun capabilities(trigger: Trigger.MissedCall) = emptySet<Capability>()
    override fun describe(trigger: Trigger.MissedCall) =
        "a missed call" + (trigger.callerHint?.let { " from $it" } ?: "")
    override fun reviewText(trigger: Trigger.MissedCall) = describe(trigger) + " (simulated on this build)"
    override fun noun(trigger: Trigger.MissedCall) = "the missed call"
    override fun rehearsalEvents(trigger: Trigger.MissedCall, atMillis: Long) =
        listOf(TriggerEvent(EventKind.MISSED_CALL, atMillis, provenance = EventProvenance.REHEARSAL))
}
