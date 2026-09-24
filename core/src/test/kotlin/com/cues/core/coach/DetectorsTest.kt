package com.cues.core.coach

import com.cues.core.assistant.RefineOperation
import com.cues.core.model.ActionId
import com.cues.core.model.Day
import com.cues.core.model.Trigger
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class DetectorsTest {
    private val now = 1_800_000_000_000L

    @Test
    fun `early stop needs three of the last five sessions, and names its routine when unambiguous`() {
        val events = (0 until 5).map { index ->
            LedgerEvent.SessionEnded("study", 45, if (index < 3) 20 else 44, "manual", now - index * DAY)
        }
        val suggestion = assertIs<Detection.Suggest>(Detectors.earlyStop(events, now)).suggestion
        assertEquals(SuggestionKind.SHORTER_DURATION, suggestion.kind)
        assertEquals("study", suggestion.routineId)
        assertEquals(RefineOperation.SetDuration(20), suggestion.operation)
    }

    @Test
    fun `early stop over more than one routine cannot be accepted`() {
        val events = (0 until 5).map { index ->
            // Same shape as the single-routine case, but the most recent
            // session belongs to a different cue — CL-27's "never fabricate
            // attribution" rule: this can only be shown as evidence.
            val routineId = if (index == 0) "other-routine" else "study"
            LedgerEvent.SessionEnded(routineId, 45, if (index < 3) 20 else 44, "manual", now - index * DAY)
        }
        val suggestion = assertIs<Detection.Suggest>(Detectors.earlyStop(events, now)).suggestion
        assertNull(suggestion.routineId)
        assertNull(suggestion.operation)
    }

    @Test
    fun `recurring skip and blocked action cite evidence counts and a real operation`() {
        val skips = (0 until 3).map { LedgerEvent.PatchCreated("skip-today", Day.FRI, now - it * 7 * DAY, routineId = "study") }
        val blocked = listOf(
            LedgerEvent.ActionBlocked("OPEN_APP", now - DAY, routineId = "study"),
            LedgerEvent.ActionBlocked("OPEN_APP", now, routineId = "study"),
        )

        val recurring = assertIs<Detection.Suggest>(Detectors.recurringSkip(skips, now)).suggestion
        assertTrue(recurring.evidence.single().count >= 3)
        assertEquals("study", recurring.routineId)
        assertEquals(RefineOperation.RemoveDays(setOf(Day.FRI)), recurring.operation)

        val blockedSuggestion = assertIs<Detection.Suggest>(Detectors.blockedAction(blocked, now)).suggestion
        assertEquals(SuggestionKind.CHECK_OR_REMOVE_ACTION, blockedSuggestion.kind)
        assertEquals("study", blockedSuggestion.routineId)
        assertEquals(RefineOperation.RemoveAction(ActionId.OPEN_APP), blockedSuggestion.operation)
    }

    @Test
    fun `patch and blocked events with no recorded routine cannot be accepted either`() {
        val skips = (0 until 3).map { LedgerEvent.PatchCreated("skip-today", Day.FRI, now - it * 7 * DAY) }
        val blocked = listOf(LedgerEvent.ActionBlocked("OPEN_APP", now - DAY), LedgerEvent.ActionBlocked("OPEN_APP", now))

        val recurring = assertIs<Detection.Suggest>(Detectors.recurringSkip(skips, now)).suggestion
        assertNull(recurring.routineId)
        assertNull(recurring.operation)

        val blockedSuggestion = assertIs<Detection.Suggest>(Detectors.blockedAction(blocked, now)).suggestion
        assertNull(blockedSuggestion.routineId)
        assertNull(blockedSuggestion.operation)
    }

    @Test
    fun `manual routine and repeated signal meet bounded thresholds`() {
        val manual = listOf(540, 550, 530, 545).mapIndexed { index, minute ->
            LedgerEvent.ManualStart(minute, Day.entries[index], now - index * DAY, routineId = "study")
        }
        val signals = (0 until 4).map { index ->
            LedgerEvent.SignalObserved("charger", "desk", 9 * 60 + index, Day.entries[index], now - index * DAY)
        }

        val manualSuggestion = assertIs<Detection.Suggest>(Detectors.manualRoutine(manual, now)).suggestion
        assertEquals("study", manualSuggestion.routineId)
        assertEquals(RefineOperation.ReplaceTrigger(Trigger.AtTime(com.cues.core.model.LocalTimeOfDay(9, 5))), manualSuggestion.operation)

        // ADD_SIGNAL_CUE proposes a *new* cue, never an edit — no routine to name.
        val signalSuggestion = assertIs<Detection.Suggest>(Detectors.signalWithoutCue(signals, now)).suggestion
        assertNull(signalSuggestion.routineId)
        assertNull(signalSuggestion.operation)
    }

    @Test
    fun `coverage gaps over thirty percent suppress conclusions`() {
        val events = listOf(
            LedgerEvent.ActionBlocked("OPEN_APP", now - DAY),
            LedgerEvent.ActionBlocked("OPEN_APP", now),
            LedgerEvent.CoverageGap(now - 6 * DAY, now, "learning disabled"),
        )

        assertIs<Detection.NotEnoughData>(Detectors.blockedAction(events, now))
    }

    @Test
    fun `policy surfaces one a day and respects temporary and permanent mutes`() {
        val store = InMemoryCoachState()
        val policy = CoachPolicy(store)
        val first = suggestion("one")
        val second = suggestion("two")

        assertEquals(first, policy.next(listOf(first, second), now))
        assertEquals(null, policy.next(listOf(second), now + 1_000))
        policy.dismiss("two", now + DAY, permanent = true)
        assertEquals(null, policy.next(listOf(second), now + 2 * DAY))
    }

    private fun suggestion(key: String) = Suggestion(key, SuggestionKind.FIX_PERMISSION, listOf(EvidenceLine("evidence", 2)), "fix it")

    private class InMemoryCoachState : CoachStateStore {
        private var state = CoachState()
        override fun loadCoachState() = state
        override fun saveCoachState(state: CoachState) { this.state = state }
    }

    private companion object { const val DAY = 86_400_000L }
}
