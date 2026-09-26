package com.cues.core.assistant

import com.cues.core.drafting.DraftTrace
import com.cues.core.model.Routine
import com.cues.core.model.Session
import java.util.UUID

/** Mutable authoring state owned by one UI/CLI conversation. It is never runtime authority. */
class Conversation(
    val id: String = "conversation-${UUID.randomUUID()}",
    val turns: MutableList<Turn> = mutableListOf(),
    var currentDraft: Routine? = null,
    var lastRoutineId: String? = null,
)

data class Turn(
    val userText: String,
    val intent: AssistantIntent,
    val reply: AssistantReply,
    val draft: Routine? = null,
    val pendingCommand: PendingCommand? = null,
    /**
     * Every drafter this turn actually asked, and what each one did — from
     * whichever [com.cues.core.drafting.DraftResult] produced [draft]. Null
     * when there was nothing to draft (a control/explain/forecast turn) or
     * only ever one drafter to ask. See [com.cues.core.review.DraftCredit]
     * for the one place this becomes displayed text.
     */
    val trace: DraftTrace? = null,
)

data class AssistantReply(
    val code: ReplyCode,
    val args: Map<String, String> = emptyMap(),
    val answeredFrom: ReplySource,
    val chips: List<ReplyChip> = emptyList(),
) {
    val text: String get() = ReplyCopy.render(code, args)
}

enum class ReplySource { PARSER, ON_DEVICE_LLM, SARVAM_CLOUD, RECEIPTS, FORECAST, ROUTINE_STORE }

enum class ReplyCode {
    DRAFT_READY,
    DRAFT_REFINED,
    NEEDS_CLARIFICATION,
    DRAFT_FAILED,
    NO_DRAFT_TO_REFINE,
    CONFIRM_COMMAND,
    ROUTINE_NOT_FOUND,
    CUES_LIST,
    FORECAST_READY,
    EXPLANATION,
    CAPABILITIES,
    HANDOFF_TO_SYSTEM_AGENT,
    FACT_REMEMBERED,
    UNSUPPORTED,
    /**
     * [com.cues.core.assistant.OnDeviceRefinePhraser] was tried — there was
     * an active draft and the deterministic router found no match — but the
     * model's restatement did not parse into any of
     * [com.cues.core.assistant.RefineGrammarParser]'s closed sentences.
     * Distinct from [UNSUPPORTED] so the UI (and CLEANUP.md CL-39) can tell
     * "nothing was tried" apart from "the model was tried and its own words
     * didn't fit the edit grammar either."
     */
    REFINE_MODEL_UNPARSED,
}

sealed interface ReplyChip {
    data class Choice(val id: String, val label: String) : ReplyChip
    data class Confirm(val commandId: String, val label: String) : ReplyChip
    data class Handoff(val agent: SystemAgent = SystemAgent.JOVI, val label: String = "Open assistant") : ReplyChip
}

enum class SystemAgent { JOVI, GOOGLE_ASSISTANT, SYSTEM }

data class PendingCommand(
    val id: String = "command-${UUID.randomUUID()}",
    val kind: ControlKind,
    val routineId: String? = null,
    val sessionId: String? = null,
)

sealed interface CommandResult {
    data class Applied(val routine: Routine? = null, val session: Session? = null) : CommandResult
    data object MissingTarget : CommandResult
    data object Rejected : CommandResult
}
