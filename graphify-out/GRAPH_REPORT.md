# Graph Report - iqoo+  (2026-09-25)

## Corpus Check
- 236 files · ~236,733 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 3062 nodes · 7190 edges · 172 communities (137 shown, 31 thin omitted)
- Extraction: 97% EXTRACTED · 3% INFERRED · 0% AMBIGUOUS · INFERRED: 189 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `89a79b25`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- Trigger
- ListenerHealth
- ReasonCode
- PatchSentence
- JsonFileStore
- WhenKind
- Condition
- Session
- RecordingExecutor
- CueService.kt
- ActionSpec
- EndCondition
- CueService
- CuesAccessibilityService
- GrammarParserTest
- CalendarConditionKit
- ActionOutcome
- TimetableExtractorTest
- CapabilityProvider
- ActionId
- TriggerEvent
- CoachState
- InferenceReport
- MainActivity.kt
- CuesTokens.kt
- ReviewScreenV2.kt
- GrammarParser
- InsightsTest
- SessionEngine
- DeviceDiagnosticsRepository
- Badges.kt
- CueCard.kt
- NowScreen.kt
- SessionState
- DESIGN_SYSTEM.md
- TriggerKit
- SignalRegistry
- UtilityId
- InsightsMain.kt
- AssistantIntent
- Ports.kt
- Finding
- DraftResult
- UtilityBinding
- CueServiceTest
- Cleanup register
- Task List
- PatchBayEditors.kt
- Suggestion
- Routine
- Context.kt
- ReplyCode
- OwnedResource
- NamedContext
- ReceiptKind
- PlaceTransitionKit
- CuesAppFunctionService.kt
- SessionService
- LocalTimeOfDay
- Functional Design Document
- OnDeviceLlmDrafter.kt
- AudioOutputAdapter.kt
- README.md
- ContextValue
- KineticButton
- SimMain.kt
- Session order
- Receipt.kt
- SkipFamily
- PatchSentenceTest
- EvaluatorTest
- DoKind
- IfKind
- InContextKit
- CuesApplication
- MainActivity
- Main.kt
- Normalizer
- Insights.kt
- EventKind
- Place
- WifiConnectionKit
- RoutineStatus
- Contexts
- JsonFileStoreTest
- SarvamClient
- Sprints 4 and 5: The cue meets the phone, then earns its claims
- AskScreen.kt
- ForecastItem
- WifiAdapter.kt
- ActionArgs
- CleanupObligation
- forecastToday
- TimetableExtractor.kt
- LedgerEvent
- SignalKitTest
- BatteryThresholdKit
- NormalizerTest
- ReceiptRecordTest
- UtilityRestoreTest
- Platform API verification — 24 September 2026
- Capability
- Product Requirements Specification
- FactKind
- UnknownRemedyTest
- ReviewCopyLinesTest
- dev
- AssistantIntent.kt
- PatchBayScreen.kt
- test_sarvam.py
- PairedDevice
- ConditionKit
- Turn
- RehearsalTest
- RearmPolicy
- TimeAdapter
- ExportImport.kt
- RefineOperation
- Cues
- Phase 1 idea submission
- Cues
- Cues Android development
- Routine.kt
- InsightsScreen.kt
- JsonFileStore.kt
- AndroidActionExecutor.kt
- .sampleExport
- OnImageSavedCallback
- QrCode.kt
- OnImageSavedCallback
- BroadcastReceiver
- AndroidCapabilityProvider
- MonitoringRepository
- NavBars.kt
- IntentRouterTest
- Working in this repository
- Working in this repository
- CalendarKitTest
- Demo script (Task 20)
- GateReadoutTest
- Conversation.kt
- ReplySource
- SuggestionKind
- BluetoothReceiver.kt
- Release gate: verification and disclosure (Task 20)
- CueTileService.kt
- CuesMotion.kt
- ReplySpeaker
- Working in this repository
- TimetableOcr.kt
- ScreenTile
- BluetoothCoverage
- ForecastStatus
- iQOO device verification matrix (Task 19)
- Permissions
- Sprint 3: Offline authoring, review and approval
- Sprint 6: Contextual, declared
- StopSessionReceiver.kt
- Developer setup
- Theme.kt
- ActionExecutor
- DetectorsTest
- OriginOS 7 experience pass
- Building during Red Light
- Cues Brain execution checklist
- DragAndDrop.kt
- InsightsBenchmarkTest
- gradlew

## God Nodes (most connected - your core abstractions)
1. `Routine` - 162 edges
2. `JsonFileStore` - 127 edges
3. `Trigger` - 121 edges
4. `Condition` - 117 edges
5. `TriggerEvent` - 90 edges
6. `CueService` - 87 edges
7. `Session` - 81 edges
8. `GrammarParser` - 80 edges
9. `ReasonCode` - 75 edges
10. `Reason` - 50 edges

## Surprising Connections (you probably didn't know these)
- `CuesApplication` --calls--> `CueService`  [EXTRACTED]
  app/src/main/kotlin/com/cues/app/CuesApplication.kt → core/src/main/kotlin/com/cues/core/CueService.kt
- `CuesApplication` --calls--> `DifferentialDrafter`  [EXTRACTED]
  app/src/main/kotlin/com/cues/app/CuesApplication.kt → core/src/main/kotlin/com/cues/core/drafting/DifferentialDrafter.kt
- `CuesApplication` --calls--> `GrammarParser`  [EXTRACTED]
  app/src/main/kotlin/com/cues/app/CuesApplication.kt → core/src/main/kotlin/com/cues/core/drafting/GrammarParser.kt
- `CuesApplication` --calls--> `AdapterSupervisor`  [EXTRACTED]
  app/src/main/kotlin/com/cues/app/CuesApplication.kt → core/src/main/kotlin/com/cues/core/signals/AdapterSupervisor.kt
- `CuesApplication` --calls--> `JsonFileStore`  [EXTRACTED]
  app/src/main/kotlin/com/cues/app/CuesApplication.kt → core/src/main/kotlin/com/cues/core/store/JsonFileStore.kt

## Import Cycles
- None detected.

## Communities (172 total, 31 thin omitted)

### Community 0 - "Trigger"
Cohesion: 0.06
Nodes (15): AudioOutput, BluetoothConnection, Charging, Manual, PlaceTransition, Trigger, WifiConnection, BluetoothKit (+7 more)

### Community 1 - "ListenerHealth"
Cohesion: 0.14
Nodes (7): ListenerHealth, SignalAdapter, AdapterSupervisor, FakeSignalAdapter, AdapterSupervisorTest, FakeAdapter, com

### Community 2 - "ReasonCode"
Cohesion: 0.04
Nodes (54): ReasonCode, AUDIO_KIND_MISMATCH, AUDIO_OUTPUT_ACTIVE, AUDIO_OUTPUT_CHANGED, AUDIO_OUTPUT_INACTIVE, AUDIO_OUTPUT_UNKNOWN, BATTERY_AS_REQUIRED, BATTERY_NOT_AS_REQUIRED (+46 more)

### Community 3 - "PatchSentence"
Cohesion: 0.19
Nodes (11): Inexpressible, KClass, No, PatchResult, PatchSelection, PatchSentence, Phrase, Sentence (+3 more)

### Community 4 - "JsonFileStore"
Cohesion: 0.09
Nodes (3): JsonFileStore, T, UsageLedgerTest

### Community 5 - "WhenKind"
Cohesion: 0.08
Nodes (20): DoSelection, IfSelection, UntilExtra, UntilExtraKind, AT_TIME, HALF_HOUR, HOUR, WhenKind (+12 more)

### Community 6 - "Condition"
Cohesion: 0.08
Nodes (19): AtPlace, AudioOutputActive, BatteryAtLeast, BatteryBelow, CalendarBusy, CalendarNotBusy, ChargingState, Condition (+11 more)

### Community 7 - "Session"
Cohesion: 0.08
Nodes (5): Session, SessionStore, ScratchStore, ApprovalsTest, InMemorySessionStore

### Community 8 - "RecordingExecutor"
Cohesion: 0.12
Nodes (4): ActionExecutor, RecordingExecutor, ToggleAttention, SessionEngineTest

### Community 9 - "CueService.kt"
Cohesion: 0.07
Nodes (16): Refiner, Result, FloatArray, NeedsClarification, NeedsConfirmation, NotFound, PersonalIndex, ReferenceResolution (+8 more)

### Community 10 - "ActionSpec"
Cohesion: 0.07
Nodes (4): ActionSpec, CleanupPolicy, ValidatorTest, ActionRegistryTest

### Community 11 - "EndCondition"
Cohesion: 0.09
Nodes (22): AtTime, Day, FRI, MON, SAT, SUN, THU, TUE (+14 more)

### Community 12 - "CueService"
Cohesion: 0.12
Nodes (4): CueService, Diagnostics, com, InsightsServiceTest

### Community 13 - "CuesAccessibilityService"
Cohesion: 0.06
Nodes (24): AccessibilityEvent, AccessibilityNodeInfo, AccessibilityService, Blocked, CuesAccessibilityService, StateFlow, MacroRunOutcome, Refused (+16 more)

### Community 14 - "GrammarParserTest"
Cohesion: 0.13
Nodes (4): corpusText(), object@L349, GrammarParserTest, parser()

### Community 15 - "CalendarConditionKit"
Cohesion: 0.19
Nodes (5): CalendarBusyKit, CalendarConditionKit, CalendarNotBusyKit, C, KClass

### Community 16 - "ActionOutcome"
Cohesion: 0.17
Nodes (4): AndroidActionExecutor, ActionExecutor, Uri, ActionOutcome

### Community 18 - "CapabilityProvider"
Cohesion: 0.14
Nodes (10): Conversation, ChatMain, FactReference, CapabilityProvider, ConversationTest, FakeModelDrafter, InferenceReportPlumbingTest, RoutineDrafter (+2 more)

### Community 19 - "ActionId"
Cohesion: 0.07
Nodes (25): FixtureExecutor, ActionExecutor, ActionId, ADD_CALENDAR_EVENT, COMPOSE_MESSAGE, MEDIA_CONTROL, NOTIFY_RESULT, OPEN_APP (+17 more)

### Community 20 - "TriggerEvent"
Cohesion: 0.06
Nodes (31): Severity, ERROR, WARNING, chargingWord(), contains(), Decision, describe(), Evaluator (+23 more)

### Community 21 - "CoachState"
Cohesion: 0.27
Nodes (3): CoachState, CoachStateStore, InMemoryCoachState

### Community 22 - "InferenceReport"
Cohesion: 0.20
Nodes (10): ConsoleHtml, ConsoleMain, CuesExporter, InferenceBackend, CPU, GPU, NPU, InferenceReport (+2 more)

### Community 23 - "MainActivity.kt"
Cohesion: 0.14
Nodes (19): availableSignalsNow(), CuesApp(), friendlyLabel(), com, mapKnown(), navigateToTab(), ReviewFlow(), signalGatesFrom() (+11 more)

### Community 24 - "CuesTokens.kt"
Cohesion: 0.22
Nodes (10): ClauseBlock(), ClauseRow(), ClauseKind, Modifier, CuesColorTokens, CuesPalette, Color, CuesType (+2 more)

### Community 25 - "ReviewScreenV2.kt"
Cohesion: 0.15
Nodes (23): cuesGridBackground(), Color, Modifier, SlabCard(), SlabTier, ONE, THREE, TWO (+15 more)

### Community 26 - "GrammarParser"
Cohesion: 0.17
Nodes (10): main(), object@L9, AmbiguousDevice, GrammarParser, IntRange, RoutineDrafter, ResolvedApp, ResolvedTrigger (+2 more)

### Community 27 - "InsightsTest"
Cohesion: 0.24
Nodes (3): LedgerView, ActionRecord, InsightsTest

### Community 28 - "SessionEngine"
Cohesion: 0.17
Nodes (9): Ended, EngineResult, ExitCancelled, ExitScheduled, Ignored, isLive(), SessionEngine, Skipped (+1 more)

### Community 29 - "DeviceDiagnosticsRepository"
Cohesion: 0.18
Nodes (13): DeviceDiagnostics, DeviceDiagnosticsRepository, RecognitionSupportCallback, ManualObservation, ISSUE_OBSERVED, NO_ISSUE_OBSERVED, NOT_CHECKED, DiagnosticCard() (+5 more)

### Community 30 - "Badges.kt"
Cohesion: 0.14
Nodes (23): ClauseBadge(), clauseColors(), ClauseKind, DO, IF, RESTORE, UNTIL, WHEN (+15 more)

### Community 31 - "CueCard.kt"
Cohesion: 0.21
Nodes (13): CardDecodeResult, CueCard, CueCards, T, Malformed, Missing, MissingEntity, Ok (+5 more)

### Community 32 - "NowScreen.kt"
Cohesion: 0.22
Nodes (21): clickableNoIndicationPublic(), CountdownRing(), EmptyState(), FilterChipRow(), formatCountdown(), androidx, Color, Composable (+13 more)

### Community 33 - "SessionState"
Cohesion: 0.09
Nodes (22): EndReason, COVERAGE_GAP, DEADLINE_REACHED, MANUAL_STOP, RECONCILED_EXPIRED, ROUTINE_PAUSED, START_FAILED, TRIGGER_REVERSED (+14 more)

### Community 34 - "DESIGN_SYSTEM.md"
Cohesion: 0.05
Nodes (37): 1. Monospaced Clause Badges, 2. Routine Slab Cards, 3. Buttons & Mechanical Triggers, 4. Origin Dynamic Islands & Capsules, 5. Input Fields & Syntax Builders, 6. Lists & Log Feeds, Accent Glow & Neon Halos, Brand & Style (+29 more)

### Community 35 - "TriggerKit"
Cohesion: 0.17
Nodes (6): EndKit, com, E, KClass, T, TriggerKit

### Community 36 - "SignalRegistry"
Cohesion: 0.19
Nodes (5): C, com, E, T, SignalRegistry

### Community 37 - "UtilityId"
Cohesion: 0.10
Nodes (15): UtilityBindingScreen(), UtilityId, EYE_PROTECTION, GAME_MODE, ULTRA_SAVER, UtilityState, OFF, ON (+7 more)

### Community 38 - "InsightsMain.kt"
Cohesion: 0.10
Nodes (20): Approvals, ArmResult, Blocked, DeleteResult, Invalid, T, MissingCapabilities, NotApproved (+12 more)

### Community 39 - "AssistantIntent"
Cohesion: 0.20
Nodes (10): AssistantIntent, Capabilities, Control, Create, Explain, Forecast, ListCues, Refine (+2 more)

### Community 40 - "Ports.kt"
Cohesion: 0.14
Nodes (11): describe(), DetailRow(), RoutineDetailScreen(), TemporaryPatchCard(), tomorrowMorningMillis(), Patch, PatchKind, SkipOccurrence (+3 more)

### Community 41 - "Finding"
Cohesion: 0.24
Nodes (4): Finding, ValidationResult, Validator, SignalText

### Community 42 - "DraftResult"
Cohesion: 0.06
Nodes (30): ClauseAccounting, IntRange, CompositeDrafter, RoutineDrafter, DifferentialDrafter, RoutineDrafter, ClauseSpan, Drafted (+22 more)

### Community 44 - "CueServiceTest"
Cohesion: 0.17
Nodes (3): CueServiceTest, RoutineDrafter, RoutineDrafter

### Community 45 - "Cleanup register"
Cohesion: 0.06
Nodes (36): CL-01 — Termux on-device build route, CL-02 — Android runtime written against real APIs, verified on none of them, CL-03 — Two drafting paths at equal weight, CL-04 — Unverified Android dependency versions, CL-05 — Pre-event code and what the documents claim, CL-06 — Device and OS assumptions, CL-07 — Named Wi-Fi requires a device decision, CL-08 — Pre-event signal framework disclosure (+28 more)

### Community 46 - "Task List"
Cohesion: 0.06
Nodes (31): Architecture Decisions, Checkpoint: Brain and Camera, Checkpoint: Bridge, Checkpoint: Coach, Checkpoint: Conversation, Checkpoint: Ecosystem, Checkpoint: Typed Actions, Definition of Complete (+23 more)

### Community 47 - "PatchBayEditors.kt"
Cohesion: 0.26
Nodes (17): DayToggleRow(), EnumChipRow(), androidx, IntRange, Modifier, T, NumberStepper(), PatchTextField() (+9 more)

### Community 48 - "Suggestion"
Cohesion: 0.41
Nodes (7): Detection, Detectors, EvidenceLine, NoPattern, NotEnoughData, Suggest, Suggestion

### Community 49 - "Routine"
Cohesion: 0.15
Nodes (7): CueCardShareScreen(), Routine, MutableClock, Rehearsal, RehearsalRow, PermissionCheckCopy, ReviewCopy

### Community 50 - "Context.kt"
Cohesion: 0.17
Nodes (4): android, ByteArray, LocalSpeechInput, RecognitionListener

### Community 51 - "ReplyCode"
Cohesion: 0.11
Nodes (16): ReplyCode, CAPABILITIES, CONFIRM_COMMAND, CUES_LIST, DRAFT_FAILED, DRAFT_READY, DRAFT_REFINED, EXPLANATION (+8 more)

### Community 52 - "OwnedResource"
Cohesion: 0.14
Nodes (8): OwnedResource, DND_CONTRIBUTION, FOCUS_TIMER, PINNED_NOTE, RINGER_MODE, UTILITY_CONTRIBUTION, ActionExecutor, ActionExecutor

### Community 53 - "NamedContext"
Cohesion: 0.16
Nodes (4): CliContexts, NamedContext, NamedContextStore, EmptyContexts

### Community 54 - "ReceiptKind"
Cohesion: 0.14
Nodes (13): EventProvenance, MANUAL, PHYSICAL, REHEARSAL, ReceiptKind, ENDED, EXIT_CANCELLED, EXIT_SCHEDULED (+5 more)

### Community 56 - "CuesAppFunctionService.kt"
Cohesion: 0.14
Nodes (14): BaseCuesAppFunctionService, CurrentContextResult, DraftCueParams, DraftCueResult, ForecastItemResult, ForecastTodayResult, StartCueParams, StartCueResult (+6 more)

### Community 57 - "SessionService"
Cohesion: 0.22
Nodes (6): android, Intent, PendingIntent, SessionService, IBinder, Service

### Community 58 - "LocalTimeOfDay"
Cohesion: 0.19
Nodes (3): Comparable, LocalTimeOfDay, SnapshotBuilderTest

### Community 59 - "Functional Design Document"
Cohesion: 0.09
Nodes (22): Action registry, Architecture, Cues Brain additions (Sprint 7), DND ownership, Event and context adapters, Expected state at a deadline (stretch), Functional Design Document, Implementation order (+14 more)

### Community 60 - "OnDeviceLlmDrafter.kt"
Cohesion: 0.23
Nodes (7): LiteRtLmSession, FakeLlmSession, InferenceOutput, RoutineDrafter, LlmSession, OnDeviceLlmDrafter, UnconfiguredLlmSession

### Community 61 - "AudioOutputAdapter.kt"
Cohesion: 0.20
Nodes (9): AudioOutputAdapter, AudioDeviceCallback, Context, AudioDeviceInfo, AudioManager, AudioKind, ANY, BLUETOOTH (+1 more)

### Community 63 - "ContextValue"
Cohesion: 0.05
Nodes (41): CalendarReadings, Context, Context, Readings, Context, WifiReadings, SnapshotBuilder, GrantCapability (+33 more)

### Community 64 - "KineticButton"
Cohesion: 0.28
Nodes (9): clickableNoIndication(), GhostButton(), HaltButton(), KineticButton(), KineticSwitch(), Modifier, CuesHaptics, rememberCuesHaptics() (+1 more)

### Community 65 - "SimMain.kt"
Cohesion: 0.21
Nodes (10): baseMillisAt(), connect(), disconnect(), heading(), ActionExecutor, LoggingExecutor, main(), report() (+2 more)

### Community 66 - "Session order"
Cohesion: 0.12
Nodes (17): 0. Connect and baseline — ~10 min, 10. M9 — QR between devices — ~10 min, needs the second phone, 1. Hero loop gate — ~30–45 min, do this before anything else, 2. M8 — screen-off pending action — ~10 min, same setup as above, 3. M1 — package discovery — ~5 min, cheap, read-only, 4. M7 — AppFunctions + Jovi handoff — ~15 min, do early while there's time to fix, 5. M2 — NPU backend + drafting latency — ~20 min if a model is staged, else ~5 min to confirm the fallback, 6. M6 — accessibility utility binding — ~15 min (+9 more)

### Community 67 - "Receipt.kt"
Cohesion: 0.30
Nodes (6): friendly(), Receipt, Receipts, timerMinutes(), triggerNoun(), unownedCaveat()

### Community 68 - "SkipFamily"
Cohesion: 0.12
Nodes (16): SkipFamily, ALREADY_RUNNING, AUDIO, BATTERY, CALENDAR, CHARGING, CONTEXT, DAY (+8 more)

### Community 71 - "DoKind"
Cohesion: 0.17
Nodes (12): DoKind, ALARM, CALENDAR_EVENT, COMPOSE_MESSAGE, FOCUS_TIMER, MEDIA, NOTIFY, OPEN_APP (+4 more)

### Community 72 - "IfKind"
Cohesion: 0.17
Nodes (12): IfKind, AT_PLACE, AUDIO_ACTIVE, BATTERY_AT_LEAST, BATTERY_BELOW, CALENDAR_BUSY, CALENDAR_FREE, CHARGING (+4 more)

### Community 74 - "CuesApplication"
Cohesion: 0.12
Nodes (13): CuesApplication, StateFlow, StoreGeneration, FakeSarvamChatSession, RoutineDrafter, SarvamChatDrafter, SarvamChatSession, SarvamHttpChatSession (+5 more)

### Community 75 - "MainActivity"
Cohesion: 0.22
Nodes (5): android, MainActivity, AppShortcuts, Context, CuesTheme()

### Community 76 - "Main.kt"
Cohesion: 0.27
Nodes (10): bold(), dim(), FixturesForCli, main(), printCorpus(), object@L148, printRehearsal(), printReview() (+2 more)

### Community 77 - "Normalizer"
Cohesion: 0.11
Nodes (6): Normalizer, ClauseKind, FILLER, MAPPED, UNACCOUNTED, TemplatesTest

### Community 78 - "Insights.kt"
Cohesion: 0.12
Nodes (17): BlockCause, BlockedGroup, CleanupLedger, Coverage, ExpiredWaitingForYou, InsightCounts, Insights, NeedsCapabilities (+9 more)

### Community 79 - "EventKind"
Cohesion: 0.11
Nodes (18): Context, Intent, TimeTriggerReceiver, EventKind, AUDIO_OUTPUT_ADDED, AUDIO_OUTPUT_REMOVED, BLUETOOTH_CONNECTED, BLUETOOTH_DISCONNECTED (+10 more)

### Community 80 - "Place"
Cohesion: 0.20
Nodes (3): Place, PlaceStore, EmptyPlaces

### Community 81 - "WifiConnectionKit"
Cohesion: 0.13
Nodes (5): describe(), KClass, semanticForm(), WifiConnectedKit, WifiConnectionKit

### Community 82 - "RoutineStatus"
Cohesion: 0.17
Nodes (8): RoutineStatus, ARMED, DISABLED, DRAFT, INVALID, PAUSED, REVIEWABLE, ClauseAccountingTest

### Community 83 - "Contexts"
Cohesion: 0.16
Nodes (3): Contexts, ContextualKitsTest, Places

### Community 85 - "SarvamClient"
Cohesion: 0.06
Nodes (25): AppBakeOff, object@L27, ByteArray, SarvamClient, Transcript, Translation, ByteArray, SarvamReadback (+17 more)

### Community 86 - "Sprints 4 and 5: The cue meets the phone, then earns its claims"
Cohesion: 0.17
Nodes (12): Exit criteria (end of event), Gate at hour 23, Not in these sprints, Pre-event Sprint 3.5: signal framework (Sep 23–25), Proposed requirement additions, R&D register, Sprint 4 test cases (on the loaner, not in :core), Sprint 4: Unattended proof (hours 16–23) (+4 more)

### Community 87 - "AskScreen.kt"
Cohesion: 0.18
Nodes (18): InstalledApp, installedApps(), Context, rankInstalledApps(), AdapterStatus, AskScreen(), Modifier, AssistantHistory() (+10 more)

### Community 88 - "ForecastItem"
Cohesion: 0.24
Nodes (12): Modifier, StatusChip(), Tone, AMBER, GO, STOP, toTone(), ForecastCard() (+4 more)

### Community 89 - "WifiAdapter.kt"
Cohesion: 0.29
Nodes (4): WifiAdapter, NetworkCallback, ConnectivityManager, Network

### Community 90 - "ActionArgs"
Cohesion: 0.12
Nodes (15): ActionArgs, Alarm, CalendarEvent, ComposeMessage, Dnd, FocusTimer, MediaControl, None (+7 more)

### Community 92 - "forecastToday"
Cohesion: 0.25
Nodes (8): Context, LiveSnapshot, Context, NowNextWidget, AppWidgetManager, AppWidgetProvider, forecastToday(), IntArray

### Community 93 - "TimetableExtractor.kt"
Cohesion: 0.28
Nodes (6): ImportEntryCard(), ImportReviewScreen(), fullName(), TimetableEntry, TimetableExtractionResult, TimetableExtractor

### Community 94 - "LedgerEvent"
Cohesion: 0.15
Nodes (10): LearningSettings(), CoachMain, ActionBlocked, CoverageGap, LedgerEvent, ManualStart, PatchCreated, SessionEnded (+2 more)

### Community 96 - "BatteryThresholdKit"
Cohesion: 0.13
Nodes (4): BatteryAtLeastKit, BatteryBelowKit, BatteryThresholdKit, C

### Community 99 - "UtilityRestoreTest"
Cohesion: 0.27
Nodes (3): ActionExecutor, StatelessUtilityExecutor, UtilityRestoreTest

### Community 100 - "Platform API verification — 24 September 2026"
Cohesion: 0.18
Nodes (10): AppFunctions, Camera and offline text recognition, Cue Cards and QR, Dependency policy, Desk Bridge and the Cue Console, LiteRT-LM and Qualcomm NPU, Live Updates, Platform API verification — 24 September 2026 (+2 more)

### Community 101 - "Capability"
Cohesion: 0.11
Nodes (23): ActionRiskRow(), grantLabel(), com, Intent, PermissionCheckRow(), RehearsalRowView(), ReviewRow(), ReviewScreen() (+15 more)

### Community 102 - "Product Requirements Specification"
Cohesion: 0.20
Nodes (10): Core journey, Principles, Product and user, Product Requirements Specification, Proposed evaluation, Requirements, Review example, Risks and scope decisions (+2 more)

### Community 103 - "FactKind"
Cohesion: 0.17
Nodes (11): FactKind, DATE, DAYS, DEVICE_ALIAS, PLACE_ALIAS, TEXT, FactSource, CAMERA (+3 more)

### Community 106 - "dev"
Cohesion: 0.36
Nodes (11): dev script, filt(), info(), need_adb(), need_sdk(), ok(), red(), sdk_dir() (+3 more)

### Community 107 - "AssistantIntent.kt"
Cohesion: 0.22
Nodes (8): ControlKind, PAUSE, RESUME, SKIP_TODAY, STOP, UnsupportedRoute, NONE, SYSTEM_AGENT

### Community 108 - "PatchBayScreen.kt"
Cohesion: 0.13
Nodes (19): Chip(), ChipGrid(), androidx, com, Modifier, T, PatchBayScreen(), Text2() (+11 more)

### Community 110 - "PairedDevice"
Cohesion: 0.15
Nodes (3): CardMain, PairedDevice, CueCardTest

### Community 113 - "RehearsalTest"
Cohesion: 0.12
Nodes (3): ActionExecutor, RehearsalTest, ActionExecutor

### Community 116 - "ExportImport.kt"
Cohesion: 0.46
Nodes (4): ExportImport, Context, Intent, Uri

### Community 117 - "RefineOperation"
Cohesion: 0.20
Nodes (10): AddAction, AddCondition, AddDays, RefineOperation, RemoveAction, RemoveCondition, RemoveDays, ReplaceTrigger (+2 more)

### Community 118 - "Cues"
Cohesion: 0.29
Nodes (7): Build boundary, Creative extensions, Cues, Final problem statement, Proposed differentiation, Relation to contextual assistants, Success

### Community 119 - "Phase 1 idea submission"
Cohesion: 0.29
Nodes (7): Attachments and remaining form fields, Demo preparation, Description, Idea title, Phase 1 idea submission, Short version, What makes the entry stand out?

### Community 120 - "Cues"
Cohesion: 0.29
Nodes (7): Building, Claim discipline, Cues, Design principles, Documents, License, Status

### Community 121 - "Cues Android development"
Cohesion: 0.33
Nodes (6): Architecture boundary, Compose interaction quality, Cues Android development, Device truth, Required checks, Safety invariants

### Community 122 - "Routine.kt"
Cohesion: 0.10
Nodes (20): Any, AudioTransition, ADDED, REMOVED, MediaCommand, NEXT, PAUSE, PLAY (+12 more)

### Community 123 - "InsightsScreen.kt"
Cohesion: 0.29
Nodes (12): clickable2(), InsightsScreen(), items2(), KpiTile(), androidx, Modifier, T, padding2() (+4 more)

### Community 124 - "JsonFileStore.kt"
Cohesion: 0.13
Nodes (8): ObservableStore, UsageLedger, Fact, FactStore, com, MacroStore, ReceiptLog, ReceiptSink

### Community 125 - "AndroidActionExecutor.kt"
Cohesion: 0.14
Nodes (12): AlarmManager, PendingIntent, ActionState, BLOCKED, COMPENSATED, COMPENSATION_FAILED, FAILED, IN_PROGRESS (+4 more)

### Community 127 - "OnImageSavedCallback"
Cohesion: 0.32
Nodes (5): CueCardScanScreen(), OnImageSavedCallback, ImageCapture, ImageCaptureException, OnImageSavedCallback

### Community 128 - "QrCode.kt"
Cohesion: 0.36
Nodes (5): CueCardScanner, Context, Uri, QrCode, Bitmap

### Community 129 - "OnImageSavedCallback"
Cohesion: 0.32
Nodes (5): ImageCapture, ImageCaptureException, OnImageSavedCallback, TimetableCaptureScreen(), OnImageSavedCallback

### Community 130 - "BroadcastReceiver"
Cohesion: 0.14
Nodes (14): BroadcastReceiver, android, BootReceiver, Context, Intent, DeadlineReceiver, Context, Intent (+6 more)

### Community 133 - "NavBars.kt"
Cohesion: 0.46
Nodes (7): CuesBottomNav(), CuesTopBar(), Composable, ImageVector, Modifier, TabItem(), TabSpec

### Community 135 - "Working in this repository"
Cohesion: 0.33
Nodes (6): Claims, Commands, graphify, Rules that are not style preferences, Two modules, and the line between them, Working in this repository

### Community 136 - "Working in this repository"
Cohesion: 0.33
Nodes (6): Claims, Commands, graphify, Rules that are not style preferences, Two modules, and the line between them, Working in this repository

### Community 138 - "Demo script (Task 20)"
Cohesion: 0.33
Nodes (6): Backup evidence, Before the demo (setup, not part of the story), Cut from the live demo until their rows pass, Demo script (Task 20), Ground rules, Live script (phone)

### Community 140 - "Conversation.kt"
Cohesion: 0.15
Nodes (11): Applied, Choice, CommandResult, Confirm, Handoff, MissingTarget, PendingCommand, Rejected (+3 more)

### Community 141 - "ReplySource"
Cohesion: 0.25
Nodes (7): ReplySource, FORECAST, ON_DEVICE_LLM, PARSER, RECEIPTS, ROUTINE_STORE, SARVAM_CLOUD

### Community 142 - "SuggestionKind"
Cohesion: 0.29
Nodes (7): SuggestionKind, ADD_SIGNAL_CUE, ADD_TIME_TRIGGER, CHECK_OR_REMOVE_ACTION, FIX_PERMISSION, REMOVE_DAY, SHORTER_DURATION

### Community 143 - "BluetoothReceiver.kt"
Cohesion: 0.53
Nodes (3): BluetoothReceiver, Context, Intent

### Community 144 - "Release gate: verification and disclosure (Task 20)"
Cohesion: 0.33
Nodes (6): Device-only items not proven, Fixed in this pass, Release gate: verification and disclosure (Task 20), Software gaps found and left open, What can be said today, What this pass verified, and where

### Community 146 - "CuesMotion.kt"
Cohesion: 0.47
Nodes (4): CuesMotion, Context, rememberReducedMotion(), systemAnimatorScale()

### Community 148 - "Working in this repository"
Cohesion: 0.33
Nodes (6): Claims, Commands, graphify, Rules that are not style preferences, Two modules, and the line between them, Working in this repository

### Community 149 - "TimetableOcr.kt"
Cohesion: 0.60
Nodes (3): Context, Uri, TimetableOcr

### Community 152 - "ForecastStatus"
Cohesion: 0.40
Nodes (5): ForecastStatus, CANNOT_TELL, NOT_TODAY, SKIPPED_BY_PATCH, WILL_ARM

### Community 153 - "iQOO device verification matrix (Task 19)"
Cohesion: 0.40
Nodes (5): Hero loop gate (carried from Sprint 4/5), How to record a result, iQOO device verification matrix (Task 19), Results, Task 19 rows

### Community 154 - "Permissions"
Cohesion: 0.40
Nodes (5): Bound services and special access (not `<uses-permission>`), Declared permissions, Deliberately absent, Package visibility, Permissions

### Community 155 - "Sprint 3: Offline authoring, review and approval"
Cohesion: 0.40
Nodes (5): Exit criteria, Not in this sprint, Sprint 3: Offline authoring, review and approval, Test cases, What to cut, in order

### Community 156 - "Sprint 6: Contextual, declared"
Cohesion: 0.40
Nodes (4): Cut order, Enhancement pass (24 Sep 2026), Risk register, Sprint 6: Contextual, declared

### Community 157 - "StopSessionReceiver.kt"
Cohesion: 0.60
Nodes (3): Context, Intent, StopSessionReceiver

### Community 158 - "Developer setup"
Cohesion: 0.50
Nodes (3): Agent skills, Developer setup, Toolchain

### Community 159 - "Theme.kt"
Cohesion: 0.50
Nodes (3): CuesColors, CuesSemanticColors, Color

### Community 160 - "ActionExecutor"
Cohesion: 0.50
Nodes (3): ActionExecutor, ActionExecutor, com

### Community 162 - "OriginOS 7 experience pass"
Cohesion: 0.50
Nodes (3): Delivery checklist, Design contract, OriginOS 7 experience pass

### Community 163 - "Building during Red Light"
Cohesion: 0.50
Nodes (4): Building during Red Light, Route A — Office Kit drives the laptop (primary), Route B — Termux on the phone (fallback, unproven), What does not depend on either route

### Community 164 - "Cues Brain execution checklist"
Cohesion: 0.50
Nodes (3): Checkpoint — Typed actions, detail (24 Sep 2026), Cues Brain execution checklist, Standing verification after each applicable slice

### Community 165 - "DragAndDrop.kt"
Cohesion: 0.83
Nodes (3): dragAndDropTextSource(), Modifier, View

### Community 173 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

## Knowledge Gaps
- **654 isolated node(s):** `object@L27`, `Unavailable`, `Succeeded`, `NOT_CHECKED`, `NO_ISSUE_OBSERVED` (+649 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 944 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **31 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `Routine` connect `Routine` to `ListenerHealth`, `JsonFileStore`, `RecordingExecutor`, `CueService.kt`, `ActionSpec`, `Conversation.kt`, `CueService`, `TriggerEvent`, `InferenceReport`, `MainActivity.kt`, `CuesTokens.kt`, `ReviewScreenV2.kt`, `GrammarParser`, `InsightsTest`, `SessionEngine`, `CueCard.kt`, `NowScreen.kt`, `SignalRegistry`, `InsightsMain.kt`, `Ports.kt`, `Finding`, `DraftResult`, `Suggestion`, `ReceiptKind`, `AudioOutputAdapter.kt`, `SimMain.kt`, `Receipt.kt`, `MainActivity`, `Main.kt`, `Normalizer`, `Insights.kt`, `AskScreen.kt`, `WifiAdapter.kt`, `forecastToday`, `SignalKitTest`, `UtilityRestoreTest`, `Capability`, `TimeAdapter`, `Routine.kt`, `JsonFileStore.kt`, `AndroidActionExecutor.kt`?**
  _High betweenness centrality (0.136) - this node is a cross-community bridge._
- **Why does `JsonFileStore` connect `JsonFileStore` to `Session`, `CueService.kt`, `CueService`, `CuesAccessibilityService`, `CapabilityProvider`, `CoachState`, `InsightsMain.kt`, `Ports.kt`, `UtilityBinding`, `CueServiceTest`, `Routine`, `NamedContext`, `SimMain.kt`, `CuesApplication`, `Place`, `JsonFileStoreTest`, `CleanupObligation`, `ReceiptRecordTest`, `UtilityRestoreTest`, `JsonFileStore.kt`?**
  _High betweenness centrality (0.055) - this node is a cross-community bridge._
- **Why does `PairedDevice` connect `PairedDevice` to `SimMain.kt`, `PatchSentence`, `WhenKind`, `InsightsMain.kt`, `CuesApplication`, `DraftResult`, `PatchBayScreen.kt`, `Main.kt`, `Normalizer`, `PatchBayEditors.kt`, `GrammarParserTest`, `CapabilityProvider`, `RoutineStatus`, `MainActivity.kt`, `SarvamClient`, `InferenceReport`, `AskScreen.kt`, `CueCard.kt`?**
  _High betweenness centrality (0.051) - this node is a cross-community bridge._
- **Are the 2 inferred relationships involving `Routine` (e.g. with `.parse()` and `.heroRoutine()`) actually correct?**
  _`Routine` has 2 INFERRED edges - model-reasoned connections that need verification._
- **Are the 15 inferred relationships involving `TriggerEvent` (e.g. with `connect()` and `run()`) actually correct?**
  _`TriggerEvent` has 15 INFERRED edges - model-reasoned connections that need verification._
- **What connects `object@L27`, `Unavailable`, `Succeeded` to the rest of the system?**
  _654 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Trigger` be split into smaller, more focused modules?**
  _Cohesion score 0.0632996632996633 - nodes in this community are weakly interconnected._