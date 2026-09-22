package com.cues.core.compile

import com.cues.core.model.*
import com.cues.core.registry.ActionRegistry
import com.cues.core.registry.ArgResult
import com.cues.core.signals.SignalRegistry

/**
 * A problem that stops a routine being armed, or a note the user should see.
 *
 * Problems carry user-facing text because they are shown in review, not only
 * logged. A validation message a person cannot act on is a failure of the
 * validator, not of the person.
 */
data class Finding(
    val severity: Severity,
    val field: String,
    val message: String,
)

enum class Severity {
    /** Blocks arming. */
    ERROR,

    /** Armable, but the user should see it before approving. */
    WARNING,
}

data class ValidationResult(
    val findings: List<Finding>,
) {
    val errors: List<Finding> get() = findings.filter { it.severity == Severity.ERROR }
    val warnings: List<Finding> get() = findings.filter { it.severity == Severity.WARNING }
    val isValid: Boolean get() = errors.isEmpty()
}

/**
 * Checks a routine independently of whatever produced it.
 *
 * This runs identically over a grammar-parsed routine and a model-drafted one.
 * That independence is the entire safety argument: a structurally valid rule
 * can still attach the wrong device, invert an "unless", or ask for something
 * the phone cannot do, and none of those are visible to the thing that wrote
 * the JSON. Validity of shape is not evidence of understanding.
 */
object Validator {

    fun validate(routine: Routine): ValidationResult {
        val findings = buildList {
            addAll(validateTrigger(routine.trigger))
            addAll(validateConditions(routine.conditions))
            addAll(validateActions(routine.actions))
            addAll(validateEndings(routine))
            addAll(validateRearm(routine.rearmPolicy))
            addAll(validateCleanup(routine.cleanupPolicy))

            if (routine.schemaVersion != SCHEMA_VERSION) {
                add(
                    Finding(
                        Severity.ERROR,
                        "schemaVersion",
                        "This cue was written for schema ${routine.schemaVersion}; this build speaks $SCHEMA_VERSION.",
                    ),
                )
            }
        }
        return ValidationResult(findings)
    }

    private fun validateTrigger(trigger: Trigger): List<Finding> = SignalRegistry.validateTrigger(trigger)

    private fun validateConditions(conditions: List<Condition>): List<Finding> = buildList {
        conditions.forEach { addAll(SignalRegistry.validateCondition(it)) }
        addAll(SignalRegistry.validateConditionSets(conditions))
        if (conditions.filterIsInstance<Condition.TimeWindow>().size > 1) {
            add(
                Finding(
                    Severity.WARNING,
                    "conditions.time",
                    "There is more than one time window. All of them must hold at once.",
                ),
            )
        }
    }

    private fun validateActions(actions: List<ActionSpec>): List<Finding> = buildList {
        if (actions.isEmpty()) {
            add(Finding(Severity.ERROR, "actions", "This cue does not do anything."))
        }

        actions.forEach { spec ->
            val definition = ActionRegistry.definition(spec.actionId)
            if (definition == null) {
                // Unreachable through the parser, which only emits registry ids.
                // It exists for drafted routines and for imports.
                add(
                    Finding(
                        Severity.ERROR,
                        "actions.${spec.actionId}",
                        "${spec.actionId} is not something Cues can do.",
                    ),
                )
                return@forEach
            }
            when (val result = definition.validate(spec.args)) {
                is ArgResult.Valid -> Unit
                is ArgResult.Invalid ->
                    add(Finding(Severity.ERROR, "actions.${spec.actionId}", result.problem))
            }
        }

        actions.groupBy { it.actionId }.filterValues { it.size > 1 }.keys.forEach { duplicated ->
            add(
                Finding(
                    Severity.ERROR,
                    "actions.$duplicated",
                    "$duplicated appears more than once.",
                ),
            )
        }
    }

    private fun validateEndings(routine: Routine): List<Finding> = buildList {
        if (routine.endConditions.isEmpty()) {
            // The product's central claim is that a cue has an ending. A cue
            // without one is not a degraded cue, it is a different thing.
            add(
                Finding(
                    Severity.ERROR,
                    "endConditions",
                    "This cue has no ending, so it would never stop on its own.",
                ),
            )
        }

        routine.endConditions.forEach { addAll(SignalRegistry.validateEnd(it)) }

        val timerMinutes = routine.actions
            .firstOrNull { it.actionId == ActionId.START_FOCUS_TIMER }
            ?.let { (it.args as? ActionArgs.FocusTimer)?.durationMinutes }

        val durationEnd = routine.endConditions.filterIsInstance<EndCondition.Duration>().firstOrNull()

        if (timerMinutes != null && durationEnd != null && timerMinutes != durationEnd.minutes) {
            add(
                Finding(
                    Severity.ERROR,
                    "endConditions.duration",
                    "The timer runs for $timerMinutes minutes but the cue ends after " +
                        "${durationEnd.minutes}. One of them is wrong.",
                ),
            )
        }

        if (routine.trigger is Trigger.Manual &&
            routine.endConditions.none { it is EndCondition.Duration || it == EndCondition.ManualStop }
        ) {
            add(
                Finding(
                    Severity.ERROR,
                    "endConditions",
                    "A cue run by hand needs a duration or a stop button to end it.",
                ),
            )
        }
    }

    private fun validateRearm(policy: RearmPolicy): List<Finding> = buildList {
        if (policy.reconnectGraceSeconds < 0) {
            add(Finding(Severity.ERROR, "rearmPolicy.grace", "The reconnect grace period cannot be negative."))
        }
        if (policy.reconnectGraceSeconds > 300) {
            add(
                Finding(
                    Severity.WARNING,
                    "rearmPolicy.grace",
                    "A ${policy.reconnectGraceSeconds}-second grace period means the cue keeps running " +
                        "for that long after you disconnect.",
                ),
            )
        }
        if (policy.maxConcurrentSessions != 1) {
            add(
                Finding(
                    Severity.ERROR,
                    "rearmPolicy.maxConcurrentSessions",
                    "This build runs one session per cue at a time.",
                ),
            )
        }
    }

    private fun validateCleanup(policy: CleanupPolicy): List<Finding> = buildList {
        if (!policy.releaseOwnedEffectsOnly) {
            // Restoring global state we did not set means guessing who else
            // wanted it. Cues does not have that information and will not
            // pretend to.
            add(
                Finding(
                    Severity.ERROR,
                    "cleanupPolicy",
                    "Cues can only release changes it made itself.",
                ),
            )
        }
    }
}
