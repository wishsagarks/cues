package com.cues.core.drafting

import com.cues.core.compile.Normalizer
import com.cues.core.model.*
import com.cues.core.registry.ActionRegistry
import com.cues.core.signals.SignalRegistry

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
    /** Re-reads bonded devices after the user grants Bluetooth access. */
    private val pairedDeviceProvider: (() -> List<PairedDevice>)? = null,
    private val contextsProvider: () -> List<NamedContext> = { emptyList() },
    private val placesProvider: () -> List<Place> = { emptyList() },
    private val idGenerator: () -> String = { "routine-" + java.util.UUID.randomUUID() },
) : RoutineDrafter {

    override val id: DraftSourceId = DraftSourceId.GRAMMAR_PARSER

    override suspend fun draft(text: String): DraftResult = parse(text)

    /** Synchronous entry point, so the CLI and tests need no coroutine. */
    fun parse(text: String): DraftResult {
        val normalized = text.lowercase().replace(Regex("[\\u2018\\u2019]"), "'")
        val consumed = mutableListOf<IntRange>()

        // Read against the ORIGINAL text, not normalized: this is the one
        // place a real display label — "Spotify", not "spotify" — needs to
        // survive into the routine. Everything else this parser extracts is
        // meaning it derives itself, so case never matters; a picked app's
        // label is the one piece of data that passes straight through.
        val resolvedApp = OPEN_APP_RESOLVED.find(text)?.let { m ->
            ResolvedApp(m.groupValues[1].trim(), m.groupValues[2].trim())
        }

        var trigger = parseTrigger(normalized, consumed)

        if (trigger is AmbiguousDevice) {
            if (trigger.candidates.isEmpty()) {
                return DraftResult.NeedsClarification(
                    id,
                    "Pair the device named in this cue in Bluetooth, then try again. Cues will bind it to that exact device.",
                    about = "trigger.device",
                    deviceCandidates = emptyList(),
                    consumed = consumed,
                    clauses = ClauseAccounting.classify(text, consumed),
                )
            }
            return DraftResult.NeedsClarification(
                id,
                "Which device did you mean: ${trigger.candidates.joinToString(", ") { it.label }}?",
                about = "trigger.device",
                deviceCandidates = trigger.candidates,
                consumed = consumed,
                clauses = ClauseAccounting.classify(text, consumed),
            )
        }

        val conditions = parseConditions(normalized, consumed)
        // A named context is a gate, never an inferred trigger. In the terse
        // "while in Desk, start …" form the only honest event is the user's
        // explicit manual run; the review makes that visible.
        if (trigger == null && conditions.any { it is Condition.InContext || it is Condition.AtPlace }) {
            trigger = ResolvedTrigger(Trigger.Manual)
        }
        if (trigger == null) return noTriggerResult(normalized)
        val resolvedTrigger = (trigger as ResolvedTrigger).trigger
        val actions = parseActions(normalized, consumed, resolvedApp)

        // An "open X" request with no resolved-app marker yet: ask which
        // installed app the user meant before going any further, the same
        // priority AmbiguousDevice gets above. Skipped once the marker is
        // present, because that means the picker already ran and parseActions
        // has already turned it into a concrete ActionSpec.OpenApp.
        if (resolvedApp == null) {
            parseAppOpenRequest(normalized)?.let { appQuery ->
                return DraftResult.NeedsClarification(
                    id,
                    "Which app did you mean by \"$appQuery\"?",
                    about = "action.app",
                    appQuery = appQuery,
                    consumed = consumed,
                    clauses = ClauseAccounting.classify(text, consumed),
                )
            }
        }

        if (actions.isEmpty()) {
            return DraftResult.NeedsClarification(
                id,
                "What should happen? Cues can start a focus timer and quiet notifications.",
                about = "actions",
                consumed = consumed,
                clauses = ClauseAccounting.classify(text, consumed),
            )
        }

        val endConditions = parseEndConditions(normalized, consumed, actions, resolvedTrigger)
        val unsupported = findUnsupported(normalized, consumed) + namedWifiUnsupported(resolvedTrigger, normalized)

        if (resolvedTrigger is Trigger.WifiConnection && resolvedTrigger.network is WifiNetwork.Named) {
            return DraftResult.NeedsClarification(
                id,
                question = "Named Wi-Fi needs location access on this build; use any Wi-Fi until the device spike is decided.",
                about = "trigger.network",
                unsupported = unsupported,
                consumed = consumed,
                clauses = ClauseAccounting.classify(text, consumed),
            )
        }

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
                rearmPolicy = parseRearmPolicy(normalized, consumed),
                requiredCapabilities = ActionRegistry.capabilitiesFor(actions),
                status = RoutineStatus.DRAFT,
            ),
        )

        return DraftResult.Drafted(
            id, routine, unsupported,
            consumed = consumed,
            clauses = ClauseAccounting.classify(text, consumed),
        )
    }

    // ------------------------------------------------------------ triggers

    private fun parseRearmPolicy(text: String, consumed: MutableList<IntRange>): RearmPolicy {
        val oncePerDay = ONCE_PER_LOCAL_DAY.find(text)
        if (oncePerDay != null) consumed += oncePerDay.range
        return RearmPolicy(oncePerLocalDay = oncePerDay != null)
    }

    /**
     * Explains why nothing could start this cue.
     *
     * "When I get to the office" has a perfectly clear trigger that Cues cannot
     * observe. Answering that with a blank "what should start this?" wastes the
     * user's time and hides the real answer, so the limitation leads.
     */
    private fun noTriggerResult(text: String): DraftResult {
        val consumed = mutableListOf<IntRange>()
        val unsupported = findUnsupported(text, consumed)
        return DraftResult.NeedsClarification(
            id,
            question = unsupported.firstOrNull()?.explanation
                ?: "What should start this cue? Try naming a device connecting, or the charger.",
            about = "trigger",
            unsupported = unsupported,
            clauses = ClauseAccounting.classify(text, consumed),
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
        val availableDevices = pairedDeviceProvider?.invoke() ?: pairedDevices

        // Preserve the existing boundary: scheduled content is not a cue just
        // because it contains a clock phrase.
        if (Regex("\\b(?:every|each)\\s+(?:morning|evening|night|day|week)\\b").containsMatchIn(text) &&
            Regex("\\b(?:compile|news|content|summari[sz]e)\\b").containsMatchIn(text)
        ) return null

        Regex("\\b(?:plug|unplug|connect|disconnect)\\w*\\s+(?:my )?(wired|bluetooth|any )?(?:headphones|headset|audio)\\b").find(text)?.let { m ->
            val removed = Regex("\\b(unplug|disconnect)").containsMatchIn(m.value)
            val kind = when {
                m.value.contains("wired") -> AudioKind.WIRED
                m.value.contains("bluetooth") -> AudioKind.BLUETOOTH
                else -> AudioKind.ANY
            }
            consumed += m.range
            return ResolvedTrigger(Trigger.AudioOutput(if (removed) AudioTransition.REMOVED else AudioTransition.ADDED, kind))
        }

        Regex("\\b(?:arrive at|leave)\\s+([a-z][a-z0-9 _-]{0,30})").find(text)?.let { m ->
            val label = m.groupValues[1].trim().trimEnd('.', ',')
            val place = placesProvider().firstOrNull { it.label.equals(label, ignoreCase = true) }
            if (place != null) {
                consumed += m.range
                return ResolvedTrigger(Trigger.PlaceTransition(place.id, place.version, place.label, if (m.value.startsWith("leave")) PlaceTransitionKind.EXIT else PlaceTransitionKind.ENTER))
            }
        }

        Regex("\\b(?:at|around)\\s+($TIME)\\b").find(text)?.let { m ->
            val time = parseTime(m.groupValues[1]) ?: return@let
            consumed += m.range
            val days = parseScheduledDays(text, consumed)
            return ResolvedTrigger(Trigger.AtTime(time, days, "system"))
        }

        Regex("\\b(?:join|connect|disconnect|leave|lose)\\b[^.]{0,30}\\bwi-?fi\\b").find(text)?.let { m ->
            val disconnects = Regex("\\b(disconnect\\w*|leave|lose)\\b").containsMatchIn(m.value)
            val named = Regex("\\b([a-z][a-z0-9_-]{1,24})\\s+wi-?fi\\b").find(m.value)
                ?.groupValues?.get(1)
                ?.takeUnless { it in setOf("to", "my", "the", "a") }
            consumed += m.range
            return ResolvedTrigger(
                Trigger.WifiConnection(
                    if (disconnects) DeviceTransition.DISCONNECTED else DeviceTransition.CONNECTED,
                    named?.let { WifiNetwork.Named(it) } ?: WifiNetwork.Any,
                ),
            )
        }
        // Every CHARGER_WORDS phrase present must be consumed, not just the
        // first one in list order — "the charger is plugged in" says both
        // "charger" and "plugged in", and stopping at "charger" alone left
        // "plugged" surfacing as an unaccounted word (see ClauseAccounting's
        // former "plug"-only filler workaround for the same root cause).
        val chargerMatches = CHARGER_WORDS.mapNotNull { word -> Regex("\\b($word)\\b").find(text) }
        if (chargerMatches.isNotEmpty()) {
            val unplugged = Regex("\\b(unplug\\w*|disconnect\\w*|stop\\w* charging|off charge)\\b").containsMatchIn(text)
            chargerMatches.forEach { consumed += it.range }
            // "when my charger connects" is the ordinary equivalent of
            // "when my charger is plugged in". The charger phrase already
            // establishes the power trigger; the connection verb merely
            // names its transition, so account for it rather than forcing a
            // user to reword an otherwise exact, safe request.
            Regex("\\b(?:connect\\w*|plug\\w*)\\b").find(text)?.let { consumed += it.range }
            return ResolvedTrigger(
                Trigger.Charging(if (unplugged) PowerTransition.UNPLUGGED else PowerTransition.PLUGGED_IN),
            )
        }

        val matches = availableDevices.filter { device ->
            device.aliases.any { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(text) }
        }

        return when {
            matches.size > 1 -> AmbiguousDevice(matches)
            matches.size == 1 -> {
                val device = matches.single()
                device.aliases.forEach { alias ->
                    Regex("\\b${Regex.escape(alias)}\\b").find(text)?.let { consumed += it.range }
                }
                val disconnectMatch = Regex("\\b(disconnect\\w*|unpair\\w*|remove\\w*)\\b").find(text)
                val connectMatch = Regex("\\bconnect\\w*\\b").find(text)
                val disconnects = disconnectMatch != null && connectMatch == null
                // The connect/disconnect keyword itself is part of what the
                // trigger means, not decoration — it must be marked consumed
                // like the Wi-Fi and charger branches above already do.
                (if (disconnects) disconnectMatch else connectMatch)?.let { consumed += it.range }
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
                AmbiguousDevice(availableDevices)

            else -> null
        }
    }

    // ---------------------------------------------------------- conditions

    private fun parseConditions(text: String, consumed: MutableList<IntRange>): List<Condition> = buildList {
        Regex("\\b(?:while\\s+in|while i'm at|when i'm at)\\s+([a-z][a-z0-9 _-]{0,30}?)(?=\\s+(?:start|quiet|end|and)\\b|[.,]|$)").find(text)?.let { match ->
            val label = match.groupValues[1].trim().trimEnd('.', ',')
            val context = contextsProvider().firstOrNull { it.label.equals(label, ignoreCase = true) }
            if (context != null) {
                consumed += match.range
                add(Condition.InContext(context.id, context.version, context.label))
            }
        }
        Regex("\\bwhile i'm at\\s+([a-z][a-z0-9 _-]{0,30}?)(?=\\s+(?:start|quiet|end|and)\\b|[.,]|$)").find(text)?.let { match ->
            val label = match.groupValues[1].trim().trimEnd('.', ',')
            val place = placesProvider().firstOrNull { it.label.equals(label, ignoreCase = true) }
            if (place != null) {
                consumed += match.range
                add(Condition.AtPlace(place.id, place.version, place.label))
            }
        }
        Regex("\\b(?:wired|bluetooth|any )?(?:headphones|headset|audio) (?:is )?active\\b").find(text)?.let { match ->
            consumed += match.range
            val kind = when { match.value.contains("wired") -> AudioKind.WIRED; match.value.contains("bluetooth") -> AudioKind.BLUETOOTH; else -> AudioKind.ANY }
            add(Condition.AudioOutputActive(kind))
        }
        Regex("\\bbattery (?:is )?(?:below|under) (\\d{1,3})%?\\b").find(text)?.let { match ->
            consumed += match.range; add(Condition.BatteryBelow(match.groupValues[1].toInt()))
        }
        Regex("\\bbattery (?:is )?(?:at least|above) (\\d{1,3})%?\\b").find(text)?.let { match ->
            consumed += match.range; add(Condition.BatteryAtLeast(match.groupValues[1].toInt()))
        }
        // "calendar is free" must be checked before "calendar is busy" would
        // otherwise be tempted to match on the shared "calendar" word; the
        // free/not-busy phrasing is matched first and, on a hit, the busy
        // pattern below simply finds nothing left to match against.
        Regex("\\b(?:my )?calendar (?:is )?(?:free|clear|not busy)\\b").find(text)?.let { match ->
            consumed += match.range; add(Condition.CalendarNotBusy)
        }
        if (Condition.CalendarNotBusy !in this) {
            Regex("\\b(?:my )?calendar (?:is )?busy\\b").find(text)?.let { match ->
                consumed += match.range; add(Condition.CalendarBusy)
            }
        }
        parseDays(text, consumed)?.let { add(it) }
        parseTimeWindow(text, consumed)?.let { add(it) }
        Regex("\\b(?:the )?phone (?:is )?(?:not |isn't |isnt )?charging\\b").find(text)?.let { match ->
            val notCharging = Regex("\\b(?:not|isn't|isnt)\\b").containsMatchIn(match.value)
            consumed += match.range
            add(Condition.ChargingState(!notCharging))
        }
        Regex("\\b(?:device|phone)\\s+connected\\b").find(text)?.let { match ->
            // The grammar cannot resolve a device from this shorthand. It is
            // intentionally left as an unsupported clause rather than guessed.
            consumed += match.range
        }
        pairedDevices.firstOrNull { device ->
            device.aliases.any { alias ->
                Regex("\\b${Regex.escape(alias)}\\b\\s+(?:is\\s+)?connected\\b").containsMatchIn(text)
            }
        }?.let { device ->
            val match = Regex(
                "\\b(?:${device.aliases.joinToString("|") { Regex.escape(it) }})\\b\\s+(?:is\\s+)?connected\\b",
            ).find(text)
            if (match != null) {
                consumed += match.range
                add(Condition.DeviceConnected(device.id, device.label))
            }
        }
    }

    private fun parseScheduledDays(text: String, consumed: MutableList<IntRange>): Set<Day> {
        Regex("\\bevery\\s+(day|weekday|weekdays|weekend|weekends)\\b").find(text)?.let { match ->
            consumed += match.range
            return when (match.groupValues[1]) {
                "weekend", "weekends" -> WEEKEND
                "weekday", "weekdays" -> WEEKDAYS
                else -> Day.entries.toSet()
            }
        }
        Regex("\\b(?:on\\s+)?(weekdays?|weekends?)\\b").find(text)?.let { match ->
            consumed += match.range
            return if (match.value.contains("weekend")) WEEKEND else WEEKDAYS
        }
        // Named days after "on" remain conditions (including on a scheduled
        // trigger); only explicit "every Monday" belongs to the trigger.
        val named = DAY_WORDS.mapNotNull { (word, day) ->
            Regex("\\bevery\\s+$word(?:s)?\\b").find(text)?.let { it to day }
        }
        if (named.isNotEmpty()) {
            named.forEach { consumed += it.first.range }
            return named.map { it.second }.toSet()
        }
        return emptySet()
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
            Regex("\\b$word(?:s)?\\b").find(text)?.let { it to day }
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

        Regex("\\b(?:before|till)\\s+($TIME)\\b").find(text)?.let { m ->
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

    /** An app the picker already resolved, carrying its original-cased label — see [parse]. */
    private data class ResolvedApp(val packageName: String, val label: String)

    private fun parseActions(text: String, consumed: MutableList<IntRange>, resolvedApp: ResolvedApp? = null): List<ActionSpec> = buildList {
        val timerMatch = Regex("\\b(\\d{1,3})[\\s-]*(minute|min|hour|hr)s?\\b").find(text)
        val wantsTimer = timerMatch != null || TIMER_WORD.containsMatchIn(text)

        if (wantsTimer) {
            val minutes = timerMatch?.let { m ->
                val n = m.groupValues[1].toInt()
                if (m.groupValues[2].startsWith("h")) n * 60 else n
            } ?: wordedDuration(text, consumed) ?: DEFAULT_FOCUS_MINUTES
            timerMatch?.let { consumed += it.range }
            // findAll, not find: "a 45-minute focus timer" says both "focus"
            // and "timer" — a single find() only consumed the first and left
            // the other to be silently waved through as filler.
            TIMER_WORD.findAll(text).forEach { consumed += it.range }
            add(ActionSpec(ActionId.START_FOCUS_TIMER, ActionArgs.FocusTimer(minutes)))
        }

        Regex("\\b(quiet|silence|mute|do not disturb|dnd|don't disturb)\\b").find(text)?.let {
            consumed += it.range
            Regex("\\bnotifications?\\b").find(text)?.let { n -> consumed += n.range }
            add(ActionSpec(ActionId.REQUEST_DND, ActionArgs.Dnd()))
        }

        Regex("\\b(?:remind me to charge|remind me about charging)\\b").find(text)?.let {
            consumed += it.range
            add(ActionSpec(ActionId.NOTIFY_RESULT, ActionArgs.Notify("The phone is not charging.")))
        }

        Regex("\\b(?:pin|pinned|keep)\\s+(?:a\\s+)?(?:note|message)\\b(?:\\s*(?:saying|that|:)?\\s+(.+?))?(?=\\s+until\\b|$)")
            .find(text)?.let { match ->
            consumed += match.range
            val message = match.groups[1]?.value?.trim()?.trim('"', '\'')?.takeIf { it.isNotBlank() }
                ?: "Cues session is active."
            add(ActionSpec(ActionId.PINNED_NOTE, ActionArgs.PinnedNote(message)))
        }

        // Deliberately worded to avoid "quiet|silence|mute|dnd|don't disturb" —
        // this is a different, owned resource from REQUEST_DND's zen rule, and
        // sharing a keyword with it would draft both actions for one phrase.
        RINGER_PATTERN.find(text)?.let { match ->
            consumed += match.range
            val mode = if ((match.groups[1] ?: match.groups[2])?.value == "vibrate") {
                RingerModeKind.VIBRATE
            } else {
                RingerModeKind.SILENT
            }
            add(ActionSpec(ActionId.RINGER_MODE, ActionArgs.RingerMode(mode)))
        }

        MEDIA_PATTERN.find(text)?.let { match ->
            consumed += match.range
            val command = when (match.groups[1]?.value) {
                "pause", "stop" -> MediaCommand.PAUSE
                "skip", "next" -> MediaCommand.NEXT
                "previous", "back" -> MediaCommand.PREVIOUS
                else -> MediaCommand.PLAY
            }
            add(ActionSpec(ActionId.MEDIA_CONTROL, ActionArgs.MediaControl(command)))
        }

        // Only present once the app picker has already run — see the
        // action.app clarification in parse(). packageName/label come solely
        // from [resolvedApp] (read against the original, case-preserved text),
        // never from surrounding prose in this lowercased copy.
        if (resolvedApp != null) {
            OPEN_APP_RESOLVED.find(text)?.let { consumed += it.range }
            OPEN_APP_REQUEST.find(text)?.let { consumed += it.range }
            add(ActionSpec(ActionId.OPEN_APP, ActionArgs.OpenApp(resolvedApp.packageName, resolvedApp.label)))
        }

        WHATSAPP_PATTERN.find(text)?.let { match ->
            consumed += match.range
            val contactHint = match.groups["contact"]?.value?.trim()?.takeIf { it.isNotBlank() }
            val body = match.groups["body"]?.value?.trim()?.trim('"', '\'')?.takeIf { it.isNotBlank() }
                ?: "Sent from Cues."
            add(ActionSpec(ActionId.COMPOSE_WHATSAPP, ActionArgs.ComposeWhatsApp(contactHint, body)))
        }

        // "Never sends" — see ActionRisk.HANDOFF. contactHint is whatever free
        // text follows "to", resolved by the OS share sheet, never by Cues.
        MESSAGE_PATTERN.find(text)?.let { match ->
            consumed += match.range
            val contactHint = match.groups["contact"]?.value?.trim()?.takeIf { it.isNotBlank() }
            val body = match.groups["body"]?.value?.trim()?.trim('"', '\'')?.takeIf { it.isNotBlank() }
                ?: "Sent from Cues."
            add(ActionSpec(ActionId.COMPOSE_MESSAGE, ActionArgs.ComposeMessage(contactHint, body)))
        }

        CALENDAR_PATTERN.find(text)?.let { match ->
            consumed += match.range
            val title = match.groups["title"]?.value?.trim()?.trim('"', '\'')?.takeIf { it.isNotBlank() }
                ?: "Cues event"
            add(ActionSpec(ActionId.ADD_CALENDAR_EVENT, ActionArgs.CalendarEvent(title, DEFAULT_CALENDAR_MINUTES)))
        }

        ALARM_PATTERN.find(text)?.let { match ->
            parseTime(match.groupValues[1])?.let { time ->
                consumed += match.range
                add(ActionSpec(ActionId.SET_ALARM, ActionArgs.Alarm(time.hour, time.minute)))
            }
        }

        LINK_PATTERN.find(text)?.let { match ->
            consumed += match.range
            val url = match.groupValues[1].trimEnd('.', ',', ')', ';')
            add(ActionSpec(ActionId.OPEN_LINK, ActionArgs.OpenLink(url)))
        }

        UTILITY_PATTERN.find(text)?.let { match ->
            consumed += match.range
            val utilityId = when (match.groupValues[2]) {
                "eye protection" -> UtilityId.EYE_PROTECTION
                "ultra saver" -> UtilityId.ULTRA_SAVER
                else -> UtilityId.GAME_MODE
            }
            val state = if (match.groupValues[1] in setOf("turn on", "enable")) UtilityState.ON else UtilityState.OFF
            add(ActionSpec(ActionId.USE_UTILITY, ActionArgs.UseUtility(utilityId, state)))
        }
    }

    /** The app name text named after "open"/"launch", when no picker has resolved one yet. */
    private fun parseAppOpenRequest(text: String): String? =
        OPEN_APP_REQUEST.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }

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

        val duration = Regex("\\bfor\\s+($TIME|half an hour|an hour|one hour)\\b").find(text)?.let { match ->
            consumed += match.range
            parseTimeAsDuration(match.groupValues[1])
        }
        if (duration != null && timerMinutes == null) add(EndCondition.Duration(duration))

        Regex("\\b(?:until|till)\\s+($TIME)\\b").find(text)?.let { match ->
            parseTime(match.groupValues[1])?.let { time ->
                consumed += match.range
                add(EndCondition.AtTime(time, zoneId = "system"))
            }
        }

        if (trigger is Trigger.AtTime &&
            actions.any { it.actionId == ActionId.NOTIFY_RESULT } &&
            none { it is EndCondition.AtTime || it is EndCondition.Duration }
        ) {
            // A one-shot reminder is still a bounded session; it must not leave
            // an active record behind forever after the notification is shown.
            add(EndCondition.Duration(1))
        }

        // "until" covers phrasing like "...until I take them out", which
        // means the same ending as "end when I disconnect" but without an
        // end/stop/finish verb of its own.
        val saysDisconnect = Regex(
            "\\b(end|stop|finish|until)\\b[^.]{0,40}\\b(disconnect\\w*|unplug\\w*|remove\\w*|take\\w* (them )?out)\\b",
        ).find(text)

        if (saysDisconnect != null) {
            consumed += saysDisconnect.range
            add(EndCondition.TriggerReversed)
        } else if (trigger is Trigger.BluetoothConnection && trigger.transition == DeviceTransition.CONNECTED ||
            trigger is Trigger.WifiConnection && trigger.transition == DeviceTransition.CONNECTED
        ) {
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
    /**
     * A fragment reported here is disclosed, not dropped — [Unsupported] is
     * shown in the review with its explanation, so ClauseAccounting must not
     * *also* flag the same words as a blocking UNACCOUNTED clause. The match
     * span is added to [consumed] as a side effect so the two mechanisms
     * agree: text is either mapped, decorative filler, disclosed-but-unsupported,
     * or genuinely unaccounted — never disclosed *and* silently re-flagged.
     */
    private fun findUnsupported(text: String, consumed: MutableList<IntRange>): List<Unsupported> =
        UNSUPPORTED_PATTERNS.mapNotNull { (pattern, explanation) ->
            val match = pattern.find(text) ?: return@mapNotNull null
            // Test the keyword, not the greedy tail. The tail deliberately runs
            // to the end of the clause so the fragment reads naturally, and it
            // would otherwise overlap every range the parser already consumed.
            val keyword = match.groups[1]?.range ?: match.range
            if (consumed.any { it.overlaps(keyword) }) return@mapNotNull null
            consumed += match.range
            Unsupported(match.value.trim(), explanation)
        }.distinctBy { it.explanation }

    private fun titleFor(trigger: Trigger, actions: List<ActionSpec>): String {
        val what = when {
            actions.any { it.actionId == ActionId.START_FOCUS_TIMER } -> "Focus"
            actions.any { it.actionId == ActionId.REQUEST_DND } -> "Quiet"
            else -> "Cue"
        }
        val whenPart = "when ${SignalRegistry.describe(trigger)}"
        return "$what $whenPart"
    }

    private fun parseTimeAsDuration(raw: String): Int? {
        val normalized = raw.trim()
        if (normalized == "half an hour") return 30
        if (normalized == "an hour" || normalized == "one hour") return 60
        val match = Regex("^(\\d{1,3})(?:\\s*)(minute|min|hour|hr)s?$").find(normalized) ?: return null
        val value = match.groupValues[1].toIntOrNull() ?: return null
        return if (match.groupValues[2].startsWith("h")) value * 60 else value
    }

    private fun namedWifiUnsupported(trigger: Trigger, text: String): List<Unsupported> {
        val wifi = trigger as? Trigger.WifiConnection ?: return emptyList()
        if (wifi.network !is WifiNetwork.Named) return emptyList()
        return listOf(
            Unsupported(
                text,
                "Named Wi-Fi needs location access on this build; use any Wi-Fi until the device spike is decided.",
            ),
        )
    }

    private companion object {
        const val DEFAULT_FOCUS_MINUTES = 25
        const val TIME = "\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?|noon|midday|midnight"

        val CHARGER_WORDS = listOf("charger", "charging", "plugged in", "plug in", "on charge")
        val TIMER_WORD = Regex("\\b(focus|timer|pomodoro|deep work|session)\\b")
        val RINGER_PATTERN = Regex("\\b(?:ringer|phone)\\s*(?:to|on)\\s*(silent|vibrate)\\b|\\b(silent|vibrate)\\s+mode\\b")
        val MEDIA_PATTERN = Regex(
            "\\b(pause|stop|play|resume|skip|next|previous|back)\\s+(?:the\\s+)?(?:music|playback|song|track)\\b",
        )

        /** A machine-written marker only the app picker produces — see the `action.app` clarification in [parse]. */
        val OPEN_APP_RESOLVED = Regex("\\(selected app:\\s*([^|)]+)\\|([^)]+)\\)")

        /**
         * "Open X"/"launch X", asked about rather than guessed at.
         *
         * Excludes an http(s)/tel target so a link ("open https://…") is never
         * mistaken for an app-name request — [LINK_PATTERN] owns that case.
         */
        val OPEN_APP_REQUEST = Regex(
            "\\b(?:open|launch)\\s+(?:the\\s+)?(?!https?://|tel:)([a-z][a-z0-9 &'-]{1,40}?)(?:\\s+app)?" +
                "(?=\\s+(?:and|when|if|until)\\b|[.,]|$)",
        )

        val MESSAGE_PATTERN = Regex(
            "\\b(?:text|message)\\s+(?:(?<contact>my\\s+\\w+|him|her|them|[a-z]+)\\s+)?" +
                "(?:saying|that|:)\\s+(?<body>.+?)(?=\\s+(?:and|when|if|until|once\\s+per\\s+(?:local\\s+)?day)\\b|[.,]|$)",
        )

        val WHATSAPP_PATTERN = Regex(
            "\\b(?:send\\s+(?:a\\s+)?)?whats\\s*app\\s+" +
                "(?:(?:message|text)\\s+)?(?:(?:to\\s+)?(?<contact>[a-z][a-z0-9 _-]{0,30})\\s+)?" +
                "(?:saying|that|:)\\s+(?<body>.+?)(?=\\s+(?:and|when|if|until|once\\s+per\\s+(?:local\\s+)?day)\\b|[.,]|$)",
        )

        val ONCE_PER_LOCAL_DAY = Regex("\\bonce\\s+per\\s+(?:local\\s+)?day\\b")

        val CALENDAR_PATTERN = Regex(
            "\\b(?:add|create)\\s+(?:a\\s+)?calendar\\s+event" +
                "(?:\\s+(?:for|titled|called)\\s+(?<title>.+?))?(?=\\s+(?:and|when|if|until)\\b|[.,]|$)",
        )
        const val DEFAULT_CALENDAR_MINUTES = 30

        val ALARM_PATTERN = Regex("\\b(?:set|create)\\s+an?\\s+alarm\\s+(?:for|at)\\s+($TIME)\\b")

        val LINK_PATTERN = Regex("\\b(?:open|visit)\\s+(https?://\\S+|tel:\\S+)")

        // CL-23 item 7: the only phrasing that reaches USE_UTILITY. The
        // catalog's own labels ("Eye protection", "Ultra saver", "Game
        // Mode") are the only names recognized — a closed vocabulary over a
        // closed action, the same discipline every other action here keeps.
        val UTILITY_PATTERN = Regex(
            "\\b(turn on|turn off|enable|disable)\\s+(eye protection|ultra saver|game mode)\\b",
        )
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
            // A second device named in a condition ("if my watch is
            // connected") that isn't in the paired-device list above never
            // reaches `Condition.DeviceConnected` — without this, it was
            // silently dropped rather than disclosed. The keyword-overlap
            // check above already skips this when the device WAS resolved.
            Regex("\\bif\\s+(?:my\\s+)?(\\w+)\\s+(?:is\\s+)?connected\\b[^.]*") to
                "Cues can only condition on a paired device it already recognizes.",
            Regex("\\b(text|message|call|email|whatsapp|remind)\\s+(?:my|him|her|them|the)\\b[^.]*") to
                "Cues does not send messages or make calls.",
            Regex("\\b(open|launch)\\s+(?:the )?(?:app|spotify|youtube|maps)\\b[^.]*") to
                "Opening other apps is not in this build.",
            Regex("\\b(wi-?fi)\\b[^.]*") to
                "Wi-Fi is a planned trigger, but it is not available yet.",
            Regex("\\b(?:every|each) (morning|evening|night|day|week)\\b[^.]*") to
                "Time-scheduled content tasks aren't supported.",
            Regex("\\b(volume)\\b[^.]*") to
                "Cues does not change the volume.",
            Regex("\\b(brightness|wallpaper|airplane mode)\\b[^.]*") to
                "Cues does not change that system setting.",
        )

        fun IntRange.overlaps(other: IntRange): Boolean = first <= other.last && other.first <= last
    }
}
