package com.cues.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import com.cues.app.drafting.LocalSpeechInput
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cues.app.data.ObservableStore
import com.cues.app.runtime.BluetoothCoverage
import com.cues.app.runtime.GraceScheduler
import com.cues.app.runtime.MonitoringRepository
import com.cues.app.runtime.LiveSnapshot
import com.cues.app.ui.AvailableSignal
import com.cues.app.ui.ContextsScreen
import com.cues.app.ui.DiagnosticsScreen
import com.cues.app.ui.MemoryScreen
import com.cues.app.ui.LearningSettings
import com.cues.app.ui.ReceiptScreen
import com.cues.app.ui.RoutineDetailScreen
import com.cues.app.ui.review.ReviewScreenV2
import com.cues.app.ui.ask.AskScreen
import com.cues.app.ui.components.CuesBottomNav
import com.cues.app.ui.components.CuesTopBar
import com.cues.app.ui.insights.InsightsScreen
import com.cues.app.ui.nav.CuesRoutes
import com.cues.app.ui.nav.tabTitle
import com.cues.app.runtime.DeviceIdentity
import com.cues.app.ui.now.NowScreen
import com.cues.app.ui.theme.CuesTheme
import com.cues.app.ui.theme.cuesTokens
import com.cues.app.ui.workbench.WorkbenchScreen
import com.cues.core.coach.CoachPolicy
import com.cues.core.coach.Detectors
import com.cues.core.coach.Suggestion
import com.cues.core.CueService
import com.cues.core.approval.ArmResult
import com.cues.core.approval.DeleteResult
import com.cues.core.drafting.PairedDevice
import com.cues.core.model.DraftSourceId
import com.cues.core.model.AudioKind
import com.cues.core.model.Capability
import com.cues.core.model.Condition
import com.cues.core.model.ContextValue
import com.cues.core.model.NamedContext
import com.cues.core.model.Place
import com.cues.core.model.Routine
import com.cues.core.model.RoutineStatus
import com.cues.core.model.Session
import com.cues.core.model.WifiNetwork
import com.cues.core.rehearsal.Rehearsal
import com.cues.core.review.forecastToday
import com.cues.core.session.isLive
import com.cues.core.assistant.Conversation
import com.cues.core.assistant.ReplyCode
import com.cues.core.assistant.Turn
import com.cues.app.runtime.DeviceDiagnosticsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.util.UUID

/**
 * Single-activity host for the 5-tab shell (redesign plan §2). navigation-
 * compose owns the back stack; the sealed-`Screen`-plus-`when` router this
 * replaced is gone. [CueService] is still the only thing any screen calls
 * into for a decision or a side effect — everything below is rendering,
 * navigation and event wiring only.
 */
class MainActivity : ComponentActivity() {

    private val allPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* grants recorded by the OS; app re-reads them on next resume */ }

    private fun requestAllPermissions() {
        val needed = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.READ_CALENDAR)
            add(Manifest.permission.BLUETOOTH_CONNECT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) allPermissionsLauncher.launch(needed.toTypedArray())
    }

    private lateinit var localSpeechInput: LocalSpeechInput
    private lateinit var replySpeaker: com.cues.app.voice.ReplySpeaker

    /** `null` whenever cloud assist has no configured API key — see CuesApplication.cloudAssistAvailable, CLEANUP.md CL-35. */
    private var sarvamClient: com.cues.app.net.SarvamClient? = null
    private var sarvamSpeechInput: com.cues.app.voice.SarvamSpeechInput? = null
    private var sarvamReadback: com.cues.app.voice.SarvamReadback? = null

    private var incomingScreenText by androidx.compose.runtime.mutableStateOf<String?>(null)
    private var newCueShortcutTick by androidx.compose.runtime.mutableStateOf(0)
    private var startRoutineShortcutId by androidx.compose.runtime.mutableStateOf<String?>(null)

    /** Untrusted text from outside Cues — a share sheet or a screen read. Never executed, only ever shown as an editable draft. */
    private fun sharedOrCapturedText(intent: android.content.Intent?): String? {
        intent ?: return null
        intent.getStringExtra(com.cues.app.runtime.CuesAccessibilityService.EXTRA_SCREEN_CAPTURE)?.let { return it }
        if (intent.action == android.content.Intent.ACTION_SEND && intent.type == "text/plain") {
            return intent.getStringExtra(android.content.Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
        }
        return null
    }

    private fun applyShortcutIntent(intent: android.content.Intent?) {
        intent ?: return
        if (intent.getBooleanExtra(com.cues.app.runtime.AppShortcuts.EXTRA_NEW_CUE, false)) {
            newCueShortcutTick++
        }
        intent.getStringExtra(com.cues.app.runtime.AppShortcuts.EXTRA_START_ROUTINE_ID)?.let {
            startRoutineShortcutId = it
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // API 35+ enforces edge-to-edge regardless; calling this explicitly
        // (rather than leaving it implicit) is what lets Theme.Cues's
        // windowLightStatusBar/windowLightNavigationBar actually control
        // system-bar icon contrast, and keeps behaviour consistent on 29-34
        // too. Scaffold's default window-insets handling below is what
        // keeps content off the status/nav bars once this is on.
        enableEdgeToEdge()
        val app = application as CuesApplication
        localSpeechInput = LocalSpeechInput(this)
        replySpeaker = com.cues.app.voice.ReplySpeaker(this)
        if (app.cloudAssistAvailable) {
            val client = com.cues.app.net.SarvamClient(BuildConfig.SARVAM_API_KEY)
            sarvamClient = client
            sarvamSpeechInput = com.cues.app.voice.SarvamSpeechInput(this, client)
            sarvamReadback = com.cues.app.voice.SarvamReadback(this, client)
        }
        incomingScreenText = sharedOrCapturedText(intent)
        applyShortcutIntent(intent)
        requestAllPermissions()

        setContent {
            CuesTheme {
                CuesApp(
                    cueService = app.cueService,
                    store = app.store,
                    storeGeneration = app.storeGeneration,
                    deviceDiagnostics = app.deviceDiagnostics,
                    monitoring = app.monitoring,
                    adapterHealth = { app.adapterSupervisor.health() },
                    syncAdapters = { app.adapterSupervisor.sync(app.cueService.list().filter { it.status == RoutineStatus.ARMED }) },
                    runBakeOff = { com.cues.app.drafting.AppBakeOff.run(app.pairedDevices(), app.onDeviceLlmSession, com.cues.app.BuildConfig.SARVAM_API_KEY) },
                    modelDownloader = app.modelDownloader,
                    onDeviceModelUserEnabled = app.onDeviceModelUserEnabled,
                    onToggleOnDeviceModel = { app.onDeviceModelUserEnabled = it },
                    probeNpu = app::probeNpu,
                    externalModelDownloader = app.externalModelDownloader,
                    externalGemmaGate = app.externalGemmaGate,
                    approveExternalCaller = app::approveExternalCaller,
                    externalModelIdentity = app::externalModelIdentity,
                    localSpeechInput = localSpeechInput,
                    pairedDevices = app::pairedDevices,
                    replySpeaker = replySpeaker,
                    cloudAssistAvailable = app.cloudAssistAvailable,
                    tryCloudAssist = app::tryCloudAssist,
                    sarvamSpeechInput = sarvamSpeechInput,
                    sarvamReadback = sarvamReadback,
                    translateToEnglish = { text -> sarvamClient?.translate(text, sourceLanguageCode = "auto", targetLanguageCode = "en-IN")?.translatedText ?: text },
                    testUtilityAction = app::testUseUtility,
                    incomingScreenText = incomingScreenText,
                    newCueShortcutTick = newCueShortcutTick,
                    startRoutineShortcutId = startRoutineShortcutId,
                    onStartRoutineShortcutConsumed = { startRoutineShortcutId = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedOrCapturedText(intent)?.let { incomingScreenText = it }
        applyShortcutIntent(intent)
    }

    override fun onDestroy() {
        if (::localSpeechInput.isInitialized) localSpeechInput.stop()
        if (::replySpeaker.isInitialized) replySpeaker.shutdown()
        super.onDestroy()
    }
}

/**
 * What a named context can be built from: signals actually readable right
 * now, never a signal Cues merely hopes is true.
 */
private fun availableSignalsNow(context: android.content.Context): List<AvailableSignal> {
    val snapshot = LiveSnapshot.current(context)
    return buildList {
        (snapshot.charging as? ContextValue.Known)?.let { known ->
            add(AvailableSignal("Phone is ${if (known.value) "charging" else "not charging"} right now", Condition.ChargingState(known.value)))
        }
        (snapshot.wifi as? ContextValue.Known)?.let { known ->
            if (known.value.connected) add(AvailableSignal("Wi-Fi is connected right now", Condition.WifiConnected(WifiNetwork.Any)))
        }
        (snapshot.connectedDeviceIds as? ContextValue.Known)?.let { known ->
            known.value.forEach { deviceId ->
                add(AvailableSignal("$deviceId is connected right now", Condition.DeviceConnected(deviceId)))
            }
        }
        (snapshot.audioOutputs as? ContextValue.Known)?.let { known ->
            known.value.filter { it != AudioKind.ANY }.forEach { kind ->
                add(AvailableSignal("${kind.name.lowercase().replaceFirstChar(Char::uppercase)} audio output is active", Condition.AudioOutputActive(kind)))
            }
        }
    }
}

/**
 * The one truthful drafter-status label, replacing the pre-Sprint-8 version
 * of this function that read `drafter.id` directly — always "ON-DEVICE
 * MODEL" the moment a [com.cues.core.drafting.DifferentialDrafter] was
 * wired in, whether or not a model was installed or turned on
 * (CLEANUP.md CL-18). [com.cues.core.CueService.DrafterSetup.model] is the
 * live read this label is honest about instead.
 */
private fun com.cues.core.CueService.DrafterSetup.friendlyLabel(): String = when (model) {
    com.cues.core.ports.ModelAvailability.READY -> "GEMMA ON-DEVICE"
    com.cues.core.ports.ModelAvailability.INSTALLED_OFF -> "GRAMMAR ONLY (MODEL OFF)"
    com.cues.core.ports.ModelAvailability.NOT_INSTALLED -> "GRAMMAR ONLY"
}

@Composable
private fun CuesApp(
    cueService: CueService,
    store: ObservableStore,
    storeGeneration: com.cues.app.data.StoreGeneration,
    deviceDiagnostics: DeviceDiagnosticsRepository,
    monitoring: MonitoringRepository,
    adapterHealth: () -> List<com.cues.core.ports.ListenerHealth>,
    syncAdapters: () -> Unit,
    runBakeOff: suspend () -> com.cues.core.corpus.BakeOffReport,
    modelDownloader: com.cues.app.drafting.ModelDownloader,
    onDeviceModelUserEnabled: Boolean = false,
    onToggleOnDeviceModel: (Boolean) -> Unit = {},
    probeNpu: (suspend (String) -> com.cues.core.drafting.InferenceOutput)? = null,
    externalModelDownloader: com.cues.app.drafting.ModelDownloader,
    externalGemmaGate: com.cues.app.devkit.ExternalGemmaGate,
    approveExternalCaller: (String) -> Unit,
    externalModelIdentity: () -> String,
    localSpeechInput: LocalSpeechInput,
    pairedDevices: () -> List<PairedDevice>,
    replySpeaker: com.cues.app.voice.ReplySpeaker,
    cloudAssistAvailable: Boolean = false,
    tryCloudAssist: suspend (String) -> com.cues.core.drafting.DraftResult = { com.cues.core.drafting.DraftResult.Failed(DraftSourceId.SARVAM_CLOUD, "Cloud assist is not configured.") },
    sarvamSpeechInput: com.cues.app.voice.SarvamSpeechInput? = null,
    sarvamReadback: com.cues.app.voice.SarvamReadback? = null,
    translateToEnglish: suspend (String) -> String = { it },
    testUtilityAction: (com.cues.core.model.UtilityId, com.cues.core.model.UtilityState) -> com.cues.core.ports.ActionOutcome,
    incomingScreenText: String? = null,
    newCueShortcutTick: Int = 0,
    startRoutineShortcutId: String? = null,
    onStartRoutineShortcutConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()

    var routines by remember { mutableStateOf(cueService.list()) }
    var liveSessions by remember { mutableStateOf(store.allUnfinished().filter { it.state.isLive() }) }
    var isDrafting by remember { mutableStateOf(false) }
    var missingCapabilities by remember { mutableStateOf<Set<Capability>>(emptySet()) }
    var diagnostics by remember { mutableStateOf(deviceDiagnostics.latest()) }
    var isBakingOff by remember { mutableStateOf(false) }
    var bakeOffReport by remember { mutableStateOf<String?>(null) }
    var isProbingNpu by remember { mutableStateOf(false) }
    var npuProbeResult by remember { mutableStateOf<String?>(null) }
    // CL-36: the "Cues Brain" download tile's state. Re-read from disk on
    // first composition — this covers both a manual adb-push side-load
    // (docs/DEVICE_MATRIX.md M2) and a download this same process already
    // completed, without either route needing to know about the other.
    var modelProvisionState by remember { mutableStateOf(modelDownloader.currentState()) }
    var modelDownloadJob by remember { mutableStateOf<Job?>(null) }
    var onDeviceModelEnabled by remember { mutableStateOf(onDeviceModelUserEnabled) }
    // CL-38: the developer-facing surface's own switch — off by default every
    // launch, independent of onDeviceModelEnabled above (that one gates cue
    // authoring; this one gates other apps on the phone calling in).
    var externalGemmaEnabled by remember { mutableStateOf(externalGemmaGate.enabled) }
    var isDiagnosticsRefreshing by remember { mutableStateOf(false) }
    var deviceCandidates by remember { mutableStateOf<List<PairedDevice>?>(null) }
    var deviceSourceText by remember { mutableStateOf<String?>(null) }
    var appQuery by remember { mutableStateOf<String?>(null) }
    var reviewBackText by remember { mutableStateOf<String?>(null) }
    var appCandidates by remember { mutableStateOf<List<com.cues.app.runtime.InstalledApp>?>(null) }
    var appSourceText by remember { mutableStateOf<String?>(null) }
    var adapterStatuses by remember { mutableStateOf(monitoring.statuses(adapterHealth())) }
    val conversation = remember { Conversation() }
    var assistantTurns by remember { mutableStateOf<List<Turn>>(emptyList()) }
    var speakReplies by remember { mutableStateOf(false) }
    // CL-35: off by default even when a key is configured — cloud assist is
    // an explicit, per-session opt-in, not a standing preference read from
    // storage. Mirrors speakReplies' own in-memory-only pattern above.
    var cloudAssistEnabled by remember { mutableStateOf(false) }
    var isTryingCloudAssist by remember { mutableStateOf(false) }
    val cloudAssistOn = cloudAssistEnabled && cloudAssistAvailable
    var utilityTestResult by remember { mutableStateOf<String?>(null) }
    val coachPolicy = remember { CoachPolicy(store) }
    var coachSuggestion by remember {
        mutableStateOf<Suggestion?>(coachPolicy.next(Detectors.all(store.ledgerEvents(), System.currentTimeMillis()), System.currentTimeMillis()))
    }
    // Non-string payloads that don't fit a nav route argument cleanly — held
    // here and read once by the pushed destination, exactly as the redesign
    // plan's route comments describe.
    var reviewDraft by remember { mutableStateOf<Routine?>(null) }
    // Kept alongside reviewDraft, not folded into it, the same
    // "snapshot once" reasoning that comment further down already applies
    // to reviewDraft itself: this is Turn.trace/DraftResult.trace for
    // whichever draft is about to be reviewed, so ReviewScreenV2's header
    // can say "confirmed by on-device model" rather than only "grammar
    // parser". Set at every call site that sets reviewDraft; null wherever
    // the draft has no trace to show (an import, a coach edit).
    var reviewDraftTrace by remember { mutableStateOf<com.cues.core.drafting.DraftTrace?>(null) }
    var importResult by remember { mutableStateOf<com.cues.core.imports.TimetableExtractionResult?>(null) }
    var cueCardShareRoutine by remember { mutableStateOf<Routine?>(null) }

    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            modelProvisionState = com.cues.core.inference.ModelProvisionState.Verifying
            scope.launch {
                modelProvisionState = modelDownloader.installFromUri(uri)
                if (modelProvisionState is com.cues.core.inference.ModelProvisionState.Installed) {
                    onDeviceModelEnabled = true
                    onToggleOnDeviceModel(true)
                    snackbarHost.showSnackbar("Gemma installed and enabled for drafting")
                } else if (modelProvisionState is com.cues.core.inference.ModelProvisionState.Failed) {
                    snackbarHost.showSnackbar((modelProvisionState as com.cues.core.inference.ModelProvisionState.Failed).reason)
                }
            }
        }
    }
    // CL-38's own "bring your own model" install path — a separate file and
    // a separate picker launch from modelPicker above, on purpose: this one
    // never touches onDeviceModelEnabled/modelProvisionState.
    val externalModelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                when (val result = externalModelDownloader.installFromUri(uri)) {
                    is com.cues.core.inference.ModelProvisionState.Installed ->
                        snackbarHost.showSnackbar("Developer-surface model installed")
                    is com.cues.core.inference.ModelProvisionState.Failed ->
                        snackbarHost.showSnackbar(result.reason)
                    else -> {}
                }
            }
        }
    }

    fun refresh() {
        routines = cueService.list()
        liveSessions = store.allUnfinished().filter { it.state.isLive() }
        syncAdapters()
        adapterStatuses = monitoring.statuses(adapterHealth())
    }

    // §10.1: disk is the truth. Every re-entry to the foreground re-reads
    // from the store rather than trusting anything cached across a stop —
    // this is what makes a background session start (seen only by
    // SessionService, possibly in a process the UI wasn't in) show up
    // correctly the next time the user actually looks at the screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                cueService.checkBluetoothCoverage(BluetoothCoverage.currentlyConnectedDeviceIds(context))
                cueService.retryPendingActions()
                refresh()
                com.cues.app.runtime.AppShortcuts.refresh(context, routines)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // A store write anywhere (this UI, or a background receiver/service in
    // this same process) bumps the generation; re-read on the next one. A
    // StateFlow, not a one-shot event — see StoreGeneration's doc comment
    // for why that's what survives a process death between a background
    // write and the next time this composition starts.
    val generation by storeGeneration.value.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(generation) { if (generation > 0) refresh() }

    fun notify(message: String) {
        scope.launch { snackbarHost.showSnackbar(message) }
    }

    androidx.compose.runtime.LaunchedEffect(incomingScreenText) {
        if (incomingScreenText != null) navController.navigateToTab(CuesRoutes.ASK)
    }
    androidx.compose.runtime.LaunchedEffect(newCueShortcutTick) {
        if (newCueShortcutTick > 0) navController.navigateToTab(CuesRoutes.ASK)
    }
    androidx.compose.runtime.LaunchedEffect(startRoutineShortcutId) {
        val routineId = startRoutineShortcutId ?: return@LaunchedEffect
        onStartRoutineShortcutConsumed()
        val routine = cueService.list().firstOrNull { it.id == routineId }
        when {
            routine == null -> notify("That cue no longer exists.")
            routine.trigger !is com.cues.core.model.Trigger.Manual -> notify("\"${routine.title}\" doesn't run by hand.")
            routine.status != RoutineStatus.ARMED -> notify("\"${routine.title}\" is ${routine.status.name.lowercase()}, not armed.")
            else -> {
                val results = cueService.onDeviceEvent(
                    com.cues.core.model.TriggerEvent(com.cues.core.model.EventKind.MANUAL_RUN, System.currentTimeMillis(), routineId = routine.id),
                )
                val started = results.filterIsInstance<com.cues.core.session.EngineResult.Started>().any()
                notify(if (started) "Started \"${routine.title}\"." else "\"${routine.title}\" did not start — its conditions weren't met.")
                refresh()
            }
        }
    }

    /**
     * The regional-language mic button's data flow (docs/FDD.md's "Optional
     * cloud assist" section, architecture decision 3): record → Sarvam STT →
     * Sarvam translate to en-IN → hand the English string to the existing,
     * untouched draft pipeline exactly as typed English input already is.
     * Both strings reach [onResult] so the caller can show/correct either
     * before submitting — [LocalSpeechInput]'s default mic button is
     * completely untouched by this.
     */
    fun startCloudVoice(onResult: (original: String, translated: String) -> Unit, onError: (String) -> Unit) {
        val input = sarvamSpeechInput
        if (input == null) {
            onError("Cloud assist is not available.")
            return
        }
        scope.launch {
            when (val result = input.listen()) {
                is com.cues.core.ports.SpeechResult.Recognized -> {
                    val translated = try {
                        translateToEnglish(result.transcript)
                    } catch (e: Exception) {
                        result.transcript
                    }
                    onResult(result.transcript, translated)
                }
                is com.cues.core.ports.SpeechResult.PermissionDenied ->
                    onError("Microphone access was not granted. Type your cue instead.")
                is com.cues.core.ports.SpeechResult.NoMatch ->
                    onError("No speech was recognised. You can correct or type your cue instead.")
                is com.cues.core.ports.SpeechResult.Unavailable ->
                    onError("Cloud speech is unavailable right now. Type your cue instead.")
                is com.cues.core.ports.SpeechResult.Failed ->
                    onError("${result.reason} Type your cue instead.")
            }
        }
    }

    /**
     * Translated read-back of the assistant's own reply text (docs/FDD.md's
     * "Optional cloud assist" section, architecture decision 4) — never
     * [ReplySpeaker]'s job, since the translation is genuinely different text
     * from what Cues rendered. Hindi is the only target offered today; there
     * is no language-picker UI yet (CLEANUP.md CL-35).
     */
    fun startCloudReadback(englishText: String, onDone: (String) -> Unit, onError: (String) -> Unit) {
        val readback = sarvamReadback
        if (readback == null) {
            onError("Cloud assist is not available.")
            return
        }
        scope.launch {
            try {
                onDone(readback.translateAndSpeak(englishText, targetLanguageCode = "hi-IN"))
            } catch (e: Exception) {
                onError(e.message ?: "Could not translate and speak that reply.")
            }
        }
    }

    fun runBakeOffNow() {
        isBakingOff = true
        scope.launch {
            bakeOffReport = runBakeOff().render()
            isBakingOff = false
        }
    }

    /**
     * Checks screen's "Test NPU on this chip anyway" button — the one place
     * this app ever calls [com.cues.app.drafting.LiteRtLmSession.probeNpuOnce]
     * (via [probeNpu]). A real attempt, timed the same way a real draft
     * would be; [npuProbeResult] renders whatever it actually returns or
     * threw, never a guess at what it "should" do on this chip.
     */
    fun probeNpuNow() {
        val probe = probeNpu ?: return
        isProbingNpu = true
        npuProbeResult = null
        scope.launch {
            npuProbeResult = try {
                val output = probe("Say hello in one short sentence.")
                val report = output.report
                "NPU loaded — ${report.loadMs}ms load, ${report.generationMs}ms generate, ${"%.1f".format(report.tokensPerSecond)} tok/s"
            } catch (e: Exception) {
                "NPU attempt failed: ${e.message ?: e::class.simpleName}"
            }
            isProbingNpu = false
        }
    }

    fun downloadModelNow() {
        modelProvisionState = com.cues.core.inference.ModelProvisionState.Downloading(0, null)
        modelDownloadJob = scope.launch {
            modelProvisionState = modelDownloader.download { downloaded, total ->
                modelProvisionState = com.cues.core.inference.ModelProvisionState.Downloading(downloaded, total)
            }
        }
    }

    fun cancelModelDownloadNow() {
        modelDownloadJob?.cancel()
        // The job's own coroutine won't reach its assignment once cancelled
        // (that's the point of cancelling it) — re-read from disk directly,
        // same as first composition, so the UI reflects reality either way.
        modelProvisionState = modelDownloader.currentState()
    }

    fun removeModelNow() {
        // File I/O off the composition/click thread (CLEANUP.md CL-18's
        // main-thread item). LocalModelRunner re-checks the model file's own
        // identity before every call, so a warm engine left open across a
        // removal cannot be mistaken for the removed file on the next draft
        // — it will simply fail to reload, honestly, the same as any other
        // missing-model case.
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            modelDownloader.remove()
            modelProvisionState = modelDownloader.currentState()
        }
    }

    /**
     * The explicit, opt-in "third opinion" (docs/FDD.md's "Optional cloud
     * assist" section): reached only from here, only after the offline pair
     * already disagreed or both failed, and only with cloud assist on.
     * [tryCloudAssist] independently validates its own result, so a
     * [com.cues.core.drafting.DraftResult.Drafted] here is exactly as trusted
     * as any other draft reaching Review.
     */
    suspend fun offerCloudAssist(fallbackMessage: String, text: String) {
        isTryingCloudAssist = true
        when (val result = tryCloudAssist(text)) {
            is com.cues.core.drafting.DraftResult.Drafted -> {
                missingCapabilities = emptySet()
                reviewDraft = result.routine
                reviewDraftTrace = result.trace
                navController.navigate(CuesRoutes.REVIEW)
            }
            else -> notify(fallbackMessage)
        }
        isTryingCloudAssist = false
    }

    fun draft(text: String) {
        isDrafting = true
        scope.launch {
            // Sprint 8 (CLEANUP.md CL-18's main-thread/robustness item): an
            // exception anywhere below — a model crash, a picker failure —
            // used to leave isDrafting stuck true, with no way back to a
            // usable Ask screen short of leaving and reopening it.
            try {
                val turn = cueService.converse(conversation, text)
                assistantTurns = conversation.turns.toList()
                if (speakReplies) replySpeaker.speak(turn.reply.text)
                val draftedRoutine = turn.draft
                when {
                    draftedRoutine != null -> {
                        missingCapabilities = emptySet()
                        reviewDraft = draftedRoutine
                        reviewDraftTrace = turn.trace
                        navController.navigate(CuesRoutes.REVIEW)
                    }
                    turn.reply.args["appQuery"] != null -> {
                        val query = turn.reply.args.getValue("appQuery")
                        appSourceText = text
                        appQuery = query
                        appCandidates = com.cues.app.runtime.rankInstalledApps(com.cues.app.runtime.installedApps(context), query)
                    }
                    turn.reply.code == ReplyCode.NEEDS_CLARIFICATION -> {
                        val ids = turn.reply.chips.filterIsInstance<com.cues.core.assistant.ReplyChip.Choice>().map { it.id }.toSet()
                        val candidates = pairedDevices().filter { it.id in ids }
                        when {
                            candidates.isNotEmpty() -> {
                                deviceSourceText = text
                                deviceCandidates = candidates
                            }
                            cloudAssistOn -> offerCloudAssist(turn.reply.text, text)
                            else -> notify(turn.reply.text)
                        }
                    }
                    cloudAssistOn && turn.reply.code == ReplyCode.DRAFT_FAILED -> offerCloudAssist(turn.reply.text, text)
                    else -> notify(turn.reply.text)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                notify(e.message ?: "Something went wrong drafting that cue.")
            } finally {
                isDrafting = false
            }
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isTopLevel = currentRoute in CuesRoutes.bottomTabs
    val t = cuesTokens

    Scaffold(
        containerColor = t.voidSurface,
        contentColor = t.inkPrimary,
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            if (isTopLevel) {
                CuesTopBar(title = tabTitle(currentRoute ?: CuesRoutes.NOW), onOpenChecks = { navController.navigate(CuesRoutes.CHECKS) })
            }
        },
        bottomBar = {
            if (isTopLevel) {
                CuesBottomNav(currentRoute = currentRoute, onSelect = { navController.navigateToTab(it) })
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            NavHost(navController = navController, startDestination = CuesRoutes.NOW) {
                composable(CuesRoutes.NOW) {
                    val snapshot = remember(liveSessions, routines) { LiveSnapshot.current(context) }
                    val forecast = remember(routines) {
                        forecastToday(routines, store.allPatches(), snapshot, ZoneId.systemDefault(), store)
                    }
                    NowScreen(
                        routines = routines,
                        liveSessions = liveSessions,
                        snapshot = snapshot,
                        chipset = remember { DeviceIdentity.chipset() },
                        phoneName = remember { DeviceIdentity.phoneName() },
                        forecast = forecast,
                        titleFor = { id -> routines.firstOrNull { it.id == id }?.title ?: id },
                        drafterLabel = cueService.diagnostics().setup.friendlyLabel(),
                        onOpenRoutine = { routine -> navController.navigate(CuesRoutes.cueDetail(routine.id)) },
                        onArmPause = { routine ->
                            val result = if (routine.status == RoutineStatus.PAUSED) cueService.resume(routine.id)
                            else cueService.pause(routine.id)?.let { ArmResult.Ok(it) }
                            when (result) {
                                is ArmResult.Ok -> refresh()
                                is ArmResult.MissingCapabilities -> {
                                    missingCapabilities = result.missing
                                    notify("Resumed as reviewable — a permission is still missing.")
                                    refresh()
                                }
                                else -> notify("Could not change this cue's status.")
                            }
                        },
                        onManualStop = { session ->
                            cueService.onManualStop(session.id)
                            GraceScheduler.cancel(context, session.id)
                            refresh()
                        },
                        onGrantCapability = { navController.navigate(CuesRoutes.CHECKS) },
                        adapterStatuses = adapterStatuses,
                    )
                }

                composable(CuesRoutes.INSIGHTS) {
                    var insightsWindow by remember { mutableStateOf(com.cues.core.insights.InsightsWindow.LAST_7_DAYS) }
                    val report = remember(insightsWindow, generation) { cueService.insights(insightsWindow) }
                    val diag = cueService.diagnostics()
                    InsightsScreen(
                        report = report,
                        window = insightsWindow,
                        onWindowChange = { insightsWindow = it },
                        onToggleLedger = { store.setSignalOptIn(it) },
                        drafterLabel = diag.setup.friendlyLabel(),
                        lastTrace = diag.lastTrace,
                        onExportConsole = {
                            val html = com.cues.app.bridge.ExportImport.buildConsoleHtml(context, cueService, store)
                            val uri = com.cues.app.bridge.ExportImport.writeShareableConsole(context, html)
                            context.startActivity(com.cues.app.bridge.ExportImport.shareIntent(context, uri))
                        },
                        onFixCapability = { navController.navigate(CuesRoutes.CHECKS) },
                        onToggleUsageTracking = { store.setUsageTrackingEnabled(it) },
                    )
                }

                composable(CuesRoutes.ASK) {
                    AskScreen(
                        isDrafting = isDrafting,
                        onDraft = ::draft,
                        drafterLabel = cueService.diagnostics().setup.friendlyLabel(),
                        onStartVoice = { onTranscript, onUnavailable -> localSpeechInput.start(onTranscript, onUnavailable) },
                        onStopVoice = { localSpeechInput.finishAndDeliver() },
                        cloudAssistAvailable = cloudAssistAvailable,
                        cloudAssistEnabled = cloudAssistEnabled,
                        onToggleCloudAssist = { cloudAssistEnabled = !cloudAssistEnabled },
                        onStartCloudVoice = ::startCloudVoice,
                        isTryingCloudAssist = isTryingCloudAssist,
                        onTranslateReadback = ::startCloudReadback,
                        deviceCandidates = deviceCandidates,
                        onSelectDevice = { device ->
                            val source = deviceSourceText
                            deviceCandidates = null
                            deviceSourceText = null
                            if (source != null) draft("$source (selected paired device: ${device.label})")
                        },
                        onDismissDevicePicker = { deviceCandidates = null; deviceSourceText = null },
                        appQuery = appQuery,
                        appCandidates = appCandidates,
                        onSelectApp = { app ->
                            val source = appSourceText
                            appCandidates = null
                            appQuery = null
                            appSourceText = null
                            if (source != null) draft("$source (selected app: ${app.packageName}|${app.label})")
                        },
                        onDismissAppPicker = { appCandidates = null; appQuery = null; appSourceText = null },
                        assistantTurns = assistantTurns,
                        onConfirmCommand = { command ->
                            if (cueService.confirm(command)) {
                                refresh()
                                notify("Done. The confirmed command was applied.")
                            } else notify("That command no longer has a valid target.")
                        },
                        onHandoffToJovi = {
                            val intent = android.content.Intent(android.content.Intent.ACTION_ASSIST)
                            if (intent.resolveActivity(context.packageManager) != null) context.startActivity(intent)
                            else notify("No system assistant is available on this phone.")
                        },
                        incomingText = incomingScreenText ?: reviewBackText,
                    )
                }

                composable(CuesRoutes.RECEIPTS) {
                    ReceiptScreen(
                        loadReceipts = { store.raw.receipts() },
                        loadReceiptRecords = { store.receiptRecords() },
                        onBack = {},
                        onSpeak = replySpeaker::speak,
                    )
                }

                composable(CuesRoutes.WORKBENCH) {
                    WorkbenchScreen(
                        onOpenPatchBay = { navController.navigate(CuesRoutes.WORKBENCH_PATCH_BAY) },
                        onOpenContexts = { navController.navigate(CuesRoutes.WORKBENCH_CONTEXTS) },
                        onOpenMemory = { navController.navigate(CuesRoutes.WORKBENCH_MEMORY) },
                        onOpenUtilityBindings = { navController.navigate(CuesRoutes.WORKBENCH_MACROS) },
                        onOpenTimetableCapture = { navController.navigate(CuesRoutes.INGEST_TIMETABLE) },
                        onOpenCueCardScan = { navController.navigate(CuesRoutes.INGEST_SCAN) },
                    )
                }

                composable(CuesRoutes.WORKBENCH_PATCH_BAY) {
                    com.cues.app.ui.workbench.PatchBayScreen(
                        devices = pairedDevices(),
                        contexts = store.allContexts(),
                        places = store.allPlaces(),
                        installedApps = { com.cues.app.runtime.installedApps(context) },
                        onCompile = { sentence ->
                            navController.popBackStack()
                            draft(sentence)
                        },
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(CuesRoutes.REVIEW) {
                    // Snapshot once: reviewDraft is nulled on approve/back to reset
                    // outer state, but this destination's own composition must keep
                    // rendering its routine for the pop transition's outgoing frames —
                    // reading reviewDraft live here re-fires the null-guard mid-transition
                    // and paints a blank frame before the nav animation finishes.
                    val routine = remember { reviewDraft }
                    val trace = remember { reviewDraftTrace }
                    if (routine == null) {
                        navController.popBackStack()
                    } else {
                        ReviewFlow(
                            routine = routine,
                            cueService = cueService,
                            missingCapabilities = missingCapabilities,
                            trace = trace,
                            onApproved = {
                                navController.navigateToTab(CuesRoutes.NOW)
                                refresh()
                                missingCapabilities = emptySet()
                                reviewDraft = null
                                reviewDraftTrace = null
                                notify("Cue armed — it runs the next time \"${routine.title}\" happens.")
                            },
                            onMissingCapabilities = { missingCapabilities = it },
                            onNotify = ::notify,
                            onBack = {
                                navController.popBackStack()
                                reviewBackText = routine.sourceText
                                reviewDraft = null
                                reviewDraftTrace = null
                            },
                        )
                    }
                }

                composable(
                    CuesRoutes.CUE_DETAIL,
                    arguments = listOf(navArgument("routineId") { type = NavType.StringType }),
                ) { backStackEntry2 ->
                    val routineId = backStackEntry2.arguments?.getString("routineId")
                    val routine = routines.firstOrNull { it.id == routineId }
                    if (routine == null) {
                        navController.popBackStack()
                    } else {
                        val activeSessionId = remember(routine.id, liveSessions) {
                            liveSessions.firstOrNull { it.routineId == routine.id }?.id
                        }
                        RoutineDetailScreen(
                            routine = routine,
                            onBack = { navController.popBackStack() },
                            onPauseResume = {
                                val result = if (routine.status == RoutineStatus.PAUSED) cueService.resume(routine.id)
                                else cueService.pause(routine.id)?.let { ArmResult.Ok(it) }
                                when (result) {
                                    is ArmResult.Ok -> refresh()
                                    is ArmResult.MissingCapabilities -> {
                                        missingCapabilities = result.missing
                                        notify("Resumed as reviewable — a permission is still missing.")
                                        refresh()
                                    }
                                    else -> notify("Could not change this cue's status.")
                                }
                            },
                            onDelete = {
                                when (val result = cueService.delete(routine.id)) {
                                    DeleteResult.Ok -> { refresh(); navController.popBackStack() }
                                    is DeleteResult.Blocked -> notify("Still cleaning up ${result.sessionsWithObligations.size} session(s) — try again shortly.")
                                }
                            },
                            onManualStop = activeSessionId?.let { sessionId ->
                                {
                                    cueService.onManualStop(sessionId)
                                    GraceScheduler.cancel(context, sessionId)
                                    refresh()
                                }
                            },
                            onDryRun = {
                                val now = System.currentTimeMillis()
                                Rehearsal.dryRun(routine, LiveSnapshot.current(context, now), now)
                            },
                            deleteBlockedReason = null,
                            activePatch = cueService.currentPatch(routine.id),
                            onSkipToday = { cueService.skipToday(routine.id); refresh() },
                            onPauseUntil = { epochMillis -> cueService.pauseUntil(routine.id, epochMillis); refresh() },
                            onClearPatch = { cueService.clearPatch(routine.id); refresh() },
                            onShareAsCard = { cueCardShareRoutine = routine; navController.navigate(CuesRoutes.CUE_CARD_SHARE) },
                            onReview = { reviewDraft = routine; reviewDraftTrace = null; navController.navigate(CuesRoutes.REVIEW) }.takeIf {
                                routine.status != RoutineStatus.ARMED && routine.status != RoutineStatus.PAUSED
                            },
                        )
                    }
                }

                composable(CuesRoutes.CHECKS) {
                    DiagnosticsScreen(
                        diagnostics = diagnostics,
                        deviceHealth = remember { com.cues.app.runtime.DeviceHealthReadings.current(context) },
                        isRefreshing = isDiagnosticsRefreshing,
                        onRefresh = {
                            isDiagnosticsRefreshing = true
                            deviceDiagnostics.refresh { diagnostics = it; isDiagnosticsRefreshing = false }
                        },
                        onRecordJoviMicOrAssist = { observation -> diagnostics = deviceDiagnostics.recordJoviMicOrAssist(observation) },
                        onRecordPermissionMonitor = { observation -> diagnostics = deviceDiagnostics.recordPermissionMonitor(observation) },
                        onBack = { navController.popBackStack() },
                        isBakingOff = isBakingOff,
                        bakeOffReport = bakeOffReport,
                        onRunBakeOff = ::runBakeOffNow,
                        cueDiagnostics = cueService.diagnostics(),
                        modelProvisionState = modelProvisionState,
                        canDownloadModel = modelDownloader.canDownload(),
                        isOnWifi = modelDownloader.isOnWifi(),
                        npuEligible = com.cues.app.drafting.LiteRtLmSession.npuSocEligible(),
                        onDeviceModelEnabled = onDeviceModelEnabled,
                        onToggleOnDeviceModel = { onDeviceModelEnabled = it; onToggleOnDeviceModel(it) },
                        onDownloadModel = ::downloadModelNow,
                        onChooseModel = { modelPicker.launch(arrayOf("application/octet-stream", "application/*", "*/*")) },
                        onCancelDownloadModel = if (modelProvisionState is com.cues.core.inference.ModelProvisionState.Downloading) ::cancelModelDownloadNow else null,
                        onRemoveModel = if (modelProvisionState is com.cues.core.inference.ModelProvisionState.Installed) ::removeModelNow else null,
                        externalGemmaEnabled = externalGemmaEnabled,
                        onToggleExternalGemma = { externalGemmaEnabled = it; externalGemmaGate.enabled = it },
                        externalModelIdentity = externalModelIdentity(),
                        onChooseExternalModel = { externalModelPicker.launch(arrayOf("application/octet-stream", "application/*", "*/*")) },
                        recentExternalCalls = externalGemmaGate.recentCalls(),
                        approvedExternalCallers = externalGemmaGate.approvedCallers(),
                        onApproveExternalCaller = approveExternalCaller,
                        onRevokeExternalCaller = externalGemmaGate::revoke,
                        inferenceEntries = store.inferenceEntries(),
                        isProbingNpu = isProbingNpu,
                        npuProbeResult = npuProbeResult,
                        onProbeNpu = probeNpu?.let { ::probeNpuNow },
                    )
                }

                composable(CuesRoutes.WORKBENCH_CONTEXTS) {
                    var contexts by remember { mutableStateOf(store.allContexts()) }
                    var places by remember { mutableStateOf(store.allPlaces()) }
                    ContextsScreen(
                        contexts = contexts,
                        places = places,
                        availableSignals = remember { availableSignalsNow(context) },
                        onSaveContext = { label, predicates ->
                            store.saveContext(NamedContext(id = "context-" + UUID.randomUUID(), label = label, version = 1, predicates = predicates))
                            contexts = store.allContexts()
                        },
                        onDeleteContext = { id -> store.deleteContext(id); contexts = store.allContexts() },
                        onSavePlace = { label, lat, lng, radius ->
                            store.savePlace(Place(id = "place-" + UUID.randomUUID(), label = label, version = 1, latitude = lat, longitude = lng, radiusMeters = radius))
                            places = store.allPlaces()
                        },
                        onDeletePlace = { id -> store.deletePlace(id); places = store.allPlaces() },
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(CuesRoutes.WORKBENCH_MEMORY) {
                    MemoryScreen(
                        facts = cueService.listFacts(),
                        onDelete = { fact -> cueService.deleteFact(fact.id); refresh() },
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(CuesRoutes.WORKBENCH_MACROS) {
                    var bindingsRefresh by remember { mutableStateOf(0) }
                    val bindingsById = remember(bindingsRefresh) { store.allBindings().associateBy { it.utilityId } }
                    com.cues.app.ui.UtilityBindingScreen(
                        bindings = bindingsById,
                        onSave = { utilityId, state, macro ->
                            val validation = com.cues.core.compile.MacroValidator.validate(macro)
                            if (!validation.isValid) {
                                validation.errors.map { it.message }
                            } else {
                                store.saveMacro(macro)
                                val existing = bindingsById[utilityId] ?: com.cues.core.model.UtilityBinding(utilityId, onMacroId = "", offMacroId = "")
                                val updated = if (state == com.cues.core.model.UtilityState.ON) existing.copy(onMacroId = macro.id) else existing.copy(offMacroId = macro.id)
                                store.saveBinding(updated)
                                bindingsRefresh++
                                emptyList()
                            }
                        },
                        onDeleteBinding = { utilityId -> store.deleteBinding(utilityId); bindingsRefresh++ },
                        onTest = { utilityId, state ->
                            scope.launch {
                                val outcome = withContext(Dispatchers.IO) { testUtilityAction(utilityId, state) }
                                utilityTestResult = "${outcome.state.name.lowercase()}: ${outcome.detail.orEmpty()}"
                            }
                        },
                        lastTestResult = utilityTestResult,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(CuesRoutes.INGEST_TIMETABLE) {
                    com.cues.app.camera.TimetableCaptureScreen(
                        onRecognized = { text ->
                            importResult = com.cues.core.imports.TimetableExtractor.extract(text)
                            navController.navigate(CuesRoutes.INGEST_IMPORT)
                        },
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(CuesRoutes.INGEST_IMPORT) {
                    val result = importResult
                    if (result == null) {
                        navController.popBackStack()
                    } else {
                        com.cues.app.ui.ImportReviewScreen(
                            result = result,
                            onReviewEntry = { entry -> entry.proposedSentence()?.let { sentence -> draft(sentence) } },
                            onBack = { navController.popBackStack() },
                        )
                    }
                }

                composable(CuesRoutes.CUE_CARD_SHARE) {
                    val routine = cueCardShareRoutine
                    if (routine == null) {
                        navController.popBackStack()
                    } else {
                        com.cues.app.ui.CueCardShareScreen(
                            routine = routine,
                            onShareAsText = { text ->
                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, text)
                                }
                                context.startActivity(android.content.Intent.createChooser(intent, "Share Cue Card"))
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                }

                composable(CuesRoutes.INGEST_SCAN) {
                    com.cues.app.camera.CueCardScanScreen(
                        onScanned = { payload ->
                            when (val result = com.cues.core.share.CueCards.reimport(payload, pairedDevices(), store.allContexts(), store.allPlaces())) {
                                is com.cues.core.share.ReimportResult.Ready -> {
                                    reviewDraft = result.routine
                                    reviewDraftTrace = null
                                    navController.navigate(CuesRoutes.REVIEW)
                                }
                                is com.cues.core.share.ReimportResult.MissingEntity ->
                                    notify("You don't have a ${result.kind} named \"${result.label}\" yet. Add it, then scan again.")
                                com.cues.core.share.ReimportResult.Tampered -> notify("This card could not be verified — it may be corrupted or edited.")
                                is com.cues.core.share.ReimportResult.Malformed -> notify("This does not look like a Cue Card. ${result.reason}")
                                is com.cues.core.share.ReimportResult.UnsupportedSchema -> notify("This card was made by a newer version of Cues.")
                            }
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }

}

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * The Review screen plus the state that belongs only to it: the normalized
 * routine and its rehearsal, both derived once per drafted routine rather
 * than recomputed on every recomposition.
 */
@Composable
private fun ReviewFlow(
    routine: Routine,
    cueService: CueService,
    missingCapabilities: Set<Capability>,
    onApproved: () -> Unit,
    onMissingCapabilities: (Set<Capability>) -> Unit,
    onNotify: (String) -> Unit,
    onBack: () -> Unit,
    trace: com.cues.core.drafting.DraftTrace? = null,
) {
    val review = remember(routine) { cueService.review(routine) }
    val rehearsal = remember(routine) { Rehearsal.run(review.normalized).rows }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, review) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onMissingCapabilities(cueService.missingCapabilities(review.normalized))
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ReviewScreenV2(
        routine = review.normalized,
        review = review,
        rehearsal = rehearsal,
        missingCapabilities = missingCapabilities,
        trace = trace,
        onApprove = {
            when (val result = cueService.approveAndArm(review.normalized)) {
                is ArmResult.Ok -> onApproved()
                is ArmResult.MissingCapabilities -> {
                    onMissingCapabilities(result.missing)
                    onNotify("Approved. Grant the missing access, then try arming again.")
                }
                is ArmResult.Invalid -> onNotify("This cue isn't valid yet — see the checks above.")
                ArmResult.NotApproved -> onNotify("Approval did not take. Try again.")
                ArmResult.NotPaused -> Unit
            }
        },
        onBack = onBack,
        onCapabilitiesChanged = { onMissingCapabilities(cueService.missingCapabilities(review.normalized)) },
    )
}
