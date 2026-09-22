package com.cues.core.drafting

import com.cues.core.compile.Validator
import com.cues.core.model.DraftSourceId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Runs the on-device model first and falls back to the grammar parser.
 *
 * The two drafting paths are carried at equal weight on purpose. The model
 * covers phrasing the grammar will never anticipate; the grammar is the path
 * that still works when the model is slow, cold, thermally throttled or simply
 * absent from the loaner phone. Which of them is worth demonstrating is a
 * question about measured latency and accuracy on the actual device, and that
 * measurement has not been taken yet.
 *
 * Two rules hold whichever wins:
 *
 * 1. A model draft is only accepted if the *independent* validator accepts it.
 *    The model does not get to vouch for its own output.
 * 2. The accepted result always names the drafter that produced it. Nothing
 *    downstream — receipt, diagnostics, demo screen — can claim the model did
 *    work the parser did.
 */
class CompositeDrafter(
    private val primary: RoutineDrafter,
    private val fallback: RoutineDrafter,
    /** Beyond this, the model has lost its slot for this request. */
    private val timeoutMillis: Long = 4_000,
    private val elapsed: () -> Long = { System.currentTimeMillis() },
) : RoutineDrafter {

    /** Reported as the primary's id; individual results carry the true source. */
    override val id: DraftSourceId = primary.id

    /** Why the fallback ran, when it ran. Surfaced in diagnostics, never hidden. */
    var lastFallbackReason: String? = null
        private set

    override suspend fun draft(text: String): DraftResult {
        lastFallbackReason = null
        val startedAt = elapsed()

        // withTimeoutOrNull returns null rather than throwing, so a timeout and
        // a crash both arrive here as "no usable answer" and both fall back.
        val primaryResult = try {
            withTimeoutOrNull(timeoutMillis) { primary.draft(text) }
                .also { if (it == null) lastFallbackReason = "The on-device model did not answer within ${timeoutMillis}ms." }
        } catch (e: CancellationException) {
            // The caller gave up on us. Not our failure to report.
            throw e
        } catch (e: Exception) {
            lastFallbackReason = "The on-device model failed: ${e.message ?: e::class.simpleName}."
            null
        }

        val accepted = when (primaryResult) {
            null -> null

            is DraftResult.Drafted ->
                if (Validator.validate(primaryResult.routine).isValid) {
                    primaryResult
                } else {
                    // A well-formed routine that means the wrong thing is the
                    // failure mode worth catching here.
                    lastFallbackReason = "The on-device model produced a cue that did not pass validation."
                    null
                }

            // A genuine question from the model is a real answer; asking beats guessing.
            is DraftResult.NeedsClarification -> primaryResult

            is DraftResult.Failed -> {
                lastFallbackReason = "The on-device model failed: ${primaryResult.reason}"
                null
            }
        }

        if (accepted != null) return accepted.withElapsed(elapsed() - startedAt)

        return fallback.draft(text).withElapsed(elapsed() - startedAt)
    }

    private fun DraftResult.withElapsed(millis: Long): DraftResult = when (this) {
        is DraftResult.Drafted -> copy(elapsedMillis = millis)
        is DraftResult.NeedsClarification -> copy(elapsedMillis = millis)
        is DraftResult.Failed -> copy(elapsedMillis = millis)
    }
}
