package com.cues.core.model

import kotlinx.serialization.Serializable

/**
 * A closed set of iQOO/OriginOS utilities Cues can bind an on/off pair to.
 *
 * Closed on purpose, the same reason [ActionId] is closed: a routine cannot
 * ask Cues to toggle a utility that isn't already a deliberate, reviewed
 * entry here. [com.cues.core.registry.UtilityCatalog] is the single place
 * that explains what each one means; this enum is just the allowlisted key.
 */
@Serializable
enum class UtilityId { EYE_PROTECTION, ULTRA_SAVER, GAME_MODE }

@Serializable
enum class UtilityState { ON, OFF }

/**
 * The taught macro pair for one utility — which [UiMacro] turns it on, which
 * turns it off. Both must exist in the [com.cues.core.ports.MacroStore]
 * before [ActionId.USE_UTILITY] can do anything but report itself blocked.
 */
@Serializable
data class UtilityBinding(
    val utilityId: UtilityId,
    val onMacroId: String,
    val offMacroId: String,
)
