package com.cues.core.signals

import com.cues.core.model.RecurrenceUnit
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class RecurrenceCalculatorTest {

    private val jan1 = LocalDate.of(2026, 1, 1)

    @Test
    fun `every 3 days is due on multiples of 3, not on the days between`() {
        assertTrue(RecurrenceCalculator.isDue(jan1, 3, RecurrenceUnit.DAYS, jan1))
        assertFalse(RecurrenceCalculator.isDue(jan1, 3, RecurrenceUnit.DAYS, jan1.plusDays(1)))
        assertFalse(RecurrenceCalculator.isDue(jan1, 3, RecurrenceUnit.DAYS, jan1.plusDays(2)))
        assertTrue(RecurrenceCalculator.isDue(jan1, 3, RecurrenceUnit.DAYS, jan1.plusDays(3)))
        assertTrue(RecurrenceCalculator.isDue(jan1, 3, RecurrenceUnit.DAYS, jan1.plusDays(6)))
    }

    @Test
    fun `a date before the anchor is never due`() {
        assertFalse(RecurrenceCalculator.isDue(jan1, 1, RecurrenceUnit.DAYS, jan1.minusDays(1)))
    }

    @Test
    fun `month-end clamping means a Jan 31 anchor is due on Feb 28, not every day of February`() {
        val jan31 = LocalDate.of(2026, 1, 31)
        assertTrue(RecurrenceCalculator.isDue(jan31, 1, RecurrenceUnit.MONTHS, LocalDate.of(2026, 2, 28)))
        assertFalse(RecurrenceCalculator.isDue(jan31, 1, RecurrenceUnit.MONTHS, LocalDate.of(2026, 2, 27)))
        assertFalse(RecurrenceCalculator.isDue(jan31, 1, RecurrenceUnit.MONTHS, LocalDate.of(2026, 2, 15)))
        assertTrue(RecurrenceCalculator.isDue(jan31, 1, RecurrenceUnit.MONTHS, LocalDate.of(2026, 3, 31)))
    }

    @Test
    fun `every 6 months from a leap-day anchor lands correctly`() {
        val leapDay = LocalDate.of(2024, 2, 29)
        assertTrue(RecurrenceCalculator.isDue(leapDay, 12, RecurrenceUnit.MONTHS, LocalDate.of(2025, 2, 28)))
    }

    @Test
    fun `every N years is due only on exact anniversaries`() {
        val anchor = LocalDate.of(2020, 6, 15)
        assertTrue(RecurrenceCalculator.isDue(anchor, 2, RecurrenceUnit.YEARS, LocalDate.of(2022, 6, 15)))
        assertFalse(RecurrenceCalculator.isDue(anchor, 2, RecurrenceUnit.YEARS, LocalDate.of(2021, 6, 15)))
    }

    @Test
    fun `nextOccurrence returns the anchor itself when asked before it`() {
        assertEquals(jan1, RecurrenceCalculator.nextOccurrence(jan1, 3, RecurrenceUnit.DAYS, jan1.minusDays(5)))
        assertEquals(jan1, RecurrenceCalculator.nextOccurrence(jan1, 3, RecurrenceUnit.DAYS, jan1))
    }

    @Test
    fun `nextOccurrence agrees with isDue for every day in a 20-day window`() {
        val anchor = LocalDate.of(2026, 1, 5)
        for (value in listOf(1, 2, 3, 5, 7)) {
            var probe = anchor
            repeat(20) {
                val next = RecurrenceCalculator.nextOccurrence(anchor, value, RecurrenceUnit.DAYS, probe)
                assertTrue(
                    RecurrenceCalculator.isDue(anchor, value, RecurrenceUnit.DAYS, next),
                    "nextOccurrence($probe) = $next is not itself due for interval $value",
                )
                assertFalse(next.isBefore(probe), "nextOccurrence($probe) = $next is before the query date")
                probe = probe.plusDays(1)
            }
        }
    }

    @Test
    fun `nextOccurrence across a month-end anchor never returns a date isDue rejects`() {
        val jan31 = LocalDate.of(2026, 1, 31)
        var probe = jan31
        repeat(120) {
            val next = RecurrenceCalculator.nextOccurrence(jan31, 1, RecurrenceUnit.MONTHS, probe)
            assertTrue(RecurrenceCalculator.isDue(jan31, 1, RecurrenceUnit.MONTHS, next))
            probe = probe.plusDays(1)
        }
    }
}
