package com.cues.core.signals

import com.cues.core.Fixtures
import com.cues.core.model.Trigger
import com.cues.core.ports.ListenerHealth
import com.cues.core.ports.SignalAdapter
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class AdapterSupervisorTest {
    @Test fun `sync starts only required adapters and exposes their health`() {
        val wifi = FakeAdapter("wifi")
        val time = FakeAdapter("time")
        AdapterSupervisor(mapOf("wifi" to wifi, "time" to time)).sync(listOf(
            Fixtures.heroRoutine().copy(trigger = Trigger.WifiConnection(com.cues.core.model.DeviceTransition.CONNECTED)),
        ))
        assertEquals(1, wifi.starts)
        assertEquals(1, time.stops)
    }
    private class FakeAdapter(override val key: String) : SignalAdapter {
        var starts = 0; var stops = 0
        override fun start(armed: List<com.cues.core.model.Routine>) { starts++ }
        override fun stop() { stops++ }
        override fun health() = ListenerHealth(key, starts > stops)
    }
}
