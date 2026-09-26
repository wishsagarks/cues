package com.cues.app.drafting

import android.content.Context
import android.os.Build
import com.cues.core.drafting.InferenceOutput
import com.cues.core.drafting.LlmSession
import java.io.File

/**
 * The real on-device runtime: LiteRT-LM, a side-loaded `.litertlm` model, and
 * an explicit NPU -> GPU -> CPU fallback chain — now a thin [LlmSession]
 * adapter over [LocalModelRunner], which owns the actual
 * [com.google.ai.edge.litertlm.Engine] and keeps it warm across calls
 * (Sprint 8; see [LocalModelRunner]'s own doc comment for exactly what that
 * changed — engine reuse, real cancellation via `cancelProcess()`, and real
 * token counts from `getBenchmarkInfo()` instead of a whitespace-split guess).
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
    context: Context,
    private val modelPath: String,
    supportedNpuSocModels: Set<String> = DEFAULT_NPU_SOC_MODELS,
) : LlmSession {

    /**
     * Exposed so `:app` can observe [LocalModelRunner.state] and call
     * [LocalModelRunner.warm]/[LocalModelRunner.release] directly (pre-warm
     * on opening Ask, release on toggle-off or low memory), not only through
     * [generate].
     */
    val runner = LocalModelRunner(context, File(modelPath), supportedNpuSocModels)

    override suspend fun generate(prompt: String): InferenceOutput = runner.generate(prompt)

    companion object {
        /**
         * SoC models the currently side-loaded model is actually built for on
         * the NPU path. Populated from docs/API_VERIFICATION.md's published
         * table (SM8750, SM8650, SM8550) — extend only after confirming a
         * matching `.litertlm` NPU asset for the exact `Build.SOC_MODEL` the
         * loaner reports, never from a guess at what an iQOO 15 "should" report.
         */
        val DEFAULT_NPU_SOC_MODELS = setOf("SM8750", "SM8650", "SM8550")

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
