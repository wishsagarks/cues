package com.cues.core.assistant

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
}

sealed interface ReplyChip {
    data class Choice(val id: String, val label: String) : ReplyChip
    data class Confirm(val commandId: String, val label: String) : ReplyChip
    data class Handoff(val agent: SystemAgent = SystemAgent.JOVI, val label: String = "Open Jovi") : ReplyChip
}

enum class SystemAgent { JOVI }

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
