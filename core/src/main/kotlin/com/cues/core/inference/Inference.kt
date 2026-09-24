package com.cues.core.inference

/**
 * Which tier a local-model run actually loaded on, in the order Cues tries
 * them: NPU first, then GPU, then CPU. Never asserted from a device's specs
 * or a plan — only from what the runtime accepted without throwing when
 * asked to load at that tier. See [InferenceReport]'s own doc comment for
 * what that promise does and does not cover.
 */
enum class InferenceBackend { NPU, GPU, CPU }

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
    /** Estimated by splitting the response on whitespace, not the model's own token count. */
    val tokensPerSecond: Double,
)
