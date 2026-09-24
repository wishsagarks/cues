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
    /** The user-visible risk class shown before approval. */
    val risk: ActionRisk,
    val requiredCapabilities: Set<Capability>,
    /** What this action makes the session responsible for releasing, if anything. */
    val owns: OwnedResource?,
    val validate: (ActionArgs) -> ArgResult,
    /**
     * Whether this action can fire unattended, or needs someone at the phone.
     *
     * Defaults to [Presence.UNATTENDED_OK], which is exactly what the four
     * original actions are: nothing here changes their behaviour.
     */
    val presence: Presence = Presence.UNATTENDED_OK,
)

/**
 * A deliberately small, closed explanation of an action's risk.
 *
 * This is registry data rather than a Compose opinion: a new action cannot
 * quietly appear in review without declaring the risk it carries.
 */
enum class ActionRisk {
    /** Cues creates an effect, records ownership, and can release it on exit. */
    OWNED_AND_REVERSIBLE,

    /** A local result notification changes no standing phone state. */
    LOCAL_NOTICE,

    /**
     * Cues opens or pre-fills something in another app; the user finishes it.
     * Success means "opened", never "done on your behalf".
     */
    HANDOFF,

    /**
     * Changes state Cues did not own before and cannot release — an alarm it
     * set, a media transport it nudged. The receipt says plainly that Cues
     * cannot undo this.
     */
    EXTERNAL_UNOWNED,

    /**
     * The highest risk class: the action's mechanism can be GUI automation
     * against another app's own screen rather than a public API. Shown in
     * red in Review, per the FDD's "Utility Bindings" section.
     */
    UI_AUTOMATION,
}

/** Whether an action can run with nobody looking, or needs someone at the phone. */
enum class Presence {
    UNATTENDED_OK,
    NEEDS_USER,
}

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
    const val MAX_MESSAGE_CHARS = 160
    const val MAX_CALENDAR_MINUTES = 24 * 60

    /**
     * The only two schemes [ActionArgs.OpenLink] may carry. A model or an
     * imported card can propose a link; it cannot propose a scheme this
     * allowlist doesn't already contain.
     */
    val ALLOWED_LINK_SCHEMES = setOf("https", "tel")

    private val definitions: Map<ActionId, ActionDefinition> = listOf(
        ActionDefinition(
            id = ActionId.START_FOCUS_TIMER,
            label = "Start a focus timer",
            risk = ActionRisk.OWNED_AND_REVERSIBLE,
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
            risk = ActionRisk.OWNED_AND_REVERSIBLE,
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
            risk = ActionRisk.LOCAL_NOTICE,
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

        ActionDefinition(
            id = ActionId.PINNED_NOTE,
            label = "Keep a pinned note visible",
            risk = ActionRisk.OWNED_AND_REVERSIBLE,
            requiredCapabilities = setOf(Capability.POST_NOTIFICATIONS),
            owns = OwnedResource.PINNED_NOTE,
            validate = { args ->
                when (args) {
                    is ActionArgs.PinnedNote -> when {
                        args.message.isBlank() -> ArgResult.Invalid("A pinned note cannot be empty.")
                        args.message.length > MAX_NOTIFY_CHARS ->
                            ArgResult.Invalid("A pinned note must be $MAX_NOTIFY_CHARS characters or fewer.")
                        else -> ArgResult.Valid(args)
                    }
                    else -> ArgResult.Invalid("A pinned note needs bounded text.")
                }
            },
        ),

        ActionDefinition(
            id = ActionId.OPEN_APP,
            label = "Open an app",
            risk = ActionRisk.HANDOFF,
            presence = Presence.NEEDS_USER,
            // Launching another app's own launcher activity needs no
            // Cues-side permission; the OS mediates it like any home-screen tap.
            requiredCapabilities = emptySet(),
            owns = null,
            validate = { args ->
                when (args) {
                    is ActionArgs.OpenApp -> when {
                        args.packageName.isBlank() -> ArgResult.Invalid("No app was chosen.")
                        args.label.isBlank() -> ArgResult.Invalid("The chosen app has no label to show.")
                        else -> ArgResult.Valid(args)
                    }
                    else -> ArgResult.Invalid("Opening an app needs a chosen app.")
                }
            },
        ),

        ActionDefinition(
            id = ActionId.COMPOSE_MESSAGE,
            label = "Pre-fill a message",
            risk = ActionRisk.HANDOFF,
            presence = Presence.NEEDS_USER,
            requiredCapabilities = emptySet(),
            owns = null,
            validate = { args ->
                when (args) {
                    is ActionArgs.ComposeMessage -> when {
                        args.text.isBlank() -> ArgResult.Invalid("A message needs text to pre-fill.")
                        args.text.length > MAX_MESSAGE_CHARS ->
                            ArgResult.Invalid("A message must be $MAX_MESSAGE_CHARS characters or fewer.")
                        else -> ArgResult.Valid(args)
                    }
                    else -> ArgResult.Invalid("Pre-filling a message needs text.")
                }
            },
        ),

        ActionDefinition(
            id = ActionId.ADD_CALENDAR_EVENT,
            label = "Add a calendar event",
            risk = ActionRisk.HANDOFF,
            presence = Presence.NEEDS_USER,
            // ACTION_INSERT opens the calendar app's own editor; Cues never
            // reads or writes the calendar provider directly.
            requiredCapabilities = emptySet(),
            owns = null,
            validate = { args ->
                when (args) {
                    is ActionArgs.CalendarEvent -> when {
                        args.title.isBlank() -> ArgResult.Invalid("A calendar event needs a title.")
                        args.durationMinutes !in 1..MAX_CALENDAR_MINUTES ->
                            ArgResult.Invalid("A calendar event must be between 1 and $MAX_CALENDAR_MINUTES minutes.")
                        else -> ArgResult.Valid(args)
                    }
                    else -> ArgResult.Invalid("Adding a calendar event needs a title and a time.")
                }
            },
        ),

        ActionDefinition(
            id = ActionId.SET_ALARM,
            label = "Set an alarm",
            risk = ActionRisk.EXTERNAL_UNOWNED,
            // AlarmClock.ACTION_SET_ALARM shows the clock app's own confirm
            // screen unless the caller passes EXTRA_SKIP_UI. Cues never
            // does, so this stays NEEDS_USER rather than claiming an
            // unattended path.
            presence = Presence.NEEDS_USER,
            requiredCapabilities = emptySet(),
            // An alarm the clock app now owns. Cues did not have one before
            // and has no way to take it back — see ActionRisk.EXTERNAL_UNOWNED.
            owns = null,
            validate = { args ->
                when (args) {
                    is ActionArgs.Alarm -> when {
                        args.hour !in 0..23 -> ArgResult.Invalid("An alarm hour must be 0-23.")
                        args.minute !in 0..59 -> ArgResult.Invalid("An alarm minute must be 0-59.")
                        else -> ArgResult.Valid(args)
                    }
                    else -> ArgResult.Invalid("Setting an alarm needs an hour and minute.")
                }
            },
        ),

        ActionDefinition(
            id = ActionId.MEDIA_CONTROL,
            label = "Control media playback",
            risk = ActionRisk.EXTERNAL_UNOWNED,
            requiredCapabilities = emptySet(),
            owns = null,
            validate = { args ->
                when (args) {
                    is ActionArgs.MediaControl -> ArgResult.Valid(args)
                    else -> ArgResult.Invalid("Controlling media needs a command.")
                }
            },
        ),

        ActionDefinition(
            id = ActionId.RINGER_MODE,
            label = "Set the ringer",
            risk = ActionRisk.OWNED_AND_REVERSIBLE,
            // Silencing the ringer via AudioManager needs the same access as
            // the app's existing quiet-notifications rule on modern Android.
            requiredCapabilities = setOf(Capability.NOTIFICATION_POLICY_ACCESS),
            owns = OwnedResource.RINGER_MODE,
            validate = { args ->
                when (args) {
                    is ActionArgs.RingerMode -> ArgResult.Valid(args)
                    else -> ArgResult.Invalid("Setting the ringer needs a mode.")
                }
            },
        ),

        ActionDefinition(
            id = ActionId.OPEN_LINK,
            label = "Open a link",
            risk = ActionRisk.HANDOFF,
            presence = Presence.NEEDS_USER,
            requiredCapabilities = emptySet(),
            owns = null,
            validate = { args ->
                when (args) {
                    is ActionArgs.OpenLink -> {
                        val scheme = runCatching { java.net.URI(args.url).scheme }.getOrNull()?.lowercase()
                        if (scheme !in ALLOWED_LINK_SCHEMES) {
                            ArgResult.Invalid("A link must start with one of: ${ALLOWED_LINK_SCHEMES.joinToString()}.")
                        } else {
                            ArgResult.Valid(args)
                        }
                    }
                    else -> ArgResult.Invalid("Opening a link needs a URL.")
                }
            },
        ),

        ActionDefinition(
            id = ActionId.USE_UTILITY,
            label = "Use an iQOO utility",
            risk = ActionRisk.UI_AUTOMATION,
            // Same reasoning as OPEN_APP and the other handoffs: nobody
            // should come back to a phone that just tapped its way through
            // another app's settings screen unattended.
            presence = Presence.NEEDS_USER,
            requiredCapabilities = setOf(Capability.ACCESSIBILITY_SERVICE),
            owns = OwnedResource.UTILITY_CONTRIBUTION,
            validate = { args ->
                when (args) {
                    is ActionArgs.UseUtility -> ArgResult.Valid(args)
                    else -> ArgResult.Invalid("Using a utility needs which one, and on or off.")
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
