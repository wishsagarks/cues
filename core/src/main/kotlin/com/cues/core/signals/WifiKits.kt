package com.cues.core.signals

import com.cues.core.compile.Finding
import com.cues.core.compile.Severity
import com.cues.core.eval.FreshnessPolicy
import com.cues.core.eval.Reason
import com.cues.core.eval.ReasonCode
import com.cues.core.eval.Truth
import com.cues.core.eval.describe
import com.cues.core.eval.freshened
import com.cues.core.model.Capability
import com.cues.core.model.Condition
import com.cues.core.model.ContextSnapshot
import com.cues.core.model.ContextValue
import com.cues.core.model.DeviceTransition
import com.cues.core.model.EventKind
import com.cues.core.model.Trigger
import com.cues.core.model.TriggerEvent
import com.cues.core.model.WifiNetwork
import kotlin.reflect.KClass

object WifiConnectionKit : TriggerKit<Trigger.WifiConnection> {
    override val type: KClass<Trigger.WifiConnection> = Trigger.WifiConnection::class
    override val adapterKey = "wifi"
    override val eventKinds = setOf(EventKind.WIFI_CONNECTED, EventKind.WIFI_DISCONNECTED)

    override fun match(trigger: Trigger.WifiConnection, event: TriggerEvent): Reason {
        val wanted = if (trigger.transition == DeviceTransition.CONNECTED) {
            EventKind.WIFI_CONNECTED
        } else EventKind.WIFI_DISCONNECTED
        if (event.kind != wanted) {
            return Reason(
                ReasonCode.WIFI_TRIGGER_KIND_MISMATCH, Truth.NO_MATCH,
                "Event was ${event.kind}, this cue listens for ${wanted}.",
            )
        }
        return when (val network = trigger.network) {
            WifiNetwork.Any -> Reason(
                ReasonCode.WIFI_TRIGGER_MATCHED, Truth.MATCH,
                "Wi-Fi ${trigger.transition.name.lowercase()}.",
            )
            is WifiNetwork.Named -> if (event.networkLabel == network.label) {
                Reason(ReasonCode.WIFI_TRIGGER_MATCHED, Truth.MATCH, "Wi-Fi ${network.label} ${trigger.transition.name.lowercase()}.")
            } else {
                Reason(
                    ReasonCode.WIFI_TRIGGER_NETWORK_MISMATCH, Truth.NO_MATCH,
                    "This was a different Wi-Fi network, not ${network.label}.",
                )
            }
        }
    }

    override fun reverses(trigger: Trigger.WifiConnection, event: TriggerEvent) =
        trigger.transition == DeviceTransition.CONNECTED && event.kind == EventKind.WIFI_DISCONNECTED

    override fun semanticForm(trigger: Trigger.WifiConnection) =
        "wifi:${trigger.transition.name}:${trigger.network.semanticForm()}"

    override fun validate(trigger: Trigger.WifiConnection) = when (val network = trigger.network) {
        WifiNetwork.Any -> emptyList()
        is WifiNetwork.Named -> listOf(
            Finding(
                Severity.ERROR,
                "trigger.network",
                "Named Wi-Fi needs location access on this build; use any Wi-Fi until the device spike is decided.",
            ),
        )
    }

    override fun capabilities(trigger: Trigger.WifiConnection) = buildSet {
        add(Capability.NETWORK_STATE)
        if (trigger.network is WifiNetwork.Named) add(Capability.LOCATION_FOR_WIFI_NAME)
    }

    override fun describe(trigger: Trigger.WifiConnection) =
        "${trigger.network.describe()} Wi-Fi ${trigger.transition.name.lowercase()}"
    override fun noun(trigger: Trigger.WifiConnection) = "Wi-Fi"

    override fun rehearsalEvents(trigger: Trigger.WifiConnection, atMillis: Long) = listOf(
        TriggerEvent(
            kind = if (trigger.transition == DeviceTransition.CONNECTED) EventKind.WIFI_CONNECTED else EventKind.WIFI_DISCONNECTED,
            atMillis = atMillis,
            networkLabel = (trigger.network as? WifiNetwork.Named)?.label,
            connectionSessionId = "rehearsal-wifi",
            provenance = com.cues.core.model.EventProvenance.REHEARSAL,
        ),
    )

    override fun reversalEvent(trigger: Trigger.WifiConnection, atMillis: Long) = TriggerEvent(
        EventKind.WIFI_DISCONNECTED,
        atMillis,
        networkLabel = (trigger.network as? WifiNetwork.Named)?.label,
        connectionSessionId = "rehearsal-wifi",
        provenance = com.cues.core.model.EventProvenance.REHEARSAL,
    )
}

object WifiConnectedKit : ConditionKit<Condition.WifiConnected> {
    override val type: KClass<Condition.WifiConnected> = Condition.WifiConnected::class

    override fun evaluate(condition: Condition.WifiConnected, snapshot: ContextSnapshot, freshness: FreshnessPolicy): Reason {
        return when (val wifi = snapshot.wifi.freshened(snapshot.nowMillis, freshness.wifiMaxAgeMillis)) {
            is ContextValue.Unknown -> Reason(
                ReasonCode.WIFI_UNKNOWN, Truth.UNKNOWN,
                "Wi-Fi state could not be read (${wifi.reason.describe()}).",
            )
            is ContextValue.Known -> when (val network = condition.network) {
                WifiNetwork.Any -> if (wifi.value.connected) {
                    Reason(ReasonCode.WIFI_CONNECTED, Truth.MATCH, "Wi-Fi is connected.")
                } else {
                    Reason(ReasonCode.WIFI_NOT_CONNECTED, Truth.NO_MATCH, "Wi-Fi is not connected.")
                }
                is WifiNetwork.Named -> when {
                    !wifi.value.connected -> Reason(ReasonCode.WIFI_NOT_CONNECTED, Truth.NO_MATCH, "Wi-Fi is not connected.")
                    wifi.value.networkLabel == null -> Reason(
                        ReasonCode.WIFI_NETWORK_UNKNOWN, Truth.UNKNOWN,
                        "The connected Wi-Fi name was withheld by the system.",
                    )
                    wifi.value.networkLabel == network.label -> Reason(
                        ReasonCode.WIFI_CONNECTED, Truth.MATCH, "Wi-Fi ${network.label} is connected.",
                    )
                    else -> Reason(
                        ReasonCode.WIFI_NOT_CONNECTED, Truth.NO_MATCH,
                        "Wi-Fi ${network.label} is not connected.",
                    )
                }
            }
        }
    }

    override fun semanticForm(condition: Condition.WifiConnected) =
        "wifiConnected:${condition.network.semanticForm()}"

    override fun validate(condition: Condition.WifiConnected) = when (val network = condition.network) {
        WifiNetwork.Any -> emptyList()
        is WifiNetwork.Named -> listOf(
            Finding(
                Severity.ERROR,
                "conditions.network",
                "Named Wi-Fi needs location access on this build; use any Wi-Fi until the device spike is decided.",
            ),
        )
    }

    override fun capabilities(condition: Condition.WifiConnected) = buildSet {
        add(Capability.NETWORK_STATE)
        if (condition.network is WifiNetwork.Named) add(Capability.LOCATION_FOR_WIFI_NAME)
    }

    override fun describe(condition: Condition.WifiConnected) = "${condition.network.describe()} Wi-Fi connected"
}

private fun WifiNetwork.semanticForm(): String = when (this) {
    WifiNetwork.Any -> "any"
    is WifiNetwork.Named -> "named:${label}"
}

private fun WifiNetwork.describe(): String = when (this) {
    WifiNetwork.Any -> "Any"
    is WifiNetwork.Named -> label
}
