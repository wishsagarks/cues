package com.cues.core.context

import com.cues.core.Fixtures
import com.cues.core.model.Routine
import com.cues.core.ports.Embedder
import com.cues.core.ports.RoutineStore
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.jupiter.api.Test

class EmbeddingPersonalIndexTest {
    @Test
    fun `embedding ranks only after lexical resolution fails and still asks for confirmation`() {
        val study = Fixtures.heroRoutine().copy(id = "study", title = "Study")
        val gym = Fixtures.heroRoutine().copy(id = "gym", title = "Gym")
        val embedder = Embedder { text ->
            when {
                "deep work" in text.lowercase() -> floatArrayOf(1f, 0f)
                "study" in text.lowercase() -> floatArrayOf(0.9f, 0.1f)
                else -> floatArrayOf(0f, 1f)
            }
        }
        val result = PersonalIndex(InMemoryRoutines(listOf(study, gym)), embedder).resolveRoutine("my deep work thing")

        assertEquals("study", assertIs<ReferenceResolution.NeedsConfirmation>(result).candidates.first().id)
    }

    private class InMemoryRoutines(private val items: List<Routine>) : RoutineStore {
        override fun save(routine: Routine) = Unit
        override fun findRoutine(id: String): Routine? = items.firstOrNull { it.id == id }
        override fun all(): List<Routine> = items
        override fun armed(): List<Routine> = items
        override fun delete(id: String) = Unit
    }
}
