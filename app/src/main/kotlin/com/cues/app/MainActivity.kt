package com.cues.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cues.core.rehearsal.Rehearsal
import com.cues.core.rehearsal.RehearsalRow

/**
 * Scaffold for the four surfaces the PRS describes: Home, Review, Routine
 * detail and Receipt.
 *
 * What is here today is the one screen that proves the core is wired in: it
 * runs a rehearsal through the shared evaluator and renders the rows. It is a
 * skeleton, and the event's UI work replaces it.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RehearsalScreen()
                }
            }
        }
    }
}

@Composable
private fun RehearsalScreen() {
    // A placeholder cue so the screen has something to show before authoring
    // exists. Replaced by the drafted routine once the Review surface lands.
    val rows: List<RehearsalRow> = rememberSampleRehearsal()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Cues", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Sample events. Nothing on your phone changes.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        rows.forEach { row ->
            Text(row.label, style = MaterialTheme.typography.titleSmall)
            Text(row.outcome, style = MaterialTheme.typography.bodyMedium)
            row.explanation.forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
            }
            Text("", modifier = Modifier.padding(bottom = 12.dp))
        }
    }
}

@Composable
private fun rememberSampleRehearsal(): List<RehearsalRow> =
    androidx.compose.runtime.remember { Rehearsal.run(SampleRoutine.focusOnEarbuds()).rows }
