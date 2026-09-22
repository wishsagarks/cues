package com.cues.core.compile

import com.cues.core.model.*
import com.cues.core.signals.SignalRegistry
import java.security.MessageDigest

/**
 * Puts a routine into canonical form and fingerprints its meaning.
 *
 * Normalization exists so that two routines with the same behaviour have the
 * same bytes. Approval is then bound to those bytes: reordering the conditions
 * in the editor is not a behaviour change and must not force reapproval, while
 * moving 18:00 to 17:00 is and must.
 */
object Normalizer {

    /**
     * Canonicalises a routine without changing what it does.
     *
     * Sorting is by a stable key rather than by construction order, and
     * capabilities are recomputed from the registry rather than carried over,
     * so a drafted routine and a hand-edited one converge on the same form.
     */
    fun normalize(routine: Routine): Routine {
        val actions = routine.actions.sortedBy { it.actionId.ordinal }
        return routine.copy(
            title = routine.title.trim(),
            sourceText = routine.sourceText.trim(),
            conditions = routine.conditions.sortedBy { it.sortKey() },
            actions = actions,
            endConditions = routine.endConditions.distinct().sortedBy { it.sortKey() },
            // Derived, never trusted from a draft.
            requiredCapabilities = SignalRegistry.capabilitiesFor(routine.copy(actions = actions)),
        )
    }

    /**
     * A digest over exactly the fields that decide behaviour.
     *
     * Excluded on purpose: [Routine.sourceText] (the user may fix a transcript
     * typo without re-approving anything), [Routine.title], [Routine.status]
     * and the digest field itself. Included: trigger, conditions, actions,
     * endings, cleanup and rearm policy.
     *
     * This detects an accidental mismatch between what was shown and what is
     * armed. It is not a defence against an attacker who already controls the
     * app's own storage, and is not presented as one.
     */
    fun digest(routine: Routine): String {
        val canonical = normalize(routine).semanticForm()
        val bytes = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** True when [routine] still means what the user approved. */
    fun matchesApproval(routine: Routine): Boolean =
        routine.approvedDigest != null && routine.approvedDigest == digest(routine)

    /**
     * A stable text rendering of executable meaning.
     *
     * Written by hand rather than delegated to JSON serialization, so that
     * adding a cosmetic field to [Routine] cannot silently invalidate every
     * previously approved routine on someone's phone.
     */
    private fun Routine.semanticForm(): String = buildString {
        append("schema=").append(schemaVersion).append('\n')
        append("trigger=").append(SignalRegistry.semanticForm(trigger)).append('\n')
        conditions.forEach { append("condition=").append(SignalRegistry.semanticForm(it)).append('\n') }
        actions.forEach { append("action=").append(it.semanticForm()).append('\n') }
        endConditions.forEach { append("end=").append(SignalRegistry.semanticForm(it)).append('\n') }
        append("cleanup=ownedOnly:").append(cleanupPolicy.releaseOwnedEffectsOnly)
            .append(",respectOverride:").append(cleanupPolicy.respectUserOverride).append('\n')
        append("rearm=grace:").append(rearmPolicy.reconnectGraceSeconds)
            .append(",cooldown:").append(rearmPolicy.cooldownSeconds)
            .append(",max:").append(rearmPolicy.maxConcurrentSessions).append('\n')
        requiredCapabilities.map { it.name }.sorted().forEach { append("capability=").append(it).append('\n') }
    }

    private fun ActionSpec.semanticForm(): String = "${actionId.name}:" + when (val a = args) {
        is ActionArgs.FocusTimer -> "minutes=${a.durationMinutes}"
        is ActionArgs.Dnd -> "allowPriority=${a.allowPriority}"
        is ActionArgs.Notify -> "message=${a.message}"
        is ActionArgs.PinnedNote -> "message=${a.message}"
        ActionArgs.None -> "none"
    }

    private fun Condition.sortKey(): String = SignalRegistry.semanticForm(this)

    private fun EndCondition.sortKey(): String = SignalRegistry.semanticForm(this)
}
