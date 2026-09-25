package com.cues.core.inference

/**
 * What state the on-device model download is in — pure data, read by the UI.
 *
 * Nothing in `:core`'s decision logic branches on this: [com.cues.core.drafting.OnDeviceLlmDrafter]
 * only ever sees whether [com.cues.core.drafting.LlmSession.generate] throws
 * right now, which the real download machinery (`app/.../drafting/ModelDownloader.kt`,
 * `:app`-only — this is I/O, not a decision) already expresses without this
 * type's help. This exists only so the "Cues Brain" tile's state machine is
 * named the same way everywhere it's read, the same reason [InferenceBackend]/
 * [InferenceReport] are named data next to the Android-bound runtime that
 * produces them.
 */
sealed interface ModelProvisionState {
    /** The honest starting state: no model file present, nothing downloading. */
    data object NotInstalled : ModelProvisionState

    /** [totalBytes] is null when the server didn't send a Content-Length. */
    data class Downloading(val bytesDownloaded: Long, val totalBytes: Long?) : ModelProvisionState

    /** Between "download finished" and "hash confirmed" — brief, but a distinct state so the UI never claims installed early. */
    data object Verifying : ModelProvisionState

    /**
     * [sha256] is null when the file's presence was read from disk rather
     * than just verified by a download in this process (e.g. after an `adb
     * push`/`run-as` side-load, or an app restart) — re-hashing a
     * potentially large file on every state read isn't done just to fill
     * this in, so a null here means "present, not re-verified," never "does
     * not match."
     */
    data class Installed(val sizeBytes: Long, val sha256: String?) : ModelProvisionState

    /** [retryable] is false for a configuration problem (e.g. no URL set) a retry can't fix. */
    data class Failed(val reason: String, val retryable: Boolean) : ModelProvisionState
}
