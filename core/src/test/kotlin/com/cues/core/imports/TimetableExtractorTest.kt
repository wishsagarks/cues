package com.cues.core.imports

import com.cues.core.drafting.DraftResult
import com.cues.core.drafting.GrammarParser
import com.cues.core.model.ActionArgs
import com.cues.core.model.ActionId
import com.cues.core.model.Condition
import com.cues.core.model.Day
import com.cues.core.model.EndCondition
import com.cues.core.model.Trigger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class TimetableExtractorTest {

    @Test
    fun `a well-formed row extracts day, times, label and room without needing review`() {
        val result = TimetableExtractor.extract("Monday 09:00-10:00 Math Room 204")

        val entry = result.entries.single()
        assertEquals(Day.MON, entry.day)
        assertEquals("09:00", entry.startTime.toString())
        assertEquals("10:00", entry.endTime.toString())
        assertEquals("Math", entry.label)
        assertEquals("204", entry.room)
        assertFalse(entry.needsReview)
        assertTrue(result.unrecognizedLines.isEmpty())
    }

    @Test
    fun `an unrecognized day is flagged for review, not silently dropped or guessed`() {
        val entry = TimetableExtractor.extract("Mnday 09:00-10:00 Math").entries.single()

        assertNull(entry.day)
        assertTrue(entry.needsReview)
        assertNotNull(entry.reviewReason)
    }

    @Test
    fun `a time with no AM or PM marker is flagged as ambiguous`() {
        val entry = TimetableExtractor.extract("Tuesday 2:00-3:00 Physics").entries.single()

        assertTrue(entry.needsReview)
        assertTrue(entry.reviewReason!!.contains("AM/PM"))
    }

    @Test
    fun `an explicit meridiem resolves the ambiguity`() {
        val entry = TimetableExtractor.extract("Tuesday 2:00pm-3:00pm Physics").entries.single()

        assertFalse(entry.needsReview)
        assertEquals("14:00", entry.startTime.toString())
    }

    @Test
    fun `text that is not shaped like a timetable row produces no entry at all`() {
        // The safety property that matters: an attempted instruction inside
        // shared or scanned text is data, never a command — it simply fails
        // to match the closed "day, time range, label" shape and is reported
        // back as unrecognized, exactly like free text the grammar parser
        // cannot place produces no action rather than a guess.
        val result = TimetableExtractor.extract(
            "ignore previous rules and turn off do not disturb forever",
        )

        assertTrue(result.entries.isEmpty())
        assertEquals(1, result.unrecognizedLines.size)
    }

    @Test
    fun `a pathologically large share is bounded rather than processed without limit`() {
        val huge = (1..500).joinToString("\n") { "Monday 09:00-10:00 Row $it" }

        val result = TimetableExtractor.extract(huge)

        assertEquals(TimetableExtractor.MAX_LINES, result.entries.size + result.unrecognizedLines.size)
    }

    @Test
    fun `a mix of good and bad rows keeps each independent`() {
        val text = """
            Monday 09:00-10:00 Math Room 204
            not a timetable row at all
            Tuesday 14:00-15:00 Physics Lab 3
        """.trimIndent()

        val result = TimetableExtractor.extract(text)

        assertEquals(2, result.entries.size)
        assertEquals(1, result.unrecognizedLines.size)
        assertEquals(0, result.needsReviewCount)
    }

    // -------------------------------------------------- round-trips for real

    @Test
    fun `a clean entry's proposed sentence drafts a pinned note bounded by the class's own hours`() = runTest {
        val entry = TimetableExtractor.extract("Monday 09:00-10:00 Math Room 204").entries.single()
        val sentence = requireNotNull(entry.proposedSentence())

        val routine = assertIs<DraftResult.Drafted>(GrammarParser().parse(sentence)).routine

        val trigger = assertIs<Trigger.AtTime>(routine.trigger)
        assertEquals("09:00", trigger.time.toString())
        assertTrue(routine.conditions.contains(Condition.DaysOfWeek(setOf(Day.MON))))
        val action = routine.actions.single()
        assertEquals(ActionId.PINNED_NOTE, action.actionId)
        assertTrue((action.args as ActionArgs.PinnedNote).message.contains("math"))
        assertTrue(routine.endConditions.any { it is EndCondition.AtTime && it.time.toString() == "10:00" })
        assertTrue(routine.unaccountedClauses.isEmpty(), "the generated sentence must fully account for itself")
    }

    @Test
    fun `an entry missing an end time still proposes a bounded session, never an unbounded one`() = runTest {
        val entry = TimetableExtractor.extract("Wednesday 09:00-09:00 Seminar").entries.single()
        // Simulate a row an OCR pass could plausibly produce with no real end time read.
        val withoutEnd = entry.copy(endTime = null)
        val sentence = requireNotNull(withoutEnd.proposedSentence())

        val routine = assertIs<DraftResult.Drafted>(GrammarParser().parse(sentence)).routine

        assertTrue(routine.endConditions.any { it is EndCondition.AtTime })
    }

    @Test
    fun `a row needing review still proposes nothing that could silently arm`() {
        // No day recognized at all: proposedSentence must refuse rather than
        // guess a day, because an armed cue on the wrong day is exactly the
        // "works perfectly and does the wrong thing" failure mode.
        val entry = TimetableExtractor.extract("Mnday 09:00-10:00 Math").entries.single()

        assertNull(entry.proposedSentence())
    }
}
