package com.cues.app.drafting

import com.cues.app.net.SarvamClient
import com.cues.core.drafting.DraftResult
import com.cues.core.drafting.GrammarParser
import com.cues.core.drafting.InferenceOutput
import com.cues.core.drafting.RoutineDrafter
import com.cues.core.inference.InferenceBackend
import com.cues.core.inference.InferenceReport
import com.cues.core.model.DraftSourceId

/**
 * A single cloud chat completion, kept behind this port for the same reason
 * [com.cues.core.drafting.LlmSession] exists: [SarvamChatDrafter] never
 * depends on which HTTP client or network is behind it, so it's fakeable in
 * tests without a real call. Returns the shared [InferenceOutput] type, the
 * same as the on-device path, so a Sarvam-drafted routine carries a real
 * [InferenceReport] too (backend [InferenceBackend.CLOUD]) instead of the gap
 * that existed before: every other drafter attached one, this one didn't.
 */
fun interface SarvamChatSession {
    suspend fun complete(prompt: String): InferenceOutput
}

/** The honest default when no Sarvam API key is configured — see [com.cues.core.drafting.UnconfiguredLlmSession]'s identical role. */
class UnconfiguredSarvamChatSession : SarvamChatSession {
    override suspend fun complete(prompt: String): InferenceOutput =
        throw IllegalStateException("Cloud assist is not configured (no Sarvam API key).")
}

/** For bake-offs and tests: a fixed answer and an optional, explicit report, no network. */
class FakeSarvamChatSession(
    private val answer: String,
    private val report: InferenceReport = InferenceReport(InferenceBackend.CLOUD, loadMs = 0, generationMs = 0, estimatedTokens = 0),
) : SarvamChatSession {
    override suspend fun complete(prompt: String): InferenceOutput = InferenceOutput(answer, report)
}

/**
 * The real backend: one Sarvam chat completion call per draft, timed here so
 * the resulting [InferenceReport] reflects this call's own latency — never a
 * device tier, since nothing about this call ran on the phone.
 */
class SarvamHttpChatSession(private val client: SarvamClient) : SarvamChatSession {
    override suspend fun complete(prompt: String): InferenceOutput {
        val start = System.nanoTime()
        val text = client.chat(prompt)
        val elapsedMs = ((System.nanoTime() - start) / 1_000_000).coerceAtLeast(1)
        val tokens = text.trim().split(Regex("\\s+")).count { it.isNotBlank() }.coerceAtLeast(1)
        return InferenceOutput(
            text = text,
            report = InferenceReport(InferenceBackend.CLOUD, loadMs = 0, generationMs = elapsedMs, estimatedTokens = tokens),
        )
    }
}

/**
 * Proposes a routine by asking Sarvam's cloud chat model for one plain-English
 * sentence in the closed grammar, then handing that sentence to [GrammarParser] — exactly
 * [com.cues.core.drafting.OnDeviceLlmDrafter]'s pattern, on purpose. The model's own words are never
 * trusted as structure, only as candidate prose for the same deterministic
 * compiler every other draft goes through, and [DifferentialDrafter.guarded]
 * (or the app-level cloud-assist fallback that calls this drafter directly)
 * independently validates the result before it can reach Review.
 *
 * When enabled, this can be the default drafting path; an invalid or failed
 * cloud response still falls back to the local parser/Gemma path.
 */
class SarvamChatDrafter(
    private val session: SarvamChatSession = UnconfiguredSarvamChatSession(),
    private val parser: GrammarParser = GrammarParser(),
) : RoutineDrafter {

    override val id: DraftSourceId = DraftSourceId.SARVAM_CLOUD

    override suspend fun draft(text: String): DraftResult = try {
        val output = session.complete(prompt(text))
        when (val parsed = parser.parse(cleanCandidate(output.text))) {
            is DraftResult.Drafted -> parsed.copy(
                source = id, consumed = emptyList(), clauses = emptyList(), inferenceReport = output.report,
            )
            is DraftResult.NeedsClarification -> parsed.copy(
                source = id, consumed = emptyList(), clauses = emptyList(), inferenceReport = output.report,
            )
            is DraftResult.Failed -> parsed.copy(source = id, inferenceReport = output.report)
        }
    } catch (e: Exception) {
        DraftResult.Failed(id, e.message ?: "Cloud assist could not produce a draft.")
    }

    private fun prompt(text: String): String = """
        You are Cues' cue translator. Return exactly one plain-English sentence
        that the Cues grammar parser can understand. Preserve the user's meaning.
        Never return code, JSON, labels, semicolons, a DSL, explanations, or markdown.
        Use the same style as these valid examples:
        - when my charger connects, silence notifications for 10 minutes
        - when my earbuds connect, start a focus timer for 25 minutes
        - when I enter the office, silence notifications until I leave
        - every weekday at 9 AM, remind me to start a focus timer for 25 minutes
        - while I am in the office, silence notifications until I leave
        User request: $text
    """.trimIndent()

    private fun cleanCandidate(raw: String): String = raw
        .replace("```", "")
        .trim()
        .removePrefix("Output:")
        .removePrefix("Cue:")
        .trim()
        .removeSurrounding("\"", "\"")
        .removeSurrounding("'", "'")
        .trim()
}
