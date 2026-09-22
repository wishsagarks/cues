package com.cues.core.signals

import com.cues.core.compile.Finding
import com.cues.core.compile.Severity
import com.cues.core.eval.Reason
import com.cues.core.eval.ReasonCode
import com.cues.core.eval.Truth
import com.cues.core.model.Capability
import com.cues.core.model.DeviceTransition
import com.cues.core.model.EventKind
import com.cues.core.model.Trigger
import com.cues.core.model.TriggerEvent
import kotlin.reflect.KClass

object BluetoothKit : TriggerKit<Trigger.BluetoothConnection> {
    override val type: KClass<Trigger.BluetoothConnection> = Trigger.BluetoothConnection::class
    override val adapterKey = "bluetooth"
    override val eventKinds = setOf(EventKind.BLUETOOTH_CONNECTED, EventKind.BLUETOOTH_DISCONNECTED)

    override fun match(trigger: Trigger.BluetoothConnection, event: TriggerEvent): Reason {
        val wanted = if (trigger.transition == DeviceTransition.CONNECTED) {
            EventKind.BLUETOOTH_CONNECTED
        } else EventKind.BLUETOOTH_DISCONNECTED
        return when {
            event.kind != wanted -> Reason(
                ReasonCode.TRIGGER_KIND_MISMATCH, Truth.NO_MATCH,
                "Event was ${event.kind}, this cue listens for ${wanted}.",
            )
            event.deviceId != trigger.deviceId -> Reason(
                ReasonCode.TRIGGER_DEVICE_MISMATCH, Truth.NO_MATCH,
                "A different device triggered this, not ${trigger.deviceLabel}.",
            )
            else -> Reason(
                ReasonCode.TRIGGER_MATCHED, Truth.MATCH,
                "${trigger.deviceLabel} ${trigger.transition.name.lowercase()}.",
            )
        }
    }

    override fun reverses(trigger: Trigger.BluetoothConnection, event: TriggerEvent) =
        event.kind == EventKind.BLUETOOTH_DISCONNECTED &&
            event.deviceId == trigger.deviceId

    override fun semanticForm(trigger: Trigger.BluetoothConnection) =
        "bluetooth:${trigger.deviceId}:${trigger.transition.name}"

    override fun validate(trigger: Trigger.BluetoothConnection): List<Finding> = buildList {
        if (trigger.deviceId.isBlank()) {
            add(Finding(Severity.ERROR, "trigger.deviceId", "Which device? Pick one from your paired devices."))
        }
        if (trigger.deviceLabel.isBlank()) {
            add(Finding(Severity.WARNING, "trigger.deviceLabel", "This device has no name to show in the review."))
        }
    }

    override fun capabilities(trigger: Trigger.BluetoothConnection) = setOf(Capability.BLUETOOTH_CONNECT)
    override fun describe(trigger: Trigger.BluetoothConnection) = "${trigger.deviceLabel} ${trigger.transition.name.lowercase()}"
    override fun reviewText(trigger: Trigger.BluetoothConnection) =
        "${trigger.deviceLabel} ${if (trigger.transition == DeviceTransition.CONNECTED) "connects" else "disconnects"}"
    override fun noun(trigger: Trigger.BluetoothConnection) = trigger.deviceLabel

    override fun rehearsalEvents(trigger: Trigger.BluetoothConnection, atMillis: Long) = listOf(
        TriggerEvent(
            kind = if (trigger.transition == DeviceTransition.CONNECTED) {
                EventKind.BLUETOOTH_CONNECTED
            } else EventKind.BLUETOOTH_DISCONNECTED,
            atMillis = atMillis,
            deviceId = trigger.deviceId,
            connectionSessionId = "rehearsal-connection",
            provenance = com.cues.core.model.EventProvenance.REHEARSAL,
        ),
    )

    override fun reversalEvent(trigger: Trigger.BluetoothConnection, atMillis: Long) = TriggerEvent(
        EventKind.BLUETOOTH_DISCONNECTED,
        atMillis,
        deviceId = trigger.deviceId,
        connectionSessionId = "rehearsal-connection",
        provenance = com.cues.core.model.EventProvenance.REHEARSAL,
    )
}
