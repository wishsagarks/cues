package com.cues.core.signals

import com.cues.core.eval.Reason
import com.cues.core.eval.ReasonCode
import com.cues.core.eval.Truth
import com.cues.core.model.EventKind
import com.cues.core.model.Trigger
import com.cues.core.model.TriggerEvent
import kotlin.reflect.KClass

object ManualKit : TriggerKit<Trigger.Manual> {
    override val type: KClass<Trigger.Manual> = Trigger.Manual::class
    override val adapterKey: String? = null
    override val eventKinds = setOf(EventKind.MANUAL_RUN)

    override fun match(trigger: Trigger.Manual, event: TriggerEvent) =
        if (event.kind == EventKind.MANUAL_RUN) {
            Reason(ReasonCode.TRIGGER_MATCHED, Truth.MATCH, "Run by hand.")
        } else {
            Reason(
                ReasonCode.TRIGGER_KIND_MISMATCH, Truth.NO_MATCH,
                "Event was ${event.kind}, this cue only runs by hand.",
            )
        }

    override fun reverses(trigger: Trigger.Manual, event: TriggerEvent) = false
    override fun semanticForm(trigger: Trigger.Manual) = "manual"
    override fun validate(trigger: Trigger.Manual) = emptyList<com.cues.core.compile.Finding>()
    override fun capabilities(trigger: Trigger.Manual) = emptySet<com.cues.core.model.Capability>()
    override fun describe(trigger: Trigger.Manual) = "manual run"
    override fun reviewText(trigger: Trigger.Manual) = "you run it by hand"
    override fun noun(trigger: Trigger.Manual) = "the run"
    override fun rehearsalEvents(trigger: Trigger.Manual, atMillis: Long) =
        listOf(TriggerEvent(EventKind.MANUAL_RUN, atMillis, provenance = com.cues.core.model.EventProvenance.REHEARSAL))
}
