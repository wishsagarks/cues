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
}
