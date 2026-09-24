package com.cues.core.context

import com.cues.core.Fixtures
import com.cues.core.model.Routine
import com.cues.core.ports.RoutineStore
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.jupiter.api.Test

class PersonalIndexTest {
    @Test
    fun `exact title wins and ambiguous lexical references produce candidates`() {
        val study = Fixtures.heroRoutine().copy(id = "study", title = "Study")
        val evening = Fixtures.heroRoutine().copy(id = "evening", title = "Evening study")
        val index = PersonalIndex(InMemoryRoutines(listOf(study, evening)))

        assertEquals("study", assertIs<ReferenceResolution.Resolved>(index.resolveRoutine("Study")).routine.id)
        assertEquals(
            setOf("study", "evening"),
            assertIs<ReferenceResolution.NeedsClarification>(index.resolveRoutine("study cue")).candidates.map { it.id }.toSet(),
        )
    }

    private class InMemoryRoutines(private val items: List<Routine>) : RoutineStore {
        override fun save(routine: Routine) = Unit
        override fun findRoutine(id: String): Routine? = items.firstOrNull { it.id == id }
        override fun all(): List<Routine> = items
        override fun armed(): List<Routine> = items
        override fun delete(id: String) = Unit
    }
}
