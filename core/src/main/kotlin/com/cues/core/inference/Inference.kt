package com.cues.core.inference

/**
 * Which tier a model run actually loaded on, in the order Cues tries the
 * on-device tiers: NPU first, then GPU, then CPU. Never asserted from a
 * device's specs or a plan — only from what the runtime accepted without
 * throwing when asked to load at that tier. See [InferenceReport]'s own doc
 * comment for what that promise does and does not cover.
 *
 * [CLOUD] is not a local tier at all — it labels a call that ran on Sarvam's
 * servers (CLEANUP.md CL-35), kept in this same enum so every drafter can
 * attach one [InferenceReport] regardless of where it ran, but never
 * conflated with NPU/GPU/CPU: a cloud call proves nothing about the phone's
 * own inference chain.
 */
enum class InferenceBackend { NPU, GPU, CPU, CLOUD }

/**
 * What actually happened the last time the on-device model ran — the whole
 * reason this exists is SPRINT_4_5's rule: "No NPU claims unless the runtime
 * reports that it used the NPU."
 *
 * A caveat that rule doesn't fully resolve: the LiteRT-LM Kotlin API this
 * project uses exposes no separate "which backend actually executed" query —
 * `backend` here is the tier whose `Engine.initialize()` call returned
 * without throwing, which is the strongest signal the public API offers, not
 * an independent read-back the way `AndroidActionExecutor`'s other
 * acquire-then-verify checks are. See CLEANUP.md CL-18.
 */
data class InferenceReport(
    val backend: InferenceBackend,
    val loadMs: Long,
    val generationMs: Long,
    /**
     * The model's real prefill+decode count when [tokenCountSource] is
     * [TokenCountSource.MEASURED] (Sprint 8, from LiteRT-LM 0.16.1's
     * `Conversation.getBenchmarkInfo()`); the pre-Sprint-8 whitespace-split
     * guess otherwise, kept as a fallback for a runtime that doesn't expose
     * benchmark info (e.g. a cloud call).
     */
    val estimatedTokens: Int,
    /** Whether [estimatedTokens] (and the fields below) came from the model's own count, or is still the whitespace-split guess. */
    val tokenCountSource: TokenCountSource = TokenCountSource.ESTIMATED,
    /** Seconds-to-milliseconds from `BenchmarkInfo.timeToFirstTokenInSecond`. Null when not measured. */
    val timeToFirstTokenMs: Long? = null,
    val prefillTokens: Int? = null,
    val decodeTokens: Int? = null,
    val decodeTokensPerSecond: Double? = null,
) {
    /** Derived, not stored, so this can never silently disagree with [estimatedTokens]/[generationMs]. */
    val tokensPerSecond: Double get() = estimatedTokens * 1_000.0 / generationMs.coerceAtLeast(1)
}

/** Whether [InferenceReport.estimatedTokens] is the model's own reported count, or a whitespace-split guess. Never shown as the same confidence in the UI (CLEANUP.md CL-18). */
enum class TokenCountSource { MEASURED, ESTIMATED }
