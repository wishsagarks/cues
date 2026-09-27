# Graph Report - iqoo+  (2026-09-27)

## Corpus Check
- 296 files · ~329,985 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 3704 nodes · 8510 edges · 218 communities (170 shown, 43 thin omitted)
- Extraction: 97% EXTRACTED · 3% INFERRED · 0% AMBIGUOUS · INFERRED: 238 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `b373e329`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- Trigger
- Ports.kt
- ReasonCode
- PatchSentence
- JsonFileStore
- DoKind
- Condition
- Session
- RecordingExecutor
- Routine
- ActionSpec
- EndCondition
- PlaceWatcherService
- CuesAccessibilityService
- GrammarParserTest
- Capability
- ActionOutcome
- TimetableExtractorTest
- CapabilityProvider
- ActionId
- OwnedResource
- DetectorsTest
- InferenceReport
- MainActivity.kt
- ClauseBlock.kt
- SlabCard
- GrammarParser
- InsightsTest
- CuesApplication
- DiagnosticsScreen.kt
- ReviewScreenV2.kt
- CueCard.kt
- NowScreen.kt
- SessionState
- DESIGN_SYSTEM.md
- EndKit
- SignalRegistry
- CuesApplication.kt
- ReviewScreen.kt
- CueService.kt
- RoutineDetailScreen
- Finding
- RoutineDrafter.kt
- Corpus
- CueServiceTest
- Cleanup register
- Task List
- PatchBayEditors.kt
- Suggestion
- Rehearsal
- RecognitionListener
- ReplyCode
- ActionExecutor
- NamedContext
- ReceiptRecord
- PlaceSupport
- CuesAppFunctionService.kt
- ActionArgs
- LocalTimeOfDay
- Functional Design Document
- InferenceOutput
- AudioOutputAdapter.kt
- README.md
- ContextValue
- KineticButton
- SimMain.kt
- Session order
- Receipt.kt
- SkipFamily
- ModelNote
- EvaluatorTest
- RecurrenceUnit
- IfKind
- DifferentialDrafter
- NavBars.kt
- MainActivity
- DeviceDiagnosticsRepository
- AndroidActionExecutor.kt
- Insights.kt
- EventKind
- Place
- Truth
- DraftResult
- DraftTrace
- JsonFileStoreTest
- SarvamClient
- Sprints 4 and 5: The cue meets the phone, then earns its claims
- AskScreen.kt
- AdapterSupervisor
- WifiAdapter.kt
- CueService
- CleanupObligation
- MonitoringRepository
- TimetableExtractor.kt
- RoutineStore
- SignalKitTest
- Refiner
- NormalizerTest
- WifiKits.kt
- CuesGemmaProvider
- Platform API verification — 24 September 2026
- ShoppingItem
- Product Requirements Specification
- FactKind
- UnknownRemedyTest
- Normalizer
- dev
- CompositeDrafter
- ExternalGemmaGate
- test_sarvam.py
- RoutineStatus
- ConditionKit
- DraftSourceId
- ActionExecutor
- ReviewCopyTest
- Context.kt
- .run
- RefineOperation
- Cues
- Phase 1 idea submission
- Cues
- Cues Android development
- Routine.kt
- InsightsScreen.kt
- JsonFileStore.kt
- SessionService
- ModelProvisionState
- OnImageSavedCallback
- QrCode.kt
- OnImageSavedCallback
- BroadcastReceiver
- Fact
- InferenceBackend
- PlanDraft
- IntentRouterTest
- Working in this repository
- Working in this repository
- SnapshotBuilder
- Demo script (Task 20)
- ArmResult
- CommandResult
- InsightsMain.kt
- SuggestionKind
- PairedDevice
- Release gate: verification and disclosure (Task 20)
- ActionRegistryTest
- CuesMotion.kt
- ReplySpeaker
- Working in this repository
- TimetableOcr.kt
- ModelReadouts.kt
- BluetoothCoverage
- ReferenceResolution
- iQOO device verification matrix (Task 19)
- Permissions
- Sprint 3: Offline authoring, review and approval
- Sprint 6: Contextual, declared
- AmbientBandClassifier
- Developer setup
- Theme.kt
- ActionExecutor
- Main.kt
- OriginOS 7 experience pass
- ApprovalsTest
- Cues Brain execution checklist
- DragAndDrop.kt
- Measured results
- Day
- LedgerEvent
- WhenKind
- SuggestionNarrator
- Fixtures
- ReviewCopyLinesTest
- gradlew
- ContextSource
- GemmaCallProtocol.kt
- UnknownReason
- Sprint 9 — Visible Intelligence, Sarvam, and Declared Sensing
- UiMacro
- forecastToday
- TriggerEvent
- NowNextWidget.kt
- ForecastStatus
- UtilityRestoreTest
- AssistantIntent.kt
- InMemoryRoutines
- build_cues_hyperdeck.mjs
- .current
- AndroidCapabilityProvider
- GraceScheduler
- InferenceReportPlumbingTest.kt
- HubPolicyTest
- FaceDownClassifierTest
- ModelCatalog
- RecurringReminderAdapter
- ActionRisk
- AtTimeEndKit
- CueTileService.kt
- LlmMain.kt
- deck-engine.js
- Verification walkthrough: place arrival, WhatsApp, regional language
- OnDeviceLlmDrafter
- BootReceiver.kt
- GraceReceiver.kt
- ModelCatalogTest
- PlaceReadings.kt
- RecurringTriggerReceiver.kt
- TimeTriggerReceiver.kt
- AppShortcuts.kt
- TranslationPrompt
- TranslationPromptTest
- UsageLedgerTest.kt
- Building during Red Light

## God Nodes (most connected - your core abstractions)
1. `Routine` - 169 edges
2. `Trigger` - 151 edges
3. `JsonFileStore` - 135 edges
4. `Condition` - 117 edges
5. `TriggerEvent` - 105 edges
6. `CueService` - 96 edges
7. `GrammarParser` - 86 edges
8. `ReasonCode` - 85 edges
9. `Session` - 81 edges
10. `CuesApplication` - 59 edges

## Surprising Connections (you probably didn't know these)
- `CuesApplication` --calls--> `OnDeviceRefinePhraser`  [EXTRACTED]
  app/src/main/kotlin/com/cues/app/CuesApplication.kt → core/src/main/kotlin/com/cues/core/assistant/OnDeviceRefinePhraser.kt
- `CuesApplication` --calls--> `CueService`  [EXTRACTED]
  app/src/main/kotlin/com/cues/app/CuesApplication.kt → core/src/main/kotlin/com/cues/core/CueService.kt
- `CuesApplication` --calls--> `DifferentialDrafter`  [EXTRACTED]
  app/src/main/kotlin/com/cues/app/CuesApplication.kt → core/src/main/kotlin/com/cues/core/drafting/DifferentialDrafter.kt
- `CuesApplication` --calls--> `GrammarParser`  [EXTRACTED]
  app/src/main/kotlin/com/cues/app/CuesApplication.kt → core/src/main/kotlin/com/cues/core/drafting/GrammarParser.kt
- `CuesApplication` --calls--> `OnDeviceLlmDrafter`  [EXTRACTED]
  app/src/main/kotlin/com/cues/app/CuesApplication.kt → core/src/main/kotlin/com/cues/core/drafting/OnDeviceLlmDrafter.kt

## Import Cycles
- None detected.

## Communities (218 total, 43 thin omitted)

### Community 0 - "Trigger"
Cohesion: 0.04
Nodes (20): AudioOutput, BluetoothConnection, Charging, Manual, MissedCall, PlaceTransition, RecurringInterval, Trigger (+12 more)

### Community 1 - "Ports.kt"
Cohesion: 0.09
Nodes (11): StateFlow, ObservableStore, StoreGeneration, InferenceLedger, Patch, UtilityBinding, com, PatchStore (+3 more)

### Community 2 - "ReasonCode"
Cohesion: 0.03
Nodes (60): ReasonCode, AUDIO_KIND_MISMATCH, AUDIO_OUTPUT_ACTIVE, AUDIO_OUTPUT_CHANGED, AUDIO_OUTPUT_INACTIVE, AUDIO_OUTPUT_UNKNOWN, BATTERY_AS_REQUIRED, BATTERY_NOT_AS_REQUIRED (+52 more)

### Community 3 - "PatchSentence"
Cohesion: 0.10
Nodes (16): ClauseKind, FILLER, MAPPED, UNACCOUNTED, Inexpressible, KClass, No, PatchResult (+8 more)

### Community 5 - "DoKind"
Cohesion: 0.08
Nodes (21): DoKind, ALARM, CALENDAR_EVENT, COMPOSE_MESSAGE, COMPOSE_WHATSAPP, FOCUS_TIMER, MEDIA, NOTIFY (+13 more)

### Community 6 - "Condition"
Cohesion: 0.05
Nodes (35): chargingWord(), contains(), describe(), freshened(), FreshnessPolicy, full(), T, Reason (+27 more)

### Community 7 - "Session"
Cohesion: 0.15
Nodes (4): Session, SessionStore, ScratchStore, InMemorySessionStore

### Community 8 - "RecordingExecutor"
Cohesion: 0.13
Nodes (4): ActionExecutor, RecordingExecutor, ToggleAttention, SessionEngineTest

### Community 9 - "Routine"
Cohesion: 0.16
Nodes (4): CueCardShareScreen(), Routine, PermissionCheckCopy, ReviewCopy

### Community 10 - "ActionSpec"
Cohesion: 0.08
Nodes (5): FixturesForCli, ActionSpec, CleanupPolicy, RearmPolicy, ValidatorTest

### Community 11 - "EndCondition"
Cohesion: 0.17
Nodes (9): AtTime, Duration, EndCondition, ManualStop, TriggerReversed, DurationEndKit, KClass, ManualStopKit (+1 more)

### Community 12 - "PlaceWatcherService"
Cohesion: 0.16
Nodes (9): android, IBinder, Intent, PendingIntent, Service, PlaceWatcherService, Location, LocationListener (+1 more)

### Community 13 - "CuesAccessibilityService"
Cohesion: 0.06
Nodes (23): AccessibilityEvent, AccessibilityNodeInfo, AccessibilityService, Blocked, CuesAccessibilityService, StateFlow, MacroRunOutcome, Refused (+15 more)

### Community 14 - "GrammarParserTest"
Cohesion: 0.12
Nodes (4): corpusText(), object@L411, GrammarParserTest, parser()

### Community 15 - "Capability"
Cohesion: 0.09
Nodes (17): Capability, ACCESSIBILITY_SERVICE, BATTERY_STATE, BLUETOOTH_CONNECT, EXACT_ALARM, LOCATION_BACKGROUND, LOCATION_FOR_WIFI_NAME, LOCATION_FOREGROUND (+9 more)

### Community 16 - "ActionOutcome"
Cohesion: 0.14
Nodes (4): AndroidActionExecutor, ActionExecutor, Uri, ActionOutcome

### Community 18 - "CapabilityProvider"
Cohesion: 0.11
Nodes (7): FactReference, CapabilityProvider, ConversationTest, InsightsServiceTest, FactTest, ReceiptRecordTest, FakeClock

### Community 19 - "ActionId"
Cohesion: 0.08
Nodes (21): FixtureExecutor, ActionExecutor, ActionId, ADD_CALENDAR_EVENT, COMPOSE_EMAIL, COMPOSE_MESSAGE, COMPOSE_WHATSAPP, MAIL_DIGEST (+13 more)

### Community 20 - "OwnedResource"
Cohesion: 0.11
Nodes (14): OwnedResource, DND_CONTRIBUTION, FOCUS_TIMER, PINNED_NOTE, RINGER_MODE, UTILITY_CONTRIBUTION, ActionDefinition, ActionRegistry (+6 more)

### Community 22 - "InferenceReport"
Cohesion: 0.19
Nodes (9): ConsoleHtml, ConsoleMain, CuesExporter, InferenceReport, ForecastItem, ReceiptEntry, CuesExporterTest, com (+1 more)

### Community 23 - "MainActivity.kt"
Cohesion: 0.15
Nodes (14): availableSignalsNow(), CuesApp(), friendlyLabel(), com, navigateToTab(), ReviewFlow(), DeviceIdentity, AvailableSignal (+6 more)

### Community 24 - "ClauseBlock.kt"
Cohesion: 0.70
Nodes (4): ClauseBlock(), ClauseRow(), ClauseKind, Modifier

### Community 25 - "SlabCard"
Cohesion: 0.11
Nodes (27): cuesGridBackground(), Color, Modifier, SlabCard(), SlabTier, ONE, THREE, TWO (+19 more)

### Community 26 - "GrammarParser"
Cohesion: 0.19
Nodes (8): AmbiguousDevice, GrammarParser, IntRange, RoutineDrafter, ResolvedApp, ResolvedTrigger, TriggerParse, Unsupported

### Community 28 - "CuesApplication"
Cohesion: 0.12
Nodes (6): CuesApplication, com, AndroidDeviceAttention, Application, DeviceAttention, ModelAvailabilityProbe

### Community 29 - "DiagnosticsScreen.kt"
Cohesion: 0.17
Nodes (22): Result, Cold, RunnerState, DeviceHealthSnapshot, checksLabel(), DeveloperSurfaceCard(), DeviceHealthCard(), DiagnosticCard() (+14 more)

### Community 30 - "ReviewScreenV2.kt"
Cohesion: 0.11
Nodes (35): ClauseBadge(), clauseColors(), ClauseKind, DO, IF, RESTORE, UNTIL, WHEN (+27 more)

### Community 31 - "CueCard.kt"
Cohesion: 0.21
Nodes (13): CardDecodeResult, CueCard, CueCards, T, Malformed, Missing, MissingEntity, Ok (+5 more)

### Community 32 - "NowScreen.kt"
Cohesion: 0.20
Nodes (23): clickableNoIndicationPublic(), CountdownRing(), EmptyState(), FilterChipRow(), FixedSegmentedControl(), formatCountdown(), androidx, Color (+15 more)

### Community 33 - "SessionState"
Cohesion: 0.10
Nodes (19): ActionRecord, EndReason, COVERAGE_GAP, DEADLINE_REACHED, MANUAL_STOP, RECONCILED_EXPIRED, ROUTINE_PAUSED, START_FAILED (+11 more)

### Community 34 - "DESIGN_SYSTEM.md"
Cohesion: 0.05
Nodes (37): 1. Monospaced Clause Badges, 2. Routine Slab Cards, 3. Buttons & Mechanical Triggers, 4. Origin Dynamic Islands & Capsules, 5. Input Fields & Syntax Builders, 6. Lists & Log Feeds, Accent Glow & Neon Halos, Brand & Style (+29 more)

### Community 35 - "EndKit"
Cohesion: 0.42
Nodes (3): EndKit, com, E

### Community 36 - "SignalRegistry"
Cohesion: 0.18
Nodes (5): C, com, E, T, SignalRegistry

### Community 37 - "CuesApplication.kt"
Cohesion: 0.10
Nodes (15): UtilityBindingScreen(), UtilityId, EYE_PROTECTION, GAME_MODE, ULTRA_SAVER, UtilityState, OFF, ON (+7 more)

### Community 38 - "ReviewScreen.kt"
Cohesion: 0.18
Nodes (13): ActionRiskRow(), grantLabel(), com, Intent, PermissionCheckRow(), RehearsalRowView(), ReviewRow(), ReviewScreen() (+5 more)

### Community 39 - "CueService.kt"
Cohesion: 0.10
Nodes (25): AssistantIntent, Capabilities, Control, Create, Explain, Forecast, ListCues, Refine (+17 more)

### Community 40 - "RoutineDetailScreen"
Cohesion: 0.25
Nodes (9): describe(), DetailRow(), RoutineDetailScreen(), TemporaryPatchCard(), tomorrowMorningMillis(), PatchKind, SkipOccurrence, SkipUntil (+1 more)

### Community 41 - "Finding"
Cohesion: 0.15
Nodes (9): Finding, Severity, ERROR, WARNING, ValidationResult, Validator, KClass, KClass (+1 more)

### Community 42 - "RoutineDrafter.kt"
Cohesion: 0.13
Nodes (11): RoutineDrafter, ClauseSpan, DifferingClause, ACTIONS, CONDITIONS, ENDING, OTHER, TRIGGER (+3 more)

### Community 43 - "Corpus"
Cohesion: 0.16
Nodes (9): main(), object@L9, BakeOff, BakeOffReport, BakeOffRow, RoutineDrafter, CaseResult, Corpus (+1 more)

### Community 44 - "CueServiceTest"
Cohesion: 0.18
Nodes (3): CueServiceTest, RoutineDrafter, RoutineDrafter

### Community 45 - "Cleanup register"
Cohesion: 0.05
Nodes (43): ~~CL-01 — Termux on-device build route~~, CL-02 — Android runtime written against real APIs, verified on none of them, CL-03 — Two drafting paths at equal weight, CL-04 — Unverified Android dependency versions, CL-05 — Pre-event code and what the documents claim, CL-06 — Device and OS assumptions, CL-07 — Named Wi-Fi requires a device decision, CL-08 — Pre-event signal framework disclosure (+35 more)

### Community 46 - "Task List"
Cohesion: 0.06
Nodes (31): Architecture Decisions, Checkpoint: Brain and Camera, Checkpoint: Bridge, Checkpoint: Coach, Checkpoint: Conversation, Checkpoint: Ecosystem, Checkpoint: Typed Actions, Definition of Complete (+23 more)

### Community 47 - "PatchBayEditors.kt"
Cohesion: 0.20
Nodes (20): CuesType, variableFont(), DayToggleRow(), EnumChipRow(), androidx, IntRange, Modifier, T (+12 more)

### Community 48 - "Suggestion"
Cohesion: 0.35
Nodes (7): Detection, Detectors, EvidenceLine, NoPattern, NotEnoughData, Suggest, Suggestion

### Community 49 - "Rehearsal"
Cohesion: 0.34
Nodes (4): MutableClock, Rehearsal, RehearsalReport, RehearsalRow

### Community 50 - "RecognitionListener"
Cohesion: 0.16
Nodes (5): android, ByteArray, LocalSpeechInput, RecognitionListener, SpeechRecognizer

### Community 51 - "ReplyCode"
Cohesion: 0.11
Nodes (17): ReplyCode, CAPABILITIES, CONFIRM_COMMAND, CUES_LIST, DRAFT_FAILED, DRAFT_READY, DRAFT_REFINED, EXPLANATION (+9 more)

### Community 53 - "NamedContext"
Cohesion: 0.10
Nodes (7): CliContexts, NamedContext, NamedContextStore, ContextualStores, EmptyContexts, Contexts, ContextualKitsTest

### Community 54 - "ReceiptRecord"
Cohesion: 0.12
Nodes (18): LegacyReceiptCard(), ReceiptScreen(), StructuredReceiptCard(), EventProvenance, MANUAL, PHYSICAL, REHEARSAL, ReceiptKind (+10 more)

### Community 56 - "CuesAppFunctionService.kt"
Cohesion: 0.13
Nodes (15): AskLocalGemmaParams, AskLocalGemmaResult, BaseCuesAppFunctionService, CurrentContextResult, DraftCueParams, DraftCueResult, ForecastItemResult, ForecastTodayResult (+7 more)

### Community 57 - "ActionArgs"
Cohesion: 0.10
Nodes (19): ActionArgs, Alarm, CalendarEvent, ComposeEmail, ComposeMessage, ComposeWhatsApp, Dnd, FocusTimer (+11 more)

### Community 58 - "LocalTimeOfDay"
Cohesion: 0.16
Nodes (3): Comparable, LocalTimeOfDay, SnapshotBuilderTest

### Community 59 - "Functional Design Document"
Cohesion: 0.09
Nodes (22): Action registry, Architecture, Cues Brain additions (Sprint 7), DND ownership, Event and context adapters, Expected state at a deadline (stretch), Functional Design Document, Implementation order (+14 more)

### Community 60 - "InferenceOutput"
Cohesion: 0.14
Nodes (10): GatedLlmSession, LiteRtLmSession, OnDeviceRefinePhraser, RefinePhrasing, OllamaLlmSession, FakeLlmSession, InferenceOutput, LlmSession (+2 more)

### Community 61 - "AudioOutputAdapter.kt"
Cohesion: 0.22
Nodes (8): AudioOutputAdapter, AudioDeviceCallback, Context, AudioDeviceInfo, AudioKind, ANY, BLUETOOTH, WIRED

### Community 63 - "ContextValue"
Cohesion: 0.14
Nodes (13): CalendarReadings, Context, Context, Readings, Radio, BLUETOOTH, LOCATION, WIFI (+5 more)

### Community 64 - "KineticButton"
Cohesion: 0.26
Nodes (10): clickableNoIndication(), GhostButton(), HaltButton(), KineticButton(), KineticSwitch(), Modifier, ShortcutDeck(), CuesHaptics (+2 more)

### Community 65 - "SimMain.kt"
Cohesion: 0.25
Nodes (10): baseMillisAt(), connect(), disconnect(), heading(), ActionExecutor, LoggingExecutor, main(), report() (+2 more)

### Community 66 - "Session order"
Cohesion: 0.12
Nodes (17): 0. Connect and baseline — ~10 min, 10. M9 — QR between devices — ~10 min, needs the second phone, 1. Hero loop gate — ~30–45 min, do this before anything else, 2. M8 — screen-off pending action — ~10 min, same setup as above, 3. M1 — package discovery — ~5 min, cheap, read-only, 4. M7 — AppFunctions + Jovi handoff — ~15 min, do early while there's time to fix, 5. M2 — NPU backend + drafting latency — ~20 min if a model is staged, else ~5 min to confirm the fallback, 6. M6 — accessibility utility binding — ~15 min (+9 more)

### Community 67 - "Receipt.kt"
Cohesion: 0.28
Nodes (7): Decision, friendly(), Receipt, Receipts, timerMinutes(), triggerNoun(), unownedCaveat()

### Community 68 - "SkipFamily"
Cohesion: 0.12
Nodes (16): SkipFamily, ALREADY_RUNNING, AUDIO, BATTERY, CALENDAR, CHARGING, CONTEXT, DAY (+8 more)

### Community 69 - "ModelNote"
Cohesion: 0.18
Nodes (9): DraftCredit, ModelNote, CHOSEN_FROM_DISAGREEMENT, MODEL_FAILED, MODEL_INVALID, MODEL_NOT_INSTALLED, MODEL_OFF, MODEL_TIMED_OUT (+1 more)

### Community 71 - "RecurrenceUnit"
Cohesion: 0.12
Nodes (6): RecurrenceUnit, DAYS, MONTHS, YEARS, RecurrenceCalculator, RecurrenceCalculatorTest

### Community 72 - "IfKind"
Cohesion: 0.17
Nodes (12): IfKind, AT_PLACE, AUDIO_ACTIVE, BATTERY_AT_LEAST, BATTERY_BELOW, CALENDAR_BUSY, CALENDAR_FREE, CHARGING (+4 more)

### Community 73 - "DifferentialDrafter"
Cohesion: 0.43
Nodes (3): DifferentialDrafter, DifferentialDrafterTest, RoutineDrafter

### Community 74 - "NavBars.kt"
Cohesion: 0.46
Nodes (7): CuesBottomNav(), CuesTopBar(), Composable, ImageVector, Modifier, TabItem(), TabSpec

### Community 75 - "MainActivity"
Cohesion: 0.15
Nodes (7): android, Bundle, MainActivity, TileService, ScreenTile, CuesTheme(), ComponentActivity

### Community 76 - "DeviceDiagnosticsRepository"
Cohesion: 0.24
Nodes (8): DeviceDiagnostics, DeviceDiagnosticsRepository, RecognitionSupportCallback, ManualObservation, ISSUE_OBSERVED, NO_ISSUE_OBSERVED, NOT_CHECKED, RecognitionSupport

### Community 77 - "AndroidActionExecutor.kt"
Cohesion: 0.08
Nodes (24): PendingIntent, AudioManager, DigestDeliveryMode, GEMMA_PARSABLE, MCQ_VOICE_WHATSAPP, SUMMARY_NEEDS_INPUT, RingerModeKind, SILENT (+16 more)

### Community 78 - "Insights.kt"
Cohesion: 0.10
Nodes (21): BlockCause, BlockedGroup, CleanupLedger, Coverage, ExpiredWaitingForYou, InferenceLedgerView, InferenceUsage, InsightCounts (+13 more)

### Community 79 - "EventKind"
Cohesion: 0.09
Nodes (19): EventKind, AUDIO_OUTPUT_ADDED, AUDIO_OUTPUT_REMOVED, BLUETOOTH_CONNECTED, BLUETOOTH_DISCONNECTED, DEADLINE_REACHED, MANUAL_RUN, MANUAL_STOP (+11 more)

### Community 80 - "Place"
Cohesion: 0.13
Nodes (4): Place, PlaceStore, EmptyPlaces, Places

### Community 81 - "Truth"
Cohesion: 0.09
Nodes (8): Evaluator, conjoin(), Truth, MATCH, NO_MATCH, UNKNOWN, GateReadoutTest, CalendarKitTest

### Community 82 - "DraftResult"
Cohesion: 0.10
Nodes (13): ModelDraftGuard, Drafted, DraftResult, Failed, NeedsClarification, BakeOffTest, RoutineDrafter, RoutineDrafter (+5 more)

### Community 83 - "DraftTrace"
Cohesion: 0.11
Nodes (19): AttemptOutcome, CANCELLED, CLARIFY, DRAFTED, FAILED, INVALID, SKIPPED_UNAVAILABLE, TIMED_OUT (+11 more)

### Community 85 - "SarvamClient"
Cohesion: 0.10
Nodes (17): ByteArray, SarvamClient, Transcript, Translation, ByteArray, SarvamReadback, ByteArray, SarvamSpeechInput (+9 more)

### Community 86 - "Sprints 4 and 5: The cue meets the phone, then earns its claims"
Cohesion: 0.17
Nodes (12): Exit criteria (end of event), Gate at hour 23, Not in these sprints, Pre-event Sprint 3.5: signal framework (Sep 23–25), Proposed requirement additions, R&D register, Sprint 4 test cases (on the loaner, not in :core), Sprint 4: Unattended proof (hours 16–23) (+4 more)

### Community 87 - "AskScreen.kt"
Cohesion: 0.16
Nodes (19): InstalledApp, installedApps(), Context, rankInstalledApps(), AdapterStatus, AskScreen(), Modifier, AssistantHistory() (+11 more)

### Community 88 - "AdapterSupervisor"
Cohesion: 0.23
Nodes (4): AdapterSupervisor, AdapterSupervisorTest, FakeAdapter, com

### Community 89 - "WifiAdapter.kt"
Cohesion: 0.29
Nodes (4): WifiAdapter, NetworkCallback, ConnectivityManager, Network

### Community 90 - "CueService"
Cohesion: 0.15
Nodes (5): CueService, Diagnostics, DrafterSetup, com, kotlinx

### Community 93 - "TimetableExtractor.kt"
Cohesion: 0.28
Nodes (6): ImportEntryCard(), ImportReviewScreen(), fullName(), TimetableEntry, TimetableExtractionResult, TimetableExtractor

### Community 94 - "RoutineStore"
Cohesion: 0.13
Nodes (5): Embedder, FloatArray, RoutineStore, EmbeddingPersonalIndexTest, InMemoryRoutines

### Community 98 - "WifiKits.kt"
Cohesion: 0.14
Nodes (5): describe(), KClass, semanticForm(), WifiConnectedKit, WifiConnectionKit

### Community 99 - "CuesGemmaProvider"
Cohesion: 0.24
Nodes (5): CuesGemmaProvider, Bundle, Uri, ContentProvider, ContentValues

### Community 100 - "Platform API verification — 24 September 2026"
Cohesion: 0.14
Nodes (13): AppFunctions, Camera and offline text recognition, Cue Cards and QR, Dependency policy, Desk Bridge and the Cue Console, Gemma model distribution (on-device "brain" download), LiteRT-LM and Qualcomm NPU, Live Updates (+5 more)

### Community 101 - "ShoppingItem"
Cohesion: 0.16
Nodes (4): ShoppingItem, ShoppingListFormatter, ShoppingListGrammar, ShoppingListGrammarTest

### Community 102 - "Product Requirements Specification"
Cohesion: 0.20
Nodes (10): Core journey, Principles, Product and user, Product Requirements Specification, Proposed evaluation, Requirements, Review example, Risks and scope decisions (+2 more)

### Community 103 - "FactKind"
Cohesion: 0.17
Nodes (11): FactKind, DATE, DAYS, DEVICE_ALIAS, PLACE_ALIAS, TEXT, FactSource, CAMERA (+3 more)

### Community 106 - "dev"
Cohesion: 0.36
Nodes (11): dev script, filt(), info(), need_adb(), need_sdk(), ok(), red(), sdk_dir() (+3 more)

### Community 107 - "CompositeDrafter"
Cohesion: 0.40
Nodes (6): CompositeDrafter, RoutineDrafter, CompositeDrafterTest, RoutineDrafter, parser(), StubDrafter

### Community 108 - "ExternalGemmaGate"
Cohesion: 0.08
Nodes (22): ExternalCallLogEntry, ExternalGemmaGate, Allowed, Denied, HubDecision, HubPolicy, ThermalStatus, CRITICAL (+14 more)

### Community 110 - "RoutineStatus"
Cohesion: 0.17
Nodes (8): RoutineStatus, ARMED, DISABLED, DRAFT, INVALID, PAUSED, REVIEWABLE, ClauseAccountingTest

### Community 111 - "ConditionKit"
Cohesion: 0.21
Nodes (5): BatteryThresholdKit, C, ConditionKit, C, KClass

### Community 112 - "DraftSourceId"
Cohesion: 0.10
Nodes (15): ExternalCallEntry, ExternalCallerLedger, InferenceCost, CostBasis, CLOUD_METERED, ON_DEVICE_FREE, UNVERIFIED, DraftSourceId (+7 more)

### Community 113 - "ActionExecutor"
Cohesion: 0.11
Nodes (4): ActionExecutor, ActionExecutor, RehearsalTest, ActionExecutor

### Community 115 - "Context.kt"
Cohesion: 0.12
Nodes (6): AlarmManager, PlaceAdapter, TimeAdapter, ListenerHealth, SignalAdapter, FakeSignalAdapter

### Community 116 - ".run"
Cohesion: 0.18
Nodes (8): AppBakeOff, object@L41, FakeSarvamChatSession, RoutineDrafter, SarvamChatDrafter, SarvamChatSession, SarvamHttpChatSession, UnconfiguredSarvamChatSession

### Community 117 - "RefineOperation"
Cohesion: 0.15
Nodes (11): AddAction, AddCondition, AddDays, RefineOperation, RemoveAction, RemoveCondition, RemoveDays, ReplaceTrigger (+3 more)

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
Cohesion: 0.07
Nodes (28): Any, AudioTransition, ADDED, REMOVED, DeviceTransition, CONNECTED, DISCONNECTED, MediaCommand (+20 more)

### Community 123 - "InsightsScreen.kt"
Cohesion: 0.36
Nodes (11): BackendPill(), clickable2(), costLine(), InsightsScreen(), items2(), KpiTile(), androidx, Modifier (+3 more)

### Community 124 - "JsonFileStore.kt"
Cohesion: 0.18
Nodes (4): CoachState, CoachStateStore, UsageLedger, InMemoryCoachState

### Community 125 - "SessionService"
Cohesion: 0.22
Nodes (6): android, IBinder, Intent, PendingIntent, Service, SessionService

### Community 126 - "ModelProvisionState"
Cohesion: 0.16
Nodes (8): Uri, ModelDownloader, Downloading, Failed, Installed, ModelProvisionState, NotInstalled, Verifying

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
Cohesion: 0.13
Nodes (14): BroadcastReceiver, android, BluetoothReceiver, Context, Intent, DeadlineReceiver, Context, Intent (+6 more)

### Community 132 - "InferenceBackend"
Cohesion: 0.11
Nodes (19): BenchmarkFields, Failed, FileKey, Generating, com, Result, StateFlow, Loading (+11 more)

### Community 133 - "PlanDraft"
Cohesion: 0.15
Nodes (10): Else, OnSignal, OnStepEnd, OnTimeout, PlanDraft, PlanStep, PlanTransition, PlanValidator (+2 more)

### Community 135 - "Working in this repository"
Cohesion: 0.33
Nodes (6): Claims, Commands, graphify, Rules that are not style preferences, Two modules, and the line between them, Working in this repository

### Community 136 - "Working in this repository"
Cohesion: 0.33
Nodes (6): Claims, Commands, graphify, Rules that are not style preferences, Two modules, and the line between them, Working in this repository

### Community 137 - "SnapshotBuilder"
Cohesion: 0.16
Nodes (4): Context, WifiReadings, SnapshotBuilder, WifiState

### Community 138 - "Demo script (Task 20)"
Cohesion: 0.33
Nodes (6): Backup evidence, Before the demo (setup, not part of the story), Cut from the live demo until their rows pass, Demo script (Task 20), Ground rules, Live script (phone)

### Community 139 - "ArmResult"
Cohesion: 0.18
Nodes (9): ArmResult, Blocked, DeleteResult, Invalid, T, MissingCapabilities, NotApproved, NotPaused (+1 more)

### Community 140 - "CommandResult"
Cohesion: 0.50
Nodes (4): Applied, CommandResult, MissingTarget, Rejected

### Community 141 - "InsightsMain.kt"
Cohesion: 0.18
Nodes (12): ChatMain, at(), connect(), hm(), main(), print(), remedyText(), run() (+4 more)

### Community 142 - "SuggestionKind"
Cohesion: 0.29
Nodes (7): SuggestionKind, ADD_SIGNAL_CUE, ADD_TIME_TRIGGER, CHECK_OR_REMOVE_ACTION, FIX_PERMISSION, REMOVE_DAY, SHORTER_DURATION

### Community 143 - "PairedDevice"
Cohesion: 0.15
Nodes (3): CardMain, PairedDevice, CueCardTest

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

### Community 150 - "ModelReadouts.kt"
Cohesion: 0.24
Nodes (12): DraftPipelineStrip(), DraftProvenance(), Modifier, label(), ModelStatusBar(), pipelineLabel(), PipelineStage(), stageLabel() (+4 more)

### Community 152 - "ReferenceResolution"
Cohesion: 0.33
Nodes (6): FloatArray, NeedsClarification, NeedsConfirmation, NotFound, ReferenceResolution, Resolved

### Community 153 - "iQOO device verification matrix (Task 19)"
Cohesion: 0.33
Nodes (6): Device context (all rows below), Hero loop gate (carried from Sprint 4/5), How to record a result, iQOO device verification matrix (Task 19), Results, Task 19 rows

### Community 154 - "Permissions"
Cohesion: 0.40
Nodes (5): Bound services and special access (not `<uses-permission>`), Declared permissions, Deliberately absent, Package visibility, Permissions

### Community 155 - "Sprint 3: Offline authoring, review and approval"
Cohesion: 0.40
Nodes (5): Exit criteria, Not in this sprint, Sprint 3: Offline authoring, review and approval, Test cases, What to cut, in order

### Community 156 - "Sprint 6: Contextual, declared"
Cohesion: 0.40
Nodes (4): Cut order, Enhancement pass (24 Sep 2026), Risk register, Sprint 6: Contextual, declared

### Community 157 - "AmbientBandClassifier"
Cohesion: 0.14
Nodes (10): AmbientBandClassifier, Band, BRIGHT, DARK, DIM, UNKNOWN, Reading, State (+2 more)

### Community 158 - "Developer setup"
Cohesion: 0.40
Nodes (4): Agent skills, Developer setup, Optional: Gemma-backed drafting without a phone, Toolchain

### Community 159 - "Theme.kt"
Cohesion: 0.50
Nodes (3): CuesColors, CuesSemanticColors, Color

### Community 160 - "ActionExecutor"
Cohesion: 0.50
Nodes (3): ActionExecutor, ActionExecutor, com

### Community 161 - "Main.kt"
Cohesion: 0.29
Nodes (12): main(), bold(), dim(), com, main(), printCorpus(), object@L168, printRehearsal() (+4 more)

### Community 162 - "OriginOS 7 experience pass"
Cohesion: 0.50
Nodes (3): Delivery checklist, Design contract, OriginOS 7 experience pass

### Community 164 - "Cues Brain execution checklist"
Cohesion: 0.50
Nodes (3): Checkpoint — Typed actions, detail (24 Sep 2026), Cues Brain execution checklist, Standing verification after each applicable slice

### Community 165 - "DragAndDrop.kt"
Cohesion: 0.83
Nodes (3): dragAndDropTextSource(), Modifier, View

### Community 166 - "Measured results"
Cohesion: 0.29
Nodes (6): Developer Gemma surface (CLEANUP.md CL-38, `docs/DEVICE_MATRIX.md` M10), How to record a result, Measured results, Model provisioning (CLEANUP.md CL-36), Office Kit transport (CLEANUP.md CL-22, `docs/DEVICE_MATRIX.md` M4), On-device inference (CLEANUP.md CL-18, `docs/DEVICE_MATRIX.md` M2)

### Community 167 - "Day"
Cohesion: 0.19
Nodes (11): Day, FRI, MON, SAT, SUN, THU, TUE, WED (+3 more)

### Community 168 - "LedgerEvent"
Cohesion: 0.17
Nodes (10): LearningSettings(), CoachMain, ActionBlocked, CoverageGap, LedgerEvent, ManualStart, PatchCreated, SessionEnded (+2 more)

### Community 169 - "WhenKind"
Cohesion: 0.17
Nodes (12): WhenKind, AT_TIME, AUDIO_ADDED, AUDIO_REMOVED, BLUETOOTH_CONNECT, BLUETOOTH_DISCONNECT, CHARGING_PLUGGED, MANUAL (+4 more)

### Community 173 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 178 - "ContextSource"
Cohesion: 0.13
Nodes (17): GrantCapability, KeepCuesRunning, OsWithholds, Remedy, TurnOnRadio, UnknownRemedy, WaitForFirstReading, ContextSource (+9 more)

### Community 180 - "UnknownReason"
Cohesion: 0.20
Nodes (7): T, UnknownReason, ADAPTER_UNAVAILABLE, NEVER_OBSERVED, PERMISSION_DENIED, REDACTED_BY_OS, STALE

### Community 181 - "Sprint 9 — Visible Intelligence, Sarvam, and Declared Sensing"
Cohesion: 0.11
Nodes (17): Acceptance matrix, Boundaries, Delivery order and cut line, Implementation status — 2026-09-26, Outcome, Required decisions before W2/W3/W5 implementation, Sprint 9 — Visible Intelligence, Sarvam, and Declared Sensing, Verification cadence (+9 more)

### Community 183 - "forecastToday"
Cohesion: 0.42
Nodes (5): ExportImport, Context, Intent, Uri, forecastToday()

### Community 184 - "TriggerEvent"
Cohesion: 0.12
Nodes (11): TriggerEvent, Ended, EngineResult, ExitCancelled, ExitScheduled, Ignored, isLive(), SessionEngine (+3 more)

### Community 185 - "NowNextWidget.kt"
Cohesion: 0.44
Nodes (5): Context, NowNextWidget, AppWidgetManager, AppWidgetProvider, IntArray

### Community 186 - "ForecastStatus"
Cohesion: 0.16
Nodes (16): Modifier, StatusChip(), Tone, AMBER, GO, STOP, toTone(), ForecastCard() (+8 more)

### Community 187 - "UtilityRestoreTest"
Cohesion: 0.27
Nodes (3): ActionExecutor, StatelessUtilityExecutor, UtilityRestoreTest

### Community 188 - "AssistantIntent.kt"
Cohesion: 0.22
Nodes (8): ControlKind, PAUSE, RESUME, SKIP_TODAY, STOP, UnsupportedRoute, NONE, SYSTEM_AGENT

### Community 190 - "build_cues_hyperdeck.mjs"
Cohesion: 0.22
Nodes (8): analysis, manifest, outputRoot, pdfPath, pptxPath, rawPptxPath, slideCopy, story

### Community 191 - ".current"
Cohesion: 0.46
Nodes (3): DeviceHealthReadings, Context, SensorGroup

### Community 193 - "GraceScheduler"
Cohesion: 0.57
Nodes (3): GraceScheduler, Context, PendingIntent

### Community 194 - "InferenceReportPlumbingTest.kt"
Cohesion: 0.13
Nodes (12): ReplySource, FORECAST, ON_DEVICE_LLM, PARSER, RECEIPTS, ROUTINE_STORE, SARVAM_CLOUD, FakeModelDrafter (+4 more)

### Community 196 - "FaceDownClassifierTest"
Cohesion: 0.21
Nodes (4): FaceDownClassifier, Reading, State, FaceDownClassifierTest

### Community 197 - "ModelCatalog"
Cohesion: 0.33
Nodes (6): KnownNpuDispatchFailure, ModelArtifact, ModelCatalog, ModelExecution, NPU_SOC_SPECIFIC, PORTABLE

### Community 199 - "ActionRisk"
Cohesion: 0.25
Nodes (6): ActionRisk, EXTERNAL_UNOWNED, HANDOFF, LOCAL_NOTICE, OWNED_AND_REVERSIBLE, UI_AUTOMATION

### Community 203 - "deck-engine.js"
Cohesion: 0.48
Nodes (5): e(), field(), mountDeck(), scenes, HyperDeckApp()

### Community 204 - "Verification walkthrough: place arrival, WhatsApp, regional language"
Cohesion: 0.29
Nodes (6): Before starting, Part A — place arrival → WhatsApp (CL-13 / R13, WhatsApp handoff), Part B — regional language authoring (CL-42), Part C — optional, quick: plain SMS/message handoff sanity check, Verification walkthrough: place arrival, WhatsApp, regional language, When you're ready

### Community 206 - "BootReceiver.kt"
Cohesion: 0.60
Nodes (3): BootReceiver, Context, Intent

### Community 207 - "GraceReceiver.kt"
Cohesion: 0.60
Nodes (3): GraceReceiver, Context, Intent

### Community 210 - "RecurringTriggerReceiver.kt"
Cohesion: 0.60
Nodes (3): Context, Intent, RecurringTriggerReceiver

### Community 211 - "TimeTriggerReceiver.kt"
Cohesion: 0.60
Nodes (3): Context, Intent, TimeTriggerReceiver

### Community 216 - "Building during Red Light"
Cohesion: 0.67
Nodes (3): Building during Red Light, Office Kit drives the laptop, What does not depend on either route

## Knowledge Gaps
- **797 isolated node(s):** `scenes`, `GemmaCallProtocol`, `object@L41`, `Cold`, `Unavailable` (+792 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 1155 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **43 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `CuesApplication` connect `CuesApplication` to `Ports.kt`, `BroadcastReceiver`, `JsonFileStore`, `PlaceWatcherService`, `InsightsMain.kt`, `PairedDevice`, `ActionOutcome`, `GrammarParser`, `DiagnosticsScreen.kt`, `CuesApplication.kt`, `CuesAppFunctionService.kt`, `NowNextWidget.kt`, `InferenceOutput`, `AudioOutputAdapter.kt`, `AndroidCapabilityProvider`, `RecurringReminderAdapter`, `DifferentialDrafter`, `CueTileService.kt`, `DeviceDiagnosticsRepository`, `OnDeviceLlmDrafter`, `BootReceiver.kt`, `GraceReceiver.kt`, `RecurringTriggerReceiver.kt`, `TimeTriggerReceiver.kt`, `SarvamClient`, `AdapterSupervisor`, `WifiAdapter.kt`, `CueService`, `MonitoringRepository`, `CuesGemmaProvider`, `ExternalGemmaGate`, `DraftSourceId`, `Context.kt`, `ModelProvisionState`?**
  _High betweenness centrality (0.084) - this node is a cross-community bridge._
- **Why does `Trigger` connect `Trigger` to `PatchSentence`, `DoKind`, `Condition`, `EndCondition`, `DetectorsTest`, `GrammarParser`, `CueCard.kt`, `NowScreen.kt`, `SignalRegistry`, `Day`, `Finding`, `Corpus`, `PlaceSupport`, `CuesAppFunctionService.kt`, `TriggerEvent`, `AssistantIntent.kt`, `RecurringReminderAdapter`, `AtTimeEndKit`, `RecurringTriggerReceiver.kt`, `TimeTriggerReceiver.kt`, `AppShortcuts.kt`, `WifiKits.kt`, `Context.kt`, `Routine.kt`?**
  _High betweenness centrality (0.077) - this node is a cross-community bridge._
- **Why does `JsonFileStore` connect `JsonFileStore` to `Ports.kt`, `Fact`, `Session`, `RecordingExecutor`, `Routine`, `InsightsMain.kt`, `CapabilityProvider`, `CuesApplication`, `CuesApplication.kt`, `CueServiceTest`, `NamedContext`, `UiMacro`, `UtilityRestoreTest`, `SimMain.kt`, `InferenceReportPlumbingTest.kt`, `Place`, `JsonFileStoreTest`, `UsageLedgerTest.kt`, `CleanupObligation`, `RoutineStore`, `JsonFileStore.kt`?**
  _High betweenness centrality (0.069) - this node is a cross-community bridge._
- **Are the 2 inferred relationships involving `Routine` (e.g. with `.parse()` and `.heroRoutine()`) actually correct?**
  _`Routine` has 2 INFERRED edges - model-reasoned connections that need verification._
- **Are the 15 inferred relationships involving `TriggerEvent` (e.g. with `connect()` and `run()`) actually correct?**
  _`TriggerEvent` has 15 INFERRED edges - model-reasoned connections that need verification._
- **What connects `scenes`, `GemmaCallProtocol`, `object@L41` to the rest of the system?**
  _797 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Trigger` be split into smaller, more focused modules?**
  _Cohesion score 0.04013377926421405 - nodes in this community are weakly interconnected._