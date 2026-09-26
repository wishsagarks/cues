package com.cues.core.plan

import com.cues.core.registry.ActionRisk
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class PlanValidatorTest {

    private fun step(id: String, risk: ActionRisk = ActionRisk.OWNED_AND_REVERSIBLE) =
        PlanStep(id, routineId = "routine-$id", risk = risk)

    @Test
    fun `a linear two-step plan with fallbacks is valid`() {
        val draft = PlanDraft(
            id = "p1",
            title = "Focus then wind down",
            steps = listOf(step("a"), step("b")),
            transitions = listOf(
                PlanTransition.OnStepEnd("a", "b"),
                PlanTransition.Else("a"),
                PlanTransition.Else("b"),
            ),
            startStepId = "a",
        )
        val result = PlanValidator.validate(draft)
        assertTrue(result.isValid, "findings: ${result.findings}")
        assertEquals(ActionRisk.OWNED_AND_REVERSIBLE, result.compositeRisk)
        assertTrue(result.stepsRequiringIndividualConfirmation.isEmpty())
    }

    @Test
    fun `an empty plan is rejected`() {
        val draft = PlanDraft("p1", "Empty", steps = emptyList(), transitions = emptyList(), startStepId = "a")
        val result = PlanValidator.validate(draft)
        assertTrue(!result.isValid)
    }

    @Test
    fun `a start step that isn't in the plan is rejected`() {
        val draft = PlanDraft("p1", "Bad start", steps = listOf(step("a")), transitions = listOf(PlanTransition.Else("a")), startStepId = "ghost")
        val result = PlanValidator.validate(draft)
        assertTrue(!result.isValid)
    }

    @Test
    fun `a transition to an unknown step is rejected`() {
        val draft = PlanDraft(
            "p1", "Dangling",
            steps = listOf(step("a")),
            transitions = listOf(PlanTransition.OnStepEnd("a", "ghost"), PlanTransition.Else("a")),
            startStepId = "a",
        )
        val result = PlanValidator.validate(draft)
        assertTrue(!result.isValid)
    }

    @Test
    fun `a step missing its fallback Else is rejected`() {
        val draft = PlanDraft("p1", "No fallback", steps = listOf(step("a")), transitions = emptyList(), startStepId = "a")
        val result = PlanValidator.validate(draft)
        assertTrue(!result.isValid)
    }

    @Test
    fun `a cycle is rejected rather than silently allowed`() {
        val draft = PlanDraft(
            "p1", "Loop",
            steps = listOf(step("a"), step("b")),
            transitions = listOf(
                PlanTransition.OnStepEnd("a", "b"),
                PlanTransition.OnStepEnd("b", "a"),
                PlanTransition.Else("a"),
                PlanTransition.Else("b"),
            ),
            startStepId = "a",
        )
        val result = PlanValidator.validate(draft)
        assertTrue(!result.isValid)
        assertTrue(result.findings.any { it.message.contains("cycle") })
    }

    @Test
    fun `duplicate step ids are rejected`() {
        val draft = PlanDraft(
            "p1", "Dup",
            steps = listOf(step("a"), step("a")),
            transitions = listOf(PlanTransition.Else("a")),
            startStepId = "a",
        )
        val result = PlanValidator.validate(draft)
        assertTrue(!result.isValid)
    }

    @Test
    fun `a chain deeper than the limit is rejected`() {
        val ids = (1..10).map { "s$it" }
        val steps = ids.map { step(it) }
        val transitions = ids.zipWithNext { from, to -> PlanTransition.OnStepEnd(from, to) } +
            ids.map { PlanTransition.Else(it) }
        val draft = PlanDraft("p1", "Too deep", steps = steps, transitions = transitions, startStepId = "s1")
        val result = PlanValidator.validate(draft)
        assertTrue(!result.isValid)
        assertTrue(result.findings.any { it.field == "transitions" && it.message.contains("depth") })
    }

    @Test
    fun `composite risk is the maximum across steps, and only its risk classes require individual confirmation`() {
        val draft = PlanDraft(
            "p1", "Mixed risk",
            steps = listOf(
                step("a", ActionRisk.LOCAL_NOTICE),
                step("b", ActionRisk.UI_AUTOMATION),
                step("c", ActionRisk.HANDOFF),
            ),
            transitions = listOf(
                PlanTransition.OnStepEnd("a", "b"),
                PlanTransition.OnStepEnd("b", "c"),
                PlanTransition.Else("a"),
                PlanTransition.Else("b"),
                PlanTransition.Else("c"),
            ),
            startStepId = "a",
        )
        val result = PlanValidator.validate(draft)
        assertTrue(result.isValid, "findings: ${result.findings}")
        assertEquals(ActionRisk.UI_AUTOMATION, result.compositeRisk)
        assertEquals(setOf("b"), result.stepsRequiringIndividualConfirmation)
    }

    @Test
    fun `a plan within the step and depth limits is accepted`() {
        val ids = (1..8).map { "s$it" }
        val steps = ids.map { step(it) }
        val transitions = ids.zipWithNext { from, to -> PlanTransition.OnStepEnd(from, to) } +
            ids.map { PlanTransition.Else(it) }
        val draft = PlanDraft("p1", "At the limit", steps = steps, transitions = transitions, startStepId = "s1")
        val result = PlanValidator.validate(draft)
        assertTrue(result.isValid, "findings: ${result.findings}")
    }
}
