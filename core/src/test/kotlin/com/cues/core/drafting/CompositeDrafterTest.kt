package com.cues.core.drafting

import com.cues.core.Fixtures
import com.cues.core.model.ActionArgs
import com.cues.core.model.ActionId
import com.cues.core.model.ActionSpec
import com.cues.core.model.DraftSourceId
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/** Stands in for the on-device model, which cannot run in a JVM test. */
private class StubDrafter(
    override val id: DraftSourceId = DraftSourceId.ON_DEVICE_LLM,
    private val delayMillis: Long = 0,
    private val result: (String) -> DraftResult,
) : RoutineDrafter {
    override suspend fun draft(text: String): DraftResult {
        if (delayMillis > 0) delay(delayMillis)
        return result(text)
    }
}

private fun parser() = GrammarParser(
    listOf(PairedDevice(Fixtures.EARBUDS_ID, Fixtures.EARBUDS_LABEL, setOf("earbuds", "buds"))),
) { "routine-test" }

private const val HERO = "when my earbuds connect after 6pm on weekdays start a 45 minute focus timer " +
    "and quiet notifications"

class CompositeDrafterTest {

    @Test
    fun `a good model draft is used and labelled as the model's`() = runTest {
        val modelRoutine = (parser().parse(HERO) as DraftResult.Drafted).routine
        val composite = CompositeDrafter(
            primary = StubDrafter { DraftResult.Drafted(DraftSourceId.ON_DEVICE_LLM, modelRoutine) },
            fallback = parser(),
        )

        val result = composite.draft(HERO)

        assertEquals(DraftSourceId.ON_DEVICE_LLM, result.source)
        assertNull(composite.lastFallbackReason)
    }

    @Test
    fun `a slow model loses its slot to the parser`() = runTest {
        val composite = CompositeDrafter(
            primary = StubDrafter(delayMillis = 10_000) {
                DraftResult.Failed(DraftSourceId.ON_DEVICE_LLM, "never reached")
            },
            fallback = parser(),
            timeoutMillis = 100,
        )

        val result = composite.draft(HERO)

        assertEquals(DraftSourceId.GRAMMAR_PARSER, result.source)
        assertTrue(assertNotNull(composite.lastFallbackReason).contains("did not answer within"))
    }

    @Test
    fun `a crashing model falls back rather than failing the request`() = runTest {
        val composite = CompositeDrafter(
            primary = StubDrafter { error("model assets missing") },
            fallback = parser(),
        )

        val result = composite.draft(HERO)

        assertIs<DraftResult.Drafted>(result)
        assertEquals(DraftSourceId.GRAMMAR_PARSER, result.source)
        assertTrue(assertNotNull(composite.lastFallbackReason).contains("model assets missing"))
    }

    @Test
    fun `a model draft that fails validation is rejected`() = runTest {
        // Well-formed, and wrong: a timer that ends after the cue does.
        val broken = (parser().parse(HERO) as DraftResult.Drafted).routine.copy(
            actions = listOf(ActionSpec(ActionId.START_FOCUS_TIMER, ActionArgs.FocusTimer(9_999))),
        )
        val composite = CompositeDrafter(
            primary = StubDrafter { DraftResult.Drafted(DraftSourceId.ON_DEVICE_LLM, broken) },
            fallback = parser(),
        )

        val result = composite.draft(HERO)

        // The model does not get to vouch for its own output.
        assertEquals(DraftSourceId.GRAMMAR_PARSER, result.source)
        assertTrue(assertNotNull(composite.lastFallbackReason).contains("did not pass validation"))
    }

    @Test
    fun `a genuine question from the model is kept`() = runTest {
        val composite = CompositeDrafter(
            primary = StubDrafter {
                DraftResult.NeedsClarification(DraftSourceId.ON_DEVICE_LLM, "Which earbuds?", "trigger.device")
            },
            fallback = parser(),
        )

        val result = composite.draft(HERO)

        // Asking is a real answer. Falling back here would replace a good
        // question with a worse guess.
        assertEquals(DraftSourceId.ON_DEVICE_LLM, result.source)
        assertNull(composite.lastFallbackReason)
    }

    @Test
    fun `a reported model failure falls back`() = runTest {
        val composite = CompositeDrafter(
            primary = StubDrafter { DraftResult.Failed(DraftSourceId.ON_DEVICE_LLM, "out of memory") },
            fallback = parser(),
        )

        val result = composite.draft(HERO)

        assertEquals(DraftSourceId.GRAMMAR_PARSER, result.source)
        assertTrue(assertNotNull(composite.lastFallbackReason).contains("out of memory"))
    }
}
