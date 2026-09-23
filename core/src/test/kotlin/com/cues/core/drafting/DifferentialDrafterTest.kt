package com.cues.core.drafting

import com.cues.core.Fixtures
import com.cues.core.model.DraftSourceId
import kotlinx.coroutines.test.runTest
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class DifferentialDrafterTest {
    private fun drafter(result: DraftResult) = object : RoutineDrafter {
        override val id = result.source
        override suspend fun draft(text: String) = result
    }
    @Test fun `semantic disagreement asks instead of selecting`() = runTest {
        val a = DraftResult.Drafted(DraftSourceId.GRAMMAR_PARSER, Fixtures.heroRoutine())
        val b = DraftResult.Drafted(DraftSourceId.ON_DEVICE_LLM, Fixtures.heroRoutine().copy(actions = emptyList()))
        val result = DifferentialDrafter(drafter(a), drafter(b)).draft("cue")
        assertTrue(assertIs<DraftResult.NeedsClarification>(result).question.contains("action"))
    }
}
