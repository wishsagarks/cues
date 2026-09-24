package com.cues.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A taught sequence of UI steps against one app's own screen.
 *
 * This exists only because some iQOO/OriginOS utilities have no public API —
 * see the FDD's "Utility Bindings" section. It is the last-resort mechanism,
 * never the first: a macro is recorded by the user performing the steps
 * themselves, never generated or edited by a model. [com.cues.core.compile.MacroValidator]
 * is the gate between "the user taught this" and "the runtime will replay
 * it", and every step here carries the [UiExpectation] the runtime checks
 * before calling that step done.
 */
@Serializable
data class UiMacro(
    val id: String,
    /** Shown in Review and in the Utility Bindings screen — e.g. "Eye protection on". */
    val label: String,
    /** The app this macro was taught against. Replay never crosses into another package. */
    val packageName: String,
    /**
     * The exact build the steps were recorded against.
     *
     * An app update can rename, move or remove a view. Replaying stale steps
     * against a changed screen is exactly the "GUI automation inherently
     * carries security and stability risks" failure mode the FDD calls out —
     * so a mismatch here is not a warning, it is a refusal
     * (`MACRO_APP_CHANGED`), checked before a single step runs.
     */
    val versionCode: Long,
    val steps: List<UiStep>,
    val createdAtMillis: Long,
)

/**
 * One taught step. The identifier is a node selector plus an expected
 * outcome — never free text a model could turn into an arbitrary command.
 */
@Serializable
sealed interface UiStep {
    val selector: UiSelector
    val expect: UiExpectation

    @Serializable
    @SerialName("click")
    data class Click(
        override val selector: UiSelector,
        override val expect: UiExpectation = UiExpectation(),
    ) : UiStep

    /** Never permitted into a password field — [com.cues.core.compile.MacroValidator] refuses this at teach time. */
    @Serializable
    @SerialName("setText")
    data class SetText(
        override val selector: UiSelector,
        val text: String,
        override val expect: UiExpectation = UiExpectation(),
    ) : UiStep

    @Serializable
    @SerialName("scroll")
    data class Scroll(
        override val selector: UiSelector,
        val direction: ScrollDirection = ScrollDirection.FORWARD,
        override val expect: UiExpectation = UiExpectation(),
    ) : UiStep
}

@Serializable
enum class ScrollDirection { FORWARD, BACKWARD }

/**
 * How the runtime finds the node a step acts on.
 *
 * At least one of [viewIdResourceName], [text] or [contentDescription] must
 * be set — [com.cues.core.compile.MacroValidator] refuses a step that names
 * nothing to find. [isPassword] is recorded at teach time from the node's own
 * `AccessibilityNodeInfo.isPassword`, never guessed from the selector text.
 */
@Serializable
data class UiSelector(
    val viewIdResourceName: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val className: String? = null,
    val isPassword: Boolean = false,
) {
    val isEmpty: Boolean get() = viewIdResourceName.isNullOrBlank() && text.isNullOrBlank() && contentDescription.isNullOrBlank()
}

/**
 * What must be true of the node after a step runs for it to count as done.
 *
 * `null` on a field means "not checked". An expectation with every field
 * null still requires the selector to have matched *something* — the runtime
 * distinguishes "matched, nothing further to confirm" from "never found".
 */
@Serializable
data class UiExpectation(
    val checked: Boolean? = null,
    val textContains: String? = null,
)
