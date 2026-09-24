package com.cues.core.drafting

import com.cues.core.Fixtures
import com.cues.core.model.ActionArgs
import com.cues.core.model.ActionId
import com.cues.core.model.ActionSpec
import com.cues.core.model.RoutineStatus
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class ClauseAccountingTest {
    @Test fun `unmapped request text is never hidden`() {
        val clauses = ClauseAccounting.classify("when earbuds connect text Mum", listOf(5..11, 13..19))
        assertEquals(listOf("text", "Mum"), clauses.filter { it.kind == ClauseKind.UNACCOUNTED }.map { it.text })
    }

    @Test fun `filler does not block but unaccounted text does`() {
        val clean = Fixtures.heroRoutine().copy(unaccountedClauses = emptyList())
        val blocked = clean.copy(unaccountedClauses = listOf("text", "Mum"), status = RoutineStatus.DRAFT)
        assertTrue(com.cues.core.compile.Validator.validate(clean).isValid)
        assertTrue(!com.cues.core.compile.Validator.validate(blocked).isValid)
    }

    @Test fun `a parser draft that drops a clause cannot validate once stamped`() {
        val sentence = "When my earbuds connect start a timer and text Mum"
        val parser = GrammarParser(listOf(PairedDevice(Fixtures.EARBUDS_ID, Fixtures.EARBUDS_LABEL, setOf("earbuds"))))
        val drafted = parser.parse(sentence) as DraftResult.Drafted

        val stamped = ClauseAccounting.stamp(sentence, drafted)

        assertEquals(listOf("text", "Mum"), stamped.routine.unaccountedClauses)
        assertTrue(!com.cues.core.compile.Validator.validate(stamped.routine).isValid)
    }
}
