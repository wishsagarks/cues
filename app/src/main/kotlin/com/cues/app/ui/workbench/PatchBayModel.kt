package com.cues.app.ui.workbench

import com.cues.core.drafting.PairedDevice
import com.cues.core.model.*

/**
 * The Workbench patch bay's own UI state, kept separate from `:core`'s
 * closed types so a half-built selection (no device chosen yet, a blank
 * text field) doesn't have to be a valid [Trigger]/[Condition]/[ActionSpec]
 * at every keystroke. [toSelection] converts once the user has enough
 * filled in, and [com.cues.core.workbench.PatchSentence] — never this file —
 * is what actually decides whether the result compiles (redesign plan §3.7).
 */

enum class WhenKind(val label: String) {
    BLUETOOTH_CONNECT("Device connects"),
    BLUETOOTH_DISCONNECT("Device disconnects"),
    CHARGING_PLUGGED("Charger plugged in"),
    WIFI_CONNECT("Wi-Fi connects"),
    WIFI_DISCONNECT("Wi-Fi disconnects"),
    AUDIO_ADDED("Audio output connects"),
    AUDIO_REMOVED("Audio output disconnects"),
    AT_TIME("At a time"),
    MANUAL("Run by hand"),
    PLACE_ENTER("Enter a place"),
    PLACE_EXIT("Exit a place"),
}

data class WhenSelection(
    val kind: WhenKind,
    val device: PairedDevice? = null,
    val hour: Int = 7,
    val minute: Int = 30,
    val place: Place? = null,
) {
    /** Whether every field this kind needs is actually filled in. */
    val isComplete: Boolean
        get() = when (kind) {
            WhenKind.BLUETOOTH_CONNECT, WhenKind.BLUETOOTH_DISCONNECT -> device != null
            WhenKind.PLACE_ENTER, WhenKind.PLACE_EXIT -> place != null
            else -> true
        }

    fun toTrigger(): Trigger? = when (kind) {
        WhenKind.BLUETOOTH_CONNECT -> device?.let { Trigger.BluetoothConnection(it.id, it.label, DeviceTransition.CONNECTED) }
        WhenKind.BLUETOOTH_DISCONNECT -> device?.let { Trigger.BluetoothConnection(it.id, it.label, DeviceTransition.DISCONNECTED) }
        WhenKind.CHARGING_PLUGGED -> Trigger.Charging(PowerTransition.PLUGGED_IN)
        WhenKind.WIFI_CONNECT -> Trigger.WifiConnection(DeviceTransition.CONNECTED)
        WhenKind.WIFI_DISCONNECT -> Trigger.WifiConnection(DeviceTransition.DISCONNECTED)
        WhenKind.AUDIO_ADDED -> Trigger.AudioOutput(AudioTransition.ADDED)
        WhenKind.AUDIO_REMOVED -> Trigger.AudioOutput(AudioTransition.REMOVED)
        WhenKind.AT_TIME -> Trigger.AtTime(LocalTimeOfDay(hour, minute))
        WhenKind.MANUAL -> Trigger.Manual
        WhenKind.PLACE_ENTER -> place?.let { Trigger.PlaceTransition(it.id, it.version, it.label, PlaceTransitionKind.ENTER) }
        WhenKind.PLACE_EXIT -> place?.let { Trigger.PlaceTransition(it.id, it.version, it.label, PlaceTransitionKind.EXIT) }
    }

    /** Ends the grammar always attaches to this trigger — shown as a locked chip, never offered as optional. */
    val impliedEnd: EndCondition?
        get() = when (kind) {
            WhenKind.BLUETOOTH_CONNECT, WhenKind.WIFI_CONNECT -> EndCondition.TriggerReversed
            else -> null
        }
}

enum class IfKind(val label: String) {
    DAYS("Days of week"),
    TIME_WINDOW("Time window"),
    CHARGING("Charging state"),
    DEVICE_CONNECTED("Another device connected"),
    BATTERY_BELOW("Battery below"),
    BATTERY_AT_LEAST("Battery at least"),
    AUDIO_ACTIVE("Audio output active"),
    CALENDAR_BUSY("Calendar busy"),
    CALENDAR_FREE("Calendar free"),
    IN_CONTEXT("In a named context"),
    AT_PLACE("At a place"),
}

data class IfSelection(
    val kind: IfKind,
    val days: Set<Day> = setOf(Day.MON, Day.TUE, Day.WED, Day.THU, Day.FRI),
    val startHour: Int = 18, val startMinute: Int = 0,
    val endHour: Int = 0, val endMinute: Int = 0,
    val charging: Boolean = true,
    val device: PairedDevice? = null,
    val percent: Int = 50,
    val audioKind: AudioKind = AudioKind.ANY,
    val context: NamedContext? = null,
    val place: Place? = null,
) {
    val isComplete: Boolean
        get() = when (kind) {
            IfKind.DAYS -> days.isNotEmpty()
            IfKind.DEVICE_CONNECTED -> device != null
            IfKind.IN_CONTEXT -> context != null
            IfKind.AT_PLACE -> place != null
            else -> true
        }

    fun toCondition(): Condition? = when (kind) {
        IfKind.DAYS -> Condition.DaysOfWeek(days)
        IfKind.TIME_WINDOW -> Condition.TimeWindow(LocalTimeOfDay(startHour, startMinute), LocalTimeOfDay(endHour, endMinute))
        IfKind.CHARGING -> Condition.ChargingState(charging)
        IfKind.DEVICE_CONNECTED -> device?.let { Condition.DeviceConnected(it.id, it.label) }
        IfKind.BATTERY_BELOW -> Condition.BatteryBelow(percent)
        IfKind.BATTERY_AT_LEAST -> Condition.BatteryAtLeast(percent)
        IfKind.AUDIO_ACTIVE -> Condition.AudioOutputActive(audioKind)
        IfKind.CALENDAR_BUSY -> Condition.CalendarBusy
        IfKind.CALENDAR_FREE -> Condition.CalendarNotBusy
        IfKind.IN_CONTEXT -> context?.let { Condition.InContext(it.id, it.version, it.label) }
        IfKind.AT_PLACE -> place?.let { Condition.AtPlace(it.id, it.version, it.label) }
    }
}

enum class DoKind(val label: String, val risk: com.cues.core.registry.ActionRisk) {
    FOCUS_TIMER("Start focus timer", com.cues.core.registry.ActionRisk.OWNED_AND_REVERSIBLE),
    QUIET("Request quiet (DND)", com.cues.core.registry.ActionRisk.OWNED_AND_REVERSIBLE),
    PINNED_NOTE("Pinned note", com.cues.core.registry.ActionRisk.OWNED_AND_REVERSIBLE),
    RINGER("Set ringer", com.cues.core.registry.ActionRisk.OWNED_AND_REVERSIBLE),
    NOTIFY("Show a result", com.cues.core.registry.ActionRisk.LOCAL_NOTICE),
    OPEN_APP("Open an app", com.cues.core.registry.ActionRisk.HANDOFF),
    COMPOSE_MESSAGE("Pre-fill a message", com.cues.core.registry.ActionRisk.HANDOFF),
    COMPOSE_WHATSAPP("Draft a WhatsApp message", com.cues.core.registry.ActionRisk.HANDOFF),
    CALENDAR_EVENT("Add calendar event", com.cues.core.registry.ActionRisk.HANDOFF),
    ALARM("Set an alarm", com.cues.core.registry.ActionRisk.EXTERNAL_UNOWNED),
    MEDIA("Media control", com.cues.core.registry.ActionRisk.EXTERNAL_UNOWNED),
    UTILITY("Use a taught utility", com.cues.core.registry.ActionRisk.UI_AUTOMATION),
}

data class DoSelection(
    val kind: DoKind,
    val minutes: Int = 45,
    val message: String = "",
    val ringer: RingerModeKind = RingerModeKind.SILENT,
    val app: com.cues.app.runtime.InstalledApp? = null,
    val contactHint: String = "",
    val calendarTitle: String = "",
    val calendarMinutes: Int = 30,
    val alarmHour: Int = 7,
    val alarmMinute: Int = 0,
    val media: MediaCommand = MediaCommand.PLAY,
    val utility: UtilityId = UtilityId.EYE_PROTECTION,
    val utilityState: UtilityState = UtilityState.ON,
) {
    val isComplete: Boolean
        get() = when (kind) {
            DoKind.PINNED_NOTE, DoKind.NOTIFY -> message.isNotBlank()
            DoKind.OPEN_APP -> app != null
            DoKind.COMPOSE_MESSAGE, DoKind.COMPOSE_WHATSAPP -> message.isNotBlank()
            DoKind.CALENDAR_EVENT -> calendarTitle.isNotBlank()
            else -> true
        }

    fun toActionSpec(): ActionSpec? = when (kind) {
        DoKind.FOCUS_TIMER -> ActionSpec(ActionId.START_FOCUS_TIMER, ActionArgs.FocusTimer(minutes))
        DoKind.QUIET -> ActionSpec(ActionId.REQUEST_DND, ActionArgs.Dnd())
        DoKind.PINNED_NOTE -> message.takeIf { it.isNotBlank() }?.let { ActionSpec(ActionId.PINNED_NOTE, ActionArgs.PinnedNote(it)) }
        DoKind.RINGER -> ActionSpec(ActionId.RINGER_MODE, ActionArgs.RingerMode(ringer))
        DoKind.NOTIFY -> message.takeIf { it.isNotBlank() }?.let { ActionSpec(ActionId.NOTIFY_RESULT, ActionArgs.Notify(it)) }
        DoKind.OPEN_APP -> app?.let { ActionSpec(ActionId.OPEN_APP, ActionArgs.OpenApp(it.packageName, it.label)) }
        DoKind.COMPOSE_MESSAGE -> message.takeIf { it.isNotBlank() }
            ?.let { ActionSpec(ActionId.COMPOSE_MESSAGE, ActionArgs.ComposeMessage(contactHint.takeIf { it.isNotBlank() }, it)) }
        DoKind.COMPOSE_WHATSAPP -> message.takeIf { it.isNotBlank() }
            ?.let { ActionSpec(ActionId.COMPOSE_WHATSAPP, ActionArgs.ComposeWhatsApp(contactHint.takeIf { it.isNotBlank() }, it)) }
        DoKind.CALENDAR_EVENT -> calendarTitle.takeIf { it.isNotBlank() }
            ?.let { ActionSpec(ActionId.ADD_CALENDAR_EVENT, ActionArgs.CalendarEvent(it, calendarMinutes)) }
        DoKind.ALARM -> ActionSpec(ActionId.SET_ALARM, ActionArgs.Alarm(alarmHour, alarmMinute))
        DoKind.MEDIA -> ActionSpec(ActionId.MEDIA_CONTROL, ActionArgs.MediaControl(media))
        DoKind.UTILITY -> ActionSpec(ActionId.USE_UTILITY, ActionArgs.UseUtility(utility, utilityState))
    }
}

/** Extra endings on top of whatever the trigger/actions already imply. Only what the grammar can actually say. */
enum class UntilExtraKind(val label: String) {
    HALF_HOUR("For half an hour"),
    HOUR("For an hour"),
    AT_TIME("At a time"),
}

data class UntilExtra(val kind: UntilExtraKind, val hour: Int = 21, val minute: Int = 0) {
    fun toEndCondition(): EndCondition = when (kind) {
        UntilExtraKind.HALF_HOUR -> EndCondition.Duration(30)
        UntilExtraKind.HOUR -> EndCondition.Duration(60)
        UntilExtraKind.AT_TIME -> EndCondition.AtTime(LocalTimeOfDay(hour, minute))
    }
}
