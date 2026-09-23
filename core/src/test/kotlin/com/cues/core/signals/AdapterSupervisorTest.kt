package com.cues.core.signals

import com.cues.core.Fixtures
import com.cues.core.model.Trigger
import com.cues.core.ports.ListenerHealth
import com.cues.core.ports.SignalAdapter
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class AdapterSupervisorTest {
    @Test fun `sync starts only the adapter a routine's key requires, and stops the rest`() {
        val wifi = FakeAdapter("wifi")
        val time = FakeAdapter("time")
        AdapterSupervisor(mapOf("wifi" to wifi, "time" to time)).sync(listOf(
            Fixtures.heroRoutine().copy(trigger = Trigger.WifiConnection(com.cues.core.model.DeviceTransition.CONNECTED)),
        ))
        // The wifi-keyed routine starts wifi and leaves it alone...
        assertEquals(1, wifi.starts)
        assertEquals(0, wifi.stops)
        // ...while the unrelated time adapter is stopped, never started.
        assertEquals(0, time.starts)
        assertEquals(1, time.stops)
    }

    @Test fun `health reflects each adapter's own reported state, not a guess`() {
        val wifi = FakeAdapter("wifi", healthy = true, detail = "listening")
        val time = FakeAdapter("time", healthy = false, detail = "no armed at-time routine")
        val supervisor = AdapterSupervisor(mapOf("wifi" to wifi, "time" to time))
        supervisor.sync(listOf(
            Fixtures.heroRoutine().copy(trigger = Trigger.WifiConnection(com.cues.core.model.DeviceTransition.CONNECTED)),
        ))

        val health = supervisor.health()
        assertEquals(2, health.size)
        assertTrue(health.any { it.key == "wifi" && it.running && it.detail == "listening" })
        assertTrue(health.any { it.key == "time" && !it.running && it.detail == "no armed at-time routine" })
    }

    private class FakeAdapter(
        override val key: String,
        private val healthy: Boolean = true,
        private val detail: String? = null,
    ) : SignalAdapter {
        var starts = 0; var stops = 0
        override fun start(armed: List<com.cues.core.model.Routine>) { starts++ }
        override fun stop() { stops++ }
        override fun health() = ListenerHealth(key, healthy, detail)
    }
}
