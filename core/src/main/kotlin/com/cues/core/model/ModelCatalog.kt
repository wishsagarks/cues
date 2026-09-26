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
 * The single source of truth for LiteRT-LM backend eligibility.
 *
 * This catalog records only the SoCs named by the project's verified LiteRT
 * asset table.  In particular, SM8850 is intentionally absent until an exact
 * artifact and an on-device NPU report are recorded; it will therefore take
 * the GPU/CPU path rather than make an unprovable acceleration claim.
 */
object ModelCatalog {
    private val artifacts = listOf(
        ModelArtifact(
            id = "litert-lm-npu-published",
            execution = ModelExecution.NPU_SOC_SPECIFIC,
            supportedSocModels = setOf("SM8750", "SM8650", "SM8550"),
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
}
