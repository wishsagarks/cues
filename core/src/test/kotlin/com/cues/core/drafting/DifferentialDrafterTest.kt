package com.cues.core.drafting

import com.cues.core.Fixtures
import com.cues.core.inference.InferenceBackend
import com.cues.core.inference.InferenceReport
import com.cues.core.model.ActionArgs
import com.cues.core.model.ActionId
import com.cues.core.model.ActionSpec
import com.cues.core.model.DraftSourceId
import com.cues.core.ports.ModelAvailability
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class DifferentialDrafterTest {
    private fun drafter(result: DraftResult) = object : RoutineDrafter {
        override val id = result.source
        override suspend fun draft(text: String) = result
    }

    @Test fun `semantic disagreement asks instead of selecting, carrying both candidates`() = runTest {
        val a = DraftResult.Drafted(DraftSourceId.GRAMMAR_PARSER, Fixtures.heroRoutine())
        // A validly-drafted but differing routine — a model reading the same
        // request as wanting only quiet notifications, not the focus timer
        // too. Both are individually valid; they simply disagree.
        val b = DraftResult.Drafted(
            DraftSourceId.ON_DEVICE_LLM,
            Fixtures.heroRoutine().copy(actions = listOf(ActionSpec(ActionId.REQUEST_DND, ActionArgs.Dnd()))),
        )
        val result = assertIs<DraftResult.NeedsClarification>(DifferentialDrafter(drafter(b), drafter(a)).draft("cue"))
        assertTrue(result.question.contains("action"))
        assertEquals(DifferingClause.ACTIONS, result.differingClause)
        assertEquals(setOf(DraftSourceId.ON_DEVICE_LLM, DraftSourceId.GRAMMAR_PARSER), result.candidates.map { it.source }.toSet())
        val trace = checkNotNull(result.trace)
        assertEquals(DraftVerdict.DISAGREED, trace.verdict)
        assertEquals(2, trace.attempts.size)
        assertTrue(trace.attempts.all { it.outcome == AttemptOutcome.DRAFTED })
    }

    @Test fun `agreement prefers the deterministic parser's own data and keeps the model's report`() = runTest {
        val report = InferenceReport(InferenceBackend.GPU, loadMs = 400, generationMs = 150, estimatedTokens = 9)
        val parserDraft = DraftResult.Drafted(DraftSourceId.GRAMMAR_PARSER, Fixtures.heroRoutine(), consumed = listOf(0..3))
        // Same normalized meaning, different sourceText/consumed — a model
        // paraphrasing the same request differently.
        val modelDraft = DraftResult.Drafted(
            DraftSourceId.ON_DEVICE_LLM,
            Fixtures.heroRoutine().copy(sourceText = "a differently worded but equivalent request"),
            inferenceReport = report,
        )
        val result = assertIs<DraftResult.Drafted>(
            DifferentialDrafter(drafter(modelDraft), drafter(parserDraft)).draft("cue"),
        )
        assertEquals(DraftSourceId.GRAMMAR_PARSER, result.source, "the parser's own auditable span data must win, not the model's")
        assertEquals(listOf(0..3), result.consumed)
        // Before Sprint 8, agreement was announced with a ⚠ finding and the
        // model's own InferenceReport was discarded outright — neither
        // happens any more; the trace is the one place this is recorded now.
        assertTrue(result.findings.none { it.message.contains("agree") }, "no WARNING finding any more — the trace's verdict is the record")
        val trace = checkNotNull(result.trace)
        assertEquals(DraftVerdict.AGREED, trace.verdict)
        val modelAttempt = trace.attempts.single { it.source == DraftSourceId.ON_DEVICE_LLM }
        assertEquals(report, modelAttempt.inferenceReport, "the model's report must survive even though the parser's copy won")
    }

    @Test fun `one drafter failing falls through to the other's answer`() = runTest {
        val ok = DraftResult.Drafted(DraftSourceId.GRAMMAR_PARSER, Fixtures.heroRoutine())
        val failed = DraftResult.Failed(DraftSourceId.ON_DEVICE_LLM, "not wired up")
        val result = assertIs<DraftResult.Drafted>(DifferentialDrafter(drafter(failed), drafter(ok)).draft("cue"))
        assertEquals(DraftSourceId.GRAMMAR_PARSER, result.source)
        assertEquals(DraftVerdict.PARSER_ONLY, checkNotNull(result.trace).verdict)
    }

    @Test fun `both drafters failing is reported, not thrown`() = runTest {
        val a = DraftResult.Failed(DraftSourceId.ON_DEVICE_LLM, "crashed")
        val b = DraftResult.Failed(DraftSourceId.GRAMMAR_PARSER, "no trigger")
        val result = DifferentialDrafter(drafter(a), drafter(b)).draft("cue")
        val failed = assertIs<DraftResult.Failed>(result)
        assertEquals(DraftVerdict.NEITHER, checkNotNull(failed.trace).verdict)
    }

    @Test fun `a model draft that fails independent validation is not trusted, but its report is kept`() = runTest {
        // An empty-actions routine fails Validator ("a cue that does nothing
        // is rejected"). The model does not get to vouch for its own output
        // just because the parser also has nothing to say.
        val report = InferenceReport(InferenceBackend.CPU, loadMs = 300, generationMs = 100, estimatedTokens = 4)
        val invalidModelDraft = DraftResult.Drafted(
            DraftSourceId.ON_DEVICE_LLM,
            Fixtures.heroRoutine().copy(actions = emptyList()),
            inferenceReport = report,
        )
        val parserFailed = DraftResult.Failed(DraftSourceId.GRAMMAR_PARSER, "no trigger")
        val result = DifferentialDrafter(drafter(invalidModelDraft), drafter(parserFailed)).draft("cue")
        val failed = assertIs<DraftResult.Failed>(result)
        val trace = checkNotNull(failed.trace)
        val modelAttempt = trace.attempts.single { it.source == DraftSourceId.ON_DEVICE_LLM }
        assertEquals(AttemptOutcome.INVALID, modelAttempt.outcome)
        assertEquals(DraftReason.VALIDATOR_REJECTED, modelAttempt.reasonCode)
        assertEquals(report, modelAttempt.inferenceReport, "the model ran and that cost is real even though its output isn't trusted")
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

    @Test fun `a drafter that hangs past the timeout is treated as failed and recorded as TIMED_OUT`() = runTest {
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
        val trace = checkNotNull(result.trace)
        val modelAttempt = trace.attempts.single { it.source == DraftSourceId.ON_DEVICE_LLM }
        assertEquals(AttemptOutcome.TIMED_OUT, modelAttempt.outcome)
        assertEquals(DraftReason.TIMEOUT_GENERATE, modelAttempt.reasonCode)
        assertNull(modelAttempt.inferenceReport, "a timed-out call never returned, so there is nothing to have carried a report")
    }

    @Test fun `an unavailable model is skipped outright, never asked and never timed out`() = runTest {
        var wasCalled = false
        val model = object : RoutineDrafter {
            override val id = DraftSourceId.ON_DEVICE_LLM
            override suspend fun draft(text: String): DraftResult {
                wasCalled = true
                return DraftResult.Drafted(id, Fixtures.heroRoutine())
            }
        }
        val ok = DraftResult.Drafted(DraftSourceId.GRAMMAR_PARSER, Fixtures.heroRoutine())
        val result = assertIs<DraftResult.Drafted>(
            DifferentialDrafter(model, drafter(ok), firstAvailability = { ModelAvailability.INSTALLED_OFF }).draft("cue"),
        )
        assertEquals(DraftSourceId.GRAMMAR_PARSER, result.source)
        assertTrue(!wasCalled, "an unavailable model must never be asked at all")
        val trace = checkNotNull(result.trace)
        assertEquals(DraftVerdict.PARSER_ONLY, trace.verdict)
        val modelAttempt = trace.attempts.single { it.source == DraftSourceId.ON_DEVICE_LLM }
        assertEquals(AttemptOutcome.SKIPPED_UNAVAILABLE, modelAttempt.outcome)
        assertEquals(DraftReason.MODEL_UNAVAILABLE_OFF, modelAttempt.reasonCode)
    }

    @Test fun `a not-installed model is skipped with its own reason code`() = runTest {
        val model = object : RoutineDrafter {
            override val id = DraftSourceId.ON_DEVICE_LLM
            override suspend fun draft(text: String): DraftResult = DraftResult.Drafted(id, Fixtures.heroRoutine())
        }
        val ok = DraftResult.Drafted(DraftSourceId.GRAMMAR_PARSER, Fixtures.heroRoutine())
        val result = assertIs<DraftResult.Drafted>(
            DifferentialDrafter(model, drafter(ok), firstAvailability = { ModelAvailability.NOT_INSTALLED }).draft("cue"),
        )
        val modelAttempt = checkNotNull(result.trace).attempts.single { it.source == DraftSourceId.ON_DEVICE_LLM }
        assertEquals(DraftReason.MODEL_UNAVAILABLE_NOT_INSTALLED, modelAttempt.reasonCode)
    }
}
