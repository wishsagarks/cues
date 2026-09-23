package com.cues.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.cues.core.model.Condition
import com.cues.core.model.ContextValue
import com.cues.core.model.NamedContext
import com.cues.core.model.Place
import com.cues.core.review.ReviewCopy
import com.cues.core.signals.SignalRegistry

/** One live signal offered as a candidate predicate when building a context. */
data class AvailableSignal(val label: String, val condition: Condition)

/**
 * Contexts are captured from what is true right now, never inferred later.
 * A signal that could not be read (no reading yet, permission denied) is
 * simply not offered — [ContextValue.Unknown] never becomes a silent "no".
 *
 * Places are entered by hand: a typed latitude/longitude is the most
 * declared a location can be, and it asks for no location permission at all
 * (see the manifest's own "no location" line).
 */
@Composable
fun ContextsScreen(
    contexts: List<NamedContext>,
    places: List<Place>,
    availableSignals: List<AvailableSignal>,
    onSaveContext: (label: String, predicates: List<Condition>) -> Unit,
    onDeleteContext: (String) -> Unit,
    onSavePlace: (label: String, lat: Double, lng: Double, radiusMeters: Int) -> Unit,
    onDeletePlace: (String) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val haptics = LocalHapticFeedback.current
    var showNewContext by remember { mutableStateOf(false) }
    var showNewPlace by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Contexts & places", style = MaterialTheme.typography.headlineSmall)
            OutlinedButton(onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onBack()
            }) { Text("Back") }
        }
        Text(
            "A named context is a visible bundle of signals you chose. Editing one re-opens approval for every cue that uses it.",
            style = MaterialTheme.typography.bodySmall,
            color = cuesColors.ink200,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            item {
                Text("NAMED CONTEXTS", style = MaterialTheme.typography.labelSmall, color = cuesColors.ink200)
            }
            items(contexts, key = { "ctx-" + it.id }) { context ->
                Surface(color = cuesColors.bg300, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(context.label, style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDeleteContext(context.id)
                            }) { Text("Delete", color = cuesColors.stop) }
                        }
                        Text(
                            context.predicates.joinToString(" and ") { SignalRegistry.reviewText(it) },
                            style = MaterialTheme.typography.bodySmall,
                            color = cuesColors.ink200,
                        )
                    }
                }
            }
            if (contexts.isEmpty()) {
                item { Text("No named contexts yet.", style = MaterialTheme.typography.bodySmall, color = cuesColors.ink200) }
            }
            item {
                if (showNewContext) {
                    NewContextForm(
                        availableSignals = availableSignals,
                        onSave = { label, predicates ->
                            onSaveContext(label, predicates)
                            showNewContext = false
                        },
                        onCancel = { showNewContext = false },
                    )
                } else {
                    OutlinedButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showNewContext = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("New context from what's true right now") }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
            item { Text("PLACES", style = MaterialTheme.typography.labelSmall, color = cuesColors.ink200) }
            items(places, key = { "place-" + it.id }) { place ->
                Surface(color = cuesColors.bg300, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(place.label, style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDeletePlace(place.id)
                            }) { Text("Delete", color = cuesColors.stop) }
                        }
                        Text(
                            "%.5f, %.5f — %dm radius".format(place.latitude, place.longitude, place.radiusMeters),
                            style = MaterialTheme.typography.bodySmall,
                            color = cuesColors.ink200,
                        )
                    }
                }
            }
            if (places.isEmpty()) {
                item { Text("No places yet.", style = MaterialTheme.typography.bodySmall, color = cuesColors.ink200) }
            }
            item {
                if (showNewPlace) {
                    NewPlaceForm(
                        onSave = { label, lat, lng, radius ->
                            onSavePlace(label, lat, lng, radius)
                            showNewPlace = false
                        },
                        onCancel = { showNewPlace = false },
                    )
                } else {
                    OutlinedButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showNewPlace = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("New place (typed coordinates — no location permission)") }
                }
            }
        }
    }
}

@Composable
private fun NewContextForm(
    availableSignals: List<AvailableSignal>,
    onSave: (String, List<Condition>) -> Unit,
    onCancel: () -> Unit,
) {
    var label by remember { mutableStateOf("") }
    val selected = remember { mutableStateOf(setOf<Int>()) }
    Surface(color = cuesColors.bg300, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Name (e.g. Desk)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            if (availableSignals.isEmpty()) {
                Text(
                    "Nothing readable right now — connect a device, plug in, or join Wi-Fi, then try again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cuesColors.ink200,
                )
            } else {
                availableSignals.forEachIndexed { index, signal ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(
                            checked = index in selected.value,
                            onCheckedChange = { checked ->
                                selected.value = if (checked) selected.value + index else selected.value - index
                            },
                        )
                        Text(signal.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onSave(label, selected.value.sorted().map { availableSignals[it].condition }) },
                    enabled = label.isNotBlank() && selected.value.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) { Text("Save") }
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun NewPlaceForm(
    onSave: (String, Double, Double, Int) -> Unit,
    onCancel: () -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf("") }
    var lng by remember { mutableStateOf("") }
    var radius by remember { mutableStateOf(150f) }
    Surface(color = cuesColors.bg300, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Name (e.g. Office)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = lat, onValueChange = { lat = it }, label = { Text("Latitude") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = lng, onValueChange = { lng = it }, label = { Text("Longitude") }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Text("Radius: ${radius.toInt()} m", style = MaterialTheme.typography.bodySmall, color = cuesColors.ink200)
            Slider(value = radius, onValueChange = { radius = it }, valueRange = 100f..1000f)
            Spacer(Modifier.height(8.dp))
            val parsedLat = lat.toDoubleOrNull()
            val parsedLng = lng.toDoubleOrNull()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onSave(label, parsedLat!!, parsedLng!!, radius.toInt()) },
                    enabled = label.isNotBlank() && parsedLat != null && parsedLng != null,
                    modifier = Modifier.weight(1f),
                ) { Text("Save") }
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            }
        }
    }
}
