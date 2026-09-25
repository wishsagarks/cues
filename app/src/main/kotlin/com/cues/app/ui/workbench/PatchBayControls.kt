package com.cues.app.ui.workbench

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cues.app.ui.components.clickableNoIndicationPublic
import com.cues.app.ui.theme.CuesShape
import com.cues.app.ui.theme.CuesType
import com.cues.app.ui.theme.cuesTokens
import com.cues.core.model.Day

/** A small stepper for a bounded int — minutes, percent, hour, minute. */
@Composable
fun NumberStepper(label: String, value: Int, onChange: (Int) -> Unit, range: IntRange, step: Int = 1, modifier: Modifier = Modifier) {
    val t = cuesTokens
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Text(label, style = CuesType.labelSmall, color = t.inkSlate, modifier = Modifier.width(90.dp))
        StepButton(Icons.Filled.Remove) { onChange((value - step).coerceIn(range)) }
        Text(
            "$value",
            style = CuesType.label,
            color = t.inkPrimary,
            modifier = Modifier.width(44.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        StepButton(Icons.Filled.Add) { onChange((value + step).coerceIn(range)) }
    }
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val t = cuesTokens
    Row(
        modifier = Modifier
            .background(t.raised, CircleShape)
            .clickableNoIndicationPublic(onClick)
            .padding(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = t.inkSecondary, modifier = Modifier.width(16.dp))
    }
}

/** An hour:minute pair, each with its own stepper. */
@Composable
fun TimeRow(label: String, hour: Int, minute: Int, onHour: (Int) -> Unit, onMinute: (Int) -> Unit, modifier: Modifier = Modifier) {
    val t = cuesTokens
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Text(label, style = CuesType.labelSmall, color = t.inkSlate, modifier = Modifier.width(90.dp))
        NumberStepper("", hour, onHour, 0..23, modifier = Modifier)
        Text(":", style = CuesType.label, color = t.inkSlate, modifier = Modifier.padding(horizontal = 2.dp))
        NumberStepper("", minute, onMinute, 0..59, step = 5, modifier = Modifier)
    }
}

/** M/T/W/T/F/S/S toggle chips for [Condition.DaysOfWeek]. */
@Composable
fun DayToggleRow(selected: Set<Day>, onToggle: (Day) -> Unit, modifier: Modifier = Modifier) {
    val t = cuesTokens
    val letters = mapOf(Day.MON to "M", Day.TUE to "T", Day.WED to "W", Day.THU to "T", Day.FRI to "F", Day.SAT to "S", Day.SUN to "S")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        letters.forEach { (day, letter) ->
            val isOn = day in selected
            Row(
                modifier = Modifier
                    .background(if (isOn) t.doYellow else t.raised, CircleShape)
                    .clickableNoIndicationPublic { onToggle(day) }
                    .padding(8.dp),
            ) {
                Text(letter, style = CuesType.labelSmall, color = if (isOn) androidx.compose.ui.graphics.Color(0xFF0B0C10) else t.inkSlate)
            }
        }
    }
}

/** A generic single-select row of small pill options for an enum-like choice. */
@Composable
fun <T> EnumChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val t = cuesTokens
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        items(options.size) { i ->
            val option = options[i]
            val isOn = option == selected
            Row(
                modifier = Modifier
                    .background(if (isOn) t.doYellow else t.raised, CuesShape.pill)
                    .clickableNoIndicationPublic { onSelect(option) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(label(option), style = CuesType.labelSmall, color = if (isOn) androidx.compose.ui.graphics.Color(0xFF0B0C10) else t.inkSecondary)
            }
        }
    }
}

@Composable
fun PatchTextField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val t = cuesTokens
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, style = CuesType.labelSmall) },
        singleLine = true,
        shape = CuesShape.pill,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = t.doYellow,
            unfocusedContainerColor = t.recess,
            focusedContainerColor = t.recess,
        ),
        modifier = modifier,
    )
}
