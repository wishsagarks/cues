package com.cues.core.review

import com.cues.core.Fixtures
import com.cues.core.compile.Validator
import com.cues.core.drafting.DraftResult
import com.cues.core.drafting.GrammarParser
import com.cues.core.drafting.PairedDevice
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import org.junit.jupiter.api.Test

/** Every checked-in template that names a device resolves against this one. */
private val PAIRED = listOf(
    PairedDevice(Fixtures.EARBUDS_ID, Fixtures.EARBUDS_LABEL, setOf("earbuds", "ear buds", "buds", "headphones", "headset")),
)

/**
 * Every checked-in template is a promise: a judge who taps it gets a clean
 * draft, not a request that quietly loses a clause. If a template cannot
 * clear this bar, it belongs out of the gallery, not exempted from the test.
 */
class TemplatesTest {

    @Test
    fun `at least one template is checked in`() {
        assertTrue(Templates.load().isNotEmpty())
    }

    @Test
    fun `every template compiles with the grammar parser and nothing is unaccounted`() {
        val parser = GrammarParser(PAIRED)
        Templates.load().forEach { template ->
            val result = parser.parse(template.sentence)
            val drafted = assertIs<DraftResult.Drafted>(result, "template '${template.label}' did not draft cleanly")
            assertTrue(drafted.unsupported.isEmpty(), "template '${template.label}' left unsupported clauses: ${drafted.unsupported}")
            assertFalse(
                drafted.clauses.any { it.kind == com.cues.core.drafting.ClauseKind.UNACCOUNTED },
                "template '${template.label}' left an unaccounted clause",
            )
            val validation = Validator.validate(com.cues.core.compile.Normalizer.normalize(drafted.routine))
            assertTrue(validation.isValid, "template '${template.label}' failed validation: ${validation.errors}")
        }
    }

    @Test
    fun `labels are unique`() {
        val labels = Templates.load().map { it.label }
        assertTrue(labels.size == labels.toSet().size, "duplicate template labels: $labels")
    }
}
