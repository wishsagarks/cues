package com.cues.core.corpus

import com.cues.core.drafting.DraftResult
import com.cues.core.drafting.RoutineDrafter
import com.cues.core.model.DraftSourceId
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class BakeOffTest {
    @Test fun `wrong meaning accepts are distinct from failures and report is deterministic`() = runTest {
        val parser = com.cues.core.drafting.GrammarParser { "test" }
        val wrong = object : RoutineDrafter {
            override val id = DraftSourceId.ON_DEVICE_LLM
            override suspend fun draft(text: String): DraftResult = parser.parse("when charger connects start a 10 minute timer")
        }
        val cases = listOf(CorpusCase("when charger connects start a 10 minute timer", mapOf("trigger" to "charging-on", "timer" to "10")))
        val good = BakeOff.run(listOf(parser), cases, nowMillis = { 1L }).rows.single()
        val bad = BakeOff.run(listOf(wrong), cases.map { it.copy(expectations = mapOf("trigger" to "bluetooth-connect")) }, nowMillis = { 1L }).rows.single()
        assertEquals(1, good.passCount)
        assertEquals(1, bad.wrongMeaningAccepts)
        assertEquals(false, good.networkOn)
    }
}
