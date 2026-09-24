package com.cues.core.assistant

import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.jupiter.api.Test

class IntentRouterTest {
    private val router = IntentRouter()

    @Test
    fun `routes cue authoring and refinement without treating general requests as cues`() {
        assertIs<AssistantIntent.Create>(router.route("every weekday at 9 start a 45 minute focus"))
        assertEquals(
            RefineOperation.SetDuration(30),
            assertIs<AssistantIntent.Refine>(router.route("make it 30 minutes")).operation,
        )
        assertEquals(
            UnsupportedRoute.SYSTEM_AGENT,
            assertIs<AssistantIntent.Unsupported>(router.route("book me a ride to the station")).routeTo,
        )
    }

    @Test
    fun `control requests are parsed as pending intent rather than authoring text`() {
        val intent = assertIs<AssistantIntent.Control>(router.route("pause my Study cue"))

        assertEquals(ControlKind.PAUSE, intent.kind)
        assertEquals("my Study cue", intent.reference)
    }

    @Test
    fun `capabilities list forecast and explanation are closed routes`() {
        assertIs<AssistantIntent.Capabilities>(router.route("what can you do?"))
        assertIs<AssistantIntent.ListCues>(router.route("list my cues"))
        assertIs<AssistantIntent.Forecast>(router.route("what runs today?"))
        assertIs<AssistantIntent.Explain>(router.route("why didn't Study run yesterday?"))
    }
}
