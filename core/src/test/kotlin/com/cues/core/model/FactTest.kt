package com.cues.core.model

import com.cues.core.CueService
import com.cues.core.Fixtures
import com.cues.core.assistant.Conversation
import com.cues.core.assistant.ReplyCode
import com.cues.core.drafting.GrammarParser
import com.cues.core.ports.CapabilityProvider
import com.cues.core.session.FakeClock
import com.cues.core.session.RecordingExecutor
import com.cues.core.store.JsonFileStore
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class FactTest {
    @Test
    fun `remember creates an explicit sourced fact and editing it invalidates dependent approval`() = runTest {
        val root = createTempDirectory("facts-test").toFile()
        try {
            val store = JsonFileStore(root)
            val service = CueService(
                store, store, store, RecordingExecutor(), FakeClock(Fixtures.NOW),
                CapabilityProvider { Capability.entries.toSet() }, GrammarParser(), facts = store,
            )

            val turn = service.converse(Conversation(), "remember my exam is Oct 12")
            assertEquals(ReplyCode.FACT_REMEMBERED, turn.reply.code)
            val fact = assertNotNull(store.allFacts().singleOrNull())
            assertEquals(FactKind.DATE, fact.kind)
            assertEquals(FactSource.SAID, fact.source)

            val routine = Fixtures.heroRoutine().copy(
                factDependencies = setOf(FactReference(fact.id, fact.version, fact.label)),
            )
            store.save(routine)
            service.saveFact(fact.copy(version = 2, value = "Oct 13"))

            val invalidated = assertNotNull(store.findRoutine(routine.id))
            assertNull(invalidated.approvedDigest)
            assertEquals(RoutineStatus.REVIEWABLE, invalidated.status)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `deleting a fact invalidates every dependent routine`() {
        val root = createTempDirectory("facts-delete-test").toFile()
        try {
            val store = JsonFileStore(root)
            val fact = Fact("exam", 1, FactKind.DATE, "exam", "Oct 12", FactSource.SAID, Fixtures.NOW)
            store.saveFact(fact)
            val routine = Fixtures.heroRoutine().copy(factDependencies = setOf(FactReference("exam", 1, "exam")))
            store.save(routine)
            val service = CueService(
                store, store, store, RecordingExecutor(), FakeClock(Fixtures.NOW),
                CapabilityProvider { Capability.entries.toSet() }, GrammarParser(), facts = store,
            )

            service.deleteFact("exam")

            assertNull(store.findFact("exam"))
            assertNull(store.findRoutine(routine.id)?.approvedDigest)
        } finally {
            root.deleteRecursively()
        }
    }
}
