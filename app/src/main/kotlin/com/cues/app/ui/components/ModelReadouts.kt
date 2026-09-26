package com.cues.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cues.app.drafting.RunnerState
import com.cues.app.ui.theme.CuesType
import com.cues.app.ui.theme.cuesTokens
import com.cues.core.drafting.AttemptOutcome
import com.cues.core.drafting.DraftTrace
import com.cues.core.inference.InferenceBackend
import com.cues.core.model.DraftSourceId
import com.cues.core.ports.ModelAvailability
import com.cues.core.review.DraftCredit

/**
 * The live authoring-model state, intentionally derived from the availability
 * probe and the runner rather than from a device's advertised hardware. A
 * "ready" model has a file and consent; it is not claimed warm or NPU-backed
 * until [RunnerState] or an inference report says so.
 */
@Composable
fun ModelStatusBar(
    availability: ModelAvailability,
    runnerState: RunnerState,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = cuesTokens
    val (label, detail, tone) = when (availability) {
        ModelAvailability.NOT_INSTALLED -> Triple(
            "GRAMMAR ONLY",
            "No on-device model is installed. Grammar parsing remains available.",
            StatusTone.UNKNOWN,
        )
        ModelAvailability.INSTALLED_OFF -> Triple(
            "GEMMA OFF",
            "Installed on this phone; it will not be asked until you enable it.",
            StatusTone.UNKNOWN,
        )
        ModelAvailability.READY -> when (runnerState) {
            RunnerState.Cold -> Triple("GEMMA READY", "On-device model is enabled; it loads when first needed.", StatusTone.WARN)
            is RunnerState.Loading -> Triple("GEMMA LOADING", "Preparing ${runnerState.tier.label()} for this phone.", StatusTone.WARN)
            is RunnerState.Warm -> Triple("GEMMA WARM", "On-device · ${runnerState.tier.label()} · reused for the next draft.", StatusTone.GO)
            is RunnerState.Generating -> Triple("GEMMA WRITING", "On-device · ${runnerState.tier.label()} · output will be validated before review.", StatusTone.WARN)
            is RunnerState.Failed -> Triple("GEMMA UNAVAILABLE", runnerState.reasonCode, StatusTone.STOP)
        }
    }

    SlabCard(modifier = modifier.fillMaxWidth(), tier = SlabTier.ONE) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                StatusPill(label, tone)
                Text(detail, style = CuesType.labelSmall, color = t.inkSlate, modifier = Modifier.padding(top = 7.dp))
            }
            Switch(
                checked = availability != ModelAvailability.NOT_INSTALLED && enabled,
                onCheckedChange = onEnabledChange,
                enabled = availability != ModelAvailability.NOT_INSTALLED,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

/** Shows every completed drafting stage (or the real runner stage while it is active). */
@Composable
fun DraftPipelineStrip(
    trace: DraftTrace?,
    runnerState: RunnerState,
    isDrafting: Boolean,
    modifier: Modifier = Modifier,
) {
    val t = cuesTokens
    val modelAttempt = trace?.attempts?.firstOrNull { it.source != DraftSourceId.GRAMMAR_PARSER }
    val grammarAttempt = trace?.attempts?.firstOrNull { it.source == DraftSourceId.GRAMMAR_PARSER }
    val model = when {
        modelAttempt != null -> "${modelAttempt.source.pipelineLabel()} · ${modelAttempt.outcome.stageLabel(modelAttempt.elapsedMillis)}"
        isDrafting && runnerState is RunnerState.Loading -> "GEMMA · LOADING ${runnerState.tier.label()}"
        isDrafting && runnerState is RunnerState.Generating -> "GEMMA · WRITING ${runnerState.tier.label()}"
        isDrafting -> "MODEL · WAITING"
        else -> "MODEL · NOT RUN"
    }
    val grammar = grammarAttempt?.outcome?.stageLabel(grammarAttempt.elapsedMillis) ?: if (isDrafting) "GRAMMAR · CHECKING" else "GRAMMAR · NOT RUN"
    val verdict = trace?.verdict?.name?.replace('_', ' ') ?: if (isDrafting) "VALIDATING" else "WAITING FOR A REQUEST"

    SlabCard(modifier = modifier.fillMaxWidth(), tier = SlabTier.TWO) {
        Text("DRAFT PIPELINE", style = CuesType.labelSmall, color = t.inkSlate)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            PipelineStage(model, Modifier.weight(1f))
            PipelineStage(grammar, Modifier.weight(1f))
            PipelineStage(verdict, Modifier.weight(1f))
        }
        if (isDrafting) {
            Text(
                "Model output is never shown as a cue until validation completes.",
                style = CuesType.labelSmall,
                color = t.inkSlate,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** A durable, compact receipt for the routine being reviewed. */
@Composable
fun DraftProvenance(
    trace: DraftTrace?,
    draftedBy: DraftSourceId,
    modifier: Modifier = Modifier,
) {
    val t = cuesTokens
    val credit = DraftCredit.credit(trace, draftedBy)
    val report = trace?.attempts?.firstOrNull { it.inferenceReport != null }?.inferenceReport
    val detail = report?.let {
        buildString {
            append(it.backend.label())
            append(" · ").append(it.generationMs).append(" ms")
            it.timeToFirstTokenMs?.let { ttft -> append(" · first token ").append(ttft).append(" ms") }
            append(" · ").append(it.estimatedTokens).append(if (it.tokenCountSource.name == "MEASURED") " measured tokens" else " estimated tokens")
        }
    } ?: "No model timing was reported for this draft."
    SlabCard(modifier = modifier.fillMaxWidth(), tier = SlabTier.TWO) {
        Text("DRAFT PROVENANCE", style = CuesType.labelSmall, color = t.inkSlate)
        Text(credit.label(), style = CuesType.body, color = t.inkPrimary, modifier = Modifier.padding(top = 4.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(detail, style = CuesType.labelSmall, color = t.inkSlate, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun PipelineStage(label: String, modifier: Modifier = Modifier) {
    Text(label, style = CuesType.labelSmall, color = cuesTokens.inkSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

private fun AttemptOutcome.stageLabel(elapsedMillis: Long): String {
    val state = when (this) {
        AttemptOutcome.DRAFTED -> "DONE"
        AttemptOutcome.CLARIFY -> "ASKED"
        AttemptOutcome.FAILED -> "FAILED"
        AttemptOutcome.TIMED_OUT -> "TIMED OUT"
        AttemptOutcome.CANCELLED -> "CANCELLED"
        AttemptOutcome.INVALID -> "REJECTED"
        AttemptOutcome.SKIPPED_UNAVAILABLE -> "SKIPPED"
    }
    return if (elapsedMillis > 0) "$state · ${elapsedMillis}ms" else state
}

private fun InferenceBackend.label(): String = when (this) {
    InferenceBackend.NPU -> "NPU"
    InferenceBackend.GPU -> "GPU"
    InferenceBackend.CPU -> "CPU"
    InferenceBackend.CLOUD -> "CLOUD"
}

private fun DraftSourceId.pipelineLabel(): String = when (this) {
    DraftSourceId.ON_DEVICE_LLM -> "GEMMA"
    DraftSourceId.SARVAM_CLOUD -> "SARVAM"
    DraftSourceId.GRAMMAR_PARSER -> "GRAMMAR"
    DraftSourceId.IMPORTED_CARD -> "IMPORT"
    DraftSourceId.EXTERNAL_GEMMA_CALL -> "LOCAL HUB"
    DraftSourceId.SYSTEM_AGENT_CALL -> "SYSTEM AGENT"
}
