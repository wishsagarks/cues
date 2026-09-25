package com.cues.app.drafting

import com.cues.app.net.SarvamClient
import com.cues.core.drafting.DraftResult
import com.cues.core.drafting.GrammarParser
import com.cues.core.drafting.RoutineDrafter
import com.cues.core.model.DraftSourceId

/**
 * A single cloud chat completion, kept behind this port for the same reason
 * [LlmSession] exists: [SarvamChatDrafter] never depends on which HTTP client
 * or network is behind it, so it's fakeable in tests without a real call.
 */
fun interface SarvamChatSession {
    suspend fun complete(prompt: String): String
}

/** The honest default when no Sarvam API key is configured — see [UnconfiguredLlmSession]'s identical role. */
class UnconfiguredSarvamChatSession : SarvamChatSession {
    override suspend fun complete(prompt: String): String =
        throw IllegalStateException("Cloud assist is not configured (no Sarvam API key).")
}

/** For bake-offs and tests: a fixed answer, no network. */
class FakeSarvamChatSession(private val answer: String) : SarvamChatSession {
    override suspend fun complete(prompt: String): String = answer
}

/** The real backend: one Sarvam chat completion call per draft. */
class SarvamHttpChatSession(private val client: SarvamClient) : SarvamChatSession {
    override suspend fun complete(prompt: String): String = client.chat(prompt)
}

/**
 * Proposes a routine by asking Sarvam's cloud chat model for one sentence in
 * the closed grammar, then handing that sentence to [GrammarParser] — exactly
 * [OnDeviceLlmDrafter]'s pattern, on purpose. The model's own words are never
 * trusted as structure, only as candidate prose for the same deterministic
 * compiler every other draft goes through, and [DifferentialDrafter.guarded]
 * (or the app-level cloud-assist fallback that calls this drafter directly)
 * independently validates the result before it can reach Review.
 *
 * This is not part of the default drafting pipeline. It is consulted only
 * when the user has opted into cloud assist and the offline drafters
 * disagreed or both failed — see CuesApplication's `tryCloudAssist` and
 * CLEANUP.md CL-35.
 */
class SarvamChatDrafter(
    private val session: SarvamChatSession = UnconfiguredSarvamChatSession(),
    private val parser: GrammarParser = GrammarParser(),
) : RoutineDrafter {

    override val id: DraftSourceId = DraftSourceId.SARVAM_CLOUD

    override suspend fun draft(text: String): DraftResult = try {
        val answer = session.complete(prompt(text))
        when (val parsed = parser.parse(answer)) {
            is DraftResult.Drafted -> parsed.copy(source = id, consumed = emptyList(), clauses = emptyList())
            is DraftResult.NeedsClarification -> parsed.copy(source = id, consumed = emptyList(), clauses = emptyList())
            is DraftResult.Failed -> parsed.copy(source = id)
        }
    } catch (e: Exception) {
        DraftResult.Failed(id, e.message ?: "Cloud assist could not produce a draft.")
    }

    private fun prompt(text: String): String =
        "Return one Cues request using only supported trigger, condition, action and ending vocabulary. Request: $text"
}
