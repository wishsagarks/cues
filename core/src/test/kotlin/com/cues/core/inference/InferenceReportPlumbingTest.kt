package com.cues.core.inference

import com.cues.core.CueService
import com.cues.core.Fixtures
import com.cues.core.assistant.Conversation
import com.cues.core.assistant.ReplyCode
import com.cues.core.drafting.DraftResult
import com.cues.core.drafting.RoutineDrafter
import com.cues.core.model.DraftSourceId
import com.cues.core.ports.CapabilityProvider
import com.cues.core.session.FakeClock
import com.cues.core.session.RecordingExecutor
import com.cues.core.store.JsonFileStore
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * A minimal stand-in for a real model-backed drafter (see
 * [com.cues.core.drafting.OnDeviceLlmDrafter] for the genuine one, which
 * takes an injectable [com.cues.core.drafting.LlmSession] and so is fakeable
 * on its own — this class exists only to keep this test's focus on
 * [CueService]'s plumbing, not drafting itself). What's `:core`'s to prove
 * here is the contract everything above a real drafter depends on: an
 * [InferenceReport] a drafter attaches to a [DraftResult.Drafted] survives
 * into [CueService.Diagnostics] and into the conversation turn that
 * displayed it, and a parser-only draft claims no backend at all.
 */
private class FakeModelDrafter(private val report: InferenceReport) : RoutineDrafter {
    override val id: DraftSourceId = DraftSourceId.ON_DEVICE_LLM
    override suspend fun draft(text: String): DraftResult = DraftResult.Drafted(
        source = id,
        routine = Fixtures.heroRoutine().copy(draftedBy = id),
        inferenceReport = report,
    )
}

class InferenceReportPlumbingTest {

    @Test
    fun `a model-backed draft's backend survives into diagnostics and the turn's reply`() = runTest {
        val root = createTempDirectory("inference-report-test").toFile()
        try {
            val store = JsonFileStore(root)
            val report = InferenceReport(InferenceBackend.NPU, loadMs = 1200, generationMs = 340, estimatedTokens = 6)
            val service = CueService(
                store, store, store, RecordingExecutor(), FakeClock(Fixtures.NOW),
                CapabilityProvider { emptySet() }, FakeModelDrafter(report),
            )

            assertNull(service.diagnostics().lastInferenceReport, "nothing has run yet")

            val turn = service.converse(Conversation(), "start a 45 minute focus when my earbuds connect")

            assertEquals(ReplyCode.DRAFT_READY, turn.reply.code)
            assertEquals("NPU", turn.reply.args["backend"])
            assertEquals(report, service.diagnostics().lastInferenceReport)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `a parser-only draft claims no backend`() = runTest {
        val root = createTempDirectory("inference-report-parser-test").toFile()
        try {
            val store = JsonFileStore(root)
            val service = CueService(
                store, store, store, RecordingExecutor(), FakeClock(Fixtures.NOW),
                CapabilityProvider { emptySet() }, com.cues.core.drafting.GrammarParser(),
            )

            val turn = service.converse(Conversation(), "start a 45 minute focus when my earbuds connect")

            assertEquals(null, turn.reply.args["backend"])
            assertNull(service.diagnostics().lastInferenceReport)
        } finally {
            root.deleteRecursively()
        }
    }
}
