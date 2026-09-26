package com.cues.core.plan

import com.cues.core.registry.ActionRisk

/**
 * Future-scope groundwork (docs/FDD.md-compatible "agent" design): a model
 * may propose this shape, once, before approval. Nothing here is a runtime
 * agent loop — every [PlanStep] only ever names an *already-approved*
 * routine by id, and every transition is a plain, declared rule a
 * deterministic coordinator can walk without consulting a model again. See
 * [PlanValidator]'s own doc comment for the line this groundwork is not
 * meant to cross, and CLEANUP.md's plan-graph entry for what remains
 * unimplemented (there is no [PlanExecutor] / session-engine wiring yet —
 * this is the typed shape and its validator only).
 */
data class PlanStep(
    val id: String,
    /** The routine this step runs — never inline behaviour, so a step can carry no risk the routine's own review didn't already disclose. */
    val routineId: String,
    val risk: ActionRisk,
)

/** A declared rule for what a coordinator does once [fromStepId] ends — never a model's live choice. */
sealed interface PlanTransition {
    val fromStepId: String

    /** [fromStepId]'s routine session ended normally — begin [toStepId]. */
    data class OnStepEnd(override val fromStepId: String, val toStepId: String) : PlanTransition

    /** A named signal (already one of the closed [com.cues.core.model.EventKind]s a routine could gate on) fired — begin [toStepId]. */
    data class OnSignal(override val fromStepId: String, val signal: String, val toStepId: String) : PlanTransition

    /** [fromStepId] has run this long without ending — begin [toStepId]. */
    data class OnTimeout(override val fromStepId: String, val afterMillis: Long, val toStepId: String) : PlanTransition

    /** No other transition out of [fromStepId] applied — stop the plan. Every step should have exactly one of these as its fallback. */
    data class Else(override val fromStepId: String) : PlanTransition
}

data class PlanDraft(
    val id: String,
    val title: String,
    val steps: List<PlanStep>,
    val transitions: List<PlanTransition>,
    val startStepId: String,
)
