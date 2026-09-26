package com.cues.core.model

/**
 * A model artifact Cues knows how to describe, distinct from a model that is
 * installed on a particular phone.  An NPU entry is deliberately specific to
 * a published artifact and its SoC list: a phone being powerful is never
 * enough reason to attempt an NPU load.
 */
data class ModelArtifact(
    val id: String,
    val execution: ModelExecution,
    val supportedSocModels: Set<String> = emptySet(),
)

enum class ModelExecution { PORTABLE, NPU_SOC_SPECIFIC }

/**
 * A published NPU-specific artifact that a real, on-device attempt has
 * confirmed fails before it ever reaches the Hexagon NPU — kept structurally
 * separate from [ModelCatalog.npuSocModels] (the set [LiteRtLmSession] uses
 * to decide whether to *attempt* an NPU load at all). A SoC never gets added
 * to [ModelCatalog.npuSocModels] on the strength of an artifact merely
 * existing; this record exists so a SoC whose artifact is published but
 * confirmed broken shows *why*, rather than the same "nothing published"
 * message a genuinely untested chip gets — those are different facts.
 */
data class KnownNpuDispatchFailure(
    val socModel: String,
    val artifactFileName: String,
    /** What a real on-device run actually reported, not a guess at the cause. */
    val failureNote: String,
)

/**
 * The single source of truth for LiteRT-LM backend eligibility.
 *
 * This catalog records only the SoCs named by the project's verified LiteRT
 * asset table. A SoC is added to [npuSocModels] only after a real on-device
 * run returns `InferenceReport.backend == NPU` with no fallback exception —
 * never from an artifact merely existing for it (see [knownDispatchFailures]
 * for exactly that case, confirmed for SM8850 on 26 Sep 2026).
 */
object ModelCatalog {
    private val artifacts = listOf(
        ModelArtifact(
            id = "litert-lm-npu-published",
            execution = ModelExecution.NPU_SOC_SPECIFIC,
            supportedSocModels = setOf("SM8750", "SM8650", "SM8550"),
        ),
    )

    /**
     * `litert-community/Gemma3-1B-IT` on Hugging Face publishes
     * `Gemma3-1B-IT_q4_ekv1280_sm8850.litertlm` (694 MB) — a real,
     * SM8850-compiled NPU artifact `docs/API_VERIFICATION.md`'s NPU table
     * didn't know about. Side-loaded and forced through
     * `LiteRtLmSession.probeNpuOnce` on the real iQOO 15 loaner
     * (`10BFBN2C30001KN`, SM8850): the flatbuffer loaded, but
     * `Engine.initialize()` threw. Logcat's root cause, verbatim:
     * `[litert_dispatch.cc:122] No dispatch library found in
     * .../lib/arm64`, `Failed to initialize Dispatch API: ... No usable
     * Dispatch runtime found`, ending in `RET_CHECK failure
     * (.../llm_litert_npu_compiled_model_executor.cc:1558) ... Failed to
     * invoke the compiled model`. Per Google's own LiteRT-LM NPU docs, the
     * missing piece is Qualcomm's QAIRT dispatch bridge
     * (`libQnnHtp*.so`/`libQnnSystem.so`/hexagon skeleton files plus a
     * Bazel-built `dispatch_api_so`) — none of which ships in the
     * `litertlm-android` AAR (confirmed: it contains only
     * `liblitertlm_jni.so`) or anywhere in this app. This is a packaging gap,
     * not a hardware or model incompatibility — see CLEANUP.md CL-18 item 3.
     */
    private val knownDispatchFailures = listOf(
        KnownNpuDispatchFailure(
            socModel = "SM8850",
            artifactFileName = "Gemma3-1B-IT_q4_ekv1280_sm8850.litertlm",
            failureNote = "A published NPU build exists for this chip, but this app has no QAIRT/Hexagon dispatch library bundled — Engine.initialize() fails before reaching the NPU. Falls back to GPU/CPU.",
        ),
    )

    fun eligibleArtifacts(socModel: String?): List<ModelArtifact> = artifacts.filter { artifact ->
        artifact.execution == ModelExecution.PORTABLE || socModel in artifact.supportedSocModels
    }

    /** SoCs for which a cataloged NPU-specific artifact exists. */
    val npuSocModels: Set<String>
        get() = artifacts
            .asSequence()
            .filter { it.execution == ModelExecution.NPU_SOC_SPECIFIC }
            .flatMap { it.supportedSocModels.asSequence() }
            .toSet()

    /** `null` for a SoC nobody has tried NPU on yet — a different, honest silence from a confirmed failure. */
    fun knownDispatchFailureFor(socModel: String?): KnownNpuDispatchFailure? =
        knownDispatchFailures.firstOrNull { it.socModel == socModel }
}
