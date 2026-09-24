package com.cues.app

import android.os.Bundle
import com.cues.app.drafting.LocalSpeechInput
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.cues.app.runtime.BluetoothCoverage
import com.cues.app.runtime.GraceScheduler
import com.cues.app.runtime.MonitoringRepository
import com.cues.app.runtime.LiveSnapshot
import com.cues.app.ui.AvailableSignal
import com.cues.app.ui.ContextsScreen
import com.cues.app.ui.CuesTheme
import com.cues.app.ui.cuesColors
import com.cues.app.ui.DiagnosticsScreen
import com.cues.app.ui.HomeScreen
import com.cues.app.ui.ReceiptScreen
import com.cues.app.ui.ReviewScreen
import com.cues.app.ui.RoutineDetailScreen
import com.cues.app.ui.TodayScreen
import com.cues.app.ui.MemoryScreen
import com.cues.app.ui.LearningSettings
import com.cues.core.coach.CoachPolicy
import com.cues.core.coach.Detectors
import com.cues.core.coach.Suggestion
import com.cues.core.CueService
import com.cues.core.approval.ArmResult
import com.cues.core.approval.DeleteResult
import com.cues.core.drafting.DraftResult
import com.cues.core.drafting.PairedDevice
import com.cues.core.model.AudioKind
import com.cues.core.model.Capability
import com.cues.core.model.Condition
import com.cues.core.model.ContextValue
import com.cues.core.model.NamedContext
import com.cues.core.model.Place
import com.cues.core.model.Routine
import com.cues.core.model.RoutineStatus
import com.cues.core.model.WifiNetwork
import com.cues.core.rehearsal.Rehearsal
import com.cues.core.review.forecastToday
import com.cues.core.session.isLive
import com.cues.core.store.JsonFileStore
import com.cues.core.assistant.Conversation
import com.cues.core.assistant.ReplyCode
import com.cues.core.assistant.Turn
import com.cues.app.runtime.DeviceDiagnosticsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.util.UUID

/**
 * Single-activity host for the four surfaces the PRS describes.
 *
 * No navigation library: a sealed [Screen] plus one `when` is the whole
 * router, which is enough for four screens and keeps this dependency-free.
 * [CueService] is the only thing any screen calls into for a decision or a
 * side effect — the composables below are rendering and event wiring only.
 */
class MainActivity : ComponentActivity() {

    private lateinit var localSpeechInput: LocalSpeechInput
    private lateinit var replySpeaker: com.cues.app.voice.ReplySpeaker

    // "Cue this screen" (Task 16): text captured by ScreenTile /
    // CuesAccessibilityService, handed in via EXTRA_SCREEN_CAPTURE. Read
    // here rather than inside the composable tree because it can arrive
    // through onNewIntent, well after setContent already ran once.
    private var incomingScreenText by androidx.compose.runtime.mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // One system-owned splash handoff on every supported Android version.
        // This must happen before Activity setup so Android 12+ does not show a
        // second, default splash between launch and Compose.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val app = application as CuesApplication
        localSpeechInput = LocalSpeechInput(this)
        replySpeaker = com.cues.app.voice.ReplySpeaker(this)
        incomingScreenText = intent?.getStringExtra(com.cues.app.runtime.CuesAccessibilityService.EXTRA_SCREEN_CAPTURE)

        setContent {
            CuesTheme {
                CuesApp(
                    cueService = app.cueService,
                    store = app.store,
                    deviceDiagnostics = app.deviceDiagnostics,
                    monitoring = app.monitoring,
                    adapterHealth = { app.adapterSupervisor.health() },
                    syncAdapters = { app.adapterSupervisor.sync(app.cueService.list().filter { it.status == RoutineStatus.ARMED }) },
                    runBakeOff = { com.cues.app.drafting.AppBakeOff.run(app.pairedDevices()) },
                    localSpeechInput = localSpeechInput,
                    pairedDevices = app::pairedDevices,
                    replySpeaker = replySpeaker,
                    testUtilityAction = app::testUseUtility,
                    incomingScreenText = incomingScreenText,
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(com.cues.app.runtime.CuesAccessibilityService.EXTRA_SCREEN_CAPTURE)?.let {
            incomingScreenText = it
        }
    }

    override fun onDestroy() {
        if (::localSpeechInput.isInitialized) localSpeechInput.stop()
        if (::replySpeaker.isInitialized) replySpeaker.shutdown()
        super.onDestroy()
    }
}

private sealed interface Screen {
    data object Home : Screen
    data class Review(val routine: Routine) : Screen
    data class Detail(val routineId: String) : Screen
    data object Receipts : Screen
    data object Diagnostics : Screen
    data object Today : Screen
    data object Contexts : Screen
    data object Memory : Screen
    data object Learning : Screen
    data object TimetableCapture : Screen
    data class ImportReview(val result: com.cues.core.imports.TimetableExtractionResult) : Screen
    data class CueCardShare(val routine: Routine) : Screen
    data object CueCardScan : Screen
    data object UtilityBindings : Screen
}

/**
 * What a named context can be built from: signals actually readable right
 * now, never a signal Cues merely hopes is true. [ContextValue.Unknown] is
 * simply not offered — the FDD's "capture selected current signals only
 * after permission" line, applied literally.
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

@Composable
private fun CuesApp(
    cueService: CueService,
    store: JsonFileStore,
    deviceDiagnostics: DeviceDiagnosticsRepository,
    monitoring: MonitoringRepository,
    adapterHealth: () -> List<com.cues.core.ports.ListenerHealth>,
    syncAdapters: () -> Unit,
    runBakeOff: suspend () -> com.cues.core.corpus.BakeOffReport,
    localSpeechInput: LocalSpeechInput,
    pairedDevices: () -> List<PairedDevice>,
    replySpeaker: com.cues.app.voice.ReplySpeaker,
    testUtilityAction: (com.cues.core.model.UtilityId, com.cues.core.model.UtilityState) -> com.cues.core.ports.ActionOutcome,
    incomingScreenText: String? = null,
) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var routines by remember { mutableStateOf(cueService.list()) }
    var isDrafting by remember { mutableStateOf(false) }
    var missingCapabilities by remember { mutableStateOf<Set<Capability>>(emptySet()) }
    var diagnostics by remember { mutableStateOf(deviceDiagnostics.latest()) }
    var isBakingOff by remember { mutableStateOf(false) }
    var bakeOffReport by remember { mutableStateOf<String?>(null) }
    var isDiagnosticsRefreshing by remember { mutableStateOf(false) }
    var deviceCandidates by remember { mutableStateOf<List<PairedDevice>?>(null) }
    var deviceSourceText by remember { mutableStateOf<String?>(null) }
    var appQuery by remember { mutableStateOf<String?>(null) }
    var appCandidates by remember { mutableStateOf<List<com.cues.app.runtime.InstalledApp>?>(null) }
    var appSourceText by remember { mutableStateOf<String?>(null) }
    var adapterStatuses by remember { mutableStateOf(monitoring.statuses(adapterHealth())) }
    val conversation = remember { Conversation() }
    var assistantTurns by remember { mutableStateOf<List<Turn>>(emptyList()) }
    // Session-only, off by default: a convenience for reading Cues' own
    // exact reply text aloud, never a paraphrase and never voice acting on
    // anything by itself — see ReplySpeaker's own doc comment.
    var speakReplies by remember { mutableStateOf(false) }
    var utilityTestResult by remember { mutableStateOf<String?>(null) }
    val coachPolicy = remember { CoachPolicy(store) }
    var coachSuggestion by remember {
        mutableStateOf<Suggestion?>(coachPolicy.next(Detectors.all(store.ledgerEvents(), System.currentTimeMillis()), System.currentTimeMillis()))
    }

    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 4.6/4.7: re-checks coverage and refreshes what Home shows every time
    // the app comes back to the foreground, not only at process start — the
    // moment a user actually looks at the screen is the moment "was anything
    // missed while I was away" matters.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                cueService.checkBluetoothCoverage(BluetoothCoverage.currentlyConnectedDeviceIds(context))
                syncAdapters()
                adapterStatuses = monitoring.statuses(adapterHealth())
                routines = cueService.list()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun refresh() {
        routines = cueService.list()
        syncAdapters()
        adapterStatuses = monitoring.statuses(adapterHealth())
    }

    fun notify(message: String) {
        scope.launch { snackbarHost.showSnackbar(message) }
    }

    fun runBakeOffNow() {
        isBakingOff = true
        scope.launch {
            bakeOffReport = runBakeOff().render()
            isBakingOff = false
        }
    }

    fun draft(text: String) {
        isDrafting = true
        scope.launch {
            val turn = cueService.converse(conversation, text)
            assistantTurns = conversation.turns.toList()
            if (speakReplies) replySpeaker.speak(turn.reply.text)
            val draftedRoutine = turn.draft
            when {
                draftedRoutine != null -> {
                    missingCapabilities = emptySet()
                    screen = Screen.Review(draftedRoutine)
                }
                turn.reply.args["appQuery"] != null -> {
                    val query = turn.reply.args.getValue("appQuery")
                    appSourceText = text
                    appQuery = query
                    // Queried fresh each time rather than cached: an app can be
                    // installed or removed between one "open X" and the next,
                    // and this list is cheap enough that staleness buys nothing.
                    appCandidates = com.cues.app.runtime.rankInstalledApps(
                        com.cues.app.runtime.installedApps(context), query,
                    )
                }
                turn.reply.code == ReplyCode.NEEDS_CLARIFICATION -> {
                    val ids = turn.reply.chips.filterIsInstance<com.cues.core.assistant.ReplyChip.Choice>().map { it.id }.toSet()
                    val candidates = pairedDevices().filter { it.id in ids }
                    if (candidates.isNotEmpty()) {
                        deviceSourceText = text
                        deviceCandidates = candidates
                    } else {
                        notify(turn.reply.text)
                    }
                }
                else -> notify(turn.reply.text)
            }
            isDrafting = false
        }
    }

    Scaffold(
        containerColor = cuesColors.bg100,
        contentColor = cuesColors.ink100,
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    (slideInHorizontally { it / 8 } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 10 } + fadeOut())
                },
                label = "screen transition",
            ) { current -> when (current) {
                Screen.Home -> HomeScreen(
                    routines = routines,
                    adapterStatuses = adapterStatuses,
                    isDrafting = isDrafting,
                    onDraft = ::draft,
                    onOpenRoutine = { routine -> screen = Screen.Detail(routine.id) },
                    onOpenReceipts = { screen = Screen.Receipts },
                    onOpenDiagnostics = { screen = Screen.Diagnostics },
                    onOpenToday = { screen = Screen.Today },
                    onOpenContexts = { screen = Screen.Contexts },
                    onOpenMemory = { screen = Screen.Memory },
                    onOpenLearning = { screen = Screen.Learning },
                    onOpenTimetableCapture = { screen = Screen.TimetableCapture },
                    onOpenCueCardScan = { screen = Screen.CueCardScan },
                    onOpenUtilityBindings = { screen = Screen.UtilityBindings },
                    onExportConsole = {
                        // A snapshot of what's true right now, shared through
                        // whatever the sheet offers (Office Kit included) —
                        // never a live link back into this phone.
                        val html = com.cues.app.bridge.ExportImport.buildConsoleHtml(context, cueService, store)
                        val uri = com.cues.app.bridge.ExportImport.writeShareableConsole(context, html)
                        context.startActivity(com.cues.app.bridge.ExportImport.shareIntent(context, uri))
                    },
                    speakReplies = speakReplies,
                    onToggleSpeakReplies = {
                        speakReplies = !speakReplies
                        if (!speakReplies) replySpeaker.stop()
                    },
                    onStartVoice = { onTranscript, onUnavailable ->
                        localSpeechInput.start(onTranscript, onUnavailable)
                    },
                    deviceCandidates = deviceCandidates,
                    onSelectDevice = { device ->
                        val source = deviceSourceText
                        deviceCandidates = null
                        deviceSourceText = null
                        if (source != null) draft("$source (selected paired device: ${device.label})")
                    },
                    onDismissDevicePicker = {
                        deviceCandidates = null
                        deviceSourceText = null
                    },
                    appQuery = appQuery,
                    appCandidates = appCandidates,
                    onSelectApp = { app ->
                        val source = appSourceText
                        appCandidates = null
                        appQuery = null
                        appSourceText = null
                        // A machine-written marker only this call site ever
                        // produces — GrammarParser trusts it because nothing
                        // else can generate it, never because it looks
                        // plausible. See GrammarParser.ResolvedApp.
                        if (source != null) draft("$source (selected app: ${app.packageName}|${app.label})")
                    },
                    onDismissAppPicker = {
                        appCandidates = null
                        appQuery = null
                        appSourceText = null
                    },
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
                    coachSuggestion = coachSuggestion,
                    onAcceptSuggestion = { suggestion ->
                        coachSuggestion = null
                        draft(suggestion.proposal)
                    },
                    onDismissSuggestion = { suggestion, permanent ->
                        coachPolicy.dismiss(suggestion.patternKey, System.currentTimeMillis(), permanent)
                        coachSuggestion = null
                    },
                )

                is Screen.Review -> ReviewFlow(
                    routine = current.routine,
                    cueService = cueService,
                    missingCapabilities = missingCapabilities,
                    onApproved = {
                        refresh()
                        missingCapabilities = emptySet()
                        screen = Screen.Home
                    },
                    onMissingCapabilities = { missingCapabilities = it },
                    onNotify = ::notify,
                    onBack = { screen = Screen.Home },
                )

                is Screen.Detail -> {
                    val routine = routines.firstOrNull { it.id == current.routineId }
                    if (routine == null) {
                        screen = Screen.Home
                    } else {
                        val activeSessionId = remember(routine.id, routines) {
                            store.activeFor(routine.id).firstOrNull { it.state.isLive() }?.id
                        }
                        RoutineDetailScreen(
                            routine = routine,
                            onBack = { screen = Screen.Home },
                            onPauseResume = {
                                val result = if (routine.status == RoutineStatus.PAUSED) {
                                    cueService.resume(routine.id)
                                } else {
                                    cueService.pause(routine.id)?.let { ArmResult.Ok(it) }
                                }
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
                                    DeleteResult.Ok -> {
                                        refresh()
                                        screen = Screen.Home
                                    }

                                    is DeleteResult.Blocked ->
                                        notify("Still cleaning up ${result.sessionsWithObligations.size} session(s) — try again shortly.")
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
                            onSkipToday = {
                                cueService.skipToday(routine.id)
                                refresh()
                            },
                            onPauseUntil = { epochMillis ->
                                cueService.pauseUntil(routine.id, epochMillis)
                                refresh()
                            },
                            onClearPatch = {
                                cueService.clearPatch(routine.id)
                                refresh()
                            },
                            onShareAsCard = { screen = Screen.CueCardShare(routine) },
                        )
                    }
                }

                Screen.Receipts -> ReceiptScreen(
                    loadReceipts = { store.receipts() },
                    onBack = { screen = Screen.Home },
                    onSpeak = replySpeaker::speak,
                )

                Screen.Diagnostics -> DiagnosticsScreen(
                    diagnostics = diagnostics,
                    isRefreshing = isDiagnosticsRefreshing,
                    onRefresh = {
                        isDiagnosticsRefreshing = true
                        deviceDiagnostics.refresh {
                            diagnostics = it
                            isDiagnosticsRefreshing = false
                        }
                    },
                    onRecordJoviMicOrAssist = { observation ->
                        diagnostics = deviceDiagnostics.recordJoviMicOrAssist(observation)
                    },
                    onRecordPermissionMonitor = { observation ->
                        diagnostics = deviceDiagnostics.recordPermissionMonitor(observation)
                    },
                    onBack = { screen = Screen.Home },
                    isBakingOff = isBakingOff,
                    bakeOffReport = bakeOffReport,
                    onRunBakeOff = ::runBakeOffNow,
                    cueDiagnostics = cueService.diagnostics(),
                )

                Screen.Today -> {
                    val forecast = remember(routines) {
                        forecastToday(
                            routines = routines,
                            patches = store.allPatches(),
                            snapshot = LiveSnapshot.current(context),
                            zone = ZoneId.systemDefault(),
                            contexts = store,
                        )
                    }
                    TodayScreen(
                        items = forecast,
                        titleFor = { id -> routines.firstOrNull { it.id == id }?.title ?: id },
                        onBack = { screen = Screen.Home },
                    )
                }

                Screen.Contexts -> {
                    var contexts by remember { mutableStateOf(store.allContexts()) }
                    var places by remember { mutableStateOf(store.allPlaces()) }
                    ContextsScreen(
                        contexts = contexts,
                        places = places,
                        availableSignals = remember { availableSignalsNow(context) },
                        onSaveContext = { label, predicates ->
                            store.saveContext(
                                NamedContext(id = "context-" + UUID.randomUUID(), label = label, version = 1, predicates = predicates),
                            )
                            contexts = store.allContexts()
                        },
                        onDeleteContext = { id ->
                            store.deleteContext(id)
                            contexts = store.allContexts()
                        },
                        onSavePlace = { label, lat, lng, radius ->
                            store.savePlace(Place(id = "place-" + UUID.randomUUID(), label = label, version = 1, latitude = lat, longitude = lng, radiusMeters = radius))
                            places = store.allPlaces()
                        },
                        onDeletePlace = { id ->
                            store.deletePlace(id)
                            places = store.allPlaces()
                        },
                        onBack = { screen = Screen.Home },
                    )
                }

                Screen.Memory -> MemoryScreen(
                    facts = cueService.listFacts(),
                    onDelete = { fact ->
                        cueService.deleteFact(fact.id)
                        refresh()
                    },
                    onBack = { screen = Screen.Home },
                )

                Screen.Learning -> LearningSettings(
                    enabled = store.signalOptIn,
                    events = store.ledgerEvents(),
                    onEnabledChange = store::setSignalOptIn,
                    onWipe = {
                        store.wipeLedger()
                        coachSuggestion = null
                    },
                    onBack = { screen = Screen.Home },
                )

                Screen.TimetableCapture -> com.cues.app.camera.TimetableCaptureScreen(
                    onRecognized = { text ->
                        // The recognized text is untrusted data from here on,
                        // exactly like shared or typed text — the extractor
                        // decides what, if anything, it proposes.
                        screen = Screen.ImportReview(com.cues.core.imports.TimetableExtractor.extract(text))
                    },
                    onBack = { screen = Screen.Home },
                )

                is Screen.ImportReview -> com.cues.app.ui.ImportReviewScreen(
                    result = current.result,
                    onReviewEntry = { entry ->
                        entry.proposedSentence()?.let { sentence ->
                            // The same drafter, Review and Approve path any
                            // typed or spoken cue goes through — an import
                            // proposal earns no shortcut around it.
                            draft(sentence)
                        }
                    },
                    onBack = { screen = Screen.Home },
                )

                is Screen.CueCardShare -> com.cues.app.ui.CueCardShareScreen(
                    routine = current.routine,
                    onShareAsText = { text ->
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, "Share Cue Card"))
                    },
                    onBack = { screen = Screen.Home },
                )

                Screen.CueCardScan -> com.cues.app.camera.CueCardScanScreen(
                    onScanned = { payload ->
                        // Untrusted text until CueCards.decode checks its
                        // digest — nothing here trusts a package/permission
                        // claim, and every device/place/context reference is
                        // re-resolved against this phone's own stores.
                        when (val result = com.cues.core.share.CueCards.reimport(
                            payload, pairedDevices(), store.allContexts(), store.allPlaces(),
                        )) {
                            is com.cues.core.share.ReimportResult.Ready -> screen = Screen.Review(result.routine)
                            is com.cues.core.share.ReimportResult.MissingEntity ->
                                notify("You don't have a ${result.kind} named \"${result.label}\" yet. Add it, then scan again.")
                            com.cues.core.share.ReimportResult.Tampered ->
                                notify("This card could not be verified — it may be corrupted or edited.")
                            is com.cues.core.share.ReimportResult.Malformed ->
                                notify("This does not look like a Cue Card. ${result.reason}")
                            is com.cues.core.share.ReimportResult.UnsupportedSchema ->
                                notify("This card was made by a newer version of Cues.")
                        }
                    },
                    onBack = { screen = Screen.Home },
                )

                Screen.UtilityBindings -> {
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
                                val existing = bindingsById[utilityId]
                                    ?: com.cues.core.model.UtilityBinding(utilityId, onMacroId = "", offMacroId = "")
                                val updated = if (state == com.cues.core.model.UtilityState.ON) {
                                    existing.copy(onMacroId = macro.id)
                                } else {
                                    existing.copy(offMacroId = macro.id)
                                }
                                store.saveBinding(updated)
                                bindingsRefresh++
                                emptyList()
                            }
                        },
                        onDeleteBinding = { utilityId ->
                            store.deleteBinding(utilityId)
                            bindingsRefresh++
                        },
                        onTest = { utilityId, state ->
                            scope.launch {
                                val outcome = withContext(Dispatchers.IO) { testUtilityAction(utilityId, state) }
                                utilityTestResult = "${outcome.state.name.lowercase()}: ${outcome.detail.orEmpty()}"
                            }
                        },
                        lastTestResult = utilityTestResult,
                        onBack = { screen = Screen.Home },
                    )
                }
            } }
        }
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
) {
    val review = remember(routine) { cueService.review(routine) }
    val rehearsal = remember(routine) { Rehearsal.run(review.normalized).rows }

    // 4.7 / AC-03: the case CL-02 flagged — a user leaves the app to grant a
    // permission (say, notification-policy access) and comes back — was
    // never re-checked before. approveAndArm already re-reads capabilities
    // at arm time, so this can't approve on stale consent, but the review
    // screen itself sat there showing the permission as still missing until
    // the user tried anyway. Re-reading on every resume fixes that without
    // touching arming's own gate.
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

    ReviewScreen(
        routine = review.normalized,
        review = review,
        rehearsal = rehearsal,
        missingCapabilities = missingCapabilities,
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
    )
}
