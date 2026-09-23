package com.cues.app

import android.os.Bundle
import com.cues.app.drafting.LocalSpeechInput
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
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
import androidx.lifecycle.LifecycleEventObserver
import com.cues.app.runtime.BluetoothCoverage
import com.cues.app.runtime.GraceScheduler
import com.cues.app.runtime.MonitoringRepository
import com.cues.app.ui.CuesTheme
import com.cues.app.ui.DiagnosticsScreen
import com.cues.app.ui.HomeScreen
import com.cues.app.ui.ReceiptScreen
import com.cues.app.ui.ReviewScreen
import com.cues.app.ui.RoutineDetailScreen
import com.cues.core.CueService
import com.cues.core.approval.ArmResult
import com.cues.core.approval.DeleteResult
import com.cues.core.drafting.DraftResult
import com.cues.core.drafting.PairedDevice
import com.cues.core.model.Capability
import com.cues.core.model.Routine
import com.cues.core.model.RoutineStatus
import com.cues.core.rehearsal.Rehearsal
import com.cues.core.session.isLive
import com.cues.core.store.JsonFileStore
import com.cues.app.runtime.DeviceDiagnosticsRepository
import kotlinx.coroutines.launch

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as CuesApplication
        localSpeechInput = LocalSpeechInput(this)

        setContent {
            CuesTheme {
                CuesApp(
                    cueService = app.cueService,
                    store = app.store,
                    deviceDiagnostics = app.deviceDiagnostics,
                    monitoring = app.monitoring,
                    localSpeechInput = localSpeechInput,
                )
            }
        }
    }

    override fun onDestroy() {
        if (::localSpeechInput.isInitialized) localSpeechInput.stop()
        super.onDestroy()
    }
}

private sealed interface Screen {
    data object Home : Screen
    data class Review(val routine: Routine) : Screen
    data class Detail(val routineId: String) : Screen
    data object Receipts : Screen
    data object Diagnostics : Screen
}

@Composable
private fun CuesApp(
    cueService: CueService,
    store: JsonFileStore,
    deviceDiagnostics: DeviceDiagnosticsRepository,
    monitoring: MonitoringRepository,
    localSpeechInput: LocalSpeechInput,
) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var routines by remember { mutableStateOf(cueService.list()) }
    var isDrafting by remember { mutableStateOf(false) }
    var missingCapabilities by remember { mutableStateOf<Set<Capability>>(emptySet()) }
    var diagnostics by remember { mutableStateOf(deviceDiagnostics.latest()) }
    var isDiagnosticsRefreshing by remember { mutableStateOf(false) }
    var deviceCandidates by remember { mutableStateOf<List<PairedDevice>?>(null) }
    var deviceSourceText by remember { mutableStateOf<String?>(null) }
    var adapterStatuses by remember { mutableStateOf(monitoring.statuses()) }

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
                adapterStatuses = monitoring.statuses()
                routines = cueService.list()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun refresh() {
        routines = cueService.list()
    }

    fun notify(message: String) {
        scope.launch { snackbarHost.showSnackbar(message) }
    }

    fun draft(text: String) {
        isDrafting = true
        scope.launch {
            when (val result = cueService.draft(text)) {
                is DraftResult.Drafted -> {
                    missingCapabilities = emptySet()
                    screen = Screen.Review(result.routine)
                }

                is DraftResult.NeedsClarification -> {
                    if (result.about == "trigger.device" && result.deviceCandidates.isNotEmpty()) {
                        deviceSourceText = text
                        deviceCandidates = result.deviceCandidates
                    } else {
                        notify(result.question)
                    }
                }

                is DraftResult.Failed -> notify("Could not draft that: ${result.reason}")
            }
            isDrafting = false
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHost) }) { padding ->
        Box(Modifier.padding(padding)) {
            when (val current = screen) {
                Screen.Home -> HomeScreen(
                    routines = routines,
                    adapterStatuses = adapterStatuses,
                    isDrafting = isDrafting,
                    onDraft = ::draft,
                    onOpenRoutine = { routine -> screen = Screen.Detail(routine.id) },
                    onOpenReceipts = { screen = Screen.Receipts },
                    onOpenDiagnostics = { screen = Screen.Diagnostics },
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
                            deleteBlockedReason = null,
                        )
                    }
                }

                Screen.Receipts -> ReceiptScreen(
                    receipts = remember { store.receipts() },
                    onBack = { screen = Screen.Home },
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
                )
            }
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
