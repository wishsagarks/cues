package com.cues.core.model

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class ModelCatalogTest {
    @Test
    fun `only cataloged socs are eligible for an NPU artifact`() {
        assertTrue(ModelCatalog.eligibleArtifacts("SM8750").any { it.execution == ModelExecution.NPU_SOC_SPECIFIC })
        assertFalse(ModelCatalog.eligibleArtifacts("SM8850").any { it.execution == ModelExecution.NPU_SOC_SPECIFIC })
        assertFalse(ModelCatalog.eligibleArtifacts(null).any { it.execution == ModelExecution.NPU_SOC_SPECIFIC })
    }

    @Test
    fun `npu allowlist is derived from npu-specific artifacts`() {
        assertEquals(setOf("SM8750", "SM8650", "SM8550"), ModelCatalog.npuSocModels)
    }

    @Test
    fun `a confirmed dispatch failure never adds its SoC to the attempt allowlist`() {
        assertTrue(ModelCatalog.knownDispatchFailureFor("SM8850") != null)
        assertFalse("SM8850" in ModelCatalog.npuSocModels)
    }

    @Test
    fun `an untested SoC has no known dispatch failure recorded`() {
        assertEquals(null, ModelCatalog.knownDispatchFailureFor("SM9999"))
        assertEquals(null, ModelCatalog.knownDispatchFailureFor(null))
    }
}
