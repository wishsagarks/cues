package com.cues.app

import android.app.Application
import android.util.Log
import com.cues.app.drafting.OnDeviceLlmDrafter
import com.cues.app.runtime.AndroidActionExecutor
import com.cues.app.runtime.AndroidCapabilityProvider
import com.cues.app.runtime.AudioOutputAdapter
import com.cues.app.runtime.BluetoothCoverage
import com.cues.app.runtime.DeviceDiagnosticsRepository
import com.cues.app.runtime.MonitoringRepository
import com.cues.app.runtime.TimeAdapter
import com.cues.app.runtime.WifiAdapter
import com.cues.core.CueService
import com.cues.core.drafting.DifferentialDrafter
import com.cues.core.drafting.GrammarParser
import com.cues.core.drafting.PairedDevice
import com.cues.core.ports.Clock
import com.cues.core.signals.AdapterSupervisor
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

    override fun onCreate() {
        super.onCreate()
        // 4.5: rebuild the zen-rule map from live system state before
        // anything might need to release one, then reconcile sessions —
        // expired ones are cleaned up, never restarted. Runs on every
        // process start, not only after BootReceiver's ACTION_BOOT_COMPLETED,
        // because an OEM-killed-and-relaunched process needs the same
        // reconciliation a device reboot does; BootReceiver's own call is
        // then a harmless, idempotent repeat in the same process.
        executor.reconcileZenRules()
        val reconciled = cueService.onBoot()
        Log.i(TAG, "reconciled ${reconciled.size} session(s) at process start")

        // R6 (4.6): the other half of "a disconnect made while the process
        // was dead appears as a coverage gap" — reconcile() above only
        // catches an expired deadline/grace, not a disconnect the process
        // never had the chance to observe at all.
        val gaps = cueService.checkBluetoothCoverage(BluetoothCoverage.currentlyConnectedDeviceIds(this))
        if (gaps.isNotEmpty()) Log.i(TAG, "found ${gaps.size} coverage gap(s) at process start")
        adapterSupervisor.sync(cueService.list().filter { it.status == com.cues.core.model.RoutineStatus.ARMED })
    }

    /** Sprint 4's monitoring-health record: last event per adapter, for Home. */
    val monitoring: MonitoringRepository by lazy { MonitoringRepository(this) }

    val clock: Clock = Clock { System.currentTimeMillis() }

    /**
     * A `:core`-owned directory under private app storage. Never
     * `getExternalFilesDir` — a cue's approval and a session's cleanup
     * obligations are not data this app is willing to have another app, or
     * the user's file manager, casually touch.
     */
    private val storeRoot: File by lazy { File(filesDir, "cues-store") }

    val store: JsonFileStore by lazy { JsonFileStore(storeRoot) }

    /** Sprint 3.0's target-phone results, kept outside the rule store. */
    val deviceDiagnostics: DeviceDiagnosticsRepository by lazy { DeviceDiagnosticsRepository(this) }

    private val executor by lazy { AndroidActionExecutor(this) }
    private val capabilities by lazy { AndroidCapabilityProvider(this) }

    /**
     * Both drafting paths, run and cross-checked rather than raced.
     *
     * Whichever answers, the result names the drafter that actually
     * produced it — [CueService.draft] stamps [com.cues.core.model.Routine.draftedBy]
     * from it — so the diagnostics screen shows what really ran, never what
     * was hoped for. Today the model is an honest stub that fails instantly,
     * so this degrades to exactly the parser's own answer; once 5.0's
     * bake-off says the model earns its slot, this is what starts asking
     * "which is intended?" the moment the two disagree, instead of quietly
     * running only the winner going forward.
     */
    private val drafter by lazy {
        DifferentialDrafter(
            first = OnDeviceLlmDrafter(),
            second = GrammarParser(
                pairedDeviceProvider = ::pairedDevices,
                contextsProvider = { store.allContexts() },
                placesProvider = { store.allPlaces() },
            ),
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
            patches = store,
            contexts = store,
            places = store,
        )
    }

    val timeAdapter by lazy { TimeAdapter(this) }

    val adapterSupervisor by lazy {
        AdapterSupervisor(mapOf(
            "wifi" to WifiAdapter(this),
            "time" to timeAdapter,
            "audio-output" to AudioOutputAdapter(this),
        ))
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
    internal fun pairedDevices(): List<PairedDevice> {
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

    private companion object {
        const val TAG = "CuesSession"
    }
}
