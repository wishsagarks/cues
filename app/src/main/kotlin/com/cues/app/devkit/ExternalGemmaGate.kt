package com.cues.app.devkit

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
class ExternalGemmaGate {
    /** Read fresh on every call by [CuesGemmaProvider], via the `GatedLlmSession` wired in `CuesApplication.externalGemmaSession`. */
    var enabled: Boolean = false

    private val log = Collections.synchronizedList(mutableListOf<ExternalCallLogEntry>())

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
    }
}
