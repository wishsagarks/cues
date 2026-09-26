package com.cues.core.drafting

import com.cues.core.compile.Validator
import com.cues.core.model.DraftSourceId

/**
 * Whether a model-backed [DraftResult.Drafted] is trusted on its own.
 *
 * The model never gets to vouch for its own output — [Validator] checks it
 * independently, the same discipline [DifferentialDrafter] already applies
 * to its own model attempt. Pulled out so [com.cues.core.CueService]'s cloud
 * assist path ([com.cues.core.CueService.draftWithCloud]) reuses exactly
 * this rule instead of re-typing it (CLEANUP.md CL-35's cloud drafter is a
 * second model-backed source, and deserves the same guard as the first).
 */
object ModelDraftGuard {
    fun rejects(result: DraftResult.Drafted): Boolean =
        result.source != DraftSourceId.GRAMMAR_PARSER && !Validator.validate(result.routine).isValid
}
