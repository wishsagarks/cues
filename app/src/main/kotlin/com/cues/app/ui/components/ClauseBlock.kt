package com.cues.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cues.app.ui.theme.CuesType
import com.cues.app.ui.theme.cuesTokens
import com.cues.core.model.Routine
import com.cues.core.review.ReviewCopy

/**
 * The shared WHEN/IF/DO/UNTIL/RESTORE rendering, reused by Now's cue cards,
 * Review, Cue Detail and Receipts (redesign plan §4). One badge + one line
 * of [ReviewCopy] text per clause; callers needing per-item rows (live gate
 * chips in Review) build their own rows instead of using this block.
 */
@Composable
fun ClauseBlock(routine: Routine, modifier: Modifier = Modifier, compact: Boolean = false) {
    Column(modifier = modifier, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(if (compact) 4.dp else 8.dp)) {
        ClauseRow(ClauseKind.WHEN, ReviewCopy.whenText(routine))
        ClauseRow(ClauseKind.IF, ReviewCopy.ifText(routine))
        ClauseRow(ClauseKind.DO, ReviewCopy.doText(routine))
        ClauseRow(ClauseKind.UNTIL, ReviewCopy.untilText(routine))
        if (!compact) {
            ClauseRow(ClauseKind.RESTORE, ReviewCopy.restoreText(routine))
        }
    }
}

@Composable
fun ClauseRow(kind: ClauseKind, text: String, modifier: Modifier = Modifier) {
    val t = cuesTokens
    Row(modifier = modifier, verticalAlignment = Alignment.Top) {
        ClauseBadge(kind, modifier = Modifier.padding(top = 1.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = CuesType.body, color = t.inkSecondary)
    }
}
