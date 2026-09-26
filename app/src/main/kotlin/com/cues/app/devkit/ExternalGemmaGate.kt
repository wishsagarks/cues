package com.cues.app.devkit

import android.content.Context
import android.os.PowerManager
import com.cues.core.hub.HubDecision
import com.cues.core.hub.HubPolicy
import com.cues.core.hub.ThermalStatus
import com.cues.core.hub.TokenBucketLimiter
import java.util.Collections

/** One attempt to reach the developer-facing Gemma surface — allowed or denied, never silently dropped either way. */
data class ExternalCallLogEntry(
    val atMillis: Long,
    val callerPackage: String,
    val allowed: Boolean,
    val reason: String? = null,
)

/**
 * The third opt-in gate (CLEANUP.md CL-38), layered on top of the two
 * [com.cues.app.drafting.GatedLlmSession]-style checks the authoring model
 * already has: a model being installed and enabled for drafting does not, by
 * itself, mean any other app on the phone may reach it through
 * [CuesGemmaProvider]. Off by default every launch, the same discipline as
 * `CuesApplication.onDeviceModelUserEnabled`/`cloudAssistEnabled`.
 *
 * Recent callers — including denied attempts — are kept for the Diagnostics
 * screen's "Recent callers" list: a blocked caller shows up as "denied,"
 * never silently drops (CLAUDE.md: "a blocked action is not a success").
 * In-memory only, cleared on process death — see CLEANUP.md CL-38.
 */
class ExternalGemmaGate(context: Context) {
    /** Read fresh on every call by [CuesGemmaProvider], via the `GatedLlmSession` wired in `CuesApplication.externalGemmaSession`. */
    var enabled: Boolean = false

    private val log = Collections.synchronizedList(mutableListOf<ExternalCallLogEntry>())
    private val rateLimiter = TokenBucketLimiter()
    private val approvals = context.getSharedPreferences("external_gemma_approvals", Context.MODE_PRIVATE)
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    fun approve(callerPackage: String, signingDigest: String) {
        approvals.edit().putString(callerPackage, signingDigest).apply()
    }
    fun revoke(callerPackage: String) { approvals.edit().remove(callerPackage).apply() }
    fun approvedCallers(): Set<String> = approvals.all.keys

    fun authorizationFailure(callerPackage: String, signingDigest: String): String? = when {
        !enabled -> "Local Gemma hub is switched off."
        approvals.getString(callerPackage, null) == null -> "Consent required for $callerPackage."
        approvals.getString(callerPackage, null) != signingDigest -> "Consent expired: $callerPackage was re-signed."
        else -> null
    }

    /**
     * The single call site [CuesApplication.generateForExternalCaller] should
     * use: authorization, thermal status and the rate limiter, in the order
     * [HubPolicy] requires so a thermally refused caller never loses a quota
     * token it will want back once the phone cools.
     */
    fun decide(callerPackage: String, signingDigest: String, atMillis: Long): HubDecision = HubPolicy.decide(
        authorizationFailure = authorizationFailure(callerPackage, signingDigest),
        thermalStatus = thermalStatus(),
        rateLimit = { rateLimiter.tryAcquire(callerPackage, atMillis) },
    )

    /**
     * The narrower gate for [com.cues.app.appfunctions.BaseCuesAppFunctionService.askLocalGemma]:
     * the platform's own system agent, not a separately installed, separately
     * signed app. There is no second APK here to pin a signing digest against
     * — `AppFunctionService` runs inside Cues' own process, invoked by the
     * OS on the phone's owner's behalf — so the only consent this checks is
     * the same [enabled] switch a human already had to flip for any hub
     * traffic to reach this phone at all. Thermal refusal and rate limiting
     * still apply, under their own bucket (`SYSTEM_AGENT_CALLER_KEY`), kept
     * separate from any installed app's own quota so one caller class can
     * never spend another's budget.
     */
    fun decideForSystemAgent(atMillis: Long): HubDecision = HubPolicy.decide(
        authorizationFailure = if (enabled) null else "Local Gemma hub is switched off.",
        thermalStatus = thermalStatus(),
        rateLimit = { rateLimiter.tryAcquire(SYSTEM_AGENT_CALLER_KEY, atMillis) },
    )

    /**
     * Mirrors `PowerManager.getCurrentThermalStatus()`'s own ladder (API 29+,
     * matching this app's `minSdk`) into [ThermalStatus] so [HubPolicy] never
     * needs an Android import. `null` from the platform — no thermal HAL, or
     * the call throwing — becomes [ThermalStatus.UNKNOWN], never a guessed
     * [ThermalStatus.NONE]: an unreadable thermal state is not the same fact
     * as a confirmed-cool phone.
     */
    private fun thermalStatus(): ThermalStatus = try {
        when (powerManager?.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> ThermalStatus.NONE
            PowerManager.THERMAL_STATUS_LIGHT -> ThermalStatus.LIGHT
            PowerManager.THERMAL_STATUS_MODERATE -> ThermalStatus.MODERATE
            PowerManager.THERMAL_STATUS_SEVERE -> ThermalStatus.SEVERE
            PowerManager.THERMAL_STATUS_CRITICAL -> ThermalStatus.CRITICAL
            PowerManager.THERMAL_STATUS_EMERGENCY -> ThermalStatus.EMERGENCY
            PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalStatus.SHUTDOWN
            else -> ThermalStatus.UNKNOWN
        }
    } catch (_: Exception) {
        ThermalStatus.UNKNOWN
    }

    fun recentCalls(): List<ExternalCallLogEntry> = log.toList().asReversed()

    fun logAllowed(callerPackage: String, atMillis: Long) {
        append(ExternalCallLogEntry(atMillis, callerPackage, allowed = true))
    }

    fun logDenied(callerPackage: String, atMillis: Long, reason: String) {
        append(ExternalCallLogEntry(atMillis, callerPackage, allowed = false, reason = reason))
    }

    private fun append(entry: ExternalCallLogEntry) {
        log.add(entry)
        while (log.size > MAX_LOG) log.removeAt(0)
    }

    private companion object {
        const val MAX_LOG = 50
        /** Not a real package name, so it can never collide with an installed app's own quota bucket. */
        const val SYSTEM_AGENT_CALLER_KEY = "system-agent"
    }
}
