package com.cues.app.ui.workbench

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cues.app.runtime.InstalledApp
import com.cues.app.ui.theme.CuesType
import com.cues.app.ui.theme.cuesTokens
import com.cues.core.drafting.PairedDevice
import com.cues.core.model.AudioKind
import com.cues.core.model.MediaCommand
import com.cues.core.model.NamedContext
import com.cues.core.model.Place
import com.cues.core.model.RingerModeKind
import com.cues.core.model.UtilityId
import com.cues.core.model.UtilityState

@Composable
private fun MissingNote(text: String) {
    Text(text, style = CuesType.labelSmall, color = cuesTokens.warn, modifier = Modifier.padding(top = 4.dp))
}

/**
 * [EnumChipRow.selected] is non-null, so a not-yet-chosen picker used to pass
 * `selected ?: options.first()` just to satisfy that type — which rendered
 * the first option as highlighted without ever calling [onSelect], so the
 * underlying [WhenSelection]/[IfSelection] field stayed null. That silent
 * mismatch was the Patch Bay "Compile to Review" bug: a place/device/context
 * chip could look chosen while `isComplete` was still false. The
 * [LaunchedEffect] below makes the visual default a real one, firing
 * [onSelect] once the moment nothing is selected yet — after that, `selected`
 * is non-null and the effect does not re-fire.
 */
@Composable
private fun DevicePickerRow(label: String, devices: List<PairedDevice>, selected: PairedDevice?, onSelect: (PairedDevice) -> Unit) {
    Column {
        Text(label, style = CuesType.labelSmall, color = cuesTokens.inkSlate)
        if (devices.isEmpty()) {
            MissingNote("No paired devices found — grant Bluetooth access, or pair one first.")
        } else {
            androidx.compose.runtime.LaunchedEffect(selected, devices) {
                if (selected == null) onSelect(devices.first())
            }
            EnumChipRow(devices, selected ?: devices.first(), { it.label }, onSelect, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun PlacePickerRow(label: String, places: List<Place>, selected: Place?, onSelect: (Place) -> Unit) {
    Column {
        Text(label, style = CuesType.labelSmall, color = cuesTokens.inkSlate)
        if (places.isEmpty()) {
            MissingNote("No saved places yet — add one in Workbench › Contexts & places.")
        } else {
            androidx.compose.runtime.LaunchedEffect(selected, places) {
                if (selected == null) onSelect(places.first())
            }
            EnumChipRow(places, selected ?: places.first(), { it.label }, onSelect, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun ContextPickerRow(contexts: List<NamedContext>, selected: NamedContext?, onSelect: (NamedContext) -> Unit) {
    Column {
        Text("Context", style = CuesType.labelSmall, color = cuesTokens.inkSlate)
        if (contexts.isEmpty()) {
            MissingNote("No named contexts yet — add one in Workbench › Contexts & places.")
        } else {
            androidx.compose.runtime.LaunchedEffect(selected, contexts) {
                if (selected == null) onSelect(contexts.first())
            }
            EnumChipRow(contexts, selected ?: contexts.first(), { it.label }, onSelect, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun WhenEditor(sel: WhenSelection, devices: List<PairedDevice>, places: List<Place>, onChange: (WhenSelection) -> Unit) {
    when (sel.kind) {
        WhenKind.BLUETOOTH_CONNECT, WhenKind.BLUETOOTH_DISCONNECT ->
            DevicePickerRow("Device", devices, sel.device) { onChange(sel.copy(device = it)) }
        WhenKind.AT_TIME ->
            TimeRow("Time", sel.hour, sel.minute, { onChange(sel.copy(hour = it)) }, { onChange(sel.copy(minute = it)) })
        WhenKind.PLACE_ENTER, WhenKind.PLACE_EXIT ->
            PlacePickerRow("Place", places, sel.place) { onChange(sel.copy(place = it)) }
        WhenKind.CHARGING_PLUGGED, WhenKind.WIFI_CONNECT, WhenKind.WIFI_DISCONNECT,
        WhenKind.AUDIO_ADDED, WhenKind.AUDIO_REMOVED, WhenKind.MANUAL,
        -> Text("No extra parameters.", style = CuesType.labelSmall, color = cuesTokens.inkSlate)
    }
}

@Composable
fun IfEditor(sel: IfSelection, devices: List<PairedDevice>, contexts: List<NamedContext>, places: List<Place>, onChange: (IfSelection) -> Unit) {
    when (sel.kind) {
        IfKind.DAYS -> DayToggleRow(
            sel.days,
            { day -> onChange(sel.copy(days = if (day in sel.days) sel.days - day else sel.days + day)) },
        )
        IfKind.TIME_WINDOW -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TimeRow("From", sel.startHour, sel.startMinute, { onChange(sel.copy(startHour = it)) }, { onChange(sel.copy(startMinute = it)) })
            TimeRow("Until", sel.endHour, sel.endMinute, { onChange(sel.copy(endHour = it)) }, { onChange(sel.copy(endMinute = it)) })
        }
        IfKind.CHARGING -> EnumChipRow(listOf(true, false), sel.charging, { if (it) "Charging" else "Not charging" }, { onChange(sel.copy(charging = it)) })
        IfKind.DEVICE_CONNECTED -> DevicePickerRow("Device", devices, sel.device) { onChange(sel.copy(device = it)) }
        IfKind.BATTERY_BELOW, IfKind.BATTERY_AT_LEAST -> NumberStepper("Percent", sel.percent, { onChange(sel.copy(percent = it)) }, 1..100, step = 5)
        IfKind.AUDIO_ACTIVE -> EnumChipRow(AudioKind.entries, sel.audioKind, { it.name.lowercase() }, { onChange(sel.copy(audioKind = it)) })
        IfKind.IN_CONTEXT -> ContextPickerRow(contexts, sel.context) { onChange(sel.copy(context = it)) }
        IfKind.AT_PLACE -> PlacePickerRow("Place", places, sel.place) { onChange(sel.copy(place = it)) }
        IfKind.CALENDAR_BUSY, IfKind.CALENDAR_FREE -> Text("No extra parameters.", style = CuesType.labelSmall, color = cuesTokens.inkSlate)
    }
}

@Composable
fun DoEditor(sel: DoSelection, installedApps: () -> List<InstalledApp>, onChange: (DoSelection) -> Unit) {
    when (sel.kind) {
        DoKind.FOCUS_TIMER -> NumberStepper("Minutes", sel.minutes, { onChange(sel.copy(minutes = it)) }, 1..480, step = 5)
        DoKind.PINNED_NOTE, DoKind.NOTIFY -> PatchTextField("Text", sel.message, { onChange(sel.copy(message = it)) })
        DoKind.RINGER -> EnumChipRow(RingerModeKind.entries, sel.ringer, { it.name.lowercase() }, { onChange(sel.copy(ringer = it)) })
        DoKind.OPEN_APP -> {
            var query by remember { mutableStateOf("") }
            Column {
                PatchTextField("Search installed apps", query, { query = it })
                val matches = remember(query) { if (query.isBlank()) emptyList() else installedApps().filter { it.label.contains(query, ignoreCase = true) }.take(6) }
                matches.forEach { app ->
                    Text(
                        app.label,
                        style = CuesType.labelSmall,
                        color = if (sel.app?.packageName == app.packageName) cuesTokens.doYellow else cuesTokens.inkSecondary,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .then(Modifier.clickable { onChange(sel.copy(app = app)) }),
                    )
                }
                sel.app?.let { Text("Selected: ${it.label}", style = CuesType.labelSmall, color = cuesTokens.go, modifier = Modifier.padding(top = 4.dp)) }
            }
        }
        DoKind.COMPOSE_MESSAGE, DoKind.COMPOSE_WHATSAPP -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PatchTextField(if (sel.kind == DoKind.COMPOSE_WHATSAPP) "Chat to choose in WhatsApp" else "Contact (optional)", sel.contactHint, { onChange(sel.copy(contactHint = it)) })
            PatchTextField("Message", sel.message, { onChange(sel.copy(message = it)) })
            if (sel.kind == DoKind.COMPOSE_WHATSAPP) MissingNote("Cues opens WhatsApp with this text; choose the chat and tap Send there.")
        }
        DoKind.CALENDAR_EVENT -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PatchTextField("Title", sel.calendarTitle, { onChange(sel.copy(calendarTitle = it)) })
            NumberStepper("Minutes", sel.calendarMinutes, { onChange(sel.copy(calendarMinutes = it)) }, 15..240, step = 15)
        }
        DoKind.ALARM -> TimeRow("At", sel.alarmHour, sel.alarmMinute, { onChange(sel.copy(alarmHour = it)) }, { onChange(sel.copy(alarmMinute = it)) })
        DoKind.MEDIA -> EnumChipRow(MediaCommand.entries, sel.media, { it.name.lowercase() }, { onChange(sel.copy(media = it)) })
        DoKind.QUIET -> Text("No extra parameters.", style = CuesType.labelSmall, color = cuesTokens.inkSlate)
        DoKind.UTILITY -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            EnumChipRow(UtilityId.entries, sel.utility, { it.name.lowercase().replace('_', ' ') }, { onChange(sel.copy(utility = it)) })
            EnumChipRow(UtilityState.entries, sel.utilityState, { it.name }, { onChange(sel.copy(utilityState = it)) })
            MissingNote("Needs a macro taught for this utility first (Workbench › Utility macros).")
        }
    }
}
