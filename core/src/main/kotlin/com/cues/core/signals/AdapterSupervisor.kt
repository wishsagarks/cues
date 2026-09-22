package com.cues.core.signals

import com.cues.core.model.Routine
import com.cues.core.ports.ListenerHealth
import com.cues.core.ports.SignalAdapter

/** Starts only the OS listeners needed by the currently armed routines. */
class AdapterSupervisor(
    private val adapters: Map<String, SignalAdapter>,
) {
    fun sync(armed: List<Routine>) {
        val byKey = armed.mapNotNull { routine ->
            SignalRegistry.adapterKey(routine.trigger)?.let { it to routine }
        }.groupBy({ it.first }, { it.second })
        adapters.forEach { (key, adapter) ->
            val routines = byKey[key].orEmpty()
            if (routines.isEmpty()) adapter.stop() else adapter.start(routines)
        }
    }

    fun health(): List<ListenerHealth> = adapters.values.map { it.health() }
}
