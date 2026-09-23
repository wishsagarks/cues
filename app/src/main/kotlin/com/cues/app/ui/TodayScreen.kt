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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.cues.core.review.FORECAST_LABEL
import com.cues.core.review.ForecastItem
import com.cues.core.review.ForecastStatus

/**
 * Today: a deterministic eligibility forecast, never an event prediction.
 *
 * [FORECAST_LABEL] is shown verbatim rather than paraphrased, so the one
 * honest disclosure this screen exists to carry cannot drift from what
 * `forecastToday` actually computes.
 */
@Composable
fun TodayScreen(
    items: List<ForecastItem>,
    titleFor: (String) -> String,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val haptics = LocalHapticFeedback.current
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Today", style = MaterialTheme.typography.headlineSmall)
            OutlinedButton(onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onBack()
            }) { Text("Back") }
        }
        Text(
            FORECAST_LABEL,
            style = MaterialTheme.typography.bodySmall,
            color = cuesColors.ink200,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        if (items.isEmpty()) {
            Text("No armed cues to forecast yet.", style = MaterialTheme.typography.bodyMedium, color = cuesColors.ink200)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(items, key = { it.routineId }) { item -> ForecastCard(titleFor(item.routineId), item) }
            }
        }
    }
}

@Composable
private fun ForecastCard(title: String, item: ForecastItem) {
    Surface(color = cuesColors.bg300, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                StatusChip(item.status.label(), item.status.toTone())
            }
            Text(item.window, style = MaterialTheme.typography.bodySmall, color = cuesColors.ink200, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.height(6.dp))
            item.reasons.forEach { reason ->
                Text(reason, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

private fun ForecastStatus.label(): String = when (this) {
    ForecastStatus.WILL_ARM -> "will arm"
    ForecastStatus.SKIPPED_BY_PATCH -> "skipped"
    ForecastStatus.CANNOT_TELL -> "unknown"
    ForecastStatus.NOT_TODAY -> "not today"
}

private fun ForecastStatus.toTone(): Tone = when (this) {
    ForecastStatus.WILL_ARM -> Tone.GO
    ForecastStatus.SKIPPED_BY_PATCH -> Tone.AMBER
    ForecastStatus.CANNOT_TELL -> Tone.AMBER
    ForecastStatus.NOT_TODAY -> Tone.AMBER
}
