package com.cues.core.drafting

import com.cues.core.compile.Normalizer
import com.cues.core.model.*
import com.cues.core.registry.ActionRegistry

/** A paired device the parser is allowed to resolve "my earbuds" to. */
data class PairedDevice(
    val id: String,
    val label: String,
    /** Words that name this device in speech: "earbuds", "buds", "headphones". */
    val aliases: Set<String>,
)

/**
 * A deterministic phrase grammar over the supported vocabulary.
 *
 * This is not an attempt at general language understanding and is never
 * presented as one. It recognises the shapes Cues supports, reports the
 * fragments it could not place, and asks when a reference is ambiguous.
 *
 * Its value at the event is that it is the path that cannot fail: no model
 * load, no thermal throttling, no cold-start latency, no variance between one
 * demo run and the next. The on-device model is scored against the same corpus
 * and takes over whenever it earns the slot.
 */
class GrammarParser(
    private val pairedDevices: List<PairedDevice> = emptyList(),
    private val idGenerator: () -> String = { "routine-" + java.util.UUID.randomUUID() },
) : RoutineDrafter {

    override val id: DraftSourceId = DraftSourceId.GRAMMAR_PARSER

    override suspend fun draft(text: String): DraftResult = parse(text)

    /** Synchronous entry point, so the CLI and tests need no coroutine. */
    fun parse(text: String): DraftResult {
        val normalized = text.lowercase().replace(Regex("[\\u2018\\u2019]"), "'")
        val consumed = mutableListOf<IntRange>()

        val trigger = parseTrigger(normalized, consumed)
            ?: return noTriggerResult(normalized)

        if (trigger is AmbiguousDevice) {
            return DraftResult.NeedsClarification(
                id,
                "Which device did you mean: ${trigger.candidates.joinToString(", ") { it.label }}?",
                about = "trigger.device",
            )
        }

        val resolvedTrigger = (trigger as ResolvedTrigger).trigger
        val conditions = parseConditions(normalized, consumed)
        val actions = parseActions(normalized, consumed)

        if (actions.isEmpty()) {
            return DraftResult.NeedsClarification(
                id,
                "What should happen? Cues can start a focus timer and quiet notifications.",
                about = "actions",
            )
        }

        val endConditions = parseEndConditions(normalized, consumed, actions, resolvedTrigger)
        val unsupported = findUnsupported(normalized, consumed)

        val routine = Normalizer.normalize(
            Routine(
                id = idGenerator(),
                version = 1,
                sourceText = text,
                title = titleFor(resolvedTrigger, actions),
                trigger = resolvedTrigger,
                conditions = conditions,
                actions = actions,
                endConditions = endConditions,
                cleanupPolicy = CleanupPolicy(),
                rearmPolicy = RearmPolicy(),
                requiredCapabilities = ActionRegistry.capabilitiesFor(actions),
                status = RoutineStatus.DRAFT,
            ),
        )

        return DraftResult.Drafted(id, routine, unsupported)
    }

    // ------------------------------------------------------------ triggers

    /**
     * Explains why nothing could start this cue.
     *
     * "When I get to the office" has a perfectly clear trigger that Cues cannot
     * observe. Answering that with a blank "what should start this?" wastes the
     * user's time and hides the real answer, so the limitation leads.
     */
    private fun noTriggerResult(text: String): DraftResult {
        val unsupported = findUnsupported(text, consumed = emptyList())
        return DraftResult.NeedsClarification(
            id,
            question = unsupported.firstOrNull()?.explanation
                ?: "What should start this cue? Try naming a device connecting, or the charger.",
            about = "trigger",
            unsupported = unsupported,
        )
    }

    /** Durations spelled out rather than written as digits. */
    private fun wordedDuration(text: String, consumed: MutableList<IntRange>): Int? {
        Regex("\\bhalf an hour\\b").find(text)?.let { consumed += it.range; return 30 }
        Regex("\\b(?:an|one|a)\\s+hour\\b").find(text)?.let { consumed += it.range; return 60 }
        return null
    }

    private sealed interface TriggerParse
    private data class ResolvedTrigger(val trigger: Trigger) : TriggerParse
    private data class AmbiguousDevice(val candidates: List<PairedDevice>) : TriggerParse

    private fun parseTrigger(text: String, consumed: MutableList<IntRange>): TriggerParse? {
        CHARGER_WORDS.forEach { word ->
            val match = Regex("\\b($word)\\b").find(text) ?: return@forEach
            val unplugged = Regex("\\b(unplug\\w*|disconnect\\w*|stop\\w* charging|off charge)\\b").containsMatchIn(text)
            consumed += match.range
            return ResolvedTrigger(
                Trigger.Charging(if (unplugged) PowerTransition.UNPLUGGED else PowerTransition.PLUGGED_IN),
            )
        }

        val matches = pairedDevices.filter { device ->
            device.aliases.any { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(text) }
        }

        return when {
            matches.size > 1 -> AmbiguousDevice(matches)
            matches.size == 1 -> {
                val device = matches.single()
                device.aliases.forEach { alias ->
                    Regex("\\b${Regex.escape(alias)}\\b").find(text)?.let { consumed += it.range }
                }
                val disconnects = Regex("\\b(disconnect\\w*|unpair\\w*|remove\\w*)\\b").containsMatchIn(text) &&
                    !Regex("\\bconnect\\w*\\b").containsMatchIn(text)
                ResolvedTrigger(
                    Trigger.BluetoothConnection(
                        device.id,
                        device.label,
                        if (disconnects) DeviceTransition.DISCONNECTED else DeviceTransition.CONNECTED,
                    ),
                )
            }

            // A device word with no paired match: the user is talking about
            // hardware we cannot resolve, which is a question, not a guess.
            DEVICE_WORDS.any { Regex("\\b$it\\b").containsMatchIn(text) } ->
                AmbiguousDevice(pairedDevices)

            else -> null
        }
    }

    // ---------------------------------------------------------- conditions

    private fun parseConditions(text: String, consumed: MutableList<IntRange>): List<Condition> = buildList {
        parseDays(text, consumed)?.let { add(it) }
        parseTimeWindow(text, consumed)?.let { add(it) }
    }

    private fun parseDays(text: String, consumed: MutableList<IntRange>): Condition.DaysOfWeek? {
        Regex("\\b(weekdays?|week days)\\b").find(text)?.let {
            consumed += it.range
            return Condition.DaysOfWeek(WEEKDAYS)
        }
        Regex("\\b(weekends?|week ends)\\b").find(text)?.let {
            consumed += it.range
            return Condition.DaysOfWeek(WEEKEND)
        }

        val named = DAY_WORDS.mapNotNull { (word, day) ->
            Regex("\\b$word\\b").find(text)?.let { it to day }
        }
        if (named.isEmpty()) return null
        named.forEach { consumed += it.first.range }
        return Condition.DaysOfWeek(named.map { it.second }.toSet())
    }

    private fun parseTimeWindow(text: String, consumed: MutableList<IntRange>): Condition.TimeWindow? {
        // "between 9 and 5" — checked first so its inner times are consumed.
        Regex("\\bbetween\\s+($TIME)\\s+(?:and|to|until)\\s+($TIME)\\b").find(text)?.let { m ->
            val start = parseTime(m.groupValues[1]) ?: return@let
            val end = parseTime(m.groupValues[2]) ?: return@let
            consumed += m.range
            return Condition.TimeWindow(start, end)
        }

        Regex("\\b(?:after|from|past)\\s+($TIME)\\b").find(text)?.let { m ->
            val start = parseTime(m.groupValues[1]) ?: return@let
            consumed += m.range
            // "After 6 PM" is normalized to a window running to midnight. The
            // review shows this interpretation rather than hiding it.
            return Condition.TimeWindow(start, LocalTimeOfDay(0, 0))
        }

        Regex("\\b(?:before|until|till)\\s+($TIME)\\b").find(text)?.let { m ->
            val end = parseTime(m.groupValues[1]) ?: return@let
            consumed += m.range
            return Condition.TimeWindow(LocalTimeOfDay(0, 0), end)
        }

        return null
    }

    /** Accepts "6pm", "6 pm", "18:00", "6:30 pm", "noon", "midnight". */
    private fun parseTime(raw: String): LocalTimeOfDay? {
        val t = raw.trim()
        if (t == "noon" || t == "midday") return LocalTimeOfDay(12, 0)
        if (t == "midnight") return LocalTimeOfDay(0, 0)

        val m = Regex("^(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?$").find(t) ?: return null
        var hour = m.groupValues[1].toIntOrNull() ?: return null
        val minute = m.groupValues[2].toIntOrNull() ?: 0
        val meridiem = m.groupValues[3]

        when (meridiem) {
            "am" -> if (hour == 12) hour = 0
            "pm" -> if (hour < 12) hour += 12
        }
        if (hour !in 0..23 || minute !in 0..59) return null
        return LocalTimeOfDay(hour, minute)
    }

    // ------------------------------------------------------------- actions

    private fun parseActions(text: String, consumed: MutableList<IntRange>): List<ActionSpec> = buildList {
        val timerMatch = Regex("\\b(\\d{1,3})[\\s-]*(minute|min|hour|hr)s?\\b").find(text)
        val wantsTimer = timerMatch != null ||
            Regex("\\b(focus|timer|pomodoro|deep work)\\b").containsMatchIn(text)

        if (wantsTimer) {
            val minutes = timerMatch?.let { m ->
                val n = m.groupValues[1].toInt()
                if (m.groupValues[2].startsWith("h")) n * 60 else n
            } ?: wordedDuration(text, consumed) ?: DEFAULT_FOCUS_MINUTES
            timerMatch?.let { consumed += it.range }
            Regex("\\b(focus|timer|pomodoro|deep work)\\b").find(text)?.let { consumed += it.range }
            add(ActionSpec(ActionId.START_FOCUS_TIMER, ActionArgs.FocusTimer(minutes)))
        }

        Regex("\\b(quiet|silence|mute|do not disturb|dnd|don't disturb)\\b").find(text)?.let {
            consumed += it.range
            Regex("\\bnotifications?\\b").find(text)?.let { n -> consumed += n.range }
            add(ActionSpec(ActionId.REQUEST_DND, ActionArgs.Dnd()))
        }
    }

    // ------------------------------------------------------------- endings

    private fun parseEndConditions(
        text: String,
        consumed: MutableList<IntRange>,
        actions: List<ActionSpec>,
        trigger: Trigger,
    ): List<EndCondition> = buildList {
        // Manual stop is always available. A cue the user cannot stop by hand
        // is not something we are willing to arm.
        add(EndCondition.ManualStop)

        val timerMinutes = actions.firstOrNull { it.actionId == ActionId.START_FOCUS_TIMER }
            ?.let { (it.args as? ActionArgs.FocusTimer)?.durationMinutes }
        if (timerMinutes != null) add(EndCondition.Duration(timerMinutes))

        val saysDisconnect = Regex(
            "\\b(end|stop|finish)\\b[^.]{0,40}\\b(disconnect\\w*|unplug\\w*|remove\\w*|take\\w* (them )?out)\\b",
        ).find(text)

        if (saysDisconnect != null) {
            consumed += saysDisconnect.range
            add(EndCondition.TriggerReversed)
        } else if (trigger is Trigger.BluetoothConnection && trigger.transition == DeviceTransition.CONNECTED) {
            // A connection-started cue ending when the connection goes away is
            // the expectation. It is added as a visible proposed default, shown
            // in the review, not smuggled in.
            add(EndCondition.TriggerReversed)
        }
    }

    // --------------------------------------------------------- leftover text

    /**
     * Reports request fragments the grammar could not place.
     *
     * Deliberately conservative: it looks for phrases that clearly ask for
     * something, rather than flagging every unconsumed word. A false "I did not
     * understand" on filler is noise; a missed "unless" is a rule that quietly
     * does the wrong thing.
     */
    private fun findUnsupported(text: String, consumed: List<IntRange>): List<Unsupported> =
        UNSUPPORTED_PATTERNS.mapNotNull { (pattern, explanation) ->
            val match = pattern.find(text) ?: return@mapNotNull null
            // Test the keyword, not the greedy tail. The tail deliberately runs
            // to the end of the clause so the fragment reads naturally, and it
            // would otherwise overlap every range the parser already consumed.
            val keyword = match.groups[1]?.range ?: match.range
            if (consumed.any { it.overlaps(keyword) }) return@mapNotNull null
            Unsupported(match.value.trim(), explanation)
        }.distinctBy { it.explanation }

    private fun titleFor(trigger: Trigger, actions: List<ActionSpec>): String {
        val what = when {
            actions.any { it.actionId == ActionId.START_FOCUS_TIMER } -> "Focus"
            actions.any { it.actionId == ActionId.REQUEST_DND } -> "Quiet"
            else -> "Cue"
        }
        val whenPart = when (trigger) {
            is Trigger.BluetoothConnection -> "when ${trigger.deviceLabel} ${
                trigger.transition.name.lowercase()
            }"

            is Trigger.Charging -> "when ${if (trigger.transition == PowerTransition.PLUGGED_IN) "charging" else "unplugged"}"
            Trigger.Manual -> "on demand"
        }
        return "$what $whenPart"
    }

    private companion object {
        const val DEFAULT_FOCUS_MINUTES = 25
        const val TIME = "\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?|noon|midday|midnight"

        val CHARGER_WORDS = listOf("charger", "charging", "plugged in", "plug in", "on charge")
        val DEVICE_WORDS = listOf(
            "earbuds", "ear buds", "buds", "headphones", "headset",
            "airpods", "speaker", "watch", "car",
        )

        val DAY_WORDS = mapOf(
            "monday" to Day.MON, "tuesday" to Day.TUE, "wednesday" to Day.WED,
            "thursday" to Day.THU, "friday" to Day.FRI, "saturday" to Day.SAT,
            "sunday" to Day.SUN,
        )

        /** Things people genuinely ask for that this build cannot do. */
        val UNSUPPORTED_PATTERNS: List<Pair<Regex, String>> = listOf(
            Regex("\\b(unless)\\b[^.]*") to
                "Cues cannot express an exception like this yet. Add it as a condition instead.",
            Regex(
                "\\b(?:when|once|if) i(?:'m| am)?\\s+" +
                    "(get to|getting to|reach|arrive|arriving|leave|leaving|at|in|near)\\b[^.]*",
            ) to "Location is not available in this build.",
            Regex("\\b(text|message|call|email|whatsapp|remind)\\s+(?:my|him|her|them|the)\\b[^.]*") to
                "Cues does not send messages or make calls.",
            Regex("\\b(open|launch)\\s+(?:the )?(?:app|spotify|youtube|maps)\\b[^.]*") to
                "Opening other apps is not in this build.",
            Regex("\\b(wi-?fi)\\b[^.]*") to
                "Wi-Fi is a planned trigger, but it is not available yet.",
            Regex("\\b(?:every|each) (morning|evening|night|day|week)\\b[^.]*") to
                "A repeating schedule is a planned trigger, but it is not available yet.",
            Regex("\\b(volume)\\b[^.]*") to
                "Cues does not change the volume.",
            Regex("\\b(brightness|wallpaper|airplane mode)\\b[^.]*") to
                "Cues does not change that system setting.",
        )

        fun IntRange.overlaps(other: IntRange): Boolean = first <= other.last && other.first <= last
    }
}
