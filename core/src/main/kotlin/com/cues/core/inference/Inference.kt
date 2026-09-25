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
    /** Estimated by splitting the response on whitespace, not the model's own tokenizer. */
    val estimatedTokens: Int,
) {
    /** Derived, not stored, so this can never silently disagree with [estimatedTokens]/[generationMs]. */
    val tokensPerSecond: Double get() = estimatedTokens * 1_000.0 / generationMs.coerceAtLeast(1)
}
