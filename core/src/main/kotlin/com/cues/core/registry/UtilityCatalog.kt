package com.cues.core.registry

import com.cues.core.model.UtilityId

/**
 * How Cues can reach one iQOO/OriginOS utility, in order of preference.
 *
 * Mirrors the FDD's mechanism list: a public API beats a discovered intent
 * beats an accessibility binding. [ACCESSIBILITY_BINDING] is recorded
 * explicitly on every entry below rather than assumed, because it is the
 * one mechanism whose risk class is [ActionRisk.UI_AUTOMATION].
 */
enum class UtilityMechanism { PUBLIC_INTENT, SETTINGS_PANEL, ACCESSIBILITY_BINDING }

/**
 * What one cataloged utility means, for review and for the runtime.
 *
 * [readBackDescription] is shown in the Utility Bindings screen so the
 * consent text can name, concretely, what "confirm the state" means for this
 * utility — never a generic "we'll check it worked".
 */
data class UtilityDefinition(
    val id: UtilityId,
    val label: String,
    val mechanism: UtilityMechanism,
    val reversible: Boolean,
    val readBackDescription: String,
)

/**
 * The closed list of utilities a [com.cues.core.model.ActionId.USE_UTILITY]
 * action may name.
 *
 * Closed the same way [ActionRegistry] is: adding an entry is a deliberate
 * act, not something a taught macro or a drafter can expand on its own. Every
 * [UtilityId] value must have an entry here — [definition] is total over the
 * enum, never a lookup that can silently miss one.
 */
object UtilityCatalog {

    private val definitions: Map<UtilityId, UtilityDefinition> = listOf(
        UtilityDefinition(
            id = UtilityId.EYE_PROTECTION,
            label = "Eye protection",
            mechanism = UtilityMechanism.ACCESSIBILITY_BINDING,
            reversible = true,
            readBackDescription = "the toggle's own checked state, read back after the tap",
        ),
        UtilityDefinition(
            id = UtilityId.ULTRA_SAVER,
            label = "Ultra saver",
            mechanism = UtilityMechanism.ACCESSIBILITY_BINDING,
            reversible = true,
            readBackDescription = "the toggle's own checked state, read back after the tap",
        ),
        UtilityDefinition(
            id = UtilityId.GAME_MODE,
            label = "Game Mode",
            mechanism = UtilityMechanism.ACCESSIBILITY_BINDING,
            reversible = true,
            readBackDescription = "the toggle's own checked state, read back after the tap",
        ),
    ).associateBy { it.id }

    init {
        // A missing entry here is a build-time mistake, not a user-facing
        // one — same reasoning as ActionRegistry's closed map, made loud
        // immediately rather than surfacing as a confusing null much later.
        val missing = UtilityId.entries.filterNot { it in definitions }
        check(missing.isEmpty()) { "UtilityCatalog is missing definitions for: $missing" }
    }

    fun definition(id: UtilityId): UtilityDefinition = definitions.getValue(id)

    val all: List<UtilityDefinition> get() = definitions.values.toList()

    /**
     * Whether a raw, not-yet-typed identifier (from stored JSON, an import,
     * or anywhere else outside the closed enum) names a utility this build
     * still recognizes. A utility retired from [UtilityId] stops being
     * recognized here even though old persisted data may still mention it.
     */
    fun isKnownRawId(raw: String): Boolean = UtilityId.entries.any { it.name == raw }
}
