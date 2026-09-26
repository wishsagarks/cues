package com.cues.core.workbench

import com.cues.core.Fixtures
import com.cues.core.compile.Normalizer
import com.cues.core.drafting.ClauseKind
import com.cues.core.drafting.DraftResult
import com.cues.core.drafting.GrammarParser
import com.cues.core.drafting.PairedDevice
import com.cues.core.model.*
import com.cues.core.registry.ActionRegistry
import com.cues.core.signals.SignalRegistry
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail
import org.junit.jupiter.api.Test

private val EARBUDS = PairedDevice(Fixtures.EARBUDS_ID, Fixtures.EARBUDS_LABEL, setOf("earbuds", "buds"))
private val WATCH = PairedDevice("11:22:33:44:55:66", "Galaxy Watch", setOf("watch"))
private val DESK = NamedContext("ctx-desk", "Desk", 1, listOf(Condition.ChargingState(true)))
private val HOME = Place("place-home", "Home", 1, 12.97, 77.59, 200)

/**
 * The guard against the Workbench offering a control the grammar can't express.
 *
 * For every kit in [SignalRegistry] and [ActionRegistry], a representative
 * patch-bay selection is turned into a sentence, and that sentence is parsed
 * by a *fresh* [GrammarParser] — not the one [PatchSentence] checks itself
 * with — and the resulting routine must have exactly the selected trigger,
 * conditions, actions and endings, drafted cleanly.
 *
 * A kit with no sample here fails [every kit in the registries has a sample
 * or a stated reason it is not in the patch bay], so adding a kit forces the
 * decision: phrase it, or list it in [PatchSentence.unavailable] with why.
 */
class PatchSentenceTest {

    private val patch = PatchSentence(listOf(EARBUDS, WATCH), { listOf(DESK) }, { listOf(HOME) })
    private val parser = GrammarParser(listOf(EARBUDS, WATCH), contextsProvider = { listOf(DESK) }, placesProvider = { listOf(HOME) })

    private val quiet = ActionSpec(ActionId.REQUEST_DND, ActionArgs.Dnd())
    private val earbudsIn = Trigger.BluetoothConnection(EARBUDS.id, EARBUDS.label, DeviceTransition.CONNECTED)
    private val sevenAm = Trigger.AtTime(LocalTimeOfDay(7, 0))
    private val connectionEnds = listOf(EndCondition.ManualStop, EndCondition.TriggerReversed)

    private fun t(hour: Int, minute: Int = 0) = LocalTimeOfDay(hour, minute)

    // ------------------------------------------------------------------ samples

    private val triggerSamples: List<Pair<String, PatchSelection>> = listOf(
        "bluetooth connect" to PatchSelection(earbudsIn, actions = listOf(quiet), ends = connectionEnds),
        "bluetooth disconnect" to PatchSelection(earbudsIn.copy(transition = DeviceTransition.DISCONNECTED), actions = listOf(quiet)),
        "charger plugged in" to PatchSelection(Trigger.Charging(PowerTransition.PLUGGED_IN), actions = listOf(quiet)),
        "manual, with a context" to PatchSelection(
            Trigger.Manual, conditions = listOf(Condition.InContext(DESK.id, DESK.version, DESK.label)), actions = listOf(quiet),
        ),
        "wifi connect" to PatchSelection(Trigger.WifiConnection(DeviceTransition.CONNECTED), actions = listOf(quiet), ends = connectionEnds),
        "wifi disconnect" to PatchSelection(Trigger.WifiConnection(DeviceTransition.DISCONNECTED), actions = listOf(quiet)),
        "at a time" to PatchSelection(Trigger.AtTime(t(7, 30)), actions = listOf(quiet)),
        "headphones in" to PatchSelection(Trigger.AudioOutput(AudioTransition.ADDED), actions = listOf(quiet)),
        "headphones out" to PatchSelection(Trigger.AudioOutput(AudioTransition.REMOVED), actions = listOf(quiet)),
        "arrive at a place" to PatchSelection(
            Trigger.PlaceTransition(HOME.id, HOME.version, HOME.label, PlaceTransitionKind.ENTER), actions = listOf(quiet),
        ),
        "leave a place" to PatchSelection(
            Trigger.PlaceTransition(HOME.id, HOME.version, HOME.label, PlaceTransitionKind.EXIT), actions = listOf(quiet),
        ),
    )

    /** Conditions ride on a scheduled trigger: the grammar reads it before any charger or device word. */
    private fun gated(vararg conditions: Condition) = PatchSelection(sevenAm, conditions.toList(), listOf(quiet))

    private val conditionSamples: List<Pair<String, PatchSelection>> = listOf(
        "named days" to gated(Condition.DaysOfWeek(setOf(Day.MON, Day.WED))),
        "weekdays on a connection cue" to PatchSelection(earbudsIn, listOf(Condition.DaysOfWeek(WEEKDAYS)), listOf(quiet), connectionEnds),
        "weekend on a connection cue" to PatchSelection(earbudsIn, listOf(Condition.DaysOfWeek(WEEKEND)), listOf(quiet), connectionEnds),
        "after a time" to gated(Condition.TimeWindow(t(18), t(0))),
        "before a time" to gated(Condition.TimeWindow(t(0), t(9, 30))),
        "between two times" to gated(Condition.TimeWindow(t(9), t(17))),
        "a window across midnight" to gated(Condition.TimeWindow(t(22), t(6))),
        "charging" to gated(Condition.ChargingState(true)),
        "not charging" to gated(Condition.ChargingState(false)),
        "another device connected" to gated(Condition.DeviceConnected(WATCH.id, WATCH.label)),
        "in a named context" to gated(Condition.InContext(DESK.id, DESK.version, DESK.label)),
        "audio active" to gated(Condition.AudioOutputActive(AudioKind.ANY)),
        "battery below" to gated(Condition.BatteryBelow(20)),
        "battery at least" to gated(Condition.BatteryAtLeast(50)),
        "at a place" to gated(Condition.AtPlace(HOME.id, HOME.version, HOME.label)),
        "calendar busy" to gated(Condition.CalendarBusy),
        "calendar free" to gated(Condition.CalendarNotBusy),
    )

    /** Actions ride on the hero trigger, which always ends when the earbuds go away. */
    private fun doing(vararg actions: ActionSpec, extraEnds: List<EndCondition> = emptyList()) =
        PatchSelection(earbudsIn, actions = actions.toList(), ends = connectionEnds + extraEnds)

    private val actionSamples: List<Pair<String, PatchSelection>> = listOf(
        "focus timer" to doing(ActionSpec(ActionId.START_FOCUS_TIMER, ActionArgs.FocusTimer(45)), extraEnds = listOf(EndCondition.Duration(45))),
        "quiet notifications" to doing(quiet),
        "charging reminder" to doing(ActionSpec(ActionId.NOTIFY_RESULT, ActionArgs.Notify("The phone is not charging."))),
        "pinned note" to doing(ActionSpec(ActionId.PINNED_NOTE, ActionArgs.PinnedNote("stay on task"))),
        "open an app" to doing(ActionSpec(ActionId.OPEN_APP, ActionArgs.OpenApp("com.spotify.music", "Spotify"))),
        "pre-fill a message" to doing(ActionSpec(ActionId.COMPOSE_MESSAGE, ActionArgs.ComposeMessage("mum", "running late"))),
        "pre-fill a message, no contact" to doing(ActionSpec(ActionId.COMPOSE_MESSAGE, ActionArgs.ComposeMessage(null, "on my way"))),
        "WhatsApp draft" to doing(ActionSpec(ActionId.COMPOSE_WHATSAPP, ActionArgs.ComposeWhatsApp("wish", "i am in office"))),
        "calendar event" to doing(ActionSpec(ActionId.ADD_CALENDAR_EVENT, ActionArgs.CalendarEvent("dentist", 30))),
        "alarm" to doing(ActionSpec(ActionId.SET_ALARM, ActionArgs.Alarm(7, 30))),
        "ringer silent" to doing(ActionSpec(ActionId.RINGER_MODE, ActionArgs.RingerMode(RingerModeKind.SILENT))),
        "ringer vibrate" to doing(ActionSpec(ActionId.RINGER_MODE, ActionArgs.RingerMode(RingerModeKind.VIBRATE))),
        "open a link" to doing(ActionSpec(ActionId.OPEN_LINK, ActionArgs.OpenLink("https://example.com/page"))),
    ) + MediaCommand.entries.map { command ->
        "media ${command.name.lowercase()}" to doing(ActionSpec(ActionId.MEDIA_CONTROL, ActionArgs.MediaControl(command)))
    } + UtilityId.entries.flatMap { utility ->
        UtilityState.entries.map { state ->
            "utility ${utility.name.lowercase()} ${state.name.lowercase()}" to
                doing(ActionSpec(ActionId.USE_UTILITY, ActionArgs.UseUtility(utility, state)))
        }
    }

    private val charger = Trigger.Charging(PowerTransition.PLUGGED_IN)

    private val endSamples: List<Pair<String, PatchSelection>> = listOf(
        "manual stop, always" to PatchSelection(charger, actions = listOf(quiet)),
        "half an hour" to PatchSelection(charger, actions = listOf(quiet), ends = listOf(EndCondition.ManualStop, EndCondition.Duration(30))),
        "an hour" to PatchSelection(charger, actions = listOf(quiet), ends = listOf(EndCondition.ManualStop, EndCondition.Duration(60))),
        "until a time" to PatchSelection(charger, actions = listOf(quiet), ends = listOf(EndCondition.ManualStop, EndCondition.AtTime(t(21)))),
        "the charger goes away" to PatchSelection(charger, actions = listOf(quiet), ends = listOf(EndCondition.ManualStop, EndCondition.TriggerReversed)),
        "headphones go away" to PatchSelection(
            Trigger.AudioOutput(AudioTransition.ADDED), actions = listOf(quiet), ends = listOf(EndCondition.ManualStop, EndCondition.TriggerReversed),
        ),
        "a pinned note after an end sentence" to PatchSelection(
            charger,
            actions = listOf(quiet, ActionSpec(ActionId.PINNED_NOTE, ActionArgs.PinnedNote("stay on task"))),
            ends = listOf(EndCondition.ManualStop, EndCondition.TriggerReversed, EndCondition.AtTime(t(21))),
        ),
        "timer length, implied" to doing(ActionSpec(ActionId.START_FOCUS_TIMER, ActionArgs.FocusTimer(25)), extraEnds = listOf(EndCondition.Duration(25))),
        "one-minute reminder, implied" to PatchSelection(
            sevenAm,
            actions = listOf(ActionSpec(ActionId.NOTIFY_RESULT, ActionArgs.Notify("The phone is not charging."))),
            ends = listOf(EndCondition.ManualStop, EndCondition.Duration(1)),
        ),
    )

    // ------------------------------------------------------------------ helper

    /**
     * Composes [selection], parses the sentence with an independent parser and
     * returns why it failed, or null when it round-tripped exactly.
     */
    private fun roundTripFailure(selection: PatchSelection): String? {
        val sentence = when (val result = patch.compose(selection)) {
            is PatchResult.Sentence -> result.text
            is PatchResult.Inexpressible -> return "refused (${result.part}): ${result.why}"
        }
        val drafted = parser.parse(sentence) as? DraftResult.Drafted ?: return "\"$sentence\" did not draft"
        if (drafted.unsupported.isNotEmpty()) return "\"$sentence\" flagged ${drafted.unsupported}"
        drafted.clauses.filter { it.kind == ClauseKind.UNACCOUNTED }.takeIf { it.isNotEmpty() }
            ?.let { return "\"$sentence\" left ${it.map { c -> c.text }} unaccounted" }
        val got = drafted.routine
        val want = Normalizer.normalize(got.copy(
            trigger = selection.trigger, conditions = selection.conditions,
            actions = selection.actions, endConditions = selection.ends,
        ))
        return when {
            got.trigger != want.trigger -> "\"$sentence\" trigger ${got.trigger} != ${want.trigger}"
            got.conditions != want.conditions -> "\"$sentence\" conditions ${got.conditions} != ${want.conditions}"
            got.actions != want.actions -> "\"$sentence\" actions ${got.actions} != ${want.actions}"
            got.endConditions != want.endConditions -> "\"$sentence\" ends ${got.endConditions} != ${want.endConditions}"
            else -> null
        }
    }

    private fun assertAllRoundTrip(samples: List<Pair<String, PatchSelection>>) {
        val failures = samples.mapNotNull { (name, selection) -> roundTripFailure(selection)?.let { "$name: $it" } }
        if (failures.isNotEmpty()) fail("${failures.size} of ${samples.size} did not round-trip:\n" + failures.joinToString("\n"))
    }

    // ------------------------------------------------------------------- tests

    @Test
    fun `every trigger kit round-trips through the grammar`() = assertAllRoundTrip(triggerSamples)

    @Test
    fun `every condition kit in the patch bay round-trips through the grammar`() = assertAllRoundTrip(conditionSamples)

    @Test
    fun `every action round-trips through the grammar`() = assertAllRoundTrip(actionSamples)

    @Test
    fun `every ending round-trips through the grammar`() = assertAllRoundTrip(endSamples)

    @Test
    fun `a full patch with several of each round-trips as one sentence`() {
        val everything = PatchSelection(
            trigger = earbudsIn,
            conditions = listOf(Condition.DaysOfWeek(WEEKDAYS), Condition.TimeWindow(t(18), t(0)), Condition.CalendarNotBusy),
            actions = listOf(
                ActionSpec(ActionId.START_FOCUS_TIMER, ActionArgs.FocusTimer(45)),
                quiet,
                ActionSpec(ActionId.USE_UTILITY, ActionArgs.UseUtility(UtilityId.EYE_PROTECTION, UtilityState.ON)),
                ActionSpec(ActionId.PINNED_NOTE, ActionArgs.PinnedNote("no email today")),
            ),
            ends = connectionEnds + EndCondition.Duration(45),
        )

        assertEquals(null, roundTripFailure(everything))
    }

    @Test
    fun `every kit in the registries has a sample or a stated reason it is not in the patch bay`() {
        val all = triggerSamples + conditionSamples + actionSamples + endSamples
        val sampledTriggers = all.map { it.second.trigger::class }.toSet()
        val sampledConditions = all.flatMap { it.second.conditions }.map { it::class }.toSet()
        val sampledActions = all.flatMap { it.second.actions }.map { it.actionId }.toSet()
        val sampledEnds = all.flatMap { it.second.ends }.map { it::class }.toSet()
        fun uncovered(types: List<KClass<*>>, sampled: Set<KClass<*>>) =
            types.filter { it !in sampled && it !in PatchSentence.unavailable }

        assertEquals(emptyList(), uncovered(SignalRegistry.triggerKits.map { it.type }, sampledTriggers))
        assertEquals(emptyList(), uncovered(SignalRegistry.conditionKits.map { it.type }, sampledConditions))
        assertEquals(emptyList(), uncovered(SignalRegistry.endKits.map { it.type }, sampledEnds))
        assertEquals(emptyList(), ActionRegistry.all.map { it.id }.filter { it !in sampledActions })
    }

    @Test
    fun `a kit listed as unavailable is refused with its reason, never phrased`() {
        // The phrase grammar has no Wi-Fi *condition* (only a Wi-Fi trigger), so
        // a chip for it would promise something the parser cannot express.
        val result = patch.compose(gated(Condition.WifiConnected()))

        val refused = assertIs<PatchResult.Inexpressible>(result)
        assertEquals("conditions", refused.part)
        assertEquals(PatchSentence.unavailable.getValue(Condition.WifiConnected::class), refused.why)
    }

    // ------------------------------------------------ refusals, each with a reason

    private fun assertRefused(selection: PatchSelection, part: String) {
        val result = patch.compose(selection)
        val refused = assertIs<PatchResult.Inexpressible>(result, "expected a refusal, got $result")
        assertEquals(part, refused.part, refused.why)
        assertTrue(refused.why.isNotBlank())
    }

    @Test
    fun `selections the grammar cannot say are refused, naming the part that does not fit`() {
        // Named Wi-Fi needs location access this build does not request.
        assertRefused(PatchSelection(Trigger.WifiConnection(DeviceTransition.CONNECTED, WifiNetwork.Named("Office")), actions = listOf(quiet), ends = connectionEnds), "trigger")
        // Trigger days also read as a day condition; the bay puts days in IF.
        assertRefused(PatchSelection(Trigger.AtTime(t(7), days = WEEKDAYS), actions = listOf(quiet)), "trigger")
        // The grammar's headphone words carry no wired/Bluetooth distinction.
        assertRefused(PatchSelection(Trigger.AudioOutput(AudioTransition.ADDED, AudioKind.WIRED), actions = listOf(quiet)), "trigger")
        // "for 45 minutes" would draft a 45-minute focus timer.
        assertRefused(PatchSelection(charger, actions = listOf(quiet), ends = listOf(EndCondition.ManualStop, EndCondition.Duration(45))), "ends")
        // Manual stop is never optional, and a connection cue always ends with its connection.
        assertRefused(PatchSelection(charger, actions = listOf(quiet), ends = emptyList()), "ends")
        assertRefused(PatchSelection(earbudsIn, actions = listOf(quiet)), "ends")
        // Free text comes back lower-cased.
        assertRefused(doing(ActionSpec(ActionId.PINNED_NOTE, ActionArgs.PinnedNote("Call Priya"))), "actions")
        // The grammar reads "unplug" as this trigger but never accounts for
        // the word, so Review would block it. A parser gap, disclosed here.
        assertRefused(PatchSelection(Trigger.Charging(PowerTransition.UNPLUGGED), actions = listOf(quiet)), "trigger")
        // A calendar title that is itself a timer phrase would add a timer.
        assertRefused(doing(ActionSpec(ActionId.ADD_CALENDAR_EVENT, ActionArgs.CalendarEvent("deep work", 30))), "actions")
        // A device with no alias the grammar knows.
        assertRefused(PatchSelection(earbudsIn.copy(deviceId = "unknown"), actions = listOf(quiet), ends = connectionEnds), "trigger")
    }

    @Test
    fun `a combination the grammar would read differently is refused by the self-check, not phrased`() {
        // A charging *condition* on a Bluetooth cue: the grammar reads the
        // word "charging" as the trigger, whatever else the sentence says.
        val result = patch.compose(
            PatchSelection(earbudsIn, listOf(Condition.ChargingState(true)), listOf(quiet), connectionEnds),
        )

        assertIs<PatchResult.Inexpressible>(result, "the sentence would draft a different cue: $result")
    }
}
