package com.cues.core.signals

import com.cues.core.eval.Truth

/**
 * A pure, hysteresis-based classifier for "the phone is lying face-down on
 * a surface" — a physical fact the user directly causes by placing the
 * phone, never an inferred activity. It is the foundation a future
 * `ConditionKit`/`SignalAdapter` pair would wrap, kept here on its own so
 * the classification logic itself is exercised in `:core`'s JVM test suite
 * rather than only ever run, unverified, on a device (the same split
 * [com.cues.core.context.UnknownRemedy] and the existing `*Kit` objects
 * already rely on).
 *
 * Two Android sensors feed this, both read behind a future `SignalAdapter`:
 * `TYPE_GRAVITY` (or `TYPE_ACCELEROMETER` as a fallback) for orientation, and
 * `TYPE_PROXIMITY` so a phone lying flat on an open desk (screen up, nothing
 * covering it) is never confused with one placed face-down. Neither reading
 * is ever treated as a stand-in for the other going missing — see
 * [Reading.gravityZ]/[Reading.proximityNear] below.
 */
object FaceDownClassifier {

    /** One sensor sample. Either field may be `null` — a real absence, never coerced to a guessed value. */
    data class Reading(val gravityZ: Double?, val proximityNear: Boolean?, val atMillis: Long)

    /**
     * [confirmed] is the answer a caller should act on. [candidate] and
     * [candidateSinceMillis] are the classifier's own bookkeeping for
     * hysteresis — a caller only ever reads [confirmed].
     */
    data class State(
        val confirmed: Truth,
        val candidate: Truth,
        val candidateSinceMillis: Long,
        val lastReadingMillis: Long,
    )

    /** A new adapter with no reading yet — [Truth.UNKNOWN], not a guessed "not face-down". */
    fun initial(nowMillis: Long): State = State(Truth.UNKNOWN, Truth.UNKNOWN, nowMillis, nowMillis)

    /**
     * A candidate must hold for [STABLE_MILLIS] before [State.confirmed]
     * moves — a phone lifted and set back down within that window never
     * flips the confirmed answer, which is what keeps a routine gated on
     * this from beginning and ending every time it's picked up to check a
     * notification.
     */
    private const val STABLE_MILLIS = 3_000L

    /**
     * No reading for this long and the last confirmed answer is no longer
     * trustworthy — [staleCheck] moves it to [Truth.UNKNOWN] rather than
     * leaving a routine gated on a fact nobody has re-observed recently.
     */
    private const val STALE_MILLIS = 15_000L

    /** Below this z-axis gravity component (m/s^2, phone flat, screen down) counts as face-down. */
    private const val GRAVITY_THRESHOLD = -8.5

    fun classify(reading: Reading, previous: State): State {
        val gravityZ = reading.gravityZ
        val proximityNear = reading.proximityNear
        if (gravityZ == null || proximityNear == null) {
            // A genuinely missing sensor, not a guess at what it would have said.
            return State(Truth.UNKNOWN, Truth.UNKNOWN, reading.atMillis, reading.atMillis)
        }

        val rawCandidate = Truth.of(gravityZ < GRAVITY_THRESHOLD && proximityNear)
        val candidateSinceMillis = if (rawCandidate == previous.candidate) {
            previous.candidateSinceMillis
        } else {
            reading.atMillis
        }
        val heldLongEnough = reading.atMillis - candidateSinceMillis >= STABLE_MILLIS
        val confirmed = if (heldLongEnough) rawCandidate else previous.confirmed

        return State(confirmed, rawCandidate, candidateSinceMillis, reading.atMillis)
    }

    /**
     * Call on a timer (or before evaluating a routine's conditions) so a
     * listener that silently stopped delivering readings doesn't leave a
     * stale confirmed answer standing forever.
     */
    fun staleCheck(previous: State, nowMillis: Long): State =
        if (nowMillis - previous.lastReadingMillis > STALE_MILLIS) {
            State(Truth.UNKNOWN, Truth.UNKNOWN, nowMillis, previous.lastReadingMillis)
        } else {
            previous
        }
}
