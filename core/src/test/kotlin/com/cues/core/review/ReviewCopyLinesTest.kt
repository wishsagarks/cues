package com.cues.core.review

import com.cues.core.Fixtures
import com.cues.core.compile.Normalizer
import com.cues.core.model.*
import com.cues.core.review.ReviewCopy.friendlyName
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class ReviewCopyLinesTest {

    private val hero = Normalizer.normalize(Fixtures.heroRoutine())

    // ---------------------------------------------------------- per-item lines

    @Test
    fun `the hero routine renders one row per condition, action and ending`() {
        assertEquals(listOf("Monday to Friday", "at or after 18:00 local time"), ReviewCopy.conditionLines(hero))
        assertEquals(
            listOf("start a 45-minute focus timer", "request our quiet-notifications rule"),
            ReviewCopy.actionLines(hero),
        )
        assertEquals(
            listOf("45 minutes have passed", "you stop it", "TWS Air Pro goes away"),
            ReviewCopy.endLines(hero),
        )
    }

    @Test
    fun `the joined sentences are exactly the lines joined, so the two can never disagree`() {
        assertEquals(ReviewCopy.conditionLines(hero).joinToString(", "), ReviewCopy.ifText(hero))
        assertEquals(ReviewCopy.actionLines(hero).joinToString(", "), ReviewCopy.doText(hero))
        assertEquals(ReviewCopy.endLines(hero).joinToString(", "), ReviewCopy.untilText(hero))
    }

    @Test
    fun `a routine with no conditions still has an IF row, and it says always`() {
        val routine = hero.copy(conditions = emptyList())

        assertEquals(listOf("always"), ReviewCopy.conditionLines(routine))
        assertEquals("always", ReviewCopy.ifText(routine))
    }

    @Test
    fun `every action gets its own row, including ones whose text contains commas`() {
        val routine = hero.copy(
            actions = listOf(
                ActionSpec(ActionId.PINNED_NOTE, ActionArgs.PinnedNote("tea, then code")),
                ActionSpec(ActionId.RINGER_MODE, ActionArgs.RingerMode(RingerModeKind.VIBRATE)),
                ActionSpec(ActionId.USE_UTILITY, ActionArgs.UseUtility(UtilityId.GAME_MODE, UtilityState.ON)),
            ),
        )

        assertEquals(
            listOf("keep \"tea, then code\" pinned", "set the ringer to vibrate", "turn game mode on"),
            ReviewCopy.actionLines(routine),
        )
    }

    // ------------------------------------------------------------------ labels

    /** Every label is a readable phrase: not the enum name, no underscores, not shouting, and unique. */
    private fun <E : Enum<E>> assertHumanised(entries: List<E>, label: (E) -> String) {
        val labels = entries.associateWith(label)
        labels.forEach { (entry, text) ->
            assertTrue(text.isNotBlank(), "$entry has a blank label")
            assertNotEquals(entry.name, text, "$entry is labelled with its raw enum name")
            assertFalse('_' in text, "$entry's label '$text' still has an underscore")
            assertNotEquals(text.uppercase(), text, "$entry's label '$text' is all capitals")
        }
        assertEquals(entries.size, labels.values.toSet().size, "two states share a label: $labels")
    }

    @Test
    fun `routine statuses are humanised`() {
        assertHumanised(RoutineStatus.entries) { it.friendlyName() }
        assertEquals("Armed", RoutineStatus.ARMED.friendlyName())
        assertEquals("Needs changes", RoutineStatus.INVALID.friendlyName())
    }

    @Test
    fun `session states are humanised, and a partial session never reads as plainly running`() {
        assertHumanised(SessionState.entries) { it.friendlyName() }
        assertEquals("Running", SessionState.ACTIVE.friendlyName())
        assertNotEquals("Running", SessionState.PARTIAL.friendlyName())
        assertEquals("Cleanup outstanding", SessionState.CLEANUP_PENDING.friendlyName())
    }

    @Test
    fun `action states are humanised, and blocked is never a kind of done`() {
        assertHumanised(ActionState.entries) { it.friendlyName() }
        assertEquals("Blocked", ActionState.BLOCKED.friendlyName())
        assertEquals("Waiting for you", ActionState.PENDING.friendlyName())
        assertEquals("Couldn't undo", ActionState.COMPENSATION_FAILED.friendlyName())
    }

    @Test
    fun `end reasons are humanised, and a coverage gap never claims a disconnect was seen`() {
        assertHumanised(EndReason.entries) { it.friendlyName() }
        assertEquals("You stopped it", EndReason.MANUAL_STOP.friendlyName())
        assertFalse("disconnect" in EndReason.COVERAGE_GAP.friendlyName().lowercase())
    }

    @Test
    fun `verification is humanised, and steps-only confirmation reads as assumed`() {
        assertHumanised(Verification.entries) { it.friendlyName() }
        assertEquals("Assumed", Verification.STEPS_CONFIRMED.friendlyName())
        assertEquals("Checked", Verification.READ_BACK.friendlyName())
    }
}
