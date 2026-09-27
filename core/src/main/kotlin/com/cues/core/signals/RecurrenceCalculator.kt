package com.cues.core.signals

import com.cues.core.model.RecurrenceUnit
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Pure day/month/year arithmetic for [com.cues.core.model.Trigger.RecurringInterval].
 *
 * A weekly [com.cues.core.model.Trigger.AtTime] cannot express "every 3 days"
 * or "every 6 months", so this is separate rather than an extension of
 * [AtTimeKit]. Delegated to `java.time` rather than a bounded loop so month
 * and year arithmetic (Jan 31 + 1 month = Feb 28/29) is handled exactly the
 * way the platform's own calendar math already handles it, and so both
 * directions of the calculation (is a date due, and when is the next one)
 * agree with each other by construction.
 */
object RecurrenceCalculator {

    /** True when [candidate] is exactly [value] whole [unit]s after [anchor]. */
    fun isDue(anchor: LocalDate, value: Int, unit: RecurrenceUnit, candidate: LocalDate): Boolean {
        require(value >= 1) { "intervalValue must be at least 1, was $value" }
        if (candidate.isBefore(anchor)) return false
        return when (unit) {
            RecurrenceUnit.DAYS -> ChronoUnit.DAYS.between(anchor, candidate) % value == 0L
            RecurrenceUnit.MONTHS -> monthsSince(anchor, candidate)?.let { it % value == 0L } ?: false
            RecurrenceUnit.YEARS -> yearsSince(anchor, candidate)?.let { it % value == 0L } ?: false
        }
    }

    /**
     * The earliest occurrence that is not before [after] — [anchor] itself
     * when [after] has not reached it yet. A short verifying walk (at most a
     * couple of steps) rather than a single division, because month/year
     * end-of-month clamping can make the naive division land one occurrence
     * early or late.
     */
    fun nextOccurrence(anchor: LocalDate, value: Int, unit: RecurrenceUnit, after: LocalDate): LocalDate {
        require(value >= 1) { "intervalValue must be at least 1, was $value" }
        if (!after.isAfter(anchor)) return anchor
        val elapsed = when (unit) {
            RecurrenceUnit.DAYS -> ChronoUnit.DAYS.between(anchor, after)
            RecurrenceUnit.MONTHS -> ChronoUnit.MONTHS.between(anchor, after)
            RecurrenceUnit.YEARS -> ChronoUnit.YEARS.between(anchor, after)
        }
        var index = elapsed / value
        var candidate = advance(anchor, unit, index * value)
        while (candidate.isBefore(after)) {
            index += 1
            candidate = advance(anchor, unit, index * value)
        }
        return candidate
    }

    private fun advance(anchor: LocalDate, unit: RecurrenceUnit, count: Long): LocalDate = when (unit) {
        RecurrenceUnit.DAYS -> anchor.plusDays(count)
        RecurrenceUnit.MONTHS -> anchor.plusMonths(count)
        RecurrenceUnit.YEARS -> anchor.plusYears(count)
    }

    /**
     * The whole-month count between [anchor] and [candidate], but only when
     * [candidate] is exactly the clamped result of advancing [anchor] by that
     * many months — e.g. an anchor of Jan 31 is only "due" on Feb 28/29 and
     * Mar 31, not on every day in between.
     *
     * [ChronoUnit.MONTHS]'s own `between` is a coarse day-position estimate,
     * not a "was the month completed" answer — for Jan 31 to Feb 28 it
     * reports 0, because day 28 has not reached day 31's position. When the
     * target month is shorter than the anchor's day-of-month, the true count
     * is exactly one more than that estimate (Jan 31 + 1 clamped month IS
     * Feb 28), so both the raw estimate and estimate-plus-one are checked
     * against the actual clamped date, and whichever one matches wins.
     */
    private fun monthsSince(anchor: LocalDate, candidate: LocalDate): Long? {
        val estimate = ChronoUnit.MONTHS.between(anchor, candidate)
        return listOf(estimate, estimate + 1).firstOrNull { anchor.plusMonths(it) == candidate }
    }

    private fun yearsSince(anchor: LocalDate, candidate: LocalDate): Long? {
        val estimate = ChronoUnit.YEARS.between(anchor, candidate)
        return listOf(estimate, estimate + 1).firstOrNull { anchor.plusYears(it) == candidate }
    }
}
