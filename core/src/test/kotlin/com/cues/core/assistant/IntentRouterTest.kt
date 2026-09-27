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

    /**
     * Every checked-in Ask-screen template must actually reach the drafter —
     * a template a judge taps and gets "That is a Jovi task" for is worse
     * than no template at all. This is the exact gap a real on-device tap
     * caught: SYSTEM_AGENT_PATTERNS' plain \bbuy\b/\bcall\b used to be
     * checked before CUE_PATTERNS and stole these before GrammarParser ever
     * saw them.
     */
    @Test
    fun `new template phrasings are routed to Create, not misread as one-off Jovi tasks`() {
        assertIs<AssistantIntent.Create>(router.route("At 18:00, automatically whatsapp and notify parents saying I'm home."))
        assertIs<AssistantIntent.Create>(router.route("Buy 2kg potato, 2kg tomato."))
        assertIs<AssistantIntent.Create>(router.route("When I miss a call, whatsapp mom saying sorry I missed your call."))
        assertIs<AssistantIntent.Create>(router.route("Every 3 days at 9:00, automatically notify saying water the plants."))
        assertIs<AssistantIntent.Create>(router.route("Check my mail as mcq."))
    }

    @Test
    fun `a genuine one-off task without a digit or trigger word still goes to the system agent`() {
        assertEquals(
            UnsupportedRoute.SYSTEM_AGENT,
            assertIs<AssistantIntent.Unsupported>(router.route("buy me a pizza")).routeTo,
        )
        assertEquals(
            UnsupportedRoute.SYSTEM_AGENT,
            assertIs<AssistantIntent.Unsupported>(router.route("call Priya now")).routeTo,
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
