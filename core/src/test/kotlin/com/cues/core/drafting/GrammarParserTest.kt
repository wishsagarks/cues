package com.cues.core.drafting

import com.cues.core.Fixtures
import com.cues.core.compile.Validator
import com.cues.core.corpus.Corpus
import com.cues.core.model.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertIs
import org.junit.jupiter.api.Test

private val PAIRED = listOf(
    PairedDevice(
        Fixtures.EARBUDS_ID,
        Fixtures.EARBUDS_LABEL,
        setOf("earbuds", "ear buds", "buds", "headphones", "headset"),
    ),
)

private fun parser() = GrammarParser(PAIRED) { "routine-test" }

class GrammarParserTest {

    @Test
    fun `the hero sentence compiles to the reviewed routine`() {
        val result = parser().parse(
            "When my earbuds connect after 6 PM on weekdays, start a 45-minute focus timer " +
                "and quiet notifications. End it if I disconnect.",
        )

        val routine = assertIs<DraftResult.Drafted>(result).routine

        assertEquals(
            Trigger.BluetoothConnection(Fixtures.EARBUDS_ID, Fixtures.EARBUDS_LABEL, DeviceTransition.CONNECTED),
            routine.trigger,
        )
        assertTrue(Condition.DaysOfWeek(WEEKDAYS) in routine.conditions)
        assertTrue(Condition.TimeWindow(LocalTimeOfDay(18, 0), LocalTimeOfDay(0, 0)) in routine.conditions)
        assertTrue(ActionSpec(ActionId.START_FOCUS_TIMER, ActionArgs.FocusTimer(45)) in routine.actions)
        assertTrue(ActionSpec(ActionId.REQUEST_DND, ActionArgs.Dnd()) in routine.actions)
        assertTrue(EndCondition.TriggerReversed in routine.endConditions)
    }

    @Test
    fun `every drafted routine passes independent validation`() {
        val drafted = Corpus.parse(corpusText())
            .map { it to parser().parse(it.input) }
            .mapNotNull { (case, result) -> (result as? DraftResult.Drafted)?.let { case to it } }

        // Whatever the parser chooses to emit, it never emits something the
        // validator would reject. A drafter that can produce an unarmable cue
        // has moved the problem rather than solved it.
        assertTrue(drafted.isNotEmpty(), "the corpus should produce some drafts")
        drafted.forEach { (case, result) ->
            val validation = Validator.validate(result.routine)
            assertTrue(
                validation.isValid,
                "invalid routine from \"${case.input}\": ${validation.errors.map { it.message }}",
            )
        }
    }

    @Test
    fun `the parser preserves meaning across the corpus`() {
        val corpus = Corpus.parse(corpusText())
        val results = corpus.map { Corpus.check(it, parser().parse(it.input)) }

        val failures = results.filterNot { it.passed }
        assertTrue(
            failures.isEmpty(),
            "unmet expectations:\n" + failures.joinToString("\n") { "  ${it.case.input}\n    ${it.failures}" },
        )
    }

    @Test
    fun `an unsupported clause is reported and never silently dropped`() {
        val result = parser().parse(
            "when my earbuds connect start a 45 minute timer unless I am on a call",
        )

        val drafted = assertIs<DraftResult.Drafted>(result)
        assertTrue(drafted.unsupported.isNotEmpty(), "the 'unless' clause must be surfaced")
        assertTrue(drafted.unsupported.any { it.fragment.startsWith("unless") })
    }

    @Test
    fun `a request to message someone is refused rather than ignored`() {
        val result = parser().parse("when my earbuds connect text my wife and start a 30 minute timer")

        val drafted = assertIs<DraftResult.Drafted>(result)
        assertTrue(drafted.unsupported.any { it.explanation.contains("does not send messages") })
        // The supported half is still drafted, so the user gets something usable
        // plus an honest account of what was left out.
        assertTrue(drafted.routine.actions.any { it.actionId == ActionId.START_FOCUS_TIMER })
    }

    @Test
    fun `a missing trigger produces a question rather than a guess`() {
        val result = parser().parse("start a focus timer")

        val clarification = assertIs<DraftResult.NeedsClarification>(result)
        assertEquals("trigger", clarification.about)
    }

    @Test
    fun `ringer phrasing drafts a ringer action, not a quiet-notifications rule`() {
        val result = parser().parse("when charging, put the phone on vibrate")

        val routine = assertIs<DraftResult.Drafted>(result).routine
        val action = routine.actions.single()
        assertEquals(ActionId.RINGER_MODE, action.actionId)
        assertEquals(RingerModeKind.VIBRATE, (action.args as ActionArgs.RingerMode).mode)
    }

    @Test
    fun `charger connects phrasing is a reviewable power shortcut`() {
        val routine = assertIs<DraftResult.Drafted>(
            parser().parse("when my charger connects, silence notifications for 10 minutes"),
        ).routine

        assertEquals(Trigger.Charging(PowerTransition.PLUGGED_IN), routine.trigger)
        assertTrue(routine.unaccountedClauses.isEmpty())
        assertTrue(Validator.validate(routine).isValid)
    }

    @Test
    fun `ringer and quiet-notifications can be drafted together without colliding`() {
        val result = parser().parse("when charging, quiet notifications and put the phone on silent")

        val routine = assertIs<DraftResult.Drafted>(result).routine
        assertEquals(
            setOf(ActionId.REQUEST_DND, ActionId.RINGER_MODE),
            routine.actions.map { it.actionId }.toSet(),
        )
    }

    @Test
    fun `utility phrasing drafts USE_UTILITY with the named utility and state (CL-23 item 7)`() {
        val on = assertIs<DraftResult.Drafted>(parser().parse("when charging, turn on eye protection")).routine
        val onAction = on.actions.single()
        assertEquals(ActionId.USE_UTILITY, onAction.actionId)
        assertEquals(UtilityId.EYE_PROTECTION, (onAction.args as ActionArgs.UseUtility).utilityId)
        assertEquals(UtilityState.ON, (onAction.args as ActionArgs.UseUtility).state)

        val off = assertIs<DraftResult.Drafted>(parser().parse("when charging, disable ultra saver")).routine
        val offAction = off.actions.single()
        assertEquals(UtilityId.ULTRA_SAVER, (offAction.args as ActionArgs.UseUtility).utilityId)
        assertEquals(UtilityState.OFF, (offAction.args as ActionArgs.UseUtility).state)
    }

    @Test
    fun `media phrasing drafts the matching media command`() {
        val result = parser().parse("when charging, pause the music")

        val routine = assertIs<DraftResult.Drafted>(result).routine
        val action = routine.actions.single()
        assertEquals(ActionId.MEDIA_CONTROL, action.actionId)
        assertEquals(MediaCommand.PAUSE, (action.args as ActionArgs.MediaControl).command)
    }

    @Test
    fun `opening an app by name asks which one, rather than guessing a package`() {
        val result = parser().parse("when charging, open spotify")

        val clarification = assertIs<DraftResult.NeedsClarification>(result)
        assertEquals("action.app", clarification.about)
        assertEquals("spotify", clarification.appQuery)
    }

    @Test
    fun `a resolved app marker drafts OPEN_APP with exactly the picked package, never a guess`() {
        // The exact re-draft shape the app layer sends once the user has
        // picked one from an installed-app query — see AndroidActionExecutor
        // and MainActivity's app-picker wiring.
        val result = parser().parse(
            "when charging, open spotify (selected app: com.spotify.music|Spotify)",
        )

        val routine = assertIs<DraftResult.Drafted>(result).routine
        val action = routine.actions.single { it.actionId == ActionId.OPEN_APP }
        assertEquals(ActionArgs.OpenApp("com.spotify.music", "Spotify"), action.args)
    }

    @Test
    fun `opening a link is not mistaken for an app-name request`() {
        val result = parser().parse("when charging, open https://example.com/page")

        val routine = assertIs<DraftResult.Drafted>(result).routine
        val action = routine.actions.single()
        assertEquals(ActionId.OPEN_LINK, action.actionId)
        assertEquals("https://example.com/page", (action.args as ActionArgs.OpenLink).url)
    }

    @Test
    fun `a pre-filled message names its contact and body, and never sends anything`() {
        val result = parser().parse("when charging, message my mom saying I'm charging now")

        val routine = assertIs<DraftResult.Drafted>(result).routine
        val args = routine.actions.single().args as ActionArgs.ComposeMessage
        assertEquals("my mom", args.contactHint)
        assertEquals("i'm charging now", args.text)
    }

    @Test
    fun `calling or emailing someone remains disclosed as unsupported`() {
        // Paired with a recognized action, the same way the app-open
        // disclosure test above must be: with nothing else to do, the parser
        // asks the generic "what should happen" question first rather than
        // ever reaching disclosure — a standing, pre-existing gap, not one
        // this phrasing introduces.
        val result = parser().parse("when charging, start a timer and call my mom")

        val drafted = assertIs<DraftResult.Drafted>(result)
        assertTrue(drafted.unsupported.any { it.explanation.contains("does not send messages") })
        assertEquals(listOf(ActionId.START_FOCUS_TIMER), drafted.routine.actions.map { it.actionId })
    }

    @Test
    fun `a calendar event names its title and begins when the action runs, not at draft time`() {
        val result = parser().parse("when charging, add a calendar event for a study block")

        val routine = assertIs<DraftResult.Drafted>(result).routine
        val args = routine.actions.single().args as ActionArgs.CalendarEvent
        assertEquals("a study block", args.title)
    }

    @Test
    fun `setting an alarm parses the spoken time`() {
        val result = parser().parse("when charging, set an alarm for 7:30 am")

        val routine = assertIs<DraftResult.Drafted>(result).routine
        val args = routine.actions.single().args as ActionArgs.Alarm
        assertEquals(7, args.hour)
        assertEquals(30, args.minute)
    }

    @Test
    fun `a Jovi-style scheduled content request is refused rather than forced into a cue`() {
        val result = parser().parse("every morning at 8 compile the news")

        val clarification = assertIs<DraftResult.NeedsClarification>(result)
        assertEquals("trigger", clarification.about)
        assertEquals("Time-scheduled content tasks aren't supported.", clarification.question)
        assertTrue(clarification.unsupported.isNotEmpty())
    }

    @Test
    fun `a missing action produces a question`() {
        val result = parser().parse("when my earbuds connect")

        assertEquals("actions", assertIs<DraftResult.NeedsClarification>(result).about)
    }

    @Test
    fun `an unresolvable device produces a question rather than a wrong device`() {
        val result = GrammarParser(PAIRED).parse("when my car stereo connects start a 20 minute timer")

        assertEquals("trigger.device", assertIs<DraftResult.NeedsClarification>(result).about)
    }

    @Test
    fun `two matching devices produce a question`() {
        val ambiguous = PAIRED + PairedDevice("99:88:77:66:55:44", "Desk speaker", setOf("speaker", "buds"))

        val result = GrammarParser(ambiguous).parse("when my buds connect start a 20 minute timer")

        val clarification = assertIs<DraftResult.NeedsClarification>(result)
        assertTrue(clarification.question.contains(Fixtures.EARBUDS_LABEL))
        assertTrue(clarification.question.contains("Desk speaker"))
        assertEquals(ambiguous, clarification.deviceCandidates)
    }

    @Test
    fun `every result names the parser as its source`() {
        val inputs = listOf(
            "when my earbuds connect start a 25 minute focus timer",
            "start a focus timer",
            "when my earbuds connect",
        )

        inputs.forEach { assertEquals(DraftSourceId.GRAMMAR_PARSER, parser().parse(it).source) }
    }

    @Test
    fun `hours are converted to minutes`() {
        val result = parser().parse("earbuds connect, focus for an hour")
        // "an hour" has no digit, so this falls back to the default rather than
        // inventing 60. The default is shown in review as a proposal.
        assertIs<DraftResult.Drafted>(result)
    }

    @Test
    fun `a numeric hour duration is converted`() {
        val result = parser().parse("when my earbuds connect start a 2 hour focus timer")

        val routine = assertIs<DraftResult.Drafted>(result).routine
        assertEquals(
            120,
            (routine.actions.first { it.actionId == ActionId.START_FOCUS_TIMER }.args as ActionArgs.FocusTimer)
                .durationMinutes,
        )
    }

    @Test
    fun `a cue always gets a manual stop`() {
        val result = parser().parse("when my earbuds connect start a 25 minute focus timer")

        assertTrue(EndCondition.ManualStop in assertIs<DraftResult.Drafted>(result).routine.endConditions)
    }

    @Test
    fun `no corpus sentence leaves real vocabulary unaccounted`() {
        // GrammarParser.parse() alone never populates Routine.unaccountedClauses
        // — CueService.draft() derives that from `consumed` after the fact —
        // so this recomputes it the same way, over the whole corpus. Without
        // this, a parser change that stops marking some real word `consumed`
        // ships silently instead of being caught here.
        Corpus.parse(corpusText()).forEach { case ->
            val result = parser().parse(case.input)
            if (result is DraftResult.Drafted) {
                val unaccounted = ClauseAccounting.unaccounted(case.input, result.consumed)
                assertTrue(unaccounted.isEmpty(), "\"${case.input}\" left $unaccounted unaccounted")
            }
        }
    }

    @Test
    fun `time formats are all understood`() {
        fun windowFor(text: String): Condition.TimeWindow? =
            (parser().parse(text) as? DraftResult.Drafted)
                ?.routine?.conditions?.filterIsInstance<Condition.TimeWindow>()?.firstOrNull()

        val base = "when my earbuds connect start a 25 minute focus timer "
        assertEquals(LocalTimeOfDay(18, 0), windowFor(base + "after 6pm")?.startInclusive)
        assertEquals(LocalTimeOfDay(18, 0), windowFor(base + "after 18:00")?.startInclusive)
        assertEquals(LocalTimeOfDay(18, 30), windowFor(base + "after 6:30 pm")?.startInclusive)
        assertEquals(LocalTimeOfDay(12, 0), windowFor(base + "after noon")?.startInclusive)
        assertEquals(LocalTimeOfDay(0, 0), windowFor(base + "after midnight")?.startInclusive)
        assertEquals(LocalTimeOfDay(9, 0), windowFor(base + "after 9am")?.startInclusive)
    }

    @Test
    fun `calendar phrasing drafts the matching condition, never both at once`() {
        val busy = parser().parse("when my earbuds connect, start a 25 minute focus timer if my calendar is busy")
        val routine = assertIs<DraftResult.Drafted>(busy).routine
        assertTrue(Condition.CalendarBusy in routine.conditions)
        assertTrue(Condition.CalendarNotBusy !in routine.conditions)

        val free = parser().parse("when my earbuds connect, start a 25 minute focus timer if my calendar is free")
        val freeRoutine = assertIs<DraftResult.Drafted>(free).routine
        assertTrue(Condition.CalendarNotBusy in freeRoutine.conditions)
        assertTrue(Condition.CalendarBusy !in freeRoutine.conditions)

        assertTrue(Validator.validate(routine).errors.isEmpty())
        assertTrue(Capability.READ_CALENDAR in routine.requiredCapabilities)
    }
}

internal fun corpusText(): String =
    checkNotNull(object {}.javaClass.getResourceAsStream("/corpus/paraphrases.txt")) {
        "corpus/paraphrases.txt missing from resources"
    }.bufferedReader().readText()
