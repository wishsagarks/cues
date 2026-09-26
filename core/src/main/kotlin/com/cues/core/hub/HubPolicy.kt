package com.cues.core.hub

/**
 * Android's `PowerManager` thermal-status ladder, mirrored here without an
 * Android import so [HubPolicy] stays pure and testable in `:core`. [UNKNOWN]
 * is its own value, never folded into [NONE] — a device that can't report
 * its thermal state is not the same fact as one confirmed cool.
 */
enum class ThermalStatus { NONE, LIGHT, MODERATE, SEVERE, CRITICAL, EMERGENCY, SHUTDOWN, UNKNOWN }

/** Why a hub request did or didn't run — every refusal names itself, never a silent drop. */
sealed interface HubDecision {
    data object Allowed : HubDecision
    data class Denied(val reasonCode: String, val detail: String) : HubDecision
}

/**
 * The one place that decides whether a local-model hub request may proceed.
 *
 * Order matters and is deliberate: an unauthorized caller is refused before
 * anything else is even considered; a thermally throttled device is refused
 * before a rate-limit token is spent, so a caller that gets turned away for
 * heat still has its full quota when the phone cools down; only then does
 * the token bucket get to say yes or no. [TokenBucketLimiter.tryAcquire]
 * must not be called until [decide] has already cleared the thermal check —
 * calling it earlier would burn a token on a request this function was
 * always going to refuse.
 */
object HubPolicy {
    /** Severities at or above this refuse a hub request outright — never load the model to find out. */
    private val REFUSED_AT = setOf(
        ThermalStatus.SEVERE,
        ThermalStatus.CRITICAL,
        ThermalStatus.EMERGENCY,
        ThermalStatus.SHUTDOWN,
    )

    fun decide(
        authorizationFailure: String?,
        thermalStatus: ThermalStatus,
        rateLimit: () -> TokenBucketLimiter.Result,
    ): HubDecision {
        authorizationFailure?.let { return HubDecision.Denied("AUTHORIZATION", it) }
        if (thermalStatus in REFUSED_AT) {
            return HubDecision.Denied(
                "THERMAL",
                "Device thermal status is $thermalStatus; the local-model hub is paused until it cools.",
            )
        }
        return when (val result = rateLimit()) {
            TokenBucketLimiter.Result.Allowed -> HubDecision.Allowed
            is TokenBucketLimiter.Result.Limited -> HubDecision.Denied(
                "RATE_LIMIT",
                "Rate limited (${result.rule}); retry in ${result.retryAfterMillis}ms.",
            )
        }
    }
}
