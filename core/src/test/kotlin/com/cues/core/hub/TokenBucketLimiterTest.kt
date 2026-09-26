package com.cues.core.hub

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class TokenBucketLimiterTest {
    @Test
    fun `limits each caller independently and refills over time`() {
        val limiter = TokenBucketLimiter(listOf(TokenBucketLimiter.Rule("minute", 2, 60_000)))
        assertEquals(TokenBucketLimiter.Result.Allowed, limiter.tryAcquire("a", 0))
        assertEquals(TokenBucketLimiter.Result.Allowed, limiter.tryAcquire("a", 0))
        assertEquals(TokenBucketLimiter.Result.Limited("minute", 30_000), limiter.tryAcquire("a", 0))
        assertEquals(TokenBucketLimiter.Result.Allowed, limiter.tryAcquire("b", 0))
        assertEquals(TokenBucketLimiter.Result.Allowed, limiter.tryAcquire("a", 30_000))
    }

    @Test
    fun `a stricter later rule does not consume earlier capacity on refusal`() {
        val limiter = TokenBucketLimiter(
            listOf(
                TokenBucketLimiter.Rule("minute", 5, 60_000),
                TokenBucketLimiter.Rule("hour", 1, 3_600_000),
            ),
        )
        assertEquals(TokenBucketLimiter.Result.Allowed, limiter.tryAcquire("a", 0))
        assertEquals(TokenBucketLimiter.Result.Limited("hour", 3_600_000), limiter.tryAcquire("a", 0))
    }
}
