package com.cues.core.workbench

import com.cues.core.compile.Normalizer
import com.cues.core.drafting.ClauseKind
import com.cues.core.drafting.DraftResult
import com.cues.core.drafting.GrammarParser
import com.cues.core.drafting.PairedDevice
import com.cues.core.model.*
import kotlin.reflect.KClass

/**
 * What the Workbench patch bay has wired up: one trigger, its gates, its
 * actions and its endings, as the same typed values a routine holds.
 *
 * [ends] must list every ending the cue will actually have, including the
 * ones the grammar always adds (a manual stop on every cue, the timer's own
 * length, a connection cue ending when the connection goes away). The bay
 * shows those as locked chips rather than pretending they are optional.
 */
data class PatchSelection(
    val trigger: Trigger,
    val conditions: List<Condition> = emptyList(),
    val actions: List<ActionSpec>,
    val ends: List<EndCondition> = listOf(EndCondition.ManualStop),
)

sealed interface PatchResult {
    /** A sentence the phrase grammar reads back as exactly this selection. */
    data class Sentence(val text: String) : PatchResult

    /** This selection has no sentence the grammar would read back faithfully. [part] names what does not fit. */
    data class Inexpressible(val part: String, val why: String) : PatchResult
}

/**
 * Turns a patch-bay selection into the canonical sentence "Compile to Review"
 * sends through `CueService.draft`.
 *
 * The Workbench never builds a [Routine] itself. It writes a sentence, and
 * that sentence goes down the one authoring path every other request takes,
 * so Review shows the drafter that genuinely drafted it and there is no
 * second road to an armed cue.
 *
 * Every sentence is checked before it is returned: [compose] parses its own
 * output with a [GrammarParser] built from the same devices, contexts and
 * places, and returns [PatchResult.Inexpressible] unless the parse is a clean
 * draft — nothing unsupported, nothing unaccounted — whose trigger,
 * conditions, actions and endings equal the selection's. A patch the parser
 * would read as something else is refused here, rather than silently
 * becoming a different cue at review. The parse is discarded; it is a check,
 * not a routine.
 *
 * Phrases are written against the grammar's own patterns, so they read a
 * little mechanically. That is deliberate: this text is the cue's source text
 * and is shown in Review, and a phrase the grammar maps word for word is the
 * only kind whose meaning Review can account for completely.
 */
class PatchSentence(
    private val devices: List<PairedDevice> = emptyList(),
    private val contexts: () -> List<NamedContext> = { emptyList() },
    private val places: () -> List<Place> = { emptyList() },
) {

    private val checker = GrammarParser(
        pairedDevices = devices,
        contextsProvider = contexts,
        placesProvider = places,
        idGenerator = { "patch-check" },
    )

    fun compose(selection: PatchSelection): PatchResult {
        unavailable[selection.trigger::class]?.let { return PatchResult.Inexpressible("trigger", it) }
        selection.conditions.firstNotNullOfOrNull { unavailable[it::class] }
            ?.let { return PatchResult.Inexpressible("conditions", it) }

        impliedEndsMissing(selection)?.let { return it }

        val trigger = triggerPhrase(selection.trigger)
        if (trigger is Phrase.No) return PatchResult.Inexpressible("trigger", trigger.why)

        val conditions = selection.conditions.map { conditionPhrase(it, selection.trigger) }
        conditions.filterIsInstance<Phrase.No>().firstOrNull()?.let { return PatchResult.Inexpressible("conditions", it.why) }

        val (pins, others) = selection.actions.partition { it.args is ActionArgs.PinnedNote }
        if (pins.size > 1) return PatchResult.Inexpressible("actions", "The grammar reads at most one pinned note.")
        val actions = others.map { actionPhrase(it) }
        actions.filterIsInstance<Phrase.No>().firstOrNull()?.let { return PatchResult.Inexpressible("actions", it.why) }
        val pin = pins.singleOrNull()?.let { actionPhrase(it) }
        if (pin is Phrase.No) return PatchResult.Inexpressible("actions", pin.why)

        val ends = explicitEnds(selection).map { endPhrase(it, selection.trigger) }
        ends.filterIsInstance<Phrase.No>().firstOrNull()?.let { return PatchResult.Inexpressible("ends", it.why) }
        val until = ends.filterIsInstance<Phrase.Until>().map { it.text }
        val endSentences = ends.filterIsInstance<Phrase.Yes>().map { it.text }

        // A pinned note's text runs to the end of the sentence (or to an
        // "until"), so it is always the last thing said, and never followed by
        // a full stop it would swallow. With no end sentence to come after it,
        // it is simply the last action; otherwise it follows the end sentences.
        val pinText = (pin as? Phrase.Yes)?.text
        val pinInline = pinText != null && endSentences.isEmpty()
        val actionTexts = actions.map { (it as Phrase.Yes).text } + listOfNotNull(pinText?.takeIf { pinInline })

        val main = buildList {
            (trigger as Phrase.Yes).text.takeIf { it.isNotEmpty() }?.let { add(it) }
            conditions.forEach { add((it as Phrase.Yes).text) }
            actionTexts.takeIf { it.isNotEmpty() }?.let { add(it.joinToString(" and ")) }
        }.joinToString(", ") + until.joinToString("") { " $it" }

        val text = buildString {
            append(main.replaceFirstChar { it.uppercase() })
            if (!pinInline) append('.')
            endSentences.forEach { append(' ').append(it) }
            if (pinText != null && !pinInline) append(' ').append(pinText.replaceFirstChar { it.uppercase() })
        }.trim()

        return verify(text, selection)
    }

    // --------------------------------------------------------------- verify

    private fun verify(text: String, selection: PatchSelection): PatchResult {
        val drafted = when (val parsed = checker.parse(text)) {
            is DraftResult.Drafted -> parsed
            is DraftResult.NeedsClarification -> return PatchResult.Inexpressible(
                "sentence", "The phrase grammar would ask a question instead: ${parsed.question}",
            )
            is DraftResult.Failed -> return PatchResult.Inexpressible("sentence", "The phrase grammar could not draft it.")
        }
        drafted.unsupported.firstOrNull()?.let {
            return PatchResult.Inexpressible("sentence", "The phrase grammar would flag \"${it.fragment}\": ${it.explanation}")
        }
        drafted.clauses.firstOrNull { it.kind == ClauseKind.UNACCOUNTED }?.let {
            return PatchResult.Inexpressible("sentence", "The phrase grammar would leave \"${it.text}\" unaccounted for.")
        }

        val got = drafted.routine
        val want = Normalizer.normalize(got.copy(
            trigger = selection.trigger,
            conditions = selection.conditions,
            actions = selection.actions,
            endConditions = selection.ends,
        ))
        return when {
            got.trigger != want.trigger -> mismatch("trigger", SignalReadback.trigger(got.trigger))
            got.conditions != want.conditions -> mismatch("conditions", got.conditions.joinToString { SignalReadback.condition(it) })
            got.actions != want.actions -> mismatch("actions", got.actions.joinToString { it.actionId.name.lowercase() })
            got.endConditions != want.endConditions -> mismatch("ends", got.endConditions.joinToString { SignalReadback.end(it) })
            else -> PatchResult.Sentence(text)
        }
    }

    private fun mismatch(part: String, readAs: String) =
        PatchResult.Inexpressible(part, "The phrase grammar would read this $part as: ${readAs.ifEmpty { "nothing" }}.")

    // --------------------------------------------------------------- endings

    /**
     * Endings the grammar adds on its own. A selection without them cannot
     * round-trip, and saying which one is kinder than a generic mismatch.
     */
    private fun impliedEndsMissing(selection: PatchSelection): PatchResult.Inexpressible? {
        if (EndCondition.ManualStop !in selection.ends) {
            return PatchResult.Inexpressible("ends", "Every cue can be stopped by hand; that ending is always on.")
        }
        timerMinutes(selection)?.let { minutes ->
            if (EndCondition.Duration(minutes) !in selection.ends) {
                return PatchResult.Inexpressible("ends", "A $minutes-minute focus timer always ends the cue after $minutes minutes.")
            }
        }
        if (endsOnReversalByDefault(selection.trigger) && EndCondition.TriggerReversed !in selection.ends) {
            return PatchResult.Inexpressible("ends", "A connection cue always ends when the connection goes away.")
        }
        return null
    }

    /** The endings that need words, in a stable order. Implied ones are left for the grammar to add. */
    private fun explicitEnds(selection: PatchSelection): List<EndCondition> {
        val timer = timerMinutes(selection)
        val oneShotReminder = oneShotReminderEnd(selection)
        return selection.ends.distinct().filterNot { end ->
            end == EndCondition.ManualStop ||
                (timer != null && end == EndCondition.Duration(timer)) ||
                (end == EndCondition.TriggerReversed && endsOnReversalByDefault(selection.trigger)) ||
                end == oneShotReminder
        }.sortedBy { if (it is EndCondition.AtTime) 1 else 0 }
    }

    /** The grammar gives a timed reminder a one-minute session when nothing else ends it. */
    private fun oneShotReminderEnd(selection: PatchSelection): EndCondition? {
        val reminder = selection.trigger is Trigger.AtTime && selection.actions.any { it.actionId == ActionId.NOTIFY_RESULT }
        val otherEnd = selection.ends.any { it is EndCondition.AtTime || (it is EndCondition.Duration && it.minutes != 1) }
        return if (reminder && !otherEnd && timerMinutes(selection) == null) EndCondition.Duration(1) else null
    }

    private fun timerMinutes(selection: PatchSelection): Int? =
        selection.actions.firstNotNullOfOrNull { (it.args as? ActionArgs.FocusTimer)?.durationMinutes }

    private fun endsOnReversalByDefault(trigger: Trigger) =
        (trigger is Trigger.BluetoothConnection && trigger.transition == DeviceTransition.CONNECTED) ||
            (trigger is Trigger.WifiConnection && trigger.transition == DeviceTransition.CONNECTED)

    private fun endPhrase(end: EndCondition, trigger: Trigger): Phrase = when (end) {
        is EndCondition.Duration -> when (end.minutes) {
            30 -> Phrase.Until("for half an hour")
            60 -> Phrase.Until("for an hour")
            // "for 45 minutes" reads to the grammar as a 45-minute focus timer.
            else -> Phrase.No("Without a focus timer, the grammar can only end a cue after half an hour or an hour.")
        }
        is EndCondition.AtTime -> when {
            end.days.isNotEmpty() -> Phrase.No("An ending time can't be limited to certain days in this grammar.")
            end.zoneId != "system" -> Phrase.No("An ending time uses the phone's own time zone in this grammar.")
            else -> Phrase.Until("until ${clock(end.time)}")
        }
        EndCondition.TriggerReversed -> when (trigger) {
            // Not "unplug": the grammar would read that as an unplug trigger.
            // And not "the charger" again: only its first mention is accounted for.
            is Trigger.Charging -> Phrase.Yes("End it if I remove it.")
            is Trigger.PlaceTransition -> Phrase.No("The grammar has no words for ending a place cue when you leave.")
            is Trigger.AtTime, Trigger.Manual -> Phrase.No("There is no connection to go away for this trigger.")
            else -> Phrase.Yes("End it if I disconnect.")
        }
        EndCondition.ManualStop -> Phrase.Yes("")
    }

    // -------------------------------------------------------------- triggers

    private fun triggerPhrase(trigger: Trigger): Phrase = when (trigger) {
        is Trigger.BluetoothConnection -> aliasFor(trigger.deviceId)?.let { alias ->
            Phrase.Yes("when my $alias ${if (trigger.transition == DeviceTransition.CONNECTED) "connect" else "disconnect"}")
        } ?: Phrase.No("${trigger.deviceLabel} has no name the phrase grammar recognizes.")
        is Trigger.Charging -> if (trigger.transition == PowerTransition.PLUGGED_IN) {
            Phrase.Yes("when I plug in the charger")
        } else {
            // The grammar does read "unplug" as this trigger, but never marks
            // the word itself as accounted for, so Review would block the cue
            // on its own trigger word. Refused until the parser consumes it.
            Phrase.No("The phrase grammar leaves \"unplug\" unaccounted for, so Review would block this cue.")
        }
        // No words of its own: the grammar makes a cue manual when it names a
        // context or place and no event. The check rejects any other shape.
        Trigger.Manual -> Phrase.Yes("")
        is Trigger.WifiConnection -> when {
            trigger.network is WifiNetwork.Named -> Phrase.No("A named Wi-Fi network needs location access this build does not use.")
            trigger.transition == DeviceTransition.CONNECTED -> Phrase.Yes("when I connect to wifi")
            // "from the": the grammar takes the word before "wifi" as a
            // network name unless it is one of its stop words.
            else -> Phrase.Yes("when I disconnect from the wifi")
        }
        is Trigger.AtTime -> when {
            // "at 7:00 on weekdays" also reads as a separate day condition.
            trigger.days.isNotEmpty() -> Phrase.No("Put the days in IF instead; the grammar reads trigger days as a condition too.")
            trigger.zoneId != "system" -> Phrase.No("A scheduled time uses the phone's own time zone in this grammar.")
            else -> Phrase.Yes("at ${clock(trigger.time)}")
        }
        is Trigger.AudioOutput -> if (trigger.kind != AudioKind.ANY) {
            Phrase.No("The grammar only reads headphones of any kind, not wired or Bluetooth specifically.")
        } else {
            Phrase.Yes(if (trigger.transition == AudioTransition.ADDED) "when I connect my headphones" else "when I disconnect my headphones")
        }
        is Trigger.PlaceTransition -> Phrase.Yes(
            "when I ${if (trigger.transition == PlaceTransitionKind.ENTER) "arrive at" else "leave"} ${trigger.label}",
        )
    }

    // ------------------------------------------------------------ conditions

    private fun conditionPhrase(condition: Condition, trigger: Trigger): Phrase = when (condition) {
        is Condition.DaysOfWeek -> when {
            condition.days.isEmpty() -> Phrase.No("No days are selected.")
            // A scheduled trigger would also take "weekdays" as its own days.
            trigger !is Trigger.AtTime && condition.days == WEEKDAYS -> Phrase.Yes("on weekdays")
            trigger !is Trigger.AtTime && condition.days == WEEKEND -> Phrase.Yes("on weekends")
            else -> Phrase.Yes("on " + Day.entries.filter { it in condition.days }.joinToString(", ") { DAY_WORDS.getValue(it) })
        }
        is Condition.TimeWindow -> {
            val start = condition.startInclusive
            val end = condition.endExclusive
            when {
                start.minutesOfDay != 0 && end.minutesOfDay == 0 -> Phrase.Yes("after ${clock(start)}")
                start.minutesOfDay == 0 && end.minutesOfDay != 0 -> Phrase.Yes("before ${clock(end)}")
                else -> Phrase.Yes("between ${clock(start)} and ${clock(end)}")
            }
        }
        is Condition.ChargingState ->
            Phrase.Yes(if (condition.charging) "while the phone is charging" else "while the phone is not charging")
        is Condition.DeviceConnected -> aliasFor(condition.deviceId)?.let { Phrase.Yes("with my $it connected") }
            ?: Phrase.No("${condition.deviceLabel} has no name the phrase grammar recognizes.")
        is Condition.WifiConnected -> Phrase.No(unavailable.getValue(Condition.WifiConnected::class))
        is Condition.InContext -> Phrase.Yes("while in ${condition.label}")
        is Condition.AudioOutputActive -> if (condition.kind != AudioKind.ANY) {
            Phrase.No("The grammar only reads audio of any kind, not wired or Bluetooth specifically.")
        } else Phrase.Yes("while audio is active")
        is Condition.BatteryBelow -> Phrase.Yes("while battery is below ${condition.percent}%")
        is Condition.BatteryAtLeast -> Phrase.Yes("while battery is at least ${condition.percent}%")
        is Condition.AtPlace -> Phrase.Yes("while I'm at ${condition.label}")
        Condition.CalendarBusy -> Phrase.Yes("while my calendar is busy")
        Condition.CalendarNotBusy -> Phrase.Yes("while my calendar is free")
    }

    // --------------------------------------------------------------- actions

    private fun actionPhrase(spec: ActionSpec): Phrase = when (val args = spec.args) {
        is ActionArgs.FocusTimer -> Phrase.Yes("start a ${args.durationMinutes}-minute focus timer")
        is ActionArgs.Dnd -> if (args.allowPriority) Phrase.Yes("quiet notifications")
        else Phrase.No("The grammar's quiet rule always lets priority through.")
        is ActionArgs.Notify -> if (args.message == CHARGE_REMINDER) Phrase.Yes("remind me to charge")
        else Phrase.No("The grammar's only result note is the charging reminder.")
        is ActionArgs.PinnedNote -> lowercaseOnly(args.message, "A pinned note")
            ?: Phrase.Yes("pin a note: ${args.message}")
        // The same marker the installed-app picker appends; the comma lets
        // the grammar account for "open <app>" as the request it answers.
        is ActionArgs.OpenApp -> Phrase.Yes("open ${args.label}, (selected app: ${args.packageName}|${args.label})")
        is ActionArgs.ComposeMessage -> lowercaseOnly(args.text, "A message")
            ?: Phrase.Yes(listOfNotNull("text", args.contactHint, "saying", args.text).joinToString(" "))
        is ActionArgs.CalendarEvent -> when {
            args.durationMinutes != CALENDAR_MINUTES ->
                Phrase.No("A calendar event from the grammar is always $CALENDAR_MINUTES minutes long.")
            else -> lowercaseOnly(args.title, "An event title") ?: Phrase.Yes("add a calendar event for ${args.title}")
        }
        is ActionArgs.Alarm -> if (args.label != null) Phrase.No("The grammar can't name an alarm.")
        else Phrase.Yes("set an alarm for ${args.hour}:%02d".format(args.minute))
        is ActionArgs.MediaControl -> Phrase.Yes(
            when (args.command) {
                MediaCommand.PLAY -> "play the music"
                MediaCommand.PAUSE -> "pause the music"
                MediaCommand.NEXT -> "skip the track"
                MediaCommand.PREVIOUS -> "previous track"
            },
        )
        is ActionArgs.RingerMode -> Phrase.Yes(if (args.mode == RingerModeKind.SILENT) "phone on silent" else "phone on vibrate")
        is ActionArgs.OpenLink -> lowercaseOnly(args.url, "A link") ?: Phrase.Yes("open ${args.url}")
        is ActionArgs.UseUtility -> Phrase.Yes(
            "turn ${if (args.state == UtilityState.ON) "on" else "off"} " +
                when (args.utilityId) {
                    UtilityId.EYE_PROTECTION -> "eye protection"
                    UtilityId.ULTRA_SAVER -> "ultra saver"
                    UtilityId.GAME_MODE -> "game mode"
                },
        )
        ActionArgs.None -> Phrase.No("This action needs its details chosen first.")
    }

    /** The grammar reads free text in lower case, so anything else would come back changed. */
    private fun lowercaseOnly(text: String, what: String): Phrase.No? =
        if (text != text.lowercase()) Phrase.No("$what is read in lower case by the grammar; \"$text\" would change.") else null

    // --------------------------------------------------------------- helpers

    private fun aliasFor(deviceId: String): String? {
        val device = devices.firstOrNull { it.id == deviceId } ?: return null
        // The most specific name reads best ("earbuds" over "buds"). An alias
        // another device also answers to would make the grammar ask which one.
        return device.aliases.sortedWith(compareByDescending<String> { it.length }.thenBy { it })
            .firstOrNull { alias -> devices.none { it.id != deviceId && alias in it.aliases } }
    }

    private fun clock(time: LocalTimeOfDay) = "${time.hour}:%02d".format(time.minute)

    private sealed interface Phrase {
        data class Yes(val text: String) : Phrase
        /** An ending phrased inline ("until 21:00"), attached to the main clause rather than its own sentence. */
        data class Until(val text: String) : Phrase
        data class No(val why: String) : Phrase
    }

    companion object {
        /**
         * Kits the patch bay offers no chip for, and why. Everything else in
         * the closed registries is expressible, and `PatchSentenceTest` proves
         * it by round-tripping every one through the grammar.
         */
        val unavailable: Map<KClass<*>, String> = mapOf(
            Condition.WifiConnected::class to
                "The phrase grammar has no words for a Wi-Fi condition yet; use a Wi-Fi trigger instead.",
        )

        private const val CHARGE_REMINDER = "The phone is not charging."
        private const val CALENDAR_MINUTES = 30

        private val DAY_WORDS = mapOf(
            Day.MON to "monday", Day.TUE to "tuesday", Day.WED to "wednesday", Day.THU to "thursday",
            Day.FRI to "friday", Day.SAT to "saturday", Day.SUN to "sunday",
        )
    }
}

/** Compact names for a parse the check disagreed with — for the refusal message only, never for a receipt. */
private object SignalReadback {
    fun trigger(trigger: Trigger) = com.cues.core.signals.SignalRegistry.describe(trigger)
    fun condition(condition: Condition) = com.cues.core.signals.SignalRegistry.describe(condition)
    fun end(end: EndCondition) = com.cues.core.signals.SignalRegistry.describe(end)
}
