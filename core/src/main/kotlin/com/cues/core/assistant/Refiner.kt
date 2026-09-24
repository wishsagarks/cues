package com.cues.core.assistant

import com.cues.core.compile.ValidationResult
import com.cues.core.compile.Validator
import com.cues.core.model.*

object Refiner {
    data class Result(val routine: Routine, val validation: ValidationResult)

    fun apply(routine: Routine, operation: RefineOperation): Result {
        val edited = when (operation) {
            is RefineOperation.SetDuration -> routine.copy(
                actions = routine.actions.map {
                    if (it.actionId == ActionId.START_FOCUS_TIMER) {
                        it.copy(args = ActionArgs.FocusTimer(operation.minutes))
                    } else it
                },
                endConditions = routine.endConditions
                    .filterNot { it is EndCondition.Duration }
                    .plus(EndCondition.Duration(operation.minutes)),
            )
            is RefineOperation.AddDays -> routine.withDays { it + operation.days }
            is RefineOperation.RemoveDays -> routine.withDays { it - operation.days }
            is RefineOperation.AddCondition -> routine.copy(conditions = routine.conditions + operation.condition)
            is RefineOperation.RemoveCondition -> routine.copy(conditions = routine.conditions - operation.condition)
            is RefineOperation.ReplaceTrigger -> routine.copy(trigger = operation.trigger)
            is RefineOperation.AddAction -> routine.copy(actions = routine.actions + operation.action)
            is RefineOperation.RemoveAction -> routine.copy(actions = routine.actions.filterNot { it.actionId == operation.actionId })
            is RefineOperation.SetEnd -> routine.copy(endConditions = routine.endConditions.filterNot { it::class == operation.end::class } + operation.end)
        }.copy(approvedDigest = null)

        val validation = Validator.validate(edited)
        return Result(
            routine = edited.copy(status = if (validation.isValid) RoutineStatus.REVIEWABLE else RoutineStatus.INVALID),
            validation = validation,
        )
    }

    private fun Routine.withDays(transform: (Set<Day>) -> Set<Day>): Routine {
        val existing = conditions.filterIsInstance<Condition.DaysOfWeek>().flatMap { it.days }.toSet()
        val replacement = transform(existing)
        return copy(
            conditions = conditions.filterNot { it is Condition.DaysOfWeek } +
                if (replacement.isEmpty()) emptyList() else listOf(Condition.DaysOfWeek(replacement)),
        )
    }
}
