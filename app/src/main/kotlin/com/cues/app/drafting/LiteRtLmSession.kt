package com.cues.app.drafting

import android.content.Context
import android.os.Build
import com.cues.core.drafting.InferenceOutput
import com.cues.core.drafting.LlmSession
import com.cues.core.inference.InferenceBackend
import com.cues.core.inference.InferenceReport
import com.cues.core.model.ModelCatalog
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The real on-device runtime: LiteRT-LM, a side-loaded `.litertlm` model, and
 * an explicit NPU -> GPU -> CPU fallback chain — now a thin [LlmSession]
 * adapter over [LocalModelRunner] for [generate], which owns the actual
 * [com.google.ai.edge.litertlm.Engine] and keeps it warm across calls
 * (Sprint 8; see [LocalModelRunner]'s own doc comment for exactly what that
 * changed — engine reuse, real cancellation via `cancelProcess()`, and real
 * token counts from `getBenchmarkInfo()` instead of a whitespace-split guess).
 * [probeNpuOnce] stays a separate, one-shot [Engine] call below — see its own
 * doc comment for why it deliberately does not go through [LocalModelRunner].
 *
 * WRITTEN AGAINST REAL APIs, VERIFIED ON NOTHING — the same discipline
 * [com.cues.app.runtime.AndroidActionExecutor] already carries; this class
 * cannot be exercised against a real model in the environment that wrote it.
 * See CLEANUP.md CL-18 for exactly what remains unverified and why.
 *
 * No model ships in the APK and none is ever downloaded here — [modelPath]
 * must already exist (a side-load, or [ModelDownloader]'s own install)
 * before this does anything. A missing file is an immediate, honest failure:
 * a stub that returned a plausible routine would make the model look like
 * it was working, which is the one outcome worth avoiding.
 */
class LiteRtLmSession(
    private val context: Context,
    private val modelPath: String,
    private val supportedNpuSocModels: Set<String> = DEFAULT_NPU_SOC_MODELS,
) : LlmSession {

    /**
     * Exposed so `:app` can observe [LocalModelRunner.state] and call
     * [LocalModelRunner.warm]/[LocalModelRunner.release] directly (pre-warm
     * on opening Ask, release on toggle-off or low memory), not only through
     * [generate].
     */
    val runner = LocalModelRunner(context, File(modelPath), supportedNpuSocModels)

    override suspend fun generate(prompt: String): InferenceOutput = runner.generate(prompt)

    /**
     * A manual, explicitly user-triggered probe: forces [InferenceBackend.NPU]
     * through a genuine, one-shot `Engine.initialize()` attempt, timed the
     * same way [generate]'s tiers are — but skipping [npuSocMatches]'s
     * allow-list gate that [generate] (via [runner]) always respects.
     *
     * This is the one deliberate exception to that gate, and it exists for
     * one reason: [ModelCatalog] can only ever be *extended* by a real,
     * on-device result, and someone has to be able to produce that result
     * without editing code first. [generate] must stay conservative because
     * it runs unattended, inside real cue drafts, where a wrong guess about
     * NPU support degrades a feature the person didn't ask to test. This
     * function is the opposite: it runs only when a person deliberately
     * chooses the moment, already reading "this is a probe, not a
     * capability" — see the Checks screen's own copy at the call site.
     *
     * It deliberately does not go through [LocalModelRunner] (or its warm
     * engine): a probe forcing an unpublished NPU tier is exactly the case
     * where a shared, kept-alive engine instance is the wrong thing to risk —
     * a native abort here must not take down a warm engine another draft
     * might be mid-call on. Its own, disposable [Engine] means a crash here
     * stays contained to this one call.
     *
     * The reason [generate] can't just try NPU-first-then-fall-back for
     * every SoC is that "falling back" assumes the failure is a catchable
     * Kotlin exception. It might not be: an NPU tier compiled for the wrong
     * Hexagon version can abort inside QNN's native dispatch layer instead of
     * throwing, taking the whole process down with it. That risk is exactly
     * why this probe is opt-in and manual rather than folded into [generate].
     */
    suspend fun probeNpuOnce(prompt: String): InferenceOutput = withContext(Dispatchers.IO) {
        if (!File(modelPath).isFile) {
            throw IllegalStateException("No side-loaded model at $modelPath.")
        }
        runTier(InferenceBackend.NPU, prompt)
    }

    private fun runTier(tier: InferenceBackend, prompt: String): InferenceOutput {
        val backend = when (tier) {
            InferenceBackend.NPU -> Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)
            InferenceBackend.GPU -> Backend.GPU()
            InferenceBackend.CPU -> Backend.CPU()
            // probeNpuOnce only ever asks for NPU — CLOUD labels a Sarvam
            // call elsewhere and should never reach a local LiteRT-LM tier
            // request.
            InferenceBackend.CLOUD -> error("CLOUD is not a local inference tier.")
        }
        val config = EngineConfig(modelPath = modelPath, backend = backend, cacheDir = context.cacheDir.path)

        val loadStart = System.nanoTime()
        Engine(config).use { engine ->
            // engine.initialize() returning without throwing is the only
            // confirmation this library's public Kotlin API offers that a
            // tier actually loaded — there is no separate "which backend is
            // this" query to read back afterward. See InferenceReport's own
            // doc comment and CLEANUP.md CL-18: this is a disclosed limit of
            // the API surface, not a skipped verification step.
            engine.initialize()
            val loadMs = (System.nanoTime() - loadStart) / 1_000_000

            engine.createConversation().use { conversation ->
                val genStart = System.nanoTime()
                val response = conversation.sendMessage(prompt)
                val genMs = ((System.nanoTime() - genStart) / 1_000_000).coerceAtLeast(1)
                // Message has no plain .text — its contents is a list of
                // typed Content variants (this model produces Text only; a
                // multi-modal reply is not something Cues' text-only prompt
                // asks for), so this joins exactly the Text pieces, in order.
                val text = response.contents.contents
                    .filterIsInstance<Content.Text>()
                    .joinToString(separator = "") { it.text }
                val tokens = text.trim().split(Regex("\\s+")).count { it.isNotBlank() }.coerceAtLeast(1)

                return InferenceOutput(
                    text = text,
                    report = InferenceReport(
                        backend = tier,
                        loadMs = loadMs,
                        generationMs = genMs,
                        estimatedTokens = tokens,
                    ),
                )
            }
        }
    }

    companion object {
        /**
         * SoC models the currently side-loaded model is actually built for on
         * the NPU path. Derived from [ModelCatalog] — the verified artifact
         * table (SM8750, SM8650, SM8550) — never a second, hand-maintained
         * list. Extend the catalog only after confirming a matching
         * `.litertlm` NPU asset for the exact `Build.SOC_MODEL` the loaner
         * reports, never from a guess at what an iQOO 15 "should" report.
         */
        val DEFAULT_NPU_SOC_MODELS: Set<String> get() = ModelCatalog.npuSocModels

        /**
         * The same one-line allowlist check [LocalModelRunner] itself runs
         * before attempting the NPU tier — pulled out so the "Cues Brain"
         * tile can show *eligibility* without constructing a session (which
         * needs a model path/`Context`) and without duplicating the
         * allowlist. This is never a claim that NPU *will* run, only that
         * this SoC is on the published table — see [com.cues.core.inference.InferenceReport]'s
         * own doc comment for the actual, only-after-a-real-run claim.
         */
        fun npuSocEligible(supportedNpuSocModels: Set<String> = DEFAULT_NPU_SOC_MODELS): Boolean {
            val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else null
            return soc != null && soc in supportedNpuSocModels
        }
    }
}
