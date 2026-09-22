package com.cues.core.registry

import com.cues.core.model.ActionArgs
import com.cues.core.model.ActionId
import com.cues.core.model.ActionSpec
import com.cues.core.model.Capability
import com.cues.core.model.OwnedResource

/**
 * What one allowlisted action needs, produces and owes.
 *
 * Note what is absent: there is no field for a command line, a package name,
 * an intent or a URI. A drafting model chooses *which* registry entry runs and
 * supplies typed arguments for it; it cannot describe a new capability into
 * existence, because the registry has no way to represent one.
 */
data class ActionDefinition(
    val id: ActionId,
    val label: String,
    val requiredCapabilities: Set<Capability>,
    /** What this action makes the session responsible for releasing, if anything. */
    val owns: OwnedResource?,
    val validate: (ActionArgs) -> ArgResult,
)

sealed interface ArgResult {
    data class Valid(val normalized: ActionArgs) : ArgResult
    data class Invalid(val problem: String) : ArgResult
}

/**
 * The closed set of things Cues can do.
 *
 * Every entry is something the app performs on its own resources. Adding an
 * entry is a deliberate act with a permission story, an argument validator and
 * a cleanup answer attached — which is precisely the review that "let the model
 * fire an arbitrary intent" skips.
 */
object ActionRegistry {

    /** Bounds chosen to be defensible in a review, not to be maximally permissive. */
    const val MIN_TIMER_MINUTES = 1
    const val MAX_TIMER_MINUTES = 8 * 60
    const val MAX_NOTIFY_CHARS = 120

    private val definitions: Map<ActionId, ActionDefinition> = listOf(
        ActionDefinition(
            id = ActionId.START_FOCUS_TIMER,
            label = "Start a focus timer",
            // Exact alarms because a countdown that ends "somewhere in the next
            // fifteen minutes" is not a countdown.
            requiredCapabilities = setOf(Capability.EXACT_ALARM, Capability.POST_NOTIFICATIONS),
            owns = OwnedResource.FOCUS_TIMER,
            validate = { args ->
                when (args) {
                    is ActionArgs.FocusTimer -> when (args.durationMinutes) {
                        in MIN_TIMER_MINUTES..MAX_TIMER_MINUTES -> ArgResult.Valid(args)
                        else -> ArgResult.Invalid(
                            "A focus timer must be between $MIN_TIMER_MINUTES and " +
                                "$MAX_TIMER_MINUTES minutes, not ${args.durationMinutes}.",
                        )
                    }

                    else -> ArgResult.Invalid("A focus timer needs a duration.")
                }
            },
        ),

        ActionDefinition(
            id = ActionId.REQUEST_DND,
            label = "Quiet notifications",
            requiredCapabilities = setOf(Capability.NOTIFICATION_POLICY_ACCESS),
            owns = OwnedResource.DND_CONTRIBUTION,
            validate = { args ->
                when (args) {
                    is ActionArgs.Dnd -> ArgResult.Valid(args)
                    else -> ArgResult.Invalid("Quiet notifications takes no arguments beyond its priority setting.")
                }
            },
        ),

        ActionDefinition(
            id = ActionId.NOTIFY_RESULT,
            label = "Show the result",
            requiredCapabilities = setOf(Capability.POST_NOTIFICATIONS),
            // Nothing to release: a notification is not a mode.
            owns = null,
            validate = { args ->
                when (args) {
                    is ActionArgs.Notify -> when {
                        args.message.isBlank() -> ArgResult.Invalid("A result note cannot be empty.")
                        args.message.length > MAX_NOTIFY_CHARS ->
                            ArgResult.Invalid("A result note must be $MAX_NOTIFY_CHARS characters or fewer.")

                        else -> ArgResult.Valid(args)
                    }

                    else -> ArgResult.Invalid("A result note needs a message.")
                }
            },
        ),
    ).associateBy { it.id }

    fun definition(id: ActionId): ActionDefinition? = definitions[id]

    fun isAllowed(id: ActionId): Boolean = id in definitions

    val all: List<ActionDefinition> get() = definitions.values.toList()

    /**
     * Derives the permissions a routine genuinely needs.
     *
     * Always computed here from the trigger and the registry. A routine's
     * `requiredCapabilities` is never accepted as drafted, because a model
     * asserting that a rule needs no permissions is a claim about our code,
     * not about the world.
     */
    fun capabilitiesFor(actions: List<ActionSpec>): Set<Capability> =
        actions.mapNotNull { definitions[it.actionId] }
            .flatMap { it.requiredCapabilities }
            .toSet()

    /** What a session running these actions will owe on the way out. */
    fun ownedResourcesFor(actions: List<ActionSpec>): Set<OwnedResource> =
        actions.mapNotNull { definitions[it.actionId]?.owns }.toSet()
}
