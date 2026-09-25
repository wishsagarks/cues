package com.cues.app.ui.workbench

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.cues.app.runtime.InstalledApp
import com.cues.app.ui.components.EmptyState
import com.cues.app.ui.components.KineticButton
import com.cues.app.ui.components.RiskTag
import com.cues.app.ui.components.SectionHeader
import com.cues.app.ui.components.SlabCard
import com.cues.app.ui.components.SlabTier
import com.cues.app.ui.components.StatusPill
import com.cues.app.ui.components.StatusTone
import com.cues.app.ui.theme.CuesShape
import com.cues.app.ui.theme.CuesType
import com.cues.app.ui.theme.cuesTokens
import com.cues.core.drafting.PairedDevice
import com.cues.core.model.EndCondition
import com.cues.core.model.NamedContext
import com.cues.core.model.Place
import com.cues.core.workbench.PatchResult
import com.cues.core.workbench.PatchSelection
import com.cues.core.workbench.PatchSentence

/**
 * The Workbench patch bay (redesign plan §3.7). Touch-to-patch, not literal
 * drag-and-drop: tap a WHEN/IF/DO chip to add or remove it, fill in whatever
 * parameters it needs inline, and [PatchSentence] — the same one
 * `PatchSentenceTest` round-trips through the grammar — decides live whether
 * the combination compiles. There is no second path to a [com.cues.core.model.Routine]:
 * "Compile to Review" sends the generated sentence through the exact same
 * `CueService.draft` every spoken or typed cue goes through, so Review shows
 * the drafter that genuinely drafted it.
 */
@Composable
fun PatchBayScreen(
    devices: List<PairedDevice>,
    contexts: List<NamedContext>,
    places: List<Place>,
    installedApps: () -> List<InstalledApp>,
    onCompile: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = cuesTokens
    val haptics = LocalHapticFeedback.current
    BackHandler(onBack = onBack)
    var whenSel by remember { mutableStateOf<WhenSelection?>(null) }
    val ifSels = remember { androidx.compose.runtime.mutableStateListOf<IfSelection>() }
    val doSels = remember { androidx.compose.runtime.mutableStateListOf<DoSelection>() }
    val untilExtras = remember { androidx.compose.runtime.mutableStateListOf<UntilExtra>() }
    var appQuery by remember { mutableStateOf("") }

    val patchSentence = remember(devices, contexts, places) {
        PatchSentence(devices = devices, contexts = { contexts }, places = { places })
    }

    val currentWhen = whenSel
    val builtEnds = remember(currentWhen, doSels.toList(), untilExtras.toList()) {
        buildList {
            add(EndCondition.ManualStop)
            currentWhen?.impliedEnd?.let { add(it) }
            doSels.firstNotNullOfOrNull { (it.toActionSpec()?.args as? com.cues.core.model.ActionArgs.FocusTimer)?.durationMinutes }
                ?.let { add(EndCondition.Duration(it)) }
            untilExtras.forEach { add(it.toEndCondition()) }
        }.distinct()
    }

    val selection: PatchSelection? = remember(currentWhen, ifSels.toList(), doSels.toList(), builtEnds) {
        val trigger = currentWhen?.takeIf { it.isComplete }?.toTrigger() ?: return@remember null
        val conditions = ifSels.filter { it.isComplete }.mapNotNull { it.toCondition() }
        val actions = doSels.filter { it.isComplete }.mapNotNull { it.toActionSpec() }
        if (actions.isEmpty()) return@remember null
        PatchSelection(trigger, conditions, actions, builtEnds)
    }

    val result: PatchResult? = remember(selection) { selection?.let { patchSentence.compose(it) } }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text("Patch bay", style = MaterialTheme.typography.headlineSmall, color = t.inkPrimary)
                OutlinedButton(onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onBack()
                }) { Text("Back") }
            }
        }
        item {
            SlabCard(tier = SlabTier.ONE, modifier = Modifier.fillMaxWidth()) {
                Text2("PATCH BAY", t.inkSlate)
                Text2(
                    "Build a cue from typed controls instead of a sentence. Every combination is checked against the same grammar a spoken cue goes through.",
                    t.inkSecondary,
                    CuesType.body,
                )
            }
        }

        item { SectionHeader("WHEN — pick one") }
        item {
            ChipGrid(WhenKind.entries) { kind ->
                Chip(kind.label, selected = currentWhen?.kind == kind) {
                    whenSel = if (currentWhen?.kind == kind) null else WhenSelection(kind)
                }
            }
        }
        currentWhen?.let { w ->
            item {
                SlabCard(tier = SlabTier.TWO, modifier = Modifier.fillMaxWidth()) {
                    WhenEditor(w, devices, places, onChange = { whenSel = it })
                    w.impliedEnd?.let {
                        Spacer(Modifier.height(6.dp))
                        StatusPill("ALWAYS ENDS: TRIGGER REVERSED", StatusTone.UNKNOWN)
                    }
                }
            }
        }

        item { SectionHeader("IF — any that apply", meta = "${ifSels.size} added") }
        item {
            ChipGrid(IfKind.entries) { kind ->
                val existing = ifSels.indexOfFirst { it.kind == kind }
                Chip(kind.label, selected = existing >= 0) {
                    if (existing >= 0) ifSels.removeAt(existing) else ifSels.add(IfSelection(kind))
                }
            }
        }
        itemsIndexed(ifSels) { index, sel ->
            SlabCard(tier = SlabTier.TWO, modifier = Modifier.fillMaxWidth()) {
                IfEditor(sel, devices, contexts, places, onChange = { ifSels[index] = it })
            }
        }

        item { SectionHeader("DO — at least one", meta = "${doSels.size} added") }
        item {
            ChipGrid(DoKind.entries) { kind ->
                val existing = doSels.indexOfFirst { it.kind == kind }
                Chip(kind.label, selected = existing >= 0, risk = kind.risk) {
                    if (existing >= 0) doSels.removeAt(existing) else doSels.add(DoSelection(kind))
                }
            }
        }
        itemsIndexed(doSels) { index, sel ->
            SlabCard(tier = SlabTier.TWO, modifier = Modifier.fillMaxWidth()) {
                DoEditor(sel, installedApps, onChange = { doSels[index] = it })
            }
        }

        item { SectionHeader("UNTIL — extra endings", meta = "beyond stop-by-hand") }
        item {
            ChipGrid(UntilExtraKind.entries) { kind ->
                val existing = untilExtras.indexOfFirst { it.kind == kind }
                Chip(kind.label, selected = existing >= 0) {
                    if (existing >= 0) untilExtras.removeAt(existing) else untilExtras.add(UntilExtra(kind))
                }
            }
        }
        itemsIndexed(untilExtras) { index, extra ->
            if (extra.kind == UntilExtraKind.AT_TIME) {
                SlabCard(tier = SlabTier.TWO, modifier = Modifier.fillMaxWidth()) {
                    TimeRow("At", extra.hour, extra.minute, { untilExtras[index] = extra.copy(hour = it) }, { untilExtras[index] = extra.copy(minute = it) })
                }
            }
        }

        item { SectionHeader("PATCH WIRE") }
        item {
            SlabCard(
                tier = SlabTier.ONE,
                edgeColor = when {
                    result is PatchResult.Sentence -> t.activeEdge
                    result is PatchResult.Inexpressible -> t.dangerEdge
                    else -> null
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                when {
                    selection == null -> Text2("Pick a WHEN and at least one DO to preview a sentence.", t.inkSlate)
                    result is PatchResult.Sentence -> {
                        Text2("GENERATED SENTENCE", t.inkSlate)
                        Text2(result.text, t.inkPrimary, CuesType.bodyLarge)
                    }
                    result is PatchResult.Inexpressible -> {
                        Text2("CAN'T COMPILE: ${result.part.uppercase()}", t.stop)
                        Text2(result.why, t.inkSecondary, CuesType.body)
                    }
                    else -> {}
                }
            }
        }
        item {
            KineticButton(
                text = "Compile to Review",
                onClick = { (result as? PatchResult.Sentence)?.let { onCompile(it.text) } },
                enabled = result is PatchResult.Sentence,
                disabledReason = (result as? PatchResult.Inexpressible)?.let { "Fix ${it.part} above" },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun Text2(text: String, color: androidx.compose.ui.graphics.Color, style: androidx.compose.ui.text.TextStyle = CuesType.labelSmall) {
    androidx.compose.material3.Text(text, style = style, color = color)
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipGrid(items: List<T>, content: @Composable (T) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { content(it) }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, risk: com.cues.core.registry.ActionRisk? = null, onClick: () -> Unit) {
    val t = cuesTokens
    Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        modifier = Modifier
            .background(if (selected) t.doYellow else t.island, CuesShape.pill)
            .border(1.dp, if (selected) androidx.compose.ui.graphics.Color.Transparent else t.hairline, CuesShape.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        androidx.compose.material3.Text(
            label,
            style = CuesType.labelSmall,
            color = if (selected) androidx.compose.ui.graphics.Color(0xFF0B0C10) else t.inkSecondary,
        )
        if (risk == com.cues.core.registry.ActionRisk.UI_AUTOMATION) {
            androidx.compose.material3.Text(" ⚠", style = CuesType.labelSmall, color = if (selected) androidx.compose.ui.graphics.Color(0xFF0B0C10) else t.untilOrange)
        }
    }
}
