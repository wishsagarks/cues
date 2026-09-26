package com.cues.core.hub

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class HubPolicyTest {
    private fun allow() = TokenBucketLimiter.Result.Allowed

    @Test
    fun `authorization failure refuses before thermal or quota are even read`() {
        var rateLimitCalled = false
        val decision = HubPolicy.decide(
            authorizationFailure = "Consent required for com.example.app",
            thermalStatus = ThermalStatus.SEVERE,
            rateLimit = { rateLimitCalled = true; allow() },
        )
        assertEquals(HubDecision.Denied("AUTHORIZATION", "Consent required for com.example.app"), decision)
        assertTrue(!rateLimitCalled, "a request refused for authorization must never spend a rate-limit token")
    }

    @Test
    fun `severe thermal status refuses before a rate-limit token is spent`() {
        var rateLimitCalled = false
        val decision = HubPolicy.decide(
            authorizationFailure = null,
            thermalStatus = ThermalStatus.SEVERE,
            rateLimit = { rateLimitCalled = true; allow() },
        )
        assertTrue(decision is HubDecision.Denied && decision.reasonCode == "THERMAL")
        assertTrue(!rateLimitCalled, "a thermally refused caller must keep its full quota once the phone cools")
    }

    @Test
    fun `moderate thermal status is not a refusal reason`() {
        val decision = HubPolicy.decide(
            authorizationFailure = null,
            thermalStatus = ThermalStatus.MODERATE,
            rateLimit = { allow() },
        )
        assertEquals(HubDecision.Allowed, decision)
    }

    @Test
    fun `unknown thermal status is treated like a normal, non-refusing reading`() {
        val decision = HubPolicy.decide(
            authorizationFailure = null,
            thermalStatus = ThermalStatus.UNKNOWN,
            rateLimit = { allow() },
        )
        assertEquals(HubDecision.Allowed, decision)
    }

    @Test
    fun `a nominal thermal status still defers to the rate limiter`() {
        val decision = HubPolicy.decide(
            authorizationFailure = null,
            thermalStatus = ThermalStatus.NONE,
            rateLimit = { TokenBucketLimiter.Result.Limited("minute", 12_345) },
        )
        assertEquals(HubDecision.Denied("RATE_LIMIT", "Rate limited (minute); retry in 12345ms."), decision)
    }
}
