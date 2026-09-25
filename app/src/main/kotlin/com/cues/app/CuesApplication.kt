package com.cues.app

import android.app.Application
import android.util.Log
import com.cues.app.data.ObservableStore
import com.cues.app.data.StoreGeneration
import com.cues.app.devkit.ExternalCallEntry
import com.cues.app.devkit.ExternalCallerLedger
import com.cues.app.devkit.ExternalGemmaGate
import com.cues.app.drafting.GatedLlmSession
import com.cues.app.drafting.LiteRtLmSession
import com.cues.app.drafting.ModelDownloader
import com.cues.app.drafting.SarvamChatDrafter
import com.cues.app.drafting.SarvamHttpChatSession
import com.cues.app.drafting.UnconfiguredSarvamChatSession
import com.cues.app.net.SarvamClient
import com.cues.app.runtime.AndroidActionExecutor
import com.cues.app.runtime.AndroidCapabilityProvider
import com.cues.app.runtime.AndroidDeviceAttention
import com.cues.app.runtime.AudioOutputAdapter
import com.cues.app.runtime.BluetoothCoverage
import com.cues.app.runtime.DeviceDiagnosticsRepository
import com.cues.app.runtime.MonitoringRepository
import com.cues.app.runtime.TimeAdapter
import com.cues.app.runtime.WifiAdapter
import com.cues.core.CueService
import com.cues.core.compile.Validator
import com.cues.core.drafting.ClauseAccounting
import com.cues.core.drafting.DifferentialDrafter
import com.cues.core.drafting.DraftResult
import com.cues.core.drafting.GrammarParser
import com.cues.core.drafting.InferenceOutput
import com.cues.core.drafting.OnDeviceLlmDrafter
import com.cues.core.drafting.PairedDevice
import com.cues.core.model.ActionArgs
import com.cues.core.model.ActionId
import com.cues.core.model.DraftSourceId
import com.cues.core.model.UtilityId
import com.cues.core.model.UtilityState
import com.cues.core.ports.ActionOutcome
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
        // Task 9: app shortcuts ("New cue", "Start ‹cue›") reflect what's
        // armed right now, refreshed on every process start the same way
        // adapterSupervisor.sync above already is.
        com.cues.app.runtime.AppShortcuts.refresh(this, cueService.list())

        // CL-25: ACTION_USER_PRESENT is one of the implicit broadcasts a
        // manifest <receiver> can no longer catch (API 26+); it has to be
        // context-registered, and the application's own lifetime is the
        // natural scope for it — this is the one moment "someone is now at
        // the phone" actually happens, as opposed to ON_RESUME's "the app
        // itself came forward", which MainActivity already re-checks for
        // coverage gaps. Unregistering is deliberately skipped: the process
        // owns this receiver for as long as it's alive, the same way
        // CuesAccessibilityService's lifetime already works.
        // RECEIVER_NOT_EXPORTED: only the system can broadcast ACTION_USER_PRESENT
        // anyway, but targetSdk 34+ requires every dynamically registered
        // receiver to say so explicitly.
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            object : android.content.BroadcastReceiver() {
                override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
                    val resumed = cueService.retryPendingActions()
                    if (resumed.isNotEmpty()) Log.i(TAG, "retried ${resumed.size} pending action(s) on user-present")
                }
            },
            android.content.IntentFilter(android.content.Intent.ACTION_USER_PRESENT),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
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

    /**
     * Redesign (§5.1/§10.1): every screen's ViewModel reads through [store]
     * and watches [storeGeneration] to know when to re-read. The generation
     * is a hint for *when* to re-read, never a cache of *what* changed — see
     * ObservableStore.kt for why a StateFlow, not a one-shot event, is what
     * survives a process death between a background write and the next time
     * the UI opens.
     */
    val storeGeneration: StoreGeneration by lazy { StoreGeneration() }

    val store: ObservableStore by lazy { ObservableStore(JsonFileStore(storeRoot), storeGeneration) }

    /** Sprint 3.0's target-phone results, kept outside the rule store. */
    val deviceDiagnostics: DeviceDiagnosticsRepository by lazy { DeviceDiagnosticsRepository(this) }

    private val executor by lazy { AndroidActionExecutor(this, macros = store, utilityBindings = store) }

    /**
     * Runs [com.cues.core.model.ActionId.USE_UTILITY] directly, outside any
     * routine or session — the Utility Bindings screen's "Test on"/"Test
     * off" buttons, so teaching a binding can be proven before it is ever
     * attached to an approved cue. Blocks the calling thread (macro replay
     * polls with bounded waits); callers must not run it on the main thread.
     */
    fun testUseUtility(utilityId: UtilityId, state: UtilityState): ActionOutcome =
        executor.execute(ActionId.USE_UTILITY, ActionArgs.UseUtility(utilityId, state), "utility-test")
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
    /**
     * Where the on-device model is expected, under private app storage —
     * never `getExternalFilesDir`. Two ways this can arrive, both writing
     * exactly this path: a manual side-load (adb cannot write here directly —
     * push to /data/local/tmp, then copy in with `run-as`, docs/DEVICE_MATRIX.md
     * M2), or the "Cues Brain" tile's [modelDownloader] (CL-36). Its absence
     * is the ordinary, honest case either way: every build without one falls
     * all the way back to the parser.
     */
    private val modelFile: File by lazy { File(filesDir, "models/model.litertlm") }

    /**
     * CL-36: the download side of the "Cues Brain" tile. `canDownload()` is
     * false — the tile honestly shows "not configured" — until both
     * `BuildConfig.GEMMA_MODEL_URL` and `_SHA256` are set from a real,
     * verified source (docs/API_VERIFICATION.md's "Gemma model distribution"
     * entry), never a guessed value. A model already present from a manual
     * `adb push`/`run-as` side-load (docs/DEVICE_MATRIX.md M2) works exactly
     * the same either way — both routes write the same file.
     */
    val modelDownloader by lazy {
        ModelDownloader(this, modelFile, BuildConfig.GEMMA_MODEL_URL, BuildConfig.GEMMA_MODEL_SHA256)
    }

    /**
     * CL-36's second gate level, mirroring [cloudAssistAvailable]/
     * `cloudAssistEnabled` below: a model being installed does not, by
     * itself, mean it's used. Starts off every launch, same as cloud assist.
     * Reachable from anywhere `drafter` is (Home, Ask, anywhere `CueService`
     * drafts) — not scoped to one screen's Compose state, since drafting
     * itself is not scoped to one screen.
     */
    var onDeviceModelUserEnabled: Boolean = false

    /**
     * The one gated on-device model session, shared by [drafter] (fresh
     * drafts) and [refinePhraser] (C1: edit phrasing) — one model, one file,
     * one gate ([onDeviceModelUserEnabled]), asked two narrower questions
     * rather than duplicated per use.
     */
    private val onDeviceLlmSession by lazy {
        GatedLlmSession(LiteRtLmSession(this, modelFile.path)) { onDeviceModelUserEnabled }
    }

    private val drafter by lazy {
        DifferentialDrafter(
            first = OnDeviceLlmDrafter(session = onDeviceLlmSession),
            second = GrammarParser(
                pairedDeviceProvider = ::pairedDevices,
                contextsProvider = { store.allContexts() },
                placesProvider = { store.allPlaces() },
            ),
        )
    }

    /** C1: model-assisted fallback for edit phrasing the deterministic router doesn't match. */
    private val refinePhraser by lazy {
        com.cues.core.assistant.OnDeviceRefinePhraser(session = onDeviceLlmSession)
    }

    // -------------------------------------------------- developer surface (E)

    /**
     * Deliberately a separate file from [modelFile]: swapping a BYOM model in
     * for the developer-facing surface ([CuesGemmaProvider]) can never
     * silently replace the trusted authoring model, and vice versa. No
     * default model ships for this path — unlike [modelFile]'s parser
     * fallback, an absent file here is an honest, disclosed failure (see
     * [generateForExternalCaller]), never a silent substitution.
     */
    private val externalModelFile: File by lazy { File(filesDir, "models/external/model.litertlm") }

    /**
     * Blank source URL/SHA on purpose: only [com.cues.app.drafting.ModelDownloader.installFromUri]'s
     * file-picker path is ever offered for this file — "bring your own
     * model," not "download the verified one" — `canDownload()` naturally
     * returns false.
     */
    val externalModelDownloader by lazy { ModelDownloader(this, externalModelFile, "", "") }

    /** CL-38's third opt-in gate: a model being installed does not mean another app on the phone may reach it. Off by default every launch. */
    val externalGemmaGate by lazy { ExternalGemmaGate() }

    /** Per-caller token/cost bookkeeping for [externalGemmaGate]'s surface — see [ExternalCallerLedger]'s own doc comment for why this is a sibling to, not a reuse of, [com.cues.core.inference.InferenceLedger]. */
    val externalCallerLedger by lazy { ExternalCallerLedger() }

    /**
     * Wholly separate from [drafter]/[onDeviceLlmSession]: never handed to
     * [OnDeviceLlmDrafter] or [DifferentialDrafter], so nothing an external
     * caller sends can ever reach cue authoring or `:core`'s trust boundary.
     */
    private val externalGemmaSession by lazy {
        GatedLlmSession(LiteRtLmSession(this, externalModelFile.path)) { externalGemmaGate.enabled }
    }

    private var cachedExternalModelIdentity: Pair<Long, String>? = null

    /**
     * File size + a truncated SHA-256 prefix of whichever model currently
     * backs [externalGemmaSession] — how a caller in [CuesGemmaProvider]
     * knows whether a Google-provisioned or a tester's own side-loaded model
     * answered. Hashed once per file (keyed on `lastModified()`), not on
     * every call.
     */
    fun externalModelIdentity(): String {
        if (!externalModelFile.isFile) return "no BYOM model installed"
        val mtime = externalModelFile.lastModified()
        cachedExternalModelIdentity?.let { (cachedMtime, identity) -> if (cachedMtime == mtime) return identity }
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        externalModelFile.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        val hashPrefix = digest.digest().joinToString("") { "%02x".format(it) }.take(12)
        val identity = "${externalModelFile.length() / 1_000_000}MB-$hashPrefix"
        cachedExternalModelIdentity = mtime to identity
        return identity
    }

    /**
     * The one entry point [CuesGemmaProvider] calls. Reuses [externalGemmaSession]'s
     * existing gate/file checks rather than re-implementing them — a denial
     * here always carries the same honest message [GatedLlmSession]/
     * [LiteRtLmSession] already produce, logged to [externalGemmaGate] and,
     * on success, recorded to [externalCallerLedger].
     */
    suspend fun generateForExternalCaller(prompt: String, callerPackage: String): Result<InferenceOutput> = try {
        val output = externalGemmaSession.generate(prompt)
        externalGemmaGate.logAllowed(callerPackage, clock.nowMillis())
        externalCallerLedger.record(
            ExternalCallEntry(
                atMillis = clock.nowMillis(),
                callerPackage = callerPackage,
                backend = output.report.backend,
                estimatedTokens = output.report.estimatedTokens,
                latencyMs = output.report.loadMs + output.report.generationMs,
            ),
        )
        Result.success(output)
    } catch (e: Exception) {
        externalGemmaGate.logDenied(callerPackage, clock.nowMillis(), e.message ?: "generation failed")
        Result.failure(e)
    }

    /**
     * CL-35: opt-in cloud assist. `null` whenever no key is configured — the
     * honest default, same shape as [modelFile]'s absence for the on-device
     * model. Nothing here is called unless a caller (the Ask flow's cloud
     * voice entry point, or [tryCloudAssist] below) explicitly reaches for
     * it; process start never touches the network.
     */
    private val sarvamClient: SarvamClient? by lazy {
        com.cues.app.BuildConfig.SARVAM_API_KEY.takeIf { it.isNotBlank() }?.let(::SarvamClient)
    }

    val cloudAssistAvailable: Boolean get() = sarvamClient != null

    private val sarvamChatDrafter by lazy {
        SarvamChatDrafter(session = sarvamClient?.let(::SarvamHttpChatSession) ?: UnconfiguredSarvamChatSession())
    }

    /**
     * The explicit, opt-in third opinion (Architecture decision 2 in
     * docs/FDD.md's "Optional cloud assist" section): called only from the
     * app layer, only after [drafter] (the offline pair) has already
     * disagreed or both failed, and only if the caller has cloud assist
     * turned on. Never wired into [drafter] itself — [DifferentialDrafter] is
     * a strict pairwise comparison and stays that way.
     *
     * Guards its result exactly like [DifferentialDrafter.guarded] guards the
     * on-device model: Sarvam's prose does not get to vouch for its own
     * structure, so a cloud draft that fails independent validation is
     * reported as failed, not shown.
     */
    suspend fun tryCloudAssist(text: String): DraftResult {
        val result = when (val r = sarvamChatDrafter.draft(text)) {
            is DraftResult.Drafted -> ClauseAccounting.stamp(text, r)
            else -> r
        }
        val unvalidated = result is DraftResult.Drafted && !Validator.validate(result.routine).isValid
        return if (unvalidated) {
            DraftResult.Failed(DraftSourceId.SARVAM_CLOUD, "Cloud assist produced a cue that did not pass independent validation.")
        } else {
            result
        }
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
            facts = store,
            usageLedger = store,
            attention = AndroidDeviceAttention(this),
            // Redesign: structured receipts alongside the text ones, so
            // Insights and the Receipts tab can filter/count by reason code
            // instead of parsing prose. Optional, like usageLedger — nothing
            // that decides behaviour reads from it.
            receiptLog = store,
            inferenceLedger = store,
            refinePhraser = refinePhraser,
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
