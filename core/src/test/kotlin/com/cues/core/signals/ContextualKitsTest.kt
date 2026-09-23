package com.cues.core.signals

import com.cues.core.Fixtures
import com.cues.core.compile.Normalizer
import com.cues.core.compile.Validator
import com.cues.core.eval.Evaluator
import com.cues.core.eval.FreshnessPolicy
import com.cues.core.eval.Truth
import com.cues.core.model.*
import com.cues.core.ports.NamedContextStore
import com.cues.core.ports.PlaceStore
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class ContextualKitsTest {
    @Test fun `named context folds members with three valued conjunction`() {
        ContextualStores.contexts = Contexts(NamedContext("desk", "Desk", 1, listOf(
            Condition.ChargingState(true), Condition.BatteryAtLeast(30),
        )))
        val routine = Fixtures.heroRoutine(conditions = listOf(Condition.InContext("desk", 1, "Desk")))
        val matches = Evaluator.evaluate(routine, Fixtures.connect(), Fixtures.snapshot().copy(
            batteryPercent = Fixtures.known(80, source = ContextSource.BATTERY_MANAGER),
        ), FreshnessPolicy.NONE)
        val unknown = Evaluator.evaluate(routine, Fixtures.connect(), Fixtures.snapshot().copy(
            batteryPercent = Fixtures.unknown(source = ContextSource.BATTERY_MANAGER),
        ), FreshnessPolicy.NONE)
        assertEquals(Truth.MATCH, matches.truth)
        assertEquals(Truth.UNKNOWN, unknown.truth)
    }

    @Test fun `editing a named context changes approval digest and nested contexts reject`() {
        val original = NamedContext("desk", "Desk", 1, listOf(Condition.ChargingState(true)))
        val store = Contexts(original)
        ContextualStores.contexts = store
        val routine = Fixtures.heroRoutine(conditions = listOf(Condition.InContext("desk", 1, "Desk")))
        val digest = Normalizer.digest(routine)
        store.value = original.copy(version = 2, predicates = listOf(Condition.ChargingState(false)))
        assertFalse(digest == Normalizer.digest(routine))
        store.value = original.copy(predicates = listOf(Condition.InContext("other", 1, "Other")))
        assertTrue(Validator.validate(routine).errors.any { it.message.contains("cannot contain another") })
    }

    @Test fun `audio and battery signals remain deterministic`() {
        val audio = Fixtures.heroRoutine(conditions = listOf(Condition.AudioOutputActive(AudioKind.BLUETOOTH)))
        val snapshot = Fixtures.snapshot().copy(audioOutputs = Fixtures.known(setOf(AudioKind.BLUETOOTH), source = ContextSource.AUDIO_MANAGER))
        assertEquals(Truth.MATCH, Evaluator.evaluate(audio, Fixtures.connect(), snapshot, FreshnessPolicy.NONE).truth)
        val low = Fixtures.heroRoutine(conditions = listOf(Condition.BatteryBelow(25)))
        assertEquals(Truth.MATCH, Evaluator.evaluate(low, Fixtures.connect(), Fixtures.snapshot().copy(batteryPercent = Fixtures.known(20, source = ContextSource.BATTERY_MANAGER)), FreshnessPolicy.NONE).truth)
    }

    @Test fun `place movement is semantic and its trigger derives background capability`() {
        ContextualStores.places = Places(Place("gym", "Gym", 1, 12.1, 77.1, 150))
        val trigger = Trigger.PlaceTransition("gym", 1, "Gym", PlaceTransitionKind.ENTER)
        val routine = Fixtures.heroRoutine().copy(trigger = trigger, conditions = emptyList())
        assertTrue(Capability.LOCATION_BACKGROUND in Normalizer.normalize(routine).requiredCapabilities)
        val digest = Normalizer.digest(routine)
        ContextualStores.places = Places(Place("gym", "Gym", 2, 12.2, 77.1, 150))
        assertFalse(digest == Normalizer.digest(routine))
    }

    private class Contexts(var value: NamedContext?) : NamedContextStore {
        override fun findContext(id: String) = value?.takeIf { it.id == id }
        override fun allContexts() = listOfNotNull(value)
        override fun saveContext(context: NamedContext) { value = context }
        override fun deleteContext(id: String) { if (value?.id == id) value = null }
    }
    private class Places(private val place: Place) : PlaceStore {
        override fun findPlace(id: String) = place.takeIf { it.id == id }
        override fun allPlaces() = listOf(place)
        override fun savePlace(place: Place) = Unit
        override fun deletePlace(id: String) = Unit
    }
}
