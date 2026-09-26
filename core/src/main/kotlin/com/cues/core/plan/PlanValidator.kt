package com.cues.core.plan

import com.cues.core.compile.Finding
import com.cues.core.compile.Severity
import com.cues.core.registry.ActionRisk

/**
 * Validates a [PlanDraft] independently of whatever proposed it — mirroring
 * [com.cues.core.compile.Validator]'s own role for a single routine.
 *
 * **The line this must never help cross:** a [PlanDraft] may be proposed by
 * a model once, before approval, exactly like any other draft. Approving one
 * commits to a fixed graph; nothing about running it may consult a model
 * again to choose the next step, read a step's own output to decide, or
 * silently substitute a different graph. A new plan is a new draft, needing
 * new approval, the same as editing an armed routine's own conditions
 * would. This validator only ever checks the graph's *shape* — it has no
 * opinion on, and no path to, how a plan runs.
 */
object PlanValidator {

    private const val MAX_STEPS = 8
    private const val MAX_DEPTH = 8

    /** Any step whose routine carries one of these risk classes needs its own confirmation inside a batch approval, never a blanket "approve all". */
    private val REQUIRES_INDIVIDUAL_CONFIRMATION = setOf(ActionRisk.UI_AUTOMATION, ActionRisk.EXTERNAL_UNOWNED)

    data class Result(
        val findings: List<Finding>,
        /** The highest risk any step carries — `null` only when [findings] already blocks approval (e.g. no steps at all). */
        val compositeRisk: ActionRisk?,
        /** Step ids the review screen must let the user confirm individually, never lump into one "approve all". */
        val stepsRequiringIndividualConfirmation: Set<String>,
    ) {
        val isValid: Boolean get() = findings.none { it.severity == Severity.ERROR }
    }

    fun validate(draft: PlanDraft): Result {
        val findings = mutableListOf<Finding>()

        if (draft.steps.isEmpty()) {
            findings += Finding(Severity.ERROR, "steps", "A plan needs at least one step.")
            return Result(findings, compositeRisk = null, stepsRequiringIndividualConfirmation = emptySet())
        }

        val stepIds = draft.steps.map { it.id }
        val duplicateIds = stepIds.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        if (duplicateIds.isNotEmpty()) {
            findings += Finding(Severity.ERROR, "steps", "Duplicate step id(s): ${duplicateIds.joinToString()}.")
        }

        if (draft.steps.size > MAX_STEPS) {
            findings += Finding(Severity.ERROR, "steps", "Plan has ${draft.steps.size} steps; the limit is $MAX_STEPS.")
        }

        val stepIdSet = stepIds.toSet()
        if (draft.startStepId !in stepIdSet) {
            findings += Finding(Severity.ERROR, "startStepId", "Start step '${draft.startStepId}' is not one of this plan's steps.")
        }

        val danglingReferences = draft.transitions.flatMap { transition ->
            val referenced = buildList {
                add(transition.fromStepId)
                when (transition) {
                    is PlanTransition.OnStepEnd -> add(transition.toStepId)
                    is PlanTransition.OnSignal -> add(transition.toStepId)
                    is PlanTransition.OnTimeout -> add(transition.toStepId)
                    is PlanTransition.Else -> Unit
                }
            }
            referenced.filter { it !in stepIdSet }
        }.distinct()
        if (danglingReferences.isNotEmpty()) {
            findings += Finding(
                Severity.ERROR,
                "transitions",
                "Transition(s) reference step id(s) not in this plan: ${danglingReferences.joinToString()}.",
            )
        }

        // Every step must have exactly one fallback Else so a coordinator
        // never has to guess what happens when nothing else matched.
        val stepsWithElse = draft.transitions.filterIsInstance<PlanTransition.Else>().map { it.fromStepId }.toSet()
        val stepsMissingElse = stepIds.filter { it !in stepsWithElse }
        if (stepsMissingElse.isNotEmpty()) {
            findings += Finding(
                Severity.ERROR,
                "transitions",
                "Step(s) missing a fallback Else transition: ${stepsMissingElse.joinToString()}.",
            )
        }

        // Cycles: rejected outright in this first version rather than
        // allowing a "bounded loop count" — a real bound needs runtime state
        // (an iteration counter a PlanExecutor would carry) this validator,
        // deliberately, has no access to. See this file's own doc comment:
        // this class checks shape only.
        val edges: Map<String, List<String>> = draft.transitions
            .mapNotNull { transition ->
                when (transition) {
                    is PlanTransition.OnStepEnd -> transition.fromStepId to transition.toStepId
                    is PlanTransition.OnSignal -> transition.fromStepId to transition.toStepId
                    is PlanTransition.OnTimeout -> transition.fromStepId to transition.toStepId
                    is PlanTransition.Else -> null
                }
            }
            .groupBy({ it.first }, { it.second })

        if (stepIdSet.isNotEmpty() && danglingReferences.isEmpty()) {
            findCycle(stepIdSet, edges)?.let { cycle ->
                findings += Finding(Severity.ERROR, "transitions", "Plan graph contains a cycle: ${cycle.joinToString(" -> ")}.")
            }
        }

        val depth = if (draft.startStepId in stepIdSet && danglingReferences.isEmpty()) {
            longestPathFrom(draft.startStepId, edges)
        } else {
            0
        }
        if (depth > MAX_DEPTH) {
            findings += Finding(Severity.ERROR, "transitions", "Plan depth from the start step is $depth; the limit is $MAX_DEPTH.")
        }

        val compositeRisk = draft.steps.maxByOrNull { it.risk.ordinal }?.risk
        val stepsRequiringIndividualConfirmation = draft.steps
            .filter { it.risk in REQUIRES_INDIVIDUAL_CONFIRMATION }
            .map { it.id }
            .toSet()

        return Result(findings, compositeRisk, stepsRequiringIndividualConfirmation)
    }

    /** DFS cycle detection; returns the cycle's step ids (start repeated at the end) if one exists, else `null`. */
    private fun findCycle(stepIds: Set<String>, edges: Map<String, List<String>>): List<String>? {
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        val path = mutableListOf<String>()

        fun visit(node: String): List<String>? {
            if (node in visiting) return path.subList(path.indexOf(node), path.size) + node
            if (node in visited) return null
            visiting += node
            path += node
            for (next in edges[node].orEmpty()) {
                visit(next)?.let { return it }
            }
            path.removeAt(path.size - 1)
            visiting -= node
            visited += node
            return null
        }

        for (id in stepIds) {
            if (id !in visited) {
                visit(id)?.let { return it }
            }
        }
        return null
    }

    /** Longest simple path length (edge count) reachable from [start] — used only to enforce [MAX_DEPTH], not to walk a real plan. */
    private fun longestPathFrom(start: String, edges: Map<String, List<String>>): Int {
        val memo = mutableMapOf<String, Int>()
        val onStack = mutableSetOf<String>()

        fun longest(node: String): Int {
            if (node in onStack) return 0 // a cycle is reported separately; don't recurse into it here
            memo[node]?.let { return it }
            onStack += node
            val best = edges[node].orEmpty().maxOfOrNull { next -> 1 + longest(next) } ?: 0
            onStack -= node
            memo[node] = best
            return best
        }

        return longest(start)
    }
}
