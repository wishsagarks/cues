package com.cues.core.drafting

import com.cues.core.Fixtures
import com.cues.core.model.ActionArgs
import com.cues.core.model.ActionId
import com.cues.core.model.ActionSpec
import com.cues.core.model.DraftSourceId
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class DifferentialDrafterTest {
    private fun drafter(result: DraftResult) = object : RoutineDrafter {
        override val id = result.source
        override suspend fun draft(text: String) = result
    }

    @Test fun `semantic disagreement asks instead of selecting`() = runTest {
        val a = DraftResult.Drafted(DraftSourceId.GRAMMAR_PARSER, Fixtures.heroRoutine())
        // A validly-drafted but differing routine — a model reading the same
        // request as wanting only quiet notifications, not the focus timer
        // too. Both are individually valid; they simply disagree.
        val b = DraftResult.Drafted(
            DraftSourceId.ON_DEVICE_LLM,
            Fixtures.heroRoutine().copy(actions = listOf(ActionSpec(ActionId.REQUEST_DND, ActionArgs.Dnd()))),
        )
        val result = DifferentialDrafter(drafter(a), drafter(b)).draft("cue")
        assertTrue(assertIs<DraftResult.NeedsClarification>(result).question.contains("action"))
    }

    @Test fun `agreement is labelled and prefers the deterministic parser's own data`() = runTest {
        val parserDraft = DraftResult.Drafted(DraftSourceId.GRAMMAR_PARSER, Fixtures.heroRoutine(), consumed = listOf(0..3))
        // Same normalized meaning, different sourceText/consumed — a model
        // paraphrasing the same request differently.
        val modelDraft = DraftResult.Drafted(
            DraftSourceId.ON_DEVICE_LLM,
            Fixtures.heroRoutine().copy(sourceText = "a differently worded but equivalent request"),
        )
        val result = assertIs<DraftResult.Drafted>(
            DifferentialDrafter(drafter(modelDraft), drafter(parserDraft)).draft("cue"),
        )
        assertEquals(DraftSourceId.GRAMMAR_PARSER, result.source, "the parser's own auditable span data must win, not the model's")
        assertEquals(listOf(0..3), result.consumed)
        assertTrue(result.findings.any { it.message.contains("agree") })
    }

    @Test fun `one drafter failing falls through to the other's answer`() = runTest {
        val ok = DraftResult.Drafted(DraftSourceId.GRAMMAR_PARSER, Fixtures.heroRoutine())
        val failed = DraftResult.Failed(DraftSourceId.ON_DEVICE_LLM, "not wired up")
        val result = assertIs<DraftResult.Drafted>(DifferentialDrafter(drafter(failed), drafter(ok)).draft("cue"))
        assertEquals(DraftSourceId.GRAMMAR_PARSER, result.source)
    }

    @Test fun `both drafters failing is reported, not thrown`() = runTest {
        val a = DraftResult.Failed(DraftSourceId.ON_DEVICE_LLM, "crashed")
        val b = DraftResult.Failed(DraftSourceId.GRAMMAR_PARSER, "no trigger")
        val result = DifferentialDrafter(drafter(a), drafter(b)).draft("cue")
        assertIs<DraftResult.Failed>(result)
    }

    @Test fun `a model draft that fails independent validation is not trusted, even alone`() = runTest {
        // An empty-actions routine fails Validator ("a cue that does nothing
        // is rejected"). The model does not get to vouch for its own output
        // just because the parser also has nothing to say.
        val invalidModelDraft = DraftResult.Drafted(DraftSourceId.ON_DEVICE_LLM, Fixtures.heroRoutine().copy(actions = emptyList()))
        val parserFailed = DraftResult.Failed(DraftSourceId.GRAMMAR_PARSER, "no trigger")
        val result = DifferentialDrafter(drafter(invalidModelDraft), drafter(parserFailed)).draft("cue")
        assertIs<DraftResult.Failed>(result)
    }

    @Test fun `a crashing drafter is caught rather than propagating`() = runTest {
        val crashing = object : RoutineDrafter {
            override val id = DraftSourceId.ON_DEVICE_LLM
            override suspend fun draft(text: String): DraftResult = error("boom")
        }
        val ok = DraftResult.Drafted(DraftSourceId.GRAMMAR_PARSER, Fixtures.heroRoutine())
        val result = assertIs<DraftResult.Drafted>(DifferentialDrafter(crashing, drafter(ok)).draft("cue"))
        assertEquals(DraftSourceId.GRAMMAR_PARSER, result.source)
    }

    @Test fun `a drafter that hangs past the timeout is treated as failed`() = runTest {
        val hanging = object : RoutineDrafter {
            override val id = DraftSourceId.ON_DEVICE_LLM
            override suspend fun draft(text: String): DraftResult {
                delay(1_000)
                return DraftResult.Drafted(id, Fixtures.heroRoutine())
            }
        }
        val ok = DraftResult.Drafted(DraftSourceId.GRAMMAR_PARSER, Fixtures.heroRoutine())
        val result = assertIs<DraftResult.Drafted>(
            DifferentialDrafter(hanging, drafter(ok), timeoutMillis = 10).draft("cue"),
        )
        assertEquals(DraftSourceId.GRAMMAR_PARSER, result.source)
    }
}
