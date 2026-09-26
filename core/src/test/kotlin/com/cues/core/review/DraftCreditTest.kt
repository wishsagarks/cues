package com.cues.core.review

import com.cues.core.drafting.AttemptOutcome
import com.cues.core.drafting.DraftReason
import com.cues.core.drafting.DraftTrace
import com.cues.core.drafting.DraftVerdict
import com.cues.core.drafting.DrafterAttempt
import com.cues.core.inference.InferenceBackend
import com.cues.core.inference.InferenceReport
import com.cues.core.model.DraftSourceId
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

/**
 * The exhaustive table this file exists to prove: every combination of
 * verdict/outcome the UI can be handed renders to exactly one label, and a
 * backend is only ever taken from a real [InferenceReport] — never from
 * eligibility, never fabricated (CLEANUP.md CL-18).
 */
class DraftCreditTest {

    private val gpuReport = InferenceReport(InferenceBackend.GPU, loadMs = 400, generationMs = 200, estimatedTokens = 10)

    @Test fun `no trace at all is a single-drafter setup, plainly labelled`() {
        val credit = DraftCredit.credit(trace = null, draftedBy = DraftSourceId.GRAMMAR_PARSER)
        assertEquals("grammar parser", credit.label())
        assertNull(credit.backend)
    }

    @Test fun `a raw model draft with no trace is still labelled as model-backed`() {
        val credit = DraftCredit.credit(trace = null, draftedBy = DraftSourceId.ON_DEVICE_LLM)
        assertEquals("on-device model · independently validated", credit.label())
    }

    @Test fun `agreement credits the parser and names the model that confirmed it`() {
        val trace = DraftTrace(
            listOf(
                DrafterAttempt(DraftSourceId.ON_DEVICE_LLM, AttemptOutcome.DRAFTED, elapsedMillis = 500, inferenceReport = gpuReport),
                DrafterAttempt(DraftSourceId.GRAMMAR_PARSER, AttemptOutcome.DRAFTED, elapsedMillis = 5),
            ),
            DraftVerdict.AGREED,
        )
        val credit = DraftCredit.credit(trace, DraftSourceId.GRAMMAR_PARSER)
        assertEquals("grammar parser · confirmed by on-device model", credit.label())
        assertEquals(InferenceBackend.GPU, credit.backend)
    }

    @Test fun `model-only success is independently validated, with its real backend`() {
        val trace = DraftTrace(
            listOf(
                DrafterAttempt(DraftSourceId.ON_DEVICE_LLM, AttemptOutcome.DRAFTED, elapsedMillis = 500, inferenceReport = gpuReport),
                DrafterAttempt(DraftSourceId.GRAMMAR_PARSER, AttemptOutcome.FAILED, "no trigger", elapsedMillis = 5),
            ),
            DraftVerdict.MODEL_ONLY,
        )
        val credit = DraftCredit.credit(trace, DraftSourceId.ON_DEVICE_LLM)
        assertEquals("on-device model · independently validated", credit.label())
        assertEquals(InferenceBackend.GPU, credit.backend)
    }

    @Test fun `parser-only with the model off says exactly that, never a fabricated backend`() {
        val trace = DraftTrace(
            listOf(
                DrafterAttempt(DraftSourceId.ON_DEVICE_LLM, AttemptOutcome.SKIPPED_UNAVAILABLE, DraftReason.MODEL_UNAVAILABLE_OFF, elapsedMillis = 0),
                DrafterAttempt(DraftSourceId.GRAMMAR_PARSER, AttemptOutcome.DRAFTED, elapsedMillis = 5),
            ),
            DraftVerdict.PARSER_ONLY,
        )
        val credit = DraftCredit.credit(trace, DraftSourceId.GRAMMAR_PARSER)
        assertEquals("grammar parser · model off", credit.label())
        assertNull(credit.backend)
    }

    @Test fun `parser-only with no model installed is distinguished from off`() {
        val trace = DraftTrace(
            listOf(
                DrafterAttempt(DraftSourceId.ON_DEVICE_LLM, AttemptOutcome.SKIPPED_UNAVAILABLE, DraftReason.MODEL_UNAVAILABLE_NOT_INSTALLED, elapsedMillis = 0),
                DrafterAttempt(DraftSourceId.GRAMMAR_PARSER, AttemptOutcome.DRAFTED, elapsedMillis = 5),
            ),
            DraftVerdict.PARSER_ONLY,
        )
        val credit = DraftCredit.credit(trace, DraftSourceId.GRAMMAR_PARSER)
        assertEquals("grammar parser · no model installed", credit.label())
    }

    @Test fun `parser-only after a model timeout says so`() {
        val trace = DraftTrace(
            listOf(
                DrafterAttempt(DraftSourceId.ON_DEVICE_LLM, AttemptOutcome.TIMED_OUT, DraftReason.TIMEOUT_GENERATE, elapsedMillis = 4000),
                DrafterAttempt(DraftSourceId.GRAMMAR_PARSER, AttemptOutcome.DRAFTED, elapsedMillis = 5),
            ),
            DraftVerdict.PARSER_ONLY,
        )
        val credit = DraftCredit.credit(trace, DraftSourceId.GRAMMAR_PARSER)
        assertEquals("grammar parser · model timed out", credit.label())
    }

    @Test fun `parser-only after an invalid model draft says so`() {
        val trace = DraftTrace(
            listOf(
                DrafterAttempt(DraftSourceId.ON_DEVICE_LLM, AttemptOutcome.INVALID, DraftReason.VALIDATOR_REJECTED, elapsedMillis = 300, inferenceReport = gpuReport),
                DrafterAttempt(DraftSourceId.GRAMMAR_PARSER, AttemptOutcome.DRAFTED, elapsedMillis = 5),
            ),
            DraftVerdict.PARSER_ONLY,
        )
        val credit = DraftCredit.credit(trace, DraftSourceId.GRAMMAR_PARSER)
        assertEquals("grammar parser · model's draft didn't validate", credit.label())
        // The model's report doesn't surface on the parser's own credit — it
        // still lives on the trace's own attempt for whoever reads that.
        assertNull(credit.backend)
    }

    @Test fun `a candidate chosen from a disagreement is credited as the user's own pick`() {
        val trace = DraftTrace(
            listOf(
                DrafterAttempt(DraftSourceId.ON_DEVICE_LLM, AttemptOutcome.DRAFTED, elapsedMillis = 500, inferenceReport = gpuReport),
                DrafterAttempt(DraftSourceId.GRAMMAR_PARSER, AttemptOutcome.DRAFTED, elapsedMillis = 5),
            ),
            DraftVerdict.DISAGREED,
        )
        val credit = DraftCredit.credit(trace, DraftSourceId.ON_DEVICE_LLM, chosenFromDisagreement = true)
        assertEquals("on-device model · chosen by you from a disagreement", credit.label())
        assertEquals(InferenceBackend.GPU, credit.backend)
    }

    @Test fun `an imported card never gets the model-backed independently-validated suffix`() {
        val credit = DraftCredit.credit(trace = null, draftedBy = DraftSourceId.IMPORTED_CARD)
        assertEquals("imported card", credit.label())
    }

    @Test fun `cloud assist is labelled distinctly from the on-device model`() {
        val cloudReport = InferenceReport(InferenceBackend.CLOUD, loadMs = 0, generationMs = 900, estimatedTokens = 30)
        val trace = DraftTrace(listOf(DrafterAttempt(DraftSourceId.SARVAM_CLOUD, AttemptOutcome.DRAFTED, elapsedMillis = 900, inferenceReport = cloudReport)), DraftVerdict.SINGLE)
        val credit = DraftCredit.credit(trace, DraftSourceId.SARVAM_CLOUD)
        assertEquals("cloud assist · independently validated", credit.label())
        assertEquals(InferenceBackend.CLOUD, credit.backend)
    }
}
