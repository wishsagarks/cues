package com.cues.app.drafting

import android.content.Context
import android.os.Build
import com.cues.core.drafting.InferenceOutput
import com.cues.core.drafting.LlmSession
import com.cues.core.inference.InferenceBackend
import com.cues.core.inference.InferenceReport
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The real on-device runtime: LiteRT-LM, a side-loaded `.litertlm` model, and
 * an explicit NPU -> GPU -> CPU fallback chain.
 *
 * WRITTEN AGAINST REAL APIs, VERIFIED ON NOTHING — the same discipline
 * [com.cues.app.runtime.AndroidActionExecutor] already carries; this class
 * cannot be exercised against a real model in the environment that wrote it.
 * See CLEANUP.md CL-18 for exactly what remains unverified and why.
 *
 * No model ships in the APK and none is ever downloaded — [modelPath] must
 * already exist from a side-load (`adb push`) before this does anything. A
 * missing file is an immediate, honest failure: a stub that returned a
 * plausible routine would make the model look like it was working, which is
 * the one outcome worth avoiding.
 */
class LiteRtLmSession(
    private val context: Context,
    private val modelPath: String,
    /**
     * SoC models the currently side-loaded model is actually built for on
     * the NPU path. Populated from docs/API_VERIFICATION.md's published
     * table (SM8750, SM8650, SM8550) — extend only after confirming a
     * matching `.litertlm` NPU asset for the exact `Build.SOC_MODEL` the
     * loaner reports, never from a guess at what an iQOO 15 "should" report.
     */
    private val supportedNpuSocModels: Set<String> = DEFAULT_NPU_SOC_MODELS,
) : LlmSession {

    override suspend fun generate(prompt: String): InferenceOutput = withContext(Dispatchers.IO) {
        if (!File(modelPath).isFile) {
            throw IllegalStateException("No side-loaded model at $modelPath.")
        }

        val tiers = buildList {
            if (npuSocMatches()) add(InferenceBackend.NPU)
            add(InferenceBackend.GPU)
            add(InferenceBackend.CPU)
        }

        var lastError: Throwable? = null
        for (tier in tiers) {
            try {
                return@withContext runTier(tier, prompt)
            } catch (e: Exception) {
                // Falls through to the next, less capable tier. Every step
                // down is a fact about *this run* — recorded in the eventual
                // report's backend field — never a tier this run silently
                // pretended to use.
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("No inference backend could load the model.")
    }

    private fun runTier(tier: InferenceBackend, prompt: String): InferenceOutput {
        val backend = when (tier) {
            InferenceBackend.NPU -> Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)
            InferenceBackend.GPU -> Backend.GPU()
            InferenceBackend.CPU -> Backend.CPU()
            // This session only ever builds its tier list from NPU/GPU/CPU (see
            // `generate()` above) — CLOUD labels a Sarvam call elsewhere and
            // should never reach a local LiteRT-LM tier request.
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

    private fun npuSocMatches(): Boolean = npuSocEligible(supportedNpuSocModels)

    companion object {
        val DEFAULT_NPU_SOC_MODELS = setOf("SM8750", "SM8650", "SM8550")

        /**
         * The same one-line allowlist check [LiteRtLmSession] itself runs
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
