package com.cues.core.coach

import com.cues.core.model.Day
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class DetectorsTest {
    private val now = 1_800_000_000_000L

    @Test
    fun `early stop needs three of the last five sessions`() {
        val events = (0 until 5).map { index ->
            LedgerEvent.SessionEnded("study", 45, if (index < 3) 20 else 44, "manual", now - index * DAY)
        }
        val suggestion = Detectors.earlyStop(events, now)
        assertEquals(SuggestionKind.SHORTER_DURATION, assertIs<Detection.Suggest>(suggestion).suggestion.kind)
    }

    @Test
    fun `recurring skip and blocked action cite evidence counts`() {
        val skips = (0 until 3).map { LedgerEvent.PatchCreated("skip-today", Day.FRI, now - it * 7 * DAY) }
        val blocked = listOf(
            LedgerEvent.ActionBlocked("OPEN_APP", now - DAY),
            LedgerEvent.ActionBlocked("OPEN_APP", now),
        )

        assertTrue(assertIs<Detection.Suggest>(Detectors.recurringSkip(skips, now)).suggestion.evidence.single().count >= 3)
        assertEquals(SuggestionKind.CHECK_OR_REMOVE_ACTION, assertIs<Detection.Suggest>(Detectors.blockedAction(blocked, now)).suggestion.kind)
    }

    @Test
    fun `manual routine and repeated signal meet bounded thresholds`() {
        val manual = listOf(540, 550, 530, 545).mapIndexed { index, minute ->
            LedgerEvent.ManualStart(minute, Day.entries[index], now - index * DAY)
        }
        val signals = (0 until 4).map { index ->
            LedgerEvent.SignalObserved("charger", "desk", 9 * 60 + index, Day.entries[index], now - index * DAY)
        }

        assertIs<Detection.Suggest>(Detectors.manualRoutine(manual, now))
        assertIs<Detection.Suggest>(Detectors.signalWithoutCue(signals, now))
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
