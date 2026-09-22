package com.cues.core

import com.cues.core.model.*

/** Shared fixtures. The hero routine from PRS.md, so tests exercise the demo path. */
object Fixtures {

    const val EARBUDS_ID = "AA:BB:CC:DD:EE:FF"
    const val EARBUDS_LABEL = "TWS Air Pro"

    /** 2026-09-28 is a Monday; these millis are a convenient fixed "now". */
    const val NOW = 1_790_000_000_000L

    fun heroRoutine(
        conditions: List<Condition> = listOf(
            Condition.DaysOfWeek(WEEKDAYS),
            Condition.TimeWindow(LocalTimeOfDay(18, 0), LocalTimeOfDay(0, 0)),
        ),
        status: RoutineStatus = RoutineStatus.ARMED,
        rearmPolicy: RearmPolicy = RearmPolicy(reconnectGraceSeconds = 20),
    ) = Routine(
        id = "routine-1",
        version = 1,
        sourceText = "When my earbuds connect after 6 PM on weekdays, start a 45-minute " +
            "focus timer and quiet notifications. End it if I disconnect.",
        title = "Focus when earbuds connect",
        trigger = Trigger.BluetoothConnection(EARBUDS_ID, EARBUDS_LABEL, DeviceTransition.CONNECTED),
        conditions = conditions,
        actions = listOf(
            ActionSpec(ActionId.START_FOCUS_TIMER, ActionArgs.FocusTimer(45)),
            ActionSpec(ActionId.REQUEST_DND, ActionArgs.Dnd()),
        ),
        endConditions = listOf(
            EndCondition.TriggerReversed,
            EndCondition.Duration(45),
            EndCondition.ManualStop,
        ),
        cleanupPolicy = CleanupPolicy(),
        rearmPolicy = rearmPolicy,
        requiredCapabilities = setOf(
            Capability.BLUETOOTH_CONNECT,
            Capability.NOTIFICATION_POLICY_ACCESS,
            Capability.EXACT_ALARM,
        ),
        approvedDigest = "test-digest",
        status = status,
    )

    fun snapshot(
        day: ContextValue<Day> = known(Day.MON),
        time: ContextValue<LocalTimeOfDay> = known(LocalTimeOfDay(18, 30)),
        charging: ContextValue<Boolean> = known(true),
        nowMillis: Long = NOW,
    ) = ContextSnapshot(
        nowMillis = nowMillis,
        localDay = day,
        localTime = time,
        zoneId = "Asia/Kolkata",
        charging = charging,
        connectedDeviceIds = known(setOf(EARBUDS_ID)),
    )

    fun <T> known(value: T, atMillis: Long = NOW, source: ContextSource = ContextSource.SYSTEM_CLOCK) =
        ContextValue.Known(value, source, atMillis)

    fun unknown(
        reason: UnknownReason = UnknownReason.PERMISSION_DENIED,
        source: ContextSource = ContextSource.BATTERY_MANAGER,
    ) = ContextValue.Unknown(reason, source)

    fun connect(
        atMillis: Long = NOW,
        connectionSessionId: String = "conn-1",
        deviceId: String = EARBUDS_ID,
    ) = TriggerEvent(EventKind.BLUETOOTH_CONNECTED, atMillis, deviceId, connectionSessionId)

    fun disconnect(atMillis: Long = NOW, connectionSessionId: String = "conn-1") =
        TriggerEvent(EventKind.BLUETOOTH_DISCONNECTED, atMillis, EARBUDS_ID, connectionSessionId)
}
