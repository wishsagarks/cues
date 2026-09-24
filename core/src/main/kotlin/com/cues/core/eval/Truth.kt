package com.cues.core.eval

import kotlinx.serialization.Serializable

/**
 * Three-valued logic, because two values are not enough to be honest.
 *
 * A phone often cannot tell whether a condition holds: a permission was
 * revoked, an adapter is off, a reading is too old to trust. Collapsing that
 * into `false` invents a fact, and collapsing it into `true` acts on one.
 * [UNKNOWN] keeps the uncertainty intact until a human sees it.
 */
@Serializable
enum class Truth {
    MATCH,
    NO_MATCH,
    UNKNOWN;

    companion object {
        fun of(value: Boolean): Truth = if (value) MATCH else NO_MATCH
    }
}

/**
 * Kleene conjunction over a routine's conditions.
 *
 * NO_MATCH dominates: one condition that definitely fails settles the question
 * however murky the rest are, and saying "Saturday is outside Monday–Friday"
 * is more useful than shrugging. Otherwise any UNKNOWN wins, so MATCH is
 * returned only when every condition is affirmatively known to hold.
 *
 * An empty condition list is MATCH — a routine gated on nothing is gated on
 * nothing, which is a decision the user made at review time.
 */
fun Iterable<Truth>.conjoin(): Truth {
    var sawUnknown = false
    for (t in this) {
        when (t) {
            Truth.NO_MATCH -> return Truth.NO_MATCH
            Truth.UNKNOWN -> sawUnknown = true
            Truth.MATCH -> Unit
        }
    }
    return if (sawUnknown) Truth.UNKNOWN else Truth.MATCH
}
