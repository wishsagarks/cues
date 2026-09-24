package com.cues.core.assistant

import com.cues.core.model.Condition
import com.cues.core.model.Day
import com.cues.core.model.EndCondition
import com.cues.core.model.Trigger

sealed interface AssistantIntent {
    data class Create(val text: String) : AssistantIntent
    data class Refine(val operation: RefineOperation) : AssistantIntent
    data class Explain(val reference: String, val day: String? = null) : AssistantIntent
    data object Forecast : AssistantIntent
    data class Control(val kind: ControlKind, val reference: String) : AssistantIntent
    data class Remember(val label: String, val value: String) : AssistantIntent
    data object ListCues : AssistantIntent
    data object Capabilities : AssistantIntent
    data class Unsupported(val fragment: String, val routeTo: UnsupportedRoute = UnsupportedRoute.NONE) : AssistantIntent
}

enum class ControlKind { PAUSE, RESUME, SKIP_TODAY, STOP }

enum class UnsupportedRoute { NONE, SYSTEM_AGENT }

sealed interface RefineOperation {
    data class SetDuration(val minutes: Int) : RefineOperation
    data class AddDays(val days: Set<Day>) : RefineOperation
    data class RemoveDays(val days: Set<Day>) : RefineOperation
    data class AddCondition(val condition: Condition) : RefineOperation
    data class RemoveCondition(val condition: Condition) : RefineOperation
    data class ReplaceTrigger(val trigger: Trigger) : RefineOperation
    data class AddAction(val action: com.cues.core.model.ActionSpec) : RefineOperation
    data class RemoveAction(val actionId: com.cues.core.model.ActionId) : RefineOperation
    data class SetEnd(val end: EndCondition) : RefineOperation
}
