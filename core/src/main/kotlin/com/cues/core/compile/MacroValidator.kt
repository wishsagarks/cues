package com.cues.core.compile

import com.cues.core.model.UiMacro
import com.cues.core.model.UiStep

/**
 * The gate between "the user taught this" and "the runtime will replay it".
 *
 * A taught macro is the last-resort mechanism for reaching a utility with no
 * public API — see the FDD's "Utility Bindings" section — and the FDD is
 * explicit that this kind of automation "inherently carries security and
 * stability risks". This validator is where that risk gets a fixed, defensible
 * boundary instead of an ad-hoc one: a small step budget, a closed package
 * denylist, and a closed word/currency denylist a CLICK step can never target.
 *
 * Deliberately independent of who taught the macro or which app it targets —
 * the same rule [Validator] already follows for a drafted routine. A macro
 * that passes this can still fail at replay (a selector that no longer
 * matches, a version mismatch); this only decides whether it is safe to *try*.
 */
object MacroValidator {

    /** A macro long enough to need this many steps is doing more than a toggle. */
    const val MAX_STEPS = 8

    /**
     * Packages a macro may never target, however it was taught.
     *
     * A closed, disclosed list — not a claim of completeness. It cannot be
     * exhaustive over every UPI, banking or password-manager app that exists;
     * see CLEANUP.md for the entry tracking that this needs a device-side
     * discovery pass (Spike U0) rather than being trusted as-is.
     */
    val DENYLISTED_PACKAGES: Set<String> = setOf(
        // Cues itself: a macro replaying steps against its own UI is not a
        // utility binding, it is a way to script around approval.
        "com.cues.android",
        // Settings and the Play Store: neither is a "utility" in the FDD's
        // sense, and both sit upstream of permissions this validator cannot see.
        "com.android.settings",
        "com.google.android.apps.vending",
        "com.android.vending",
        // A representative, non-exhaustive set of UPI / payment apps.
        "net.one97.paytm",
        "com.phonepe.app",
        "com.google.android.apps.nbu.paisa.user",
        "in.org.npci.upiapp",
        "com.csam.icici.bank.imobile",
        "com.snapwork.hdfc",
        "com.sbi.lotusintouch",
        "com.axis.mobile",
        // Password managers.
        "com.lastpass.lpandroid",
        "com.agilebits.onepassword",
        "com.dashlane",
        "com.google.android.apps.passwordsafety",
    )

    /**
     * Words that make a CLICK step a payment, deletion or submission rather
     * than a toggle. Matched against a step's own label — [UiStep.selector]'s
     * `text`/`contentDescription` — never against the app's surrounding
     * screen, so a "Pay" button elsewhere on a page a macro merely scrolls
     * past does not itself refuse the macro.
     */
    val DENYLISTED_CLICK_WORDS: Set<String> = setOf(
        "send", "pay", "buy", "confirm", "delete", "transfer", "submit",
        "post", "place order",
    )

    private val CURRENCY_PATTERN = Regex("[$₹€£]\\s?\\d|\\b\\d+(?:[.,]\\d+)?\\s?(?:usd|inr|eur|gbp|rs)\\b", RegexOption.IGNORE_CASE)

    fun validate(macro: UiMacro): ValidationResult {
        val findings = buildList {
            if (macro.steps.isEmpty()) {
                add(Finding(Severity.ERROR, "steps", "A macro needs at least one step."))
            }
            if (macro.steps.size > MAX_STEPS) {
                add(
                    Finding(
                        Severity.ERROR,
                        "steps",
                        "A macro can have at most $MAX_STEPS steps; this one has ${macro.steps.size}.",
                    ),
                )
            }
            if (macro.packageName.isBlank()) {
                add(Finding(Severity.ERROR, "packageName", "A macro needs the app it was taught against."))
            }
            if (macro.packageName in DENYLISTED_PACKAGES) {
                add(
                    Finding(
                        Severity.ERROR,
                        "packageName",
                        "${macro.packageName} is not a package Cues will automate.",
                    ),
                )
            }

            macro.steps.forEachIndexed { index, step ->
                addAll(validateStep(index, step))
            }
        }
        return ValidationResult(findings)
    }

    private fun validateStep(index: Int, step: UiStep): List<Finding> = buildList {
        val field = "steps[$index]"

        if (step.selector.isEmpty) {
            add(Finding(Severity.ERROR, field, "This step names nothing to find on screen."))
        }

        if (step is UiStep.SetText && step.selector.isPassword) {
            add(Finding(Severity.ERROR, field, "A macro may never type into a password field."))
        }

        // "A macro may stop before a denylisted button, but it may never tap
        // one" — only CLICK steps are checked against the word list. A SCROLL
        // or SET_TEXT step naming the same label (scrolling a denylisted
        // button into view, say) is not itself a tap.
        if (step is UiStep.Click) {
            val label = listOfNotNull(step.selector.text, step.selector.contentDescription)
                .joinToString(" ")
                .lowercase()
            val matchedWord = DENYLISTED_CLICK_WORDS.firstOrNull { word ->
                Regex("\\b${Regex.escape(word)}\\b").containsMatchIn(label)
            }
            if (matchedWord != null) {
                add(Finding(Severity.ERROR, field, "This step would tap \"$matchedWord\", which a macro may never do."))
            }
            if (CURRENCY_PATTERN.containsMatchIn(label)) {
                add(Finding(Severity.ERROR, field, "This step would tap something naming a currency amount."))
            }
        }
    }

    /** True only when [validate] finds no errors — a convenience for callers that don't need the findings themselves. */
    fun isSafe(macro: UiMacro): Boolean = validate(macro).isValid
}
