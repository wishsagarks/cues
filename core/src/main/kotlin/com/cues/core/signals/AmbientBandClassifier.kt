package com.cues.core.signals

/**
 * A pure, debounced classifier that quantizes `TYPE_LIGHT` lux readings into
 * three declared bands — never the raw lux value, matching the project's
 * "receipts from reason codes, never a raw signal dump" rule. Like
 * [FaceDownClassifier], this is the classification logic a future
 * `ConditionKit`/`SignalAdapter` would wrap, kept separately testable here.
 */
object AmbientBandClassifier {

    enum class Band { DARK, DIM, BRIGHT, UNKNOWN }

    data class Reading(val lux: Double?, val atMillis: Long)

    data class State(
        val confirmed: Band,
        val candidate: Band,
        val candidateSinceMillis: Long,
        val lastReadingMillis: Long,
    )

    /** Below this lux counts as [Band.DARK] — a dim bedroom, a pocket, screen-down on a desk at night. */
    private const val DARK_MAX_LUX = 10.0

    /** Below this (and at or above [DARK_MAX_LUX]) counts as [Band.DIM] — indoor lighting. At or above it is [Band.BRIGHT]. */
    private const val DIM_MAX_LUX = 100.0

    /** A band must hold this long before [State.confirmed] moves, so a hand briefly shading the sensor doesn't flip it. */
    private const val DEBOUNCE_MILLIS = 2_000L

    /** No reading for this long and the confirmed band is no longer trustworthy. */
    private const val STALE_MILLIS = 30_000L

    fun initial(nowMillis: Long): State = State(Band.UNKNOWN, Band.UNKNOWN, nowMillis, nowMillis)

    private fun bandFor(lux: Double): Band = when {
        lux < DARK_MAX_LUX -> Band.DARK
        lux < DIM_MAX_LUX -> Band.DIM
        else -> Band.BRIGHT
    }

    fun classify(reading: Reading, previous: State): State {
        val lux = reading.lux
        if (lux == null || lux < 0.0) {
            // A negative reading is not a physically real lux value — treated the same as a missing sensor.
            return State(Band.UNKNOWN, Band.UNKNOWN, reading.atMillis, reading.atMillis)
        }

        val rawCandidate = bandFor(lux)
        val candidateSinceMillis = if (rawCandidate == previous.candidate) {
            previous.candidateSinceMillis
        } else {
            reading.atMillis
        }
        val heldLongEnough = reading.atMillis - candidateSinceMillis >= DEBOUNCE_MILLIS
        val confirmed = if (heldLongEnough) rawCandidate else previous.confirmed

        return State(confirmed, rawCandidate, candidateSinceMillis, reading.atMillis)
    }

    fun staleCheck(previous: State, nowMillis: Long): State =
        if (nowMillis - previous.lastReadingMillis > STALE_MILLIS) {
            State(Band.UNKNOWN, Band.UNKNOWN, nowMillis, previous.lastReadingMillis)
        } else {
            previous
        }
}
