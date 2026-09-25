package com.cues.app.drafting

import com.cues.core.drafting.InferenceOutput
import com.cues.core.drafting.LlmSession

/**
 * The same two-level opt-in gate CL-35's cloud assist already established
 * (`CuesApplication.cloudAssistAvailable`/`cloudAssistEnabled`): a model
 * being installed does not, by itself, mean it's used. [enabled] is read
 * fresh on every call — a runtime switch the user turns on, off by default
 * every launch, layered on top of [delegate]'s own "is a file even there"
 * check.
 */
class GatedLlmSession(
    private val delegate: LlmSession,
    private val enabled: () -> Boolean,
) : LlmSession {
    override suspend fun generate(prompt: String): InferenceOutput =
        if (enabled()) delegate.generate(prompt)
        else throw IllegalStateException("The on-device model is installed but not turned on.")
}
