package com.cues.core.eval

import kotlinx.serialization.Serializable

/**
 * Why the evaluator decided what it decided.
 *
 * Receipts are rendered from these codes and the values that produced them —
 * never from a model asked to explain a decision after the fact. A generated
 * justification can be fluent and wrong, and a user who is deciding whether to
 * trust an unattended rule is exactly the person who cannot afford that.
 */
@Serializable
enum class ReasonCode {
    TRIGGER_MATCHED,
    TRIGGER_KIND_MISMATCH,
    TRIGGER_DEVICE_MISMATCH,

    DAY_IN_SET,
    DAY_NOT_IN_SET,
    DAY_UNKNOWN,

    TIME_IN_WINDOW,
    TIME_OUTSIDE_WINDOW,
    TIME_UNKNOWN,

    CHARGING_AS_REQUIRED,
    CHARGING_NOT_AS_REQUIRED,
    CHARGING_UNKNOWN,

    DEVICE_CONNECTED,
    DEVICE_NOT_CONNECTED,
    DEVICE_CONNECTED_UNKNOWN,

    WIFI_TRIGGER_MATCHED,
    WIFI_TRIGGER_KIND_MISMATCH,
    WIFI_TRIGGER_NETWORK_MISMATCH,
    WIFI_CONNECTED,
    WIFI_NOT_CONNECTED,
    WIFI_UNKNOWN,
    WIFI_NETWORK_UNKNOWN,

    CONTEXT_MATCHED,
    CONTEXT_NOT_MATCHED,
    CONTEXT_UNKNOWN,
    CONTEXT_MISMATCH,
    PATCH_SKIPPED_UNTIL,
    PATCH_SKIPPED_TODAY,
    AUDIO_OUTPUT_CHANGED,
    AUDIO_KIND_MISMATCH,
    AUDIO_OUTPUT_ACTIVE,
    AUDIO_OUTPUT_INACTIVE,
    AUDIO_OUTPUT_UNKNOWN,
    BATTERY_AS_REQUIRED,
    BATTERY_NOT_AS_REQUIRED,
    BATTERY_UNKNOWN,
    PLACE_ENTERED,
    PLACE_NOT_INSIDE,
    PLACE_UNKNOWN,
    PLACE_MISMATCH,
    CALENDAR_AS_REQUIRED,
    CALENDAR_NOT_AS_REQUIRED,
    CALENDAR_UNKNOWN,

    TIME_TRIGGER_MATCHED,
    TIME_TRIGGER_KIND_MISMATCH,
    TIME_TRIGGER_DAY_MISMATCH,
    TIME_TRIGGER_ZONE_MISMATCH,
    TIME_END_SCHEDULED,
    COVERAGE_GAP,

    ROUTINE_NOT_ARMED,
    DUPLICATE_EVENT_SAME_CONNECTION,
    SESSION_ALREADY_ACTIVE,
    COOLDOWN_ACTIVE,
    DAILY_LIMIT_ACTIVE,
}

/**
 * One condition's verdict, with the observed values that justify it.
 *
 * Serializable so a structured receipt can persist the exact verdicts the
 * evaluator produced, rather than a re-derivation of them.
 */
@Serializable
data class Reason(
    val code: ReasonCode,
    val truth: Truth,
    /** Human-readable, rendered from observed values. No model in this path. */
    val detail: String,
)
