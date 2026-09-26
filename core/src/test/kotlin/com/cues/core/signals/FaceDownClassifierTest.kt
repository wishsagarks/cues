package com.cues.core.signals

import com.cues.core.eval.Truth
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class FaceDownClassifierTest {
    private fun faceDownReading(atMillis: Long) = FaceDownClassifier.Reading(gravityZ = -9.7, proximityNear = true, atMillis = atMillis)
    private fun faceUpReading(atMillis: Long) = FaceDownClassifier.Reading(gravityZ = 9.7, proximityNear = false, atMillis = atMillis)

    @Test
    fun `a missing sensor reading is UNKNOWN, never a guessed false`() {
        val previous = FaceDownClassifier.classify(faceDownReading(0), FaceDownClassifier.initial(0))
            .let { FaceDownClassifier.classify(faceDownReading(3_000), it) }
        assertEquals(Truth.MATCH, previous.confirmed)

        val missingGravity = FaceDownClassifier.classify(
            FaceDownClassifier.Reading(gravityZ = null, proximityNear = true, atMillis = 3_100),
            previous,
        )
        assertEquals(Truth.UNKNOWN, missingGravity.confirmed)

        val missingProximity = FaceDownClassifier.classify(
            FaceDownClassifier.Reading(gravityZ = -9.7, proximityNear = null, atMillis = 3_200),
            previous,
        )
        assertEquals(Truth.UNKNOWN, missingProximity.confirmed)
    }

    @Test
    fun `a brief face-down blip under the stability window never flips confirmed`() {
        // Settle on a confirmed NO_MATCH (face-up) baseline first.
        var state = FaceDownClassifier.classify(faceUpReading(0), FaceDownClassifier.initial(0))
        state = FaceDownClassifier.classify(faceUpReading(3_000), state)
        assertEquals(Truth.NO_MATCH, state.confirmed)

        // Only 1s of face-down — under the 3s stability window.
        state = FaceDownClassifier.classify(faceDownReading(3_500), state)
        assertEquals(Truth.NO_MATCH, state.confirmed, "a blip should not have confirmed face-down yet")

        // Back to face-up before the window elapses.
        state = FaceDownClassifier.classify(faceUpReading(4_000), state)
        assertEquals(Truth.NO_MATCH, state.confirmed)
    }

    @Test
    fun `face-down held for the full stability window confirms MATCH`() {
        var state = FaceDownClassifier.initial(0)
        state = FaceDownClassifier.classify(faceDownReading(0), state)
        assertEquals(Truth.UNKNOWN, state.confirmed, "confirmed starts UNKNOWN until the window elapses")

        state = FaceDownClassifier.classify(faceDownReading(2_900), state)
        assertEquals(Truth.UNKNOWN, state.confirmed, "still under 3s since the candidate first appeared")

        state = FaceDownClassifier.classify(faceDownReading(3_000), state)
        assertEquals(Truth.MATCH, state.confirmed, "3s stable face-down should now confirm")
    }

    @Test
    fun `a stale reading reverts confirmed to UNKNOWN`() {
        var state = FaceDownClassifier.classify(faceDownReading(0), FaceDownClassifier.initial(0))
        state = FaceDownClassifier.classify(faceDownReading(3_000), state)
        assertEquals(Truth.MATCH, state.confirmed)

        val fresh = FaceDownClassifier.staleCheck(state, nowMillis = 3_000 + 10_000)
        assertEquals(Truth.MATCH, fresh.confirmed, "not stale yet at 10s")

        val stale = FaceDownClassifier.staleCheck(state, nowMillis = 3_000 + 20_000)
        assertEquals(Truth.UNKNOWN, stale.confirmed, "no reading for 20s should be treated as UNKNOWN")
    }
}
