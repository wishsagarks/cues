package com.cues.core.inference

import com.cues.core.model.DraftSourceId
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class LocalHubInferenceTest {
    @Test
    fun `local hub sources are accounted as on-device inference`() {
        listOf(DraftSourceId.EXTERNAL_GEMMA_CALL, DraftSourceId.SYSTEM_AGENT_CALL).forEach { source ->
            assertEquals(0.0 to CostBasis.ON_DEVICE_FREE, InferenceCost.costFor(source, estimatedTokens = 42))
        }
    }

    @Test
    fun `self-authored inference has no external caller`() {
        val entry = InferenceLedgerEntry(
            atMillis = 1,
            source = DraftSourceId.ON_DEVICE_LLM,
            backend = InferenceBackend.CPU,
            estimatedTokens = 1,
            latencyMs = 1,
            costUsd = 0.0,
            costBasis = CostBasis.ON_DEVICE_FREE,
        )
        assertNull(entry.callerPackage)
    }
}
