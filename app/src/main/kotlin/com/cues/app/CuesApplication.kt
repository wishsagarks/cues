package com.cues.app

import android.app.Application
import com.cues.app.drafting.OnDeviceLlmDrafter
import com.cues.app.runtime.AndroidActionExecutor
import com.cues.app.runtime.AndroidCapabilityProvider
import com.cues.core.CueService
import com.cues.core.drafting.CompositeDrafter
import com.cues.core.drafting.GrammarParser
import com.cues.core.drafting.PairedDevice
import com.cues.core.ports.Clock
import com.cues.core.store.JsonFileStore
import java.io.File
import java.time.ZoneId

/**
 * Wiring.
 *
 * Deliberately hand-rolled rather than annotated. The graph is a handful of
 * objects deep and the event has nineteen build hours in it; a
 * dependency-injection framework would cost more in build time and
 * puzzlement than it saves.
 *
 * Everything Android-specific lives here and in `runtime/`. [CueService]
 * itself is the only thing the rest of the app — activities, receivers, the
 * session service — is meant to call.
 */
class CuesApplication : Application() {

    val clock: Clock = Clock { System.currentTimeMillis() }

    /**
     * A `:core`-owned directory under private app storage. Never
     * `getExternalFilesDir` — a cue's approval and a session's cleanup
     * obligations are not data this app is willing to have another app, or
     * the user's file manager, casually touch.
     */
    private val storeRoot: File by lazy { File(filesDir, "cues-store") }

    val store: JsonFileStore by lazy { JsonFileStore(storeRoot) }

    private val executor by lazy { AndroidActionExecutor(this) }
    private val capabilities by lazy { AndroidCapabilityProvider(this) }

    /**
     * Both drafting paths, model first, parser behind it.
     *
     * Whichever answers, the result names the drafter that actually
     * produced it — [CueService.draft] stamps [com.cues.core.model.Routine.draftedBy]
     * from it — so the diagnostics screen shows what really ran, never what
     * was hoped for.
     */
    private val drafter by lazy {
        CompositeDrafter(
            primary = OnDeviceLlmDrafter(),
            fallback = GrammarParser(pairedDevices()),
        )
    }

    val cueService: CueService by lazy {
        CueService(
            routines = store,
            sessions = store,
            receipts = store,
            executor = executor,
            clock = clock,
            capabilities = capabilities,
            drafter = drafter,
            zoneId = { ZoneId.systemDefault() },
        )
    }

    /**
     * Paired devices for entity resolution.
     *
     * WRITTEN AGAINST A REAL API, VERIFIED ON NOTHING — see CLEANUP.md
     * CL-04/CL-06. `BluetoothAdapter.bondedDevices` needs BLUETOOTH_CONNECT
     * at runtime and returns nothing useful before that permission is
     * granted; until then "my earbuds" resolves against nothing, which is
     * the correct behaviour for an unresolved reference anyway — the parser
     * asks which device is meant rather than guessing.
     */
    private fun pairedDevices(): List<PairedDevice> {
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter
            ?: return emptyList()

        val hasPermission = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S ||
            androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.BLUETOOTH_CONNECT,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasPermission) return emptyList()

        return try {
            adapter.bondedDevices.map { device ->
                PairedDevice(
                    id = device.address,
                    label = device.name ?: device.address,
                    // Real alias resolution from free text ("my earbuds", "the buds")
                    // to a specific bonded device is UI work for the device picker
                    // review calls for (RV-01); this gives the parser the device's
                    // own name to match against in the meantime.
                    aliases = setOf((device.name ?: device.address).lowercase()),
                )
            }
        } catch (e: SecurityException) {
            emptyList()
        }
    }
}
