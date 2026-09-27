package com.cues.core.signals

import com.cues.core.Fixtures
import com.cues.core.eval.Evaluator
import com.cues.core.eval.FreshnessPolicy
import com.cues.core.eval.ReasonCode
import com.cues.core.eval.Truth
import com.cues.core.model.EventKind
import com.cues.core.model.Trigger
import com.cues.core.model.TriggerEvent
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * MissedCallKit's own capabilities() must stay empty — this is a real,
 * evaluable trigger, but it is deliberately never backed by a real listener
 * on this build (no READ_CALL_LOG/READ_PHONE_STATE), so it must never cause a
 * routine to request one.
 */
class MissedCallKitTest {

    private val routine = Fixtures.heroRoutine(conditions = emptyList()).copy(trigger = Trigger.MissedCall())

    @Test
    fun `a missed-call event matches, and requests no telephony capability`() {
        val decision = Evaluator.evaluate(
            routine,
            TriggerEvent(EventKind.MISSED_CALL, Fixtures.NOW),
            Fixtures.snapshot(),
            FreshnessPolicy.NONE,
        )
        assertEquals(Truth.MATCH, decision.truth)
        assertEquals(ReasonCode.MISSED_CALL_MATCHED, decision.reasons.single().code)
        assertTrue(SignalRegistry.capabilitiesFor(routine).none { it.name.contains("CALL") || it.name.contains("PHONE") })
    }

    @Test
    fun `an unrelated event kind does not match`() {
        val decision = Evaluator.evaluate(
            routine,
            TriggerEvent(EventKind.MANUAL_RUN, Fixtures.NOW),
            Fixtures.snapshot(),
            FreshnessPolicy.NONE,
        )
        assertEquals(Truth.NO_MATCH, decision.truth)
        assertEquals(ReasonCode.MISSED_CALL_KIND_MISMATCH, decision.reasons.single().code)
    }

    @Test
    fun `has no real OS adapter on this build`() {
        assertEquals(null, SignalRegistry.adapterKey(Trigger.MissedCall()))
    }
}
