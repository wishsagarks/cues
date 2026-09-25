package com.cues.app.devkit

/**
 * The wire contract for [CuesGemmaProvider] — the one place this app and any
 * caller app must agree on method names and Bundle keys. Deliberately plain
 * constants, not a generated AIDL interface: see [CuesGemmaProvider]'s own
 * doc comment for why a call-only `ContentProvider`, not a bound service, is
 * this surface's mechanism.
 */
object GemmaCallProtocol {
    /** `content://<applicationId>` + this = the provider's authority. */
    const val AUTHORITY_SUFFIX = ".gemma"

    /** method: returns [KEY_TEXT]/[KEY_BACKEND]/[KEY_ESTIMATED_TOKENS]/[KEY_LATENCY_MS]/[KEY_MODEL_IDENTITY], or [KEY_ERROR]. */
    const val METHOD_GENERATE = "generate"

    /** method: returns [KEY_MODEL_IDENTITY] alone (or [KEY_ERROR]) — "what would answer right now," no generation. */
    const val METHOD_MODEL_INFO = "modelInfo"

    /** `call()`'s `arg`, or an extras entry under this key: the prompt text for [METHOD_GENERATE]. */
    const val KEY_PROMPT = "prompt"

    const val KEY_TEXT = "text"

    /** One of [com.cues.core.inference.InferenceBackend]'s names — the real backend the call actually ran on. */
    const val KEY_BACKEND = "backend"
    const val KEY_ESTIMATED_TOKENS = "estimatedTokens"
    const val KEY_LATENCY_MS = "latencyMs"

    /** File size + a truncated SHA-256 prefix of whichever `.litertlm` model actually answered — see [CuesGemmaProvider]. */
    const val KEY_MODEL_IDENTITY = "modelIdentity"

    /** Present, and the only key present, on failure — a denied/missing-model/crashed call is disclosed, never a silent empty success. */
    const val KEY_ERROR = "error"
}
