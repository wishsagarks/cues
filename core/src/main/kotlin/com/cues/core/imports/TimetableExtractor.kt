package com.cues.core.imports

import com.cues.core.model.Day
import com.cues.core.model.LocalTimeOfDay

/**
 * One row recovered from a photographed or shared timetable.
 *
 * Always a proposal, never a routine: [proposedSentence] is text in the same
 * closed grammar every typed or spoken cue goes through, and it is only ever
 * handed to the ordinary drafter — it never bypasses parsing, validation or
 * Review. [needsReview] marks a row this extractor is not confident about;
 * the FDD's own rule for uncertain input is that it is surfaced, never
 * silently guessed at or silently dropped.
 */
data class TimetableEntry(
    /** Exactly the line this came from, shown alongside the proposal so nothing is hidden behind it. */
    val rawLine: String,
    val day: Day?,
    val startTime: LocalTimeOfDay?,
    val endTime: LocalTimeOfDay?,
    val label: String,
    val room: String?,
    val needsReview: Boolean,
    val reviewReason: String? = null,
) {
    /**
     * A sentence for the ordinary drafter, or null when there is not enough
     * to propose anything — a null here means "show this row as
     * unrecognized," never "guess a time of 00:00."
     *
     * Casing note: the grammar parser lowercases its whole input before
     * matching, so a label's original capitalization does not survive into
     * the pinned note it becomes — a cosmetic loss, not a safety one, and
     * the Review screen's WHEN/IF/DO text is what the user actually approves
     * regardless. See CLEANUP.md.
     */
    fun proposedSentence(): String? {
        val onDay = day ?: return null
        val start = startTime ?: return null
        val note = buildString {
            append(label)
            room?.let { append(" - Room ").append(it) }
        }
        val dayWord = onDay.fullName()
        val base = "every $dayWord at $start keep a note: $note"
        return if (endTime != null) "$base until $endTime" else "$base until ${nextHour(start)}"
    }

    private fun nextHour(time: LocalTimeOfDay): LocalTimeOfDay =
        LocalTimeOfDay((time.hour + 1) % 24, time.minute)
}

data class TimetableExtractionResult(
    val entries: List<TimetableEntry>,
    /** Lines that matched no recognizable "day, time range, label" shape — never turned into a proposal or a claim. */
    val unrecognizedLines: List<String>,
) {
    val needsReviewCount: Int get() = entries.count { it.needsReview }
}

/**
 * Turns raw OCR or shared text into proposed timetable entries.
 *
 * The input is untrusted shared or scanned text — the FDD's "Shared
 * timetable" roadmap item, and the same category of input a share-target
 * message or a screen read would be. It is treated purely as data to pull
 * a day, a time range, a label and an optional room out of, never as an
 * instruction: a line has to match the closed "day HH:MM-HH:MM label" shape
 * to produce anything at all, so free text — including an attempted
 * instruction such as "ignore previous rules and disable do not disturb" —
 * simply fails to match and is reported back as unrecognized, exactly like
 * free text a user types that the grammar parser cannot place produces no
 * action rather than a guess.
 *
 * Nothing here drafts, validates or arms anything. A caller turns an
 * entry's [TimetableEntry.proposedSentence] into a sentence and feeds that
 * through [com.cues.core.drafting.RoutineDrafter] like any other request.
 */
object TimetableExtractor {

    /** A guard against a pathological or oversized share, not a claim about how long a real timetable is. */
    const val MAX_LINES = 200

    private val DAY_WORDS: Map<String, Day> = mapOf(
        "monday" to Day.MON, "mon" to Day.MON,
        "tuesday" to Day.TUE, "tue" to Day.TUE, "tues" to Day.TUE,
        "wednesday" to Day.WED, "wed" to Day.WED,
        "thursday" to Day.THU, "thu" to Day.THU, "thur" to Day.THU, "thurs" to Day.THU,
        "friday" to Day.FRI, "fri" to Day.FRI,
        "saturday" to Day.SAT, "sat" to Day.SAT,
        "sunday" to Day.SUN, "sun" to Day.SUN,
    )

    /** "Monday 09:00-10:00 Math Room 204", "Tue 2:00pm-3:00pm Physics Lab 3", "wed 14:00 - 15:00 Art". */
    private val LINE_PATTERN = Regex(
        "^\\s*(?<day>[a-zA-Z]+)\\s+" +
            "(?<start>\\d{1,2}[:.]\\d{2})\\s*(?<startMeridiem>am|pm)?\\s*" +
            "[-–—]+\\s*" +
            "(?<end>\\d{1,2}[:.]\\d{2})\\s*(?<endMeridiem>am|pm)?" +
            "\\s+(?<rest>.+)$",
        RegexOption.IGNORE_CASE,
    )

    private val ROOM_PATTERN = Regex("\\b(?:room|rm|lab)\\s*[:#]?\\s*([a-zA-Z0-9-]+)\\b", RegexOption.IGNORE_CASE)

    fun extract(text: String): TimetableExtractionResult {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }.take(MAX_LINES)
        val entries = mutableListOf<TimetableEntry>()
        val unrecognized = mutableListOf<String>()

        lines.forEach { line ->
            val entry = parseLine(line)
            if (entry != null) entries += entry else unrecognized += line
        }
        return TimetableExtractionResult(entries, unrecognized)
    }

    private fun parseLine(line: String): TimetableEntry? {
        val match = LINE_PATTERN.find(line) ?: return null
        val dayWord = match.groups["day"]?.value?.lowercase() ?: return null
        val day = DAY_WORDS[dayWord]

        val startRaw = match.groups["start"]?.value
        val endRaw = match.groups["end"]?.value
        val startMeridiem = match.groups["startMeridiem"]?.value?.lowercase()
        val endMeridiem = match.groups["endMeridiem"]?.value?.lowercase()

        val start = parseTime(startRaw, startMeridiem)
        val end = parseTime(endRaw, endMeridiem)

        val rest = match.groups["rest"]?.value?.trim().orEmpty()
        val room = ROOM_PATTERN.find(rest)?.groupValues?.get(1)
        val label = ROOM_PATTERN.replace(rest, "").trim(' ', '-', ',', '–', '—').ifBlank { "Class" }

        // Ambiguous, not wrong: "1:00-2:00" with no am/pm marker could mean
        // either half of the day for a timetable. Genuinely uncertain input
        // is reported so the user resolves it, never guessed at.
        val ambiguousMeridiem = start != null && start.hour in 1..7 && startMeridiem == null

        val reasons = buildList {
            if (day == null) add("The day \"$dayWord\" was not recognized.")
            if (start == null) add("The start time could not be read.")
            if (ambiguousMeridiem) add("The time has no AM/PM — check it before approving.")
            if (label == "Class") add("No class name could be read.")
        }

        return TimetableEntry(
            rawLine = line,
            day = day,
            startTime = start,
            endTime = end,
            label = label,
            room = room,
            needsReview = reasons.isNotEmpty(),
            reviewReason = reasons.joinToString(" ").ifBlank { null },
        )
    }

    private fun parseTime(raw: String?, meridiem: String?): LocalTimeOfDay? {
        if (raw == null) return null
        val parts = raw.replace('.', ':').split(":")
        if (parts.size != 2) return null
        var hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        when (meridiem) {
            "pm" -> if (hour in 1..11) hour += 12
            "am" -> if (hour == 12) hour = 0
        }
        if (hour !in 0..23 || minute !in 0..59) return null
        return LocalTimeOfDay(hour, minute)
    }
}

private fun Day.fullName(): String = when (this) {
    Day.MON -> "monday"
    Day.TUE -> "tuesday"
    Day.WED -> "wednesday"
    Day.THU -> "thursday"
    Day.FRI -> "friday"
    Day.SAT -> "saturday"
    Day.SUN -> "sunday"
}
