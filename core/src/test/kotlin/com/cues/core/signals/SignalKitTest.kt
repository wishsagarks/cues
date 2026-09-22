package com.cues.core.signals

import com.cues.core.Fixtures
import com.cues.core.compile.Normalizer
import com.cues.core.compile.Validator
import com.cues.core.eval.Evaluator
import com.cues.core.eval.FreshnessPolicy
import com.cues.core.eval.ReasonCode
import com.cues.core.eval.Truth
import com.cues.core.model.*
import com.cues.core.ports.ListenerHealth
import com.cues.core.ports.SignalAdapter
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class SignalKitTest {

    @Test
    fun `registry covers every closed signal subtype`() {
        assertEquals(5, SignalRegistry.triggerKits.size)
        assertEquals(5, SignalRegistry.conditionKits.size)
        assertEquals(4, SignalRegistry.endKits.size)
    }

    @Test
    fun `existing hero digest stays byte-for-byte stable`() {
        assertEquals(
            "e02d8a4acaf953badac9a8c849fe8e2133ae59ece8e8e728136d7d8ea76b9a9a",
            Normalizer.digest(Fixtures.heroRoutine()),
        )
    }

    @Test
    fun `any wifi trigger matches and unrelated events do not`() {
        val routine = Fixtures.heroRoutine().copy(
            trigger = Trigger.WifiConnection(DeviceTransition.CONNECTED),
            conditions = emptyList(),
            endConditions = listOf(EndCondition.ManualStop),
        )
        val context = Fixtures.snapshot().copy(
            wifi = Fixtures.known(WifiState(true), source = ContextSource.WIFI_MANAGER),
        )

        val match = Evaluator.evaluate(
            routine,
            TriggerEvent(EventKind.WIFI_CONNECTED, Fixtures.NOW),
            context,
            FreshnessPolicy.NONE,
        )
        val noMatch = Evaluator.evaluate(
            routine,
            TriggerEvent(EventKind.WIFI_DISCONNECTED, Fixtures.NOW),
            context,
            FreshnessPolicy.NONE,
        )

        assertEquals(Truth.MATCH, match.truth)
        assertEquals(ReasonCode.WIFI_TRIGGER_MATCHED, match.reasons.single().code)
        assertEquals(Truth.NO_MATCH, noMatch.truth)
    }

    @Test
    fun `wifi unknown remains unknown and named wifi is not armable`() {
        val condition = Condition.WifiConnected(WifiNetwork.Any)
        val unknown = Evaluator.evaluate(
            Fixtures.heroRoutine().copy(conditions = listOf(condition)),
            Fixtures.connect(),
            Fixtures.snapshot().copy(wifi = Fixtures.unknown(UnknownReason.REDACTED_BY_OS, ContextSource.WIFI_MANAGER)),
            FreshnessPolicy.NONE,
        )
        assertEquals(Truth.UNKNOWN, unknown.truth)
        assertEquals(ReasonCode.WIFI_UNKNOWN, unknown.reasons.last().code)

        val named = Fixtures.heroRoutine().copy(
            trigger = Trigger.WifiConnection(DeviceTransition.CONNECTED, WifiNetwork.Named("home")),
        )
        assertTrue(Validator.validate(named).errors.any { it.message.contains("needs location access") })
    }

    @Test
    fun `at-time end schedules the next occurrence in the approved zone`() {
        val session = Session(
            id = "s",
            routineId = "r",
            routineVersion = 1,
            startedAtMillis = Fixtures.NOW,
            admissionKey = "k",
            observedInputs = Fixtures.snapshot(),
            state = SessionState.ACTIVE,
        )
        val end = EndCondition.AtTime(LocalTimeOfDay(7, 0), zoneId = "Asia/Kolkata")
        assertTrue(requireNotNull(SignalRegistry.schedule(end, session)) > Fixtures.NOW)
    }

    @Test
    fun `supervisor starts only adapters required by armed triggers`() {
        val bluetooth = RecordingAdapter("bluetooth")
        val wifi = RecordingAdapter("wifi")
        val supervisor = AdapterSupervisor(mapOf("bluetooth" to bluetooth, "wifi" to wifi))
        supervisor.sync(listOf(Fixtures.heroRoutine()))

        assertTrue(bluetooth.running)
        assertTrue(!wifi.running)
    }

    private class RecordingAdapter(override val key: String) : SignalAdapter {
        var running = false
        override fun start(armed: List<Routine>) { running = true }
        override fun stop() { running = false }
        override fun health() = ListenerHealth(key, running)
    }
}
