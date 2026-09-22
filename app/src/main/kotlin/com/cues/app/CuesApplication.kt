package com.cues.app

import android.app.Application
import com.cues.app.drafting.OnDeviceLlmDrafter
import com.cues.app.runtime.AndroidActionExecutor
import com.cues.core.drafting.CompositeDrafter
import com.cues.core.drafting.GrammarParser
import com.cues.core.drafting.PairedDevice
import com.cues.core.drafting.RoutineDrafter
import com.cues.core.ports.ActionExecutor
import com.cues.core.ports.Clock

/**
 * Wiring.
 *
 * Deliberately hand-rolled rather than annotated. The graph is four objects
 * deep and the event has nineteen build hours in it; a dependency-injection
 * framework would cost more in build time and puzzlement than it saves.
 */
class CuesApplication : Application() {

    val clock: Clock = Clock { System.currentTimeMillis() }

    val executor: ActionExecutor by lazy { AndroidActionExecutor(this) }

    /**
     * Both drafting paths, with the model first and the parser behind it.
     *
     * Whichever answers, the result names the drafter that produced it, so the
     * diagnostics screen shows what actually ran rather than what was hoped
     * for.
     */
    val drafter: RoutineDrafter by lazy {
        CompositeDrafter(
            primary = OnDeviceLlmDrafter(),
            fallback = GrammarParser(pairedDevices()),
        )
    }

    /**
     * Paired devices for entity resolution.
     *
     * Placeholder. The real list comes from BluetoothAdapter.bondedDevices,
     * which needs BLUETOOTH_CONNECT at runtime. Until then "my earbuds"
     * resolves against nothing and the parser asks which device is meant —
     * which is the correct behaviour for an unresolvable reference anyway.
     */
    private fun pairedDevices(): List<PairedDevice> = emptyList()
}
