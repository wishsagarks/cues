package com.cues.core.compile

import com.cues.core.model.UiExpectation
import com.cues.core.model.UiMacro
import com.cues.core.model.UiSelector
import com.cues.core.model.UiStep
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class MacroValidatorTest {

    private fun click(text: String? = null, contentDescription: String? = null, id: String? = null) = UiStep.Click(
        UiSelector(viewIdResourceName = id, text = text, contentDescription = contentDescription),
    )

    private fun macro(
        steps: List<UiStep>,
        packageName: String = "com.example.originos",
    ) = UiMacro(
        id = "macro-1",
        label = "Eye protection on",
        packageName = packageName,
        versionCode = 1,
        steps = steps,
        createdAtMillis = 0,
    )

    @Test
    fun `a clean toggle macro is valid`() {
        val result = MacroValidator.validate(
            macro(listOf(click(id = "com.example.originos:id/eye_protection_switch"))),
        )
        assertTrue(result.isValid, result.errors.toString())
    }

    @Test
    fun `an empty macro is invalid`() {
        assertFalse(MacroValidator.isSafe(macro(emptyList())))
    }

    @Test
    fun `more than the step budget is invalid`() {
        val steps = (1..MacroValidator.MAX_STEPS + 1).map { click(id = "id-$it") }
        assertFalse(MacroValidator.isSafe(macro(steps)))
    }

    @Test
    fun `exactly the step budget is valid`() {
        val steps = (1..MacroValidator.MAX_STEPS).map { click(id = "id-$it") }
        assertTrue(MacroValidator.isSafe(macro(steps)))
    }

    @Test
    fun `a step naming nothing to find is invalid`() {
        assertFalse(MacroValidator.isSafe(macro(listOf(UiStep.Click(UiSelector())))))
    }

    @Test
    fun `setting text into a password field is always invalid`() {
        val step = UiStep.SetText(UiSelector(viewIdResourceName = "id/pin", isPassword = true), text = "1234")
        assertFalse(MacroValidator.isSafe(macro(listOf(step))))
    }

    @Test
    fun `setting text into a non-password field is fine`() {
        val step = UiStep.SetText(UiSelector(viewIdResourceName = "id/note"), text = "hello")
        assertTrue(MacroValidator.isSafe(macro(listOf(step))))
    }

    @Test
    fun `a macro targeting a denylisted package is invalid`() {
        val result = MacroValidator.validate(macro(listOf(click(id = "id/x")), packageName = "com.android.settings"))
        assertFalse(result.isValid)
    }

    @Test
    fun `cues own package is denylisted`() {
        assertFalse(MacroValidator.isSafe(macro(listOf(click(id = "id/x")), packageName = "com.cues.android")))
    }

    @Test
    fun `clicking a denylisted word is refused, whatever case it is written in`() {
        listOf("Send", "PAY", "confirm", "Delete", "Place order").forEach { label ->
            assertFalse(MacroValidator.isSafe(macro(listOf(click(text = label)))), "label=$label")
        }
    }

    @Test
    fun `clicking a currency amount is refused`() {
        assertFalse(MacroValidator.isSafe(macro(listOf(click(text = "Pay ₹499")))))
        assertFalse(MacroValidator.isSafe(macro(listOf(click(text = "499 INR")))))
    }

    @Test
    fun `a macro may scroll past a denylisted label without tapping it`() {
        // "Stop before a denylisted button, never tap one": a SCROLL step
        // that merely names the button as a landmark is not itself a tap.
        val scroll = UiStep.Scroll(UiSelector(text = "Confirm purchase"))
        assertTrue(MacroValidator.isSafe(macro(listOf(scroll))))
    }

    @Test
    fun `a denylisted word that is only a substring of an innocent label does not false-positive`() {
        // "send" must not match "sending" or "sender" — word-boundary match only.
        assertTrue(MacroValidator.isSafe(macro(listOf(click(text = "Sender name")))))
    }

    @Test
    fun `an ordinary toggle-labeled click is fine`() {
        assertTrue(MacroValidator.isSafe(macro(listOf(click(contentDescription = "Eye protection")))))
    }

    @Test
    fun `checking the expectation shape does not itself change validity`() {
        val step = click(id = "id/x").copy(expect = UiExpectation(checked = true))
        assertTrue(MacroValidator.isSafe(macro(listOf(step))))
    }
}
