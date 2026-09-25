package com.cues.app.devkit

import com.cues.core.inference.CostBasis
import com.cues.core.inference.InferenceBackend
import com.cues.core.inference.InferenceCost
import com.cues.core.model.DraftSourceId
import java.util.Collections

/** One successful call through the developer-facing Gemma surface. */
data class ExternalCallEntry(
    val atMillis: Long,
    val callerPackage: String,
    val backend: InferenceBackend,
    val estimatedTokens: Int,
    val latencyMs: Long,
)

/**
 * Per-caller usage for the developer-facing Gemma surface — a **sibling**
 * type to [com.cues.core.inference.InferenceLedger], never an extension of
 * it: this tracks who outside Cues called in, not Cues' own authoring-time
 * drafting cost accounting, and lives entirely in `:app` since it never
 * needs to cross the trust boundary `:core` enforces for cue authoring.
 *
 * In-memory only for now (see CLEANUP.md CL-38) — cleared on process death,
 * the same "disclose, don't fake" discipline the rest of this codebase
 * already keeps: a half-built persistence layer that has never been
 * exercised would be a worse claim than an in-memory one that is exactly
 * what it appears to be.
 */
class ExternalCallerLedger {
    private val entries = Collections.synchronizedList(mutableListOf<ExternalCallEntry>())

    fun record(entry: ExternalCallEntry) {
        entries.add(entry)
        while (entries.size > MAX_ENTRIES) entries.removeAt(0)
    }

    fun recentEntries(): List<ExternalCallEntry> = entries.toList().asReversed()

    /**
     * What [callerPackage]'s on-device tokens would have cost on Sarvam's
     * rate — reuses [InferenceCost.costFor], the one place a $/token
     * assumption is allowed to exist, never a second formula. `UNVERIFIED`
     * until a real Sarvam rate is pinned (CLEANUP.md CL-37), exactly like
     * every other cost figure in this app.
     */
    fun savedForCaller(callerPackage: String): Pair<Double, CostBasis> {
        val tokens = entries.filter { it.callerPackage == callerPackage }.sumOf { it.estimatedTokens }
        return InferenceCost.costFor(DraftSourceId.SARVAM_CLOUD, tokens)
    }

    private companion object {
        const val MAX_ENTRIES = 200
    }
}
