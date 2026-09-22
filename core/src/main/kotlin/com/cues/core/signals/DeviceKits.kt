package com.cues.core.signals

import com.cues.core.compile.Finding
import com.cues.core.compile.Severity
import com.cues.core.eval.FreshnessPolicy
import com.cues.core.eval.Reason
import com.cues.core.eval.ReasonCode
import com.cues.core.eval.Truth
import com.cues.core.eval.chargingWord
import com.cues.core.eval.contains
import com.cues.core.eval.describe
import com.cues.core.eval.full
import com.cues.core.eval.freshened
import com.cues.core.model.Capability
import com.cues.core.model.Condition
import com.cues.core.model.ContextSnapshot
import com.cues.core.model.ContextValue
import com.cues.core.model.Day
import kotlin.reflect.KClass

object DaysOfWeekKit : ConditionKit<Condition.DaysOfWeek> {
    override val type: KClass<Condition.DaysOfWeek> = Condition.DaysOfWeek::class

    override fun evaluate(condition: Condition.DaysOfWeek, snapshot: ContextSnapshot, freshness: FreshnessPolicy) =
        when (val day = snapshot.localDay) {
            is ContextValue.Unknown -> Reason(
                ReasonCode.DAY_UNKNOWN, Truth.UNKNOWN,
                "The day could not be read (${day.reason.describe()}).",
            )
            is ContextValue.Known -> if (day.value in condition.days) {
                Reason(ReasonCode.DAY_IN_SET, Truth.MATCH, "${day.value.full()} is included.")
            } else {
                Reason(
                    ReasonCode.DAY_NOT_IN_SET, Truth.NO_MATCH,
                    "${day.value.full()} is outside ${condition.days.describe()}.",
                )
            }
        }

    override fun semanticForm(condition: Condition.DaysOfWeek) =
        "days:" + Day.entries.filter { it in condition.days }.joinToString(",") { it.name }

    override fun validate(condition: Condition.DaysOfWeek) = if (condition.days.isEmpty()) {
        listOf(Finding(Severity.ERROR, "conditions.days", "No days are selected, so this cue could never run."))
    } else emptyList()

    override fun validateSet(conditions: List<Condition.DaysOfWeek>): List<Finding> {
        if (conditions.size <= 1) return emptyList()
        val intersection = conditions.map { it.days }.reduce { a, b -> a intersect b }
        return if (intersection.isEmpty()) {
            listOf(
                Finding(
                    Severity.ERROR,
                    "conditions.days",
                    "The day conditions contradict each other, so no day can satisfy them both.",
                ),
            )
        } else emptyList()
    }

    override fun capabilities(condition: Condition.DaysOfWeek) = emptySet<Capability>()
    override fun describe(condition: Condition.DaysOfWeek) = condition.days.describe()
    override fun reviewText(condition: Condition.DaysOfWeek) = when (condition.days) {
        com.cues.core.model.WEEKDAYS -> "Monday to Friday"
        com.cues.core.model.WEEKEND -> "Saturday and Sunday"
        else -> condition.days.sortedBy { it.ordinal }
            .joinToString(", ") { it.name.lowercase().replaceFirstChar(Char::uppercase) }
    }
}

object TimeWindowKit : ConditionKit<Condition.TimeWindow> {
    override val type: KClass<Condition.TimeWindow> = Condition.TimeWindow::class
    override fun evaluate(condition: Condition.TimeWindow, snapshot: ContextSnapshot, freshness: FreshnessPolicy) =
        when (val time = snapshot.localTime) {
            is ContextValue.Unknown -> Reason(
                ReasonCode.TIME_UNKNOWN, Truth.UNKNOWN,
                "The local time could not be read (${time.reason.describe()}).",
            )
            is ContextValue.Known -> if (condition.contains(time.value)) {
                Reason(ReasonCode.TIME_IN_WINDOW, Truth.MATCH, "${time.value} is within ${condition.describe()}.")
            } else {
                Reason(ReasonCode.TIME_OUTSIDE_WINDOW, Truth.NO_MATCH, "${time.value} is outside ${condition.describe()}.")
            }
        }

    override fun semanticForm(condition: Condition.TimeWindow) = "time:${condition.startInclusive}-${condition.endExclusive}"
    override fun validate(condition: Condition.TimeWindow) = emptyList<Finding>()
    override fun capabilities(condition: Condition.TimeWindow) = emptySet<Capability>()
    override fun describe(condition: Condition.TimeWindow) = condition.describe()
    override fun reviewText(condition: Condition.TimeWindow) = if (condition.endExclusive.minutesOfDay == 0) {
        "at or after ${condition.startInclusive} local time"
    } else "${condition.startInclusive} to ${condition.endExclusive} local time"
}

object ChargingStateKit : ConditionKit<Condition.ChargingState> {
    override val type: KClass<Condition.ChargingState> = Condition.ChargingState::class
    override fun evaluate(condition: Condition.ChargingState, snapshot: ContextSnapshot, freshness: FreshnessPolicy): Reason {
        val wanted = condition.charging
        return when (val charging = snapshot.charging.freshened(snapshot.nowMillis, freshness.chargingMaxAgeMillis)) {
            is ContextValue.Unknown -> Reason(
                ReasonCode.CHARGING_UNKNOWN, Truth.UNKNOWN,
                "Charging state could not be read (${charging.reason.describe()}).",
            )
            is ContextValue.Known -> if (charging.value == wanted) {
                Reason(ReasonCode.CHARGING_AS_REQUIRED, Truth.MATCH, "The phone is ${charging.value.chargingWord()}, as required.")
            } else {
                Reason(
                    ReasonCode.CHARGING_NOT_AS_REQUIRED, Truth.NO_MATCH,
                    "The phone is ${charging.value.chargingWord()}, but this cue needs it ${wanted.chargingWord()}.",
                )
            }
        }
    }

    override fun semanticForm(condition: Condition.ChargingState) = "charging:${condition.charging}"
    override fun validate(condition: Condition.ChargingState) = emptyList<Finding>()
    override fun validateSet(conditions: List<Condition.ChargingState>) =
        if (conditions.map { it.charging }.toSet().size > 1) {
            listOf(
                Finding(
                    Severity.ERROR,
                    "conditions.charging",
                    "This cue requires the phone to be both charging and not charging.",
                ),
            )
        } else emptyList()

    override fun capabilities(condition: Condition.ChargingState) = setOf(Capability.BATTERY_STATE)
    override fun describe(condition: Condition.ChargingState) = if (condition.charging) "charging" else "not charging"
    override fun reviewText(condition: Condition.ChargingState) =
        if (condition.charging) "the phone is charging" else "the phone is not charging"
}

object DeviceConnectedKit : ConditionKit<Condition.DeviceConnected> {
    override val type: KClass<Condition.DeviceConnected> = Condition.DeviceConnected::class
    override fun evaluate(condition: Condition.DeviceConnected, snapshot: ContextSnapshot, freshness: FreshnessPolicy) =
        when (val connected = snapshot.connectedDeviceIds.freshened(snapshot.nowMillis, freshness.connectedDevicesMaxAgeMillis)) {
            is ContextValue.Unknown -> Reason(
                ReasonCode.DEVICE_CONNECTED_UNKNOWN, Truth.UNKNOWN,
                "Connected devices could not be read (${connected.reason.describe()}).",
            )
            is ContextValue.Known -> if (condition.deviceId in connected.value) {
                Reason(ReasonCode.DEVICE_CONNECTED, Truth.MATCH, "${condition.deviceLabel} is connected.")
            } else {
                Reason(ReasonCode.DEVICE_NOT_CONNECTED, Truth.NO_MATCH, "${condition.deviceLabel} is not connected.")
            }
        }

    override fun semanticForm(condition: Condition.DeviceConnected) = "deviceConnected:${condition.deviceId}"

    override fun validate(condition: Condition.DeviceConnected) = buildList {
        if (condition.deviceId.isBlank()) {
            add(Finding(Severity.ERROR, "conditions.deviceId", "Which device should be connected?"))
        }
    }

    override fun capabilities(condition: Condition.DeviceConnected) = setOf(Capability.BLUETOOTH_CONNECT)
    override fun describe(condition: Condition.DeviceConnected) = "${condition.deviceLabel} connected"
}
