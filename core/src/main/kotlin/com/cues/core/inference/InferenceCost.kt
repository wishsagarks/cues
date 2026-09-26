package com.cues.core.inference

import com.cues.core.model.DraftSourceId

/**
 * The one place a $/token assumption is allowed to exist.
 *
 * [sarvamCostPer1kTokensUsd] stays `null` — every Sarvam call in the ledger
 * shows [CostBasis.UNVERIFIED] ("cost not verified"), never a fabricated
 * `$0.00` or an invented rate — until a real per-token or per-call price is
 * read from Sarvam's own published pricing and recorded in
 * `docs/API_VERIFICATION.md`'s "Sarvam AI pricing" entry, the same "record
 * the source, never guess" discipline as the rest of this codebase.
 */
object InferenceCost {
    val sarvamCostPer1kTokensUsd: Double? = null

    /** On-device cost is always, structurally, exactly $0 — real, not estimated, since no network call happened. */
    fun costFor(source: DraftSourceId, estimatedTokens: Int): Pair<Double, CostBasis> = when (source) {
        DraftSourceId.ON_DEVICE_LLM,
        DraftSourceId.EXTERNAL_GEMMA_CALL,
        DraftSourceId.SYSTEM_AGENT_CALL -> 0.0 to CostBasis.ON_DEVICE_FREE
        DraftSourceId.SARVAM_CLOUD -> sarvamCostPer1kTokensUsd
            ?.let { rate -> (rate * estimatedTokens / 1000.0) to CostBasis.CLOUD_METERED }
            ?: (0.0 to CostBasis.UNVERIFIED)
        DraftSourceId.GRAMMAR_PARSER, DraftSourceId.IMPORTED_CARD -> 0.0 to CostBasis.ON_DEVICE_FREE
    }
}
