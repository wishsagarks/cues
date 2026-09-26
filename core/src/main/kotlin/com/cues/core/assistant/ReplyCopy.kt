package com.cues.core.assistant

/** Deterministic user-facing text. Arguments come from typed state, never model prose. */
object ReplyCopy {
    fun render(code: ReplyCode, args: Map<String, String> = emptyMap()): String = when (code) {
        ReplyCode.DRAFT_READY -> "I drafted this cue. Review its full lifetime before you arm it."
        ReplyCode.DRAFT_REFINED -> "I updated the draft. Its previous approval no longer applies."
        ReplyCode.NEEDS_CLARIFICATION -> args["question"] ?: "I need one detail before I can draft that."
        ReplyCode.DRAFT_FAILED -> "I could not make a safe draft from that request."
        ReplyCode.NO_DRAFT_TO_REFINE -> "There is no draft in this conversation to change."
        ReplyCode.CONFIRM_COMMAND -> "Confirm this control action before Cues runs it."
        ReplyCode.ROUTINE_NOT_FOUND -> "I could not tell which cue you meant."
        ReplyCode.CUES_LIST -> args["summary"] ?: "You have no cues yet."
        ReplyCode.FORECAST_READY -> args["summary"] ?: "There are no armed cues to forecast."
        ReplyCode.EXPLANATION -> args["summary"] ?: "There is no recorded explanation for that cue yet."
        ReplyCode.CAPABILITIES -> "Ask Cues to create, refine, explain, forecast, pause, resume, skip, or stop your cues. General tasks go to Jovi."
        ReplyCode.HANDOFF_TO_SYSTEM_AGENT -> "That is a Jovi task. Cues handles when-and-until behavior."
        ReplyCode.FACT_REMEMBERED -> "Saved to Declared Memory. You can review or delete it at any time."
        ReplyCode.UNSUPPORTED -> "I can only help with your cues and their declared context."
        ReplyCode.REFINE_MODEL_UNPARSED -> "The on-device model restated that, but not as an edit Cues supports."
    }
}
