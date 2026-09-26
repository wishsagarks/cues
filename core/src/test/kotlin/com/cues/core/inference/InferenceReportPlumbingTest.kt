package com.cues.core.inference

import com.cues.core.CueService
import com.cues.core.Fixtures
import com.cues.core.assistant.Conversation
import com.cues.core.assistant.ReplyCode
import com.cues.core.assistant.ReplySource
import com.cues.core.drafting.AttemptOutcome
import com.cues.core.drafting.DraftResult
import com.cues.core.drafting.DraftVerdict
import com.cues.core.drafting.RoutineDrafter
import com.cues.core.model.DraftSourceId
import com.cues.core.ports.CapabilityProvider
import com.cues.core.session.FakeClock
import com.cues.core.session.RecordingExecutor
import com.cues.core.store.JsonFileStore
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
 * into [CueService.lastTrace] and into the conversation turn that displayed
 * it, and a parser-only draft claims no backend at all.
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
    fun `a model-backed draft's backend survives into a trace and the turn's reply`() = runTest {
        val root = createTempDirectory("inference-report-test").toFile()
        try {
            val store = JsonFileStore(root)
            val report = InferenceReport(InferenceBackend.NPU, loadMs = 1200, generationMs = 340, estimatedTokens = 6)
            val service = CueService(
                store, store, store, RecordingExecutor(), FakeClock(Fixtures.NOW),
                CapabilityProvider { emptySet() }, FakeModelDrafter(report),
            )

            assertNull(service.diagnostics().lastTrace, "nothing has run yet")

            val turn = service.converse(Conversation(), "start a 45 minute focus when my earbuds connect")

            assertEquals(ReplyCode.DRAFT_READY, turn.reply.code)
            assertEquals("NPU", turn.reply.args["backend"])
            // FakeModelDrafter isn't wrapped in a DifferentialDrafter, so it
            // carries no DraftTrace of its own — CueService synthesizes a
            // one-attempt SINGLE trace rather than losing the report, which
            // is exactly the plumbing this test exists to prove.
            val trace = service.diagnostics().lastTrace
            checkNotNull(trace)
            assertEquals(DraftVerdict.SINGLE, trace.verdict)
            assertEquals(1, trace.attempts.size)
            assertEquals(report, trace.attempts.single().inferenceReport)
            assertEquals(AttemptOutcome.DRAFTED, trace.attempts.single().outcome)
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
            assertNull(service.diagnostics().lastTrace, "a plain parser carries no report to synthesize a trace from")
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `an agreeing model attempt's report is ledgered even though the parser's copy wins`() = runTest {
        val root = createTempDirectory("inference-report-differential-test").toFile()
        try {
            val store = JsonFileStore(root)
            store.setUsageTrackingEnabled(true)
            val agreeingModel = FakeModelDrafter(InferenceReport(InferenceBackend.GPU, loadMs = 500, generationMs = 200, estimatedTokens = 12))
            // A fake parser double, not the real GrammarParser: what this
            // test proves is CueService's plumbing, not whether this exact
            // sentence happens to parse the same way as Fixtures.heroRoutine.
            val fakeParser = object : RoutineDrafter {
                override val id = DraftSourceId.GRAMMAR_PARSER
                override suspend fun draft(text: String) = DraftResult.Drafted(id, Fixtures.heroRoutine())
            }
            val drafter = com.cues.core.drafting.DifferentialDrafter(agreeingModel, fakeParser)
            val service = CueService(
                store, store, store, RecordingExecutor(), FakeClock(Fixtures.NOW),
                CapabilityProvider { emptySet() }, drafter, inferenceLedger = store,
            )

            val turn = service.converse(Conversation(), "start a 45 minute focus when my earbuds connect")

            assertEquals(DraftSourceId.GRAMMAR_PARSER, turn.draft?.draftedBy, "the parser's own span data wins on agreement")
            // Before Sprint 8's DraftTrace, an agreeing model attempt's own
            // InferenceReport was discarded outright — the parser's copy won
            // and nothing recorded that the model ran at all.
            val entries = store.inferenceEntries()
            assertEquals(1, entries.size, "the fake parser carries no report of its own — only the model's is ledgered")
            assertEquals(DraftSourceId.ON_DEVICE_LLM, entries.single().source)
            assertEquals(InferenceBackend.GPU, entries.single().backend)
            assertEquals(DraftVerdict.AGREED, entries.single().verdict)
            assertEquals(AttemptOutcome.DRAFTED, entries.single().outcome)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `cloud assist drafts through CueService are ledgered and become a turn`() = runTest {
        val root = createTempDirectory("inference-report-cloud-test").toFile()
        try {
            val store = JsonFileStore(root)
            store.setUsageTrackingEnabled(true)
            val cloudReport = InferenceReport(InferenceBackend.CLOUD, loadMs = 0, generationMs = 900, estimatedTokens = 30)
            val cloud = object : RoutineDrafter {
                override val id = DraftSourceId.SARVAM_CLOUD
                override suspend fun draft(text: String) = DraftResult.Drafted(
                    source = id,
                    routine = Fixtures.heroRoutine().copy(draftedBy = id),
                    inferenceReport = cloudReport,
                )
            }
            val service = CueService(
                store, store, store, RecordingExecutor(), FakeClock(Fixtures.NOW),
                CapabilityProvider { emptySet() }, com.cues.core.drafting.GrammarParser(), inferenceLedger = store,
            )

            val turn = service.draftWithCloud(Conversation(), "start a 45 minute focus when my earbuds connect", cloud)

            assertEquals(ReplyCode.DRAFT_READY, turn.reply.code)
            assertEquals(ReplySource.SARVAM_CLOUD, turn.reply.answeredFrom)
            assertEquals(DraftSourceId.SARVAM_CLOUD, turn.draft?.draftedBy)
            assertTrue(store.inferenceEntries().any { it.source == DraftSourceId.SARVAM_CLOUD && it.backend == InferenceBackend.CLOUD })
        } finally {
            root.deleteRecursively()
        }
    }
}
