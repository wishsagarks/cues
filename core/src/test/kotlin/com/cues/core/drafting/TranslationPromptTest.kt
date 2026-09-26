package com.cues.core.drafting

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TranslationPromptTest {
    @Test fun `prompt names the source language and carries the request verbatim`() {
        val prompt = TranslationPrompt.build("ऑफिस पहुंचने पर मम्मी को बताओ", "Hindi")
        assertTrue(prompt.contains("Hindi"))
        assertTrue(prompt.contains("ऑफिस पहुंचने पर मम्मी को बताओ"))
        assertTrue(prompt.contains("Reply with only the"))
    }

    @Test fun `clean strips wrappers a model might add`() {
        assertEquals("when I arrive at office, text mum", TranslationPrompt.clean("\"when I arrive at office, text mum\""))
        assertEquals("when I arrive at office, text mum", TranslationPrompt.clean("Translation: when I arrive at office, text mum"))
        assertEquals("when I arrive at office, text mum", TranslationPrompt.clean("```when I arrive at office, text mum```"))
    }
}
