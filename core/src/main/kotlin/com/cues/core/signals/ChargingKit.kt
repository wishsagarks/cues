package com.cues.core.signals

import com.cues.core.eval.Reason
import com.cues.core.eval.ReasonCode
import com.cues.core.eval.Truth
import com.cues.core.model.Capability
import com.cues.core.model.EventKind
import com.cues.core.model.PowerTransition
import com.cues.core.model.Trigger
import com.cues.core.model.TriggerEvent
import kotlin.reflect.KClass

object ChargingKit : TriggerKit<Trigger.Charging> {
    override val type: KClass<Trigger.Charging> = Trigger.Charging::class
    override val adapterKey = "power"
    override val eventKinds = setOf(EventKind.POWER_CONNECTED, EventKind.POWER_DISCONNECTED)

    override fun match(trigger: Trigger.Charging, event: TriggerEvent): Reason {
        val wanted = if (trigger.transition == PowerTransition.PLUGGED_IN) {
            EventKind.POWER_CONNECTED
        } else EventKind.POWER_DISCONNECTED
        return if (event.kind == wanted) {
            Reason(ReasonCode.TRIGGER_MATCHED, Truth.MATCH, "Power ${trigger.transition.name.lowercase()}.")
        } else {
            Reason(
                ReasonCode.TRIGGER_KIND_MISMATCH, Truth.NO_MATCH,
                "Event was ${event.kind}, this cue listens for ${wanted}.",
            )
        }
    }

    override fun reverses(trigger: Trigger.Charging, event: TriggerEvent) =
        event.kind == EventKind.POWER_DISCONNECTED

    override fun semanticForm(trigger: Trigger.Charging) = "charging:${trigger.transition.name}"
    override fun validate(trigger: Trigger.Charging) = emptyList<com.cues.core.compile.Finding>()
    override fun capabilities(trigger: Trigger.Charging) = setOf(Capability.BATTERY_STATE)
    override fun describe(trigger: Trigger.Charging) = "the charger ${trigger.transition.name.lowercase()}"
    override fun reviewText(trigger: Trigger.Charging) =
        "the charger ${if (trigger.transition == PowerTransition.PLUGGED_IN) "is plugged in" else "is unplugged"}"
    override fun noun(trigger: Trigger.Charging) = "the charger"

    override fun rehearsalEvents(trigger: Trigger.Charging, atMillis: Long) = listOf(
        TriggerEvent(
            if (trigger.transition == PowerTransition.PLUGGED_IN) EventKind.POWER_CONNECTED else EventKind.POWER_DISCONNECTED,
            atMillis = atMillis,
            connectionSessionId = "rehearsal-power",
            provenance = com.cues.core.model.EventProvenance.REHEARSAL,
        ),
    )

    override fun reversalEvent(trigger: Trigger.Charging, atMillis: Long) = TriggerEvent(
        EventKind.POWER_DISCONNECTED,
        atMillis,
        connectionSessionId = "rehearsal-power",
        provenance = com.cues.core.model.EventProvenance.REHEARSAL,
    )
}
