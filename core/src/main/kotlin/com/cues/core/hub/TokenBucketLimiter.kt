package com.cues.core.hub

/** A clock-injected, per-caller token bucket for the local-model hub. */
class TokenBucketLimiter(
    private val rules: List<Rule> = listOf(
        Rule("minute", capacity = 5, refillPeriodMillis = 60_000),
        Rule("hour", capacity = 30, refillPeriodMillis = 3_600_000),
    ),
) {
    init {
        require(rules.isNotEmpty())
        require(rules.all { it.capacity > 0 && it.refillPeriodMillis > 0 })
    }

    data class Rule(val name: String, val capacity: Int, val refillPeriodMillis: Long)

    sealed interface Result {
        data object Allowed : Result
        data class Limited(val rule: String, val retryAfterMillis: Long) : Result
    }

    private data class Bucket(var tokens: Double, var refreshedAtMillis: Long)
    private val buckets = mutableMapOf<String, MutableMap<String, Bucket>>()

    /**
     * Consumes one token from every policy bucket only if all can serve the
     * request.  A caller denied by the hourly policy never burns its minute
     * quota, which matters for an explainable refusal.
     */
    @Synchronized
    fun tryAcquire(caller: String, nowMillis: Long): Result {
        val callerBuckets = buckets.getOrPut(caller) {
            rules.associate { rule -> rule.name to Bucket(rule.capacity.toDouble(), nowMillis) }.toMutableMap()
        }
        val candidates = rules.map { rule ->
            val bucket = callerBuckets.getValue(rule.name)
            refill(bucket, rule, nowMillis)
            rule to bucket
        }
        candidates.firstOrNull { (_, bucket) -> bucket.tokens < 1.0 }?.let { (rule, bucket) ->
            // Calculate in period units to avoid a reciprocal's rounding
            // turning an exact one-hour wait into 3,600,001 ms.
            val wait = kotlin.math.ceil(
                (1.0 - bucket.tokens) * rule.refillPeriodMillis / rule.capacity - 1e-9,
            ).toLong().coerceAtLeast(1)
            return Result.Limited(rule.name, wait)
        }
        candidates.forEach { (_, bucket) -> bucket.tokens -= 1.0 }
        return Result.Allowed
    }

    private fun refill(bucket: Bucket, rule: Rule, nowMillis: Long) {
        val elapsed = (nowMillis - bucket.refreshedAtMillis).coerceAtLeast(0)
        bucket.tokens = (bucket.tokens + elapsed.toDouble() * rule.capacity / rule.refillPeriodMillis)
            .coerceAtMost(rule.capacity.toDouble())
        bucket.refreshedAtMillis = maxOf(bucket.refreshedAtMillis, nowMillis)
    }
}
