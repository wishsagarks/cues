package com.cues.app.ui.workbench

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.cues.app.ui.components.RiskTag
import com.cues.app.ui.components.SectionHeader
import com.cues.app.ui.components.SlabCard
import com.cues.app.ui.components.SlabTier
import com.cues.app.ui.theme.CuesType
import com.cues.app.ui.theme.cuesTokens
import com.cues.core.registry.ActionRisk

/**
 * Workbench (redesign plan §3.7): the entry hub for the build/teach/ingest/
 * declare tools. A full patch-bay rebuild is deferred (see the redesign
 * plan's phasing) — this consolidates the existing, tested editors
 * (Contexts, Memory, Utility bindings, timetable/Cue Card ingest) behind one
 * restyled shell rather than Home's old flat button row.
 */
@Composable
fun WorkbenchScreen(
    onOpenContexts: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenUtilityBindings: () -> Unit,
    onOpenTimetableCapture: () -> Unit,
    onOpenCueCardScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { SectionHeader("DECLARE", meta = "what Cues is allowed to know") }
        item {
            WorkbenchRow("Contexts & places", "Named signal groups and saved places", Icons.Filled.PinDrop, onOpenContexts)
        }
        item {
            WorkbenchRow("Memory", "Facts Cues can reference in a cue", Icons.Filled.Memory, onOpenMemory)
        }

        item { SectionHeader("BUILD", meta = "high-risk, last resort") }
        item {
            WorkbenchRow(
                "Utility macros",
                "Teach a UI sequence for a toggle with no public API",
                Icons.Filled.TouchApp,
                onOpenUtilityBindings,
                risk = ActionRisk.UI_AUTOMATION,
            )
        }

        item { SectionHeader("INGEST", meta = "imported text is data, never authority") }
        item {
            WorkbenchRow("Capture a timetable", "Camera + on-device OCR, reviewed before it drafts anything", Icons.Filled.CameraAlt, onOpenTimetableCapture)
        }
        item {
            WorkbenchRow("Scan a Cue Card", "Import a cue shared from another phone, as a draft", Icons.Filled.QrCodeScanner, onOpenCueCardScan)
        }

        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun WorkbenchRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    risk: ActionRisk? = null,
) {
    val t = cuesTokens
    SlabCard(
        tier = SlabTier.ONE,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(icon, contentDescription = null, tint = t.doYellow, modifier = Modifier.height(22.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(title, style = CuesType.title, color = t.inkPrimary)
                Text(subtitle, style = CuesType.labelSmall, color = t.inkSlate, modifier = Modifier.padding(top = 2.dp))
                if (risk != null) {
                    RiskTag(risk, modifier = Modifier.padding(top = 6.dp))
                }
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = t.inkSlate)
        }
    }
}
