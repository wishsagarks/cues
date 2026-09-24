package com.cues.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.cues.app.camera.QrCode
import com.cues.core.model.Routine
import com.cues.core.share.CueCards

/**
 * A cue, offline, as a scannable code — never a live link to this phone.
 *
 * The QR payload is exactly [CueCards.encode]'s output: a normalized
 * behaviour, its schema and a digest. Nothing about this phone's approval,
 * granted permissions or live state travels with it — the receiving phone
 * re-derives everything it needs to check, and starts the cue over from a
 * fresh, unapproved draft.
 */
@Composable
fun CueCardShareScreen(routine: Routine, onShareAsText: (String) -> Unit, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    val encoded = remember(routine.id, routine.version) { CueCards.encode(CueCards.from(routine)) }
    val qrBitmap = remember(encoded) { QrCode.encode(encoded) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Share as a Cue Card", style = MaterialTheme.typography.headlineSmall)
            OutlinedButton(onClick = onBack) { Text("Done") }
        }
        Text(
            "Whoever scans this gets ${routine.title}, as a fresh draft on their own phone — never armed, never carrying this phone's approval or permissions.",
            style = MaterialTheme.typography.bodySmall,
            color = cuesColors.ink200,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        )
        Image(
            bitmap = qrBitmap.asImageBitmap(),
            contentDescription = "Cue Card QR code for ${routine.title}",
            modifier = Modifier.fillMaxWidth().size(280.dp),
        )
        OutlinedButton(
            onClick = { onShareAsText(encoded) },
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        ) { Text("Share as text instead") }
    }
}
