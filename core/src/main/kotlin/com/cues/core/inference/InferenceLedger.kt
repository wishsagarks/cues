package com.cues.core.inference

import com.cues.core.model.DraftSourceId
import kotlinx.serialization.Serializable

/**
 * How a call's [InferenceLedgerEntry.costUsd] was arrived at.
 *
 * [UNVERIFIED] is deliberately not the same as "$0.00" — a Sarvam call with
 * no verified rate on file shows "cost not verified" in the UI, never a
 * fabricated free figure. See `docs/API_VERIFICATION.md`'s "Sarvam AI
 * pricing" entry.
 */
enum class CostBasis {
    /** A real fact, not an estimate: no network call happened, so there is nothing to have cost anything. */
    ON_DEVICE_FREE,

    /** [InferenceCost.sarvamCostPer1kTokensUsd] was set from a real, recorded rate when this entry was written. */
    CLOUD_METERED,

    /** No verified rate existed yet when this entry was written. */
    UNVERIFIED,
}

/**
 * One inference call, kept for the analytics tab's token/cost section —
 * separate from [com.cues.core.coach.LedgerEvent] on purpose: this is cost
 * accounting, not signal-pattern learning, so it gets its own opt-in and its
 * own retention window (see [InferenceLedger]'s doc comment).
 */
@Serializable
data class InferenceLedgerEntry(
    val atMillis: Long,
    val source: DraftSourceId,
    val backend: InferenceBackend,
    val estimatedTokens: Int,
    val latencyMs: Long,
    val costUsd: Double,
    val costBasis: CostBasis,
)

/**
 * Persistence for [InferenceLedgerEntry] — a separate opt-in from
 * [com.cues.core.coach.UsageLedger]'s `signalOptIn`, off by default, and a
 * longer retention window than that ledger's 14 days: 30, matching
 * `InsightsWindow.LAST_30_DAYS` in `core/.../insights/Insights.kt`, so the
 * longest window the tab offers is never silently under-reported by a
 * shorter-lived ledger.
 */
interface InferenceLedger {
    val usageTrackingEnabled: Boolean
    fun setUsageTrackingEnabled(enabled: Boolean)
    fun appendInference(entry: InferenceLedgerEntry)
    fun inferenceEntries(): List<InferenceLedgerEntry>
    fun wipeInferenceLedger()
}
