package com.cues.core.signals

import com.cues.core.signals.AmbientBandClassifier.Band
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class AmbientBandClassifierTest {
    private fun reading(lux: Double, atMillis: Long) = AmbientBandClassifier.Reading(lux, atMillis)

    @Test
    fun `a missing or negative sensor reading is UNKNOWN, never DARK`() {
        val warm = FastForwardTo.bright(atMillis = 5_000)
        val missing = AmbientBandClassifier.classify(AmbientBandClassifier.Reading(null, 5_100), warm)
        assertEquals(Band.UNKNOWN, missing.confirmed)

        val negative = AmbientBandClassifier.classify(AmbientBandClassifier.Reading(-1.0, 5_200), warm)
        assertEquals(Band.UNKNOWN, negative.confirmed)
    }

    @Test
    fun `bands are quantized at the documented lux boundaries`() {
        assertEquals(Band.DARK, settled(lux = 5.0))
        assertEquals(Band.DIM, settled(lux = 50.0))
        assertEquals(Band.BRIGHT, settled(lux = 500.0))
    }

    @Test
    fun `a brief flicker under the debounce window never flips confirmed`() {
        var state = AmbientBandClassifier.initial(0)
        state = AmbientBandClassifier.classify(reading(500.0, 0), state) // BRIGHT candidate
        state = AmbientBandClassifier.classify(reading(500.0, 2_000), state) // confirmed BRIGHT
        assertEquals(Band.BRIGHT, state.confirmed)

        // A hand covers the sensor for under 2s.
        state = AmbientBandClassifier.classify(reading(2.0, 2_500), state)
        assertEquals(Band.BRIGHT, state.confirmed, "a sub-debounce flicker should not have flipped confirmed yet")

        state = AmbientBandClassifier.classify(reading(500.0, 2_900), state)
        assertEquals(Band.BRIGHT, state.confirmed)
    }

    @Test
    fun `a stale reading reverts confirmed to UNKNOWN`() {
        val state = FastForwardTo.bright(atMillis = 2_000)
        val fresh = AmbientBandClassifier.staleCheck(state, nowMillis = 2_000 + 15_000)
        assertEquals(Band.BRIGHT, fresh.confirmed, "not stale yet at 15s")

        val stale = AmbientBandClassifier.staleCheck(state, nowMillis = 2_000 + 35_000)
        assertEquals(Band.UNKNOWN, stale.confirmed, "no reading for 35s should be treated as UNKNOWN")
    }

    private fun settled(lux: Double): Band {
        var state = AmbientBandClassifier.initial(0)
        state = AmbientBandClassifier.classify(reading(lux, 0), state)
        state = AmbientBandClassifier.classify(reading(lux, 2_000), state)
        return state.confirmed
    }

    private object FastForwardTo {
        fun bright(atMillis: Long): AmbientBandClassifier.State {
            var state = AmbientBandClassifier.initial(atMillis - 2_000)
            state = AmbientBandClassifier.classify(AmbientBandClassifier.Reading(500.0, atMillis - 2_000), state)
            state = AmbientBandClassifier.classify(AmbientBandClassifier.Reading(500.0, atMillis), state)
            return state
        }
    }
}
