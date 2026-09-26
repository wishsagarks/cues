# Graph Report - iqoo+  (2026-09-26)

## Corpus Check
- 273 files · ~269,601 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 3475 nodes · 8030 edges · 209 communities (164 shown, 41 thin omitted)
- Extraction: 97% EXTRACTED · 3% INFERRED · 0% AMBIGUOUS · INFERRED: 220 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `a724b30d`
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
- Routine
- ActionSpec
- EndCondition
- CueService
- CuesAccessibilityService
- GrammarParserTest
- CalendarKit.kt
- ActionOutcome
- TimetableExtractorTest
- CapabilityProvider
- ActionId
- ActionRegistry
- DetectorsTest
- InferenceReport
- MainActivity.kt
- CuesType
- ReviewScreenV2.kt
- GrammarParser
- InsightsTest
- CuesApplication
- DiagnosticsScreen.kt
- Badges.kt
- CueCard.kt
- NowScreen.kt
- SessionState
- DESIGN_SYSTEM.md
- TriggerKit
- SignalRegistry
- UtilityId
- ArmResult
- AssistantIntent
- Patch
- Finding
- DraftResult
- Ports.kt
- CueServiceTest
- Cleanup register
- Task List
- PairedDevice
- Detectors
- Rehearsal
- RecognitionListener
- ReplyCode
- OwnedResource
- NamedContext
- ContextSnapshot
- PlaceSupport
- CuesAppFunctionService.kt
- SessionService
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
- Truth
- EvaluatorTest
- DoKind
- IfKind
- DifferentialDrafter
- SarvamChatDrafter.kt
- MainActivity
- Main.kt
- Normalizer
- Insights.kt
- EventKind
- Place
- WifiKits.kt
- ClauseAccountingTest.kt
- DraftTrace
- JsonFileStoreTest
- SpeechResult
- Sprints 4 and 5: The cue meets the phone, then earns its claims
- AskScreen.kt
- ReviewCopy
- .run
- ActionArgs
- SessionWindowTest
- NowNextWidget.kt
- TimetableExtractor.kt
- LedgerEvent
- SignalKitTest
- ApprovalsTest
- NormalizerTest
- ReceiptRecordTest
- CuesGemmaProvider
- Platform API verification — 24 September 2026
- Capability
- Product Requirements Specification
- FactKind
- UnknownRemedyTest
- ReviewCopyLinesTest
- dev
- CompositeDrafter
- ExternalGemmaGate
- test_sarvam.py
- RoutineStatus
- BatteryThresholdKit
- DraftSourceId
- ActionExecutor
- ReviewCopyTest
- Context.kt
- forecastToday
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
- Fact
- InferenceBackend
- PlanDraft
- CueService.kt
- Working in this repository
- Working in this repository
- UnknownReason
- Demo script (Task 20)
- GateReadoutTest
- Conversation.kt
- InsightsMain.kt
- SuggestionKind
- BluetoothReceiver.kt
- Release gate: verification and disclosure (Task 20)
- ActionRegistryTest
- CuesMotion.kt
- ReplySpeaker
- Working in this repository
- TimetableOcr.kt
- ScreenTile
- BluetoothCoverage
- PersonalIndex
- iQOO device verification matrix (Task 19)
- Permissions
- Sprint 3: Offline authoring, review and approval
- Sprint 6: Contextual, declared
- AmbientBandClassifier
- Developer setup
- Theme.kt
- ActionExecutor
- CoachPolicy
- OriginOS 7 experience pass
- SarvamReadback
- Cues Brain execution checklist
- DragAndDrop.kt
- Measured results
- SnapshotBuilder
- MacroValidatorTest
- UtilityCatalogTest
- Suggestion
- DeviceIdentity
- Corpus
- gradlew
- Fixtures
- GemmaCallProtocol.kt
- PatchSentenceTest
- Workstreams
- Remedy
- Contexts
- EngineResult
- Day
- StatusChip
- UtilityRestoreTest
- SarvamClient
- PatchSentence.kt
- UiMacro
- UiStep
- AndroidCapabilityProvider
- GraceScheduler
- ReplySource
- HubPolicyTest
- FaceDownClassifierTest
- ModelCatalog
- FaceDownClassifier
- AtTimeEndKit
- .captureScreenText
- CueTileService.kt
- ClauseAccounting
- AppShortcuts.kt
- .performPlayback
- .draftWithCloud
- UtilityMechanism
- InsightsBenchmarkTest
- ModelCatalogTest

## God Nodes (most connected - your core abstractions)
1. `Routine` - 162 edges
2. `JsonFileStore` - 135 edges
3. `Trigger` - 121 edges
4. `Condition` - 117 edges
5. `CueService` - 96 edges
6. `TriggerEvent` - 90 edges
7. `GrammarParser` - 82 edges
8. `Session` - 81 edges
9. `ReasonCode` - 75 edges
10. `CuesApplication` - 56 edges

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

## Communities (209 total, 41 thin omitted)

### Community 0 - "Trigger"
Cohesion: 0.05
Nodes (17): TriggerEvent, AudioOutput, BluetoothConnection, Charging, Manual, PlaceTransition, Trigger, WifiConnection (+9 more)

### Community 1 - "ListenerHealth"
Cohesion: 0.09
Nodes (11): WifiAdapter, NetworkCallback, ConnectivityManager, ListenerHealth, SignalAdapter, AdapterSupervisor, FakeSignalAdapter, AdapterSupervisorTest (+3 more)

### Community 2 - "ReasonCode"
Cohesion: 0.04
Nodes (54): ReasonCode, AUDIO_KIND_MISMATCH, AUDIO_OUTPUT_ACTIVE, AUDIO_OUTPUT_CHANGED, AUDIO_OUTPUT_INACTIVE, AUDIO_OUTPUT_UNKNOWN, BATTERY_AS_REQUIRED, BATTERY_NOT_AS_REQUIRED (+46 more)

### Community 3 - "PatchSentence"
Cohesion: 0.34
Nodes (5): No, PatchSentence, Phrase, Until, Yes

### Community 4 - "JsonFileStore"
Cohesion: 0.08
Nodes (4): InferenceLedgerEntry, JsonFileStore, T, UsageLedgerTest

### Community 5 - "WhenKind"
Cohesion: 0.08
Nodes (20): DoSelection, IfSelection, UntilExtra, UntilExtraKind, AT_TIME, HALF_HOUR, HOUR, WhenKind (+12 more)

### Community 6 - "Condition"
Cohesion: 0.05
Nodes (24): Reason, AtPlace, AudioOutputActive, BatteryAtLeast, BatteryBelow, CalendarBusy, CalendarNotBusy, ChargingState (+16 more)

### Community 7 - "Session"
Cohesion: 0.10
Nodes (6): CleanupObligation, Session, SessionStore, ScratchStore, isLive(), InMemorySessionStore

### Community 8 - "RecordingExecutor"
Cohesion: 0.12
Nodes (5): SessionEngine, ActionExecutor, RecordingExecutor, ToggleAttention, SessionEngineTest

### Community 9 - "Routine"
Cohesion: 0.09
Nodes (11): CueCardShareScreen(), Refiner, Result, Routine, Embedder, FloatArray, RoutineStore, EmbeddingPersonalIndexTest (+3 more)

### Community 10 - "ActionSpec"
Cohesion: 0.09
Nodes (5): FixturesForCli, ActionSpec, CleanupPolicy, RearmPolicy, ValidatorTest

### Community 11 - "EndCondition"
Cohesion: 0.17
Nodes (9): AtTime, Duration, EndCondition, ManualStop, TriggerReversed, DurationEndKit, KClass, ManualStopKit (+1 more)

### Community 12 - "CueService"
Cohesion: 0.15
Nodes (5): CueService, Diagnostics, DrafterSetup, com, kotlinx

### Community 13 - "CuesAccessibilityService"
Cohesion: 0.18
Nodes (5): AccessibilityEvent, AccessibilityNodeInfo, AccessibilityService, CuesAccessibilityService, StateFlow

### Community 14 - "GrammarParserTest"
Cohesion: 0.12
Nodes (4): corpusText(), object@L349, GrammarParserTest, parser()

### Community 15 - "CalendarKit.kt"
Cohesion: 0.20
Nodes (5): CalendarBusyKit, CalendarConditionKit, CalendarNotBusyKit, C, KClass

### Community 16 - "ActionOutcome"
Cohesion: 0.17
Nodes (4): AndroidActionExecutor, ActionExecutor, Uri, ActionOutcome

### Community 18 - "CapabilityProvider"
Cohesion: 0.12
Nodes (12): Conversation, FactReference, CapabilityProvider, ConversationTest, FakeModelDrafter, InferenceReportPlumbingTest, RoutineDrafter, RoutineDrafter (+4 more)

### Community 19 - "ActionId"
Cohesion: 0.10
Nodes (17): FixtureExecutor, ActionExecutor, ActionId, ADD_CALENDAR_EVENT, COMPOSE_MESSAGE, MEDIA_CONTROL, NOTIFY_RESULT, OPEN_APP (+9 more)

### Community 20 - "ActionRegistry"
Cohesion: 0.18
Nodes (8): ActionDefinition, ActionRegistry, ArgResult, Invalid, Presence, NEEDS_USER, UNATTENDED_OK, Valid

### Community 22 - "InferenceReport"
Cohesion: 0.22
Nodes (12): ConsoleHtml, ConsoleMain, CuesExporter, InferenceReport, ForecastItem, ForecastStatus, CANNOT_TELL, NOT_TODAY (+4 more)

### Community 23 - "MainActivity.kt"
Cohesion: 0.17
Nodes (19): availableSignalsNow(), CuesApp(), friendlyLabel(), com, navigateToTab(), ReviewFlow(), CuesBottomNav(), CuesTopBar() (+11 more)

### Community 24 - "CuesType"
Cohesion: 0.33
Nodes (7): ClauseBlock(), ClauseRow(), ClauseKind, Modifier, CuesType, variableFont(), Font

### Community 25 - "ReviewScreenV2.kt"
Cohesion: 0.13
Nodes (30): cuesGridBackground(), Color, Modifier, SlabCard(), SlabTier, ONE, THREE, TWO (+22 more)

### Community 26 - "GrammarParser"
Cohesion: 0.14
Nodes (9): AmbiguousDevice, GrammarParser, IntRange, RoutineDrafter, ResolvedApp, ResolvedTrigger, TriggerParse, TemplatesTest (+1 more)

### Community 27 - "InsightsTest"
Cohesion: 0.24
Nodes (3): LedgerView, ActionRecord, InsightsTest

### Community 28 - "CuesApplication"
Cohesion: 0.10
Nodes (10): CuesApplication, com, Result, StateFlow, StoreGeneration, ExternalCallEntry, ExternalCallerLedger, AndroidDeviceAttention (+2 more)

### Community 29 - "DiagnosticsScreen.kt"
Cohesion: 0.07
Nodes (36): Uri, ModelDownloader, DeviceDiagnostics, DeviceDiagnosticsRepository, RecognitionSupportCallback, ManualObservation, ISSUE_OBSERVED, NO_ISSUE_OBSERVED (+28 more)

### Community 30 - "Badges.kt"
Cohesion: 0.10
Nodes (29): ClauseBadge(), clauseColors(), ClauseKind, DO, IF, RESTORE, UNTIL, WHEN (+21 more)

### Community 31 - "CueCard.kt"
Cohesion: 0.18
Nodes (14): CardMain, CardDecodeResult, CueCard, CueCards, T, Malformed, Missing, MissingEntity (+6 more)

### Community 32 - "NowScreen.kt"
Cohesion: 0.20
Nodes (23): clickableNoIndicationPublic(), CountdownRing(), EmptyState(), FilterChipRow(), FixedSegmentedControl(), formatCountdown(), androidx, Color (+15 more)

### Community 33 - "SessionState"
Cohesion: 0.20
Nodes (10): SessionState, ACTIVE, CANCELLED, CLEANUP_PENDING, COMPLETED, ENDING, EXIT_PENDING, FAILED (+2 more)

### Community 34 - "DESIGN_SYSTEM.md"
Cohesion: 0.05
Nodes (37): 1. Monospaced Clause Badges, 2. Routine Slab Cards, 3. Buttons & Mechanical Triggers, 4. Origin Dynamic Islands & Capsules, 5. Input Fields & Syntax Builders, 6. Lists & Log Feeds, Accent Glow & Neon Halos, Brand & Style (+29 more)

### Community 35 - "TriggerKit"
Cohesion: 0.12
Nodes (8): ConditionKit, EndKit, C, com, E, KClass, T, TriggerKit

### Community 36 - "SignalRegistry"
Cohesion: 0.18
Nodes (5): C, com, E, T, SignalRegistry

### Community 37 - "UtilityId"
Cohesion: 0.18
Nodes (10): UtilityBindingScreen(), UtilityId, EYE_PROTECTION, GAME_MODE, ULTRA_SAVER, UtilityState, OFF, ON (+2 more)

### Community 38 - "ArmResult"
Cohesion: 0.14
Nodes (11): Approvals, ArmResult, Blocked, DeleteResult, Invalid, T, MissingCapabilities, NotApproved (+3 more)

### Community 39 - "AssistantIntent"
Cohesion: 0.20
Nodes (12): AssistantIntent, Capabilities, Control, Create, Explain, Forecast, ListCues, Refine (+4 more)

### Community 40 - "Patch"
Cohesion: 0.17
Nodes (11): describe(), DetailRow(), RoutineDetailScreen(), TemporaryPatchCard(), tomorrowMorningMillis(), Patch, PatchKind, SkipOccurrence (+3 more)

### Community 41 - "Finding"
Cohesion: 0.17
Nodes (8): Finding, Severity, ERROR, WARNING, ValidationResult, Validator, KClass, SignalText

### Community 42 - "DraftResult"
Cohesion: 0.09
Nodes (19): RoutineDrafter, ClauseKind, FILLER, MAPPED, UNACCOUNTED, ClauseSpan, DifferingClause, ACTIONS (+11 more)

### Community 43 - "Ports.kt"
Cohesion: 0.17
Nodes (3): UtilityBinding, MacroStore, UtilityBindingStore

### Community 44 - "CueServiceTest"
Cohesion: 0.17
Nodes (3): CueServiceTest, RoutineDrafter, RoutineDrafter

### Community 45 - "Cleanup register"
Cohesion: 0.05
Nodes (42): ~~CL-01 — Termux on-device build route~~, CL-02 — Android runtime written against real APIs, verified on none of them, CL-03 — Two drafting paths at equal weight, CL-04 — Unverified Android dependency versions, CL-05 — Pre-event code and what the documents claim, CL-06 — Device and OS assumptions, CL-07 — Named Wi-Fi requires a device decision, CL-08 — Pre-event signal framework disclosure (+34 more)

### Community 46 - "Task List"
Cohesion: 0.06
Nodes (31): Architecture Decisions, Checkpoint: Brain and Camera, Checkpoint: Bridge, Checkpoint: Coach, Checkpoint: Conversation, Checkpoint: Ecosystem, Checkpoint: Typed Actions, Definition of Complete (+23 more)

### Community 47 - "PairedDevice"
Cohesion: 0.15
Nodes (27): CuesShape, DayToggleRow(), EnumChipRow(), androidx, IntRange, Modifier, T, NumberStepper() (+19 more)

### Community 48 - "Detectors"
Cohesion: 0.38
Nodes (6): Detection, Detectors, EvidenceLine, NoPattern, NotEnoughData, Suggest

### Community 49 - "Rehearsal"
Cohesion: 0.39
Nodes (3): MutableClock, Rehearsal, RehearsalRow

### Community 50 - "RecognitionListener"
Cohesion: 0.16
Nodes (5): android, ByteArray, LocalSpeechInput, RecognitionListener, SpeechRecognizer

### Community 51 - "ReplyCode"
Cohesion: 0.11
Nodes (17): ReplyCode, CAPABILITIES, CONFIRM_COMMAND, CUES_LIST, DRAFT_FAILED, DRAFT_READY, DRAFT_REFINED, EXPLANATION (+9 more)

### Community 52 - "OwnedResource"
Cohesion: 0.18
Nodes (8): OwnedResource, DND_CONTRIBUTION, FOCUS_TIMER, PINNED_NOTE, RINGER_MODE, UTILITY_CONTRIBUTION, ActionExecutor, ActionExecutor

### Community 53 - "NamedContext"
Cohesion: 0.15
Nodes (5): CliContexts, NamedContext, NamedContextStore, ContextualStores, EmptyContexts

### Community 54 - "ContextSnapshot"
Cohesion: 0.14
Nodes (18): LegacyReceiptCard(), ReceiptScreen(), StructuredReceiptCard(), ContextSnapshot, EventProvenance, MANUAL, PHYSICAL, REHEARSAL (+10 more)

### Community 56 - "CuesAppFunctionService.kt"
Cohesion: 0.13
Nodes (15): AskLocalGemmaParams, AskLocalGemmaResult, BaseCuesAppFunctionService, CurrentContextResult, DraftCueParams, DraftCueResult, ForecastItemResult, ForecastTodayResult (+7 more)

### Community 57 - "SessionService"
Cohesion: 0.22
Nodes (6): android, Intent, PendingIntent, SessionService, IBinder, Service

### Community 58 - "LocalTimeOfDay"
Cohesion: 0.19
Nodes (3): Comparable, LocalTimeOfDay, SnapshotBuilderTest

### Community 59 - "Functional Design Document"
Cohesion: 0.09
Nodes (22): Action registry, Architecture, Cues Brain additions (Sprint 7), DND ownership, Event and context adapters, Expected state at a deadline (stretch), Functional Design Document, Implementation order (+14 more)

### Community 60 - "InferenceOutput"
Cohesion: 0.14
Nodes (11): GatedLlmSession, LiteRtLmSession, OnDeviceRefinePhraser, RefinePhrasing, OllamaLlmSession, FakeLlmSession, InferenceOutput, LlmSession (+3 more)

### Community 61 - "AudioOutputAdapter.kt"
Cohesion: 0.31
Nodes (5): AudioOutputAdapter, AudioDeviceCallback, Context, AudioDeviceInfo, AudioManager

### Community 62 - "README.md"
Cohesion: 0.21
Nodes (4): Sprint 5 device runbook, Building during Red Light, Office Kit drives the laptop, What does not depend on either route

### Community 63 - "ContextValue"
Cohesion: 0.19
Nodes (9): CalendarReadings, Context, Context, Readings, UnreadableInputs, ContextValue, Known, T (+1 more)

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
Cohesion: 0.28
Nodes (7): Decision, friendly(), Receipt, Receipts, timerMinutes(), triggerNoun(), unownedCaveat()

### Community 68 - "SkipFamily"
Cohesion: 0.12
Nodes (16): SkipFamily, ALREADY_RUNNING, AUDIO, BATTERY, CALENDAR, CHARGING, CONTEXT, DAY (+8 more)

### Community 69 - "Truth"
Cohesion: 0.16
Nodes (10): Evaluator, FreshnessPolicy, conjoin(), Truth, MATCH, NO_MATCH, UNKNOWN, RehearsalReport (+2 more)

### Community 71 - "DoKind"
Cohesion: 0.17
Nodes (12): DoKind, ALARM, CALENDAR_EVENT, COMPOSE_MESSAGE, FOCUS_TIMER, MEDIA, NOTIFY, OPEN_APP (+4 more)

### Community 72 - "IfKind"
Cohesion: 0.17
Nodes (12): IfKind, AT_PLACE, AUDIO_ACTIVE, BATTERY_AT_LEAST, BATTERY_BELOW, CALENDAR_BUSY, CALENDAR_FREE, CHARGING (+4 more)

### Community 73 - "DifferentialDrafter"
Cohesion: 0.21
Nodes (8): DifferentialDrafter, DifferentialDrafterTest, RoutineDrafter, RoutineDrafter, RoutineDrafter, RoutineDrafter, RoutineDrafter, RoutineDrafter

### Community 74 - "SarvamChatDrafter.kt"
Cohesion: 0.23
Nodes (6): FakeSarvamChatSession, RoutineDrafter, SarvamChatDrafter, SarvamChatSession, SarvamHttpChatSession, UnconfiguredSarvamChatSession

### Community 75 - "MainActivity"
Cohesion: 0.27
Nodes (5): android, Bundle, MainActivity, CuesTheme(), ComponentActivity

### Community 76 - "Main.kt"
Cohesion: 0.35
Nodes (11): main(), bold(), dim(), com, main(), printCorpus(), object@L168, printRehearsal() (+3 more)

### Community 78 - "Insights.kt"
Cohesion: 0.10
Nodes (21): BlockCause, BlockedGroup, CleanupLedger, Coverage, ExpiredWaitingForYou, InferenceLedgerView, InferenceUsage, InsightCounts (+13 more)

### Community 79 - "EventKind"
Cohesion: 0.11
Nodes (18): Context, Intent, PowerReceiver, EventKind, AUDIO_OUTPUT_ADDED, AUDIO_OUTPUT_REMOVED, BLUETOOTH_CONNECTED, BLUETOOTH_DISCONNECTED (+10 more)

### Community 80 - "Place"
Cohesion: 0.20
Nodes (3): Place, PlaceStore, EmptyPlaces

### Community 81 - "WifiKits.kt"
Cohesion: 0.13
Nodes (13): chargingWord(), contains(), describe(), freshened(), full(), T, DeviceTransition, CONNECTED (+5 more)

### Community 83 - "DraftTrace"
Cohesion: 0.07
Nodes (28): AttemptOutcome, CANCELLED, CLARIFY, DRAFTED, FAILED, INVALID, SKIPPED_UNAVAILABLE, TIMED_OUT (+20 more)

### Community 85 - "SpeechResult"
Cohesion: 0.21
Nodes (9): ByteArray, SarvamSpeechInput, Failed, NoMatch, PermissionDenied, Recognized, SpeechInput, SpeechResult (+1 more)

### Community 86 - "Sprints 4 and 5: The cue meets the phone, then earns its claims"
Cohesion: 0.17
Nodes (12): Exit criteria (end of event), Gate at hour 23, Not in these sprints, Pre-event Sprint 3.5: signal framework (Sep 23–25), Proposed requirement additions, R&D register, Sprint 4 test cases (on the loaner, not in :core), Sprint 4: Unattended proof (hours 16–23) (+4 more)

### Community 87 - "AskScreen.kt"
Cohesion: 0.18
Nodes (18): InstalledApp, installedApps(), Context, rankInstalledApps(), AdapterStatus, AskScreen(), Modifier, AssistantHistory() (+10 more)

### Community 89 - ".run"
Cohesion: 0.14
Nodes (10): AppBakeOff, object@L41, main(), object@L9, BakeOff, BakeOffReport, BakeOffRow, RoutineDrafter (+2 more)

### Community 90 - "ActionArgs"
Cohesion: 0.12
Nodes (15): ActionArgs, Alarm, CalendarEvent, ComposeMessage, Dnd, FocusTimer, MediaControl, None (+7 more)

### Community 92 - "NowNextWidget.kt"
Cohesion: 0.44
Nodes (5): Context, NowNextWidget, AppWidgetManager, AppWidgetProvider, IntArray

### Community 93 - "TimetableExtractor.kt"
Cohesion: 0.28
Nodes (6): ImportEntryCard(), ImportReviewScreen(), fullName(), TimetableEntry, TimetableExtractionResult, TimetableExtractor

### Community 94 - "LedgerEvent"
Cohesion: 0.17
Nodes (10): LearningSettings(), CoachMain, ActionBlocked, CoverageGap, LedgerEvent, ManualStart, PatchCreated, SessionEnded (+2 more)

### Community 99 - "CuesGemmaProvider"
Cohesion: 0.24
Nodes (5): CuesGemmaProvider, Bundle, Uri, ContentProvider, ContentValues

### Community 100 - "Platform API verification — 24 September 2026"
Cohesion: 0.14
Nodes (13): AppFunctions, Camera and offline text recognition, Cue Cards and QR, Dependency policy, Desk Bridge and the Cue Console, Gemma model distribution (on-device "brain" download), LiteRT-LM and Qualcomm NPU, Live Updates (+5 more)

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

### Community 107 - "CompositeDrafter"
Cohesion: 0.33
Nodes (6): CompositeDrafter, RoutineDrafter, CompositeDrafterTest, RoutineDrafter, parser(), StubDrafter

### Community 108 - "ExternalGemmaGate"
Cohesion: 0.08
Nodes (22): ExternalCallLogEntry, ExternalGemmaGate, Allowed, Denied, HubDecision, HubPolicy, ThermalStatus, CRITICAL (+14 more)

### Community 110 - "RoutineStatus"
Cohesion: 0.11
Nodes (8): RoutineStatus, ARMED, DISABLED, DRAFT, INVALID, PAUSED, REVIEWABLE, CueCardTest

### Community 111 - "BatteryThresholdKit"
Cohesion: 0.24
Nodes (3): BatteryAtLeastKit, BatteryThresholdKit, C

### Community 112 - "DraftSourceId"
Cohesion: 0.07
Nodes (19): ModelDraftGuard, InferenceCost, CostBasis, CLOUD_METERED, ON_DEVICE_FREE, UNVERIFIED, DraftSourceId, EXTERNAL_GEMMA_CALL (+11 more)

### Community 113 - "ActionExecutor"
Cohesion: 0.11
Nodes (4): ActionExecutor, ActionExecutor, RehearsalTest, ActionExecutor

### Community 115 - "Context.kt"
Cohesion: 0.17
Nodes (4): AlarmManager, MonitoringRepository, TimeAdapter, NotificationManager

### Community 116 - "forecastToday"
Cohesion: 0.42
Nodes (5): ExportImport, Context, Intent, Uri, forecastToday()

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
Cohesion: 0.10
Nodes (20): Any, AudioTransition, ADDED, REMOVED, MediaCommand, NEXT, PAUSE, PLAY (+12 more)

### Community 123 - "InsightsScreen.kt"
Cohesion: 0.33
Nodes (12): BackendPill(), clickable2(), costLine(), InsightsScreen(), items2(), KpiTile(), androidx, Modifier (+4 more)

### Community 124 - "JsonFileStore.kt"
Cohesion: 0.13
Nodes (8): ObservableStore, CoachState, CoachStateStore, UsageLedger, InferenceLedger, com, ReceiptLog, ReceiptSink

### Community 125 - "AndroidActionExecutor.kt"
Cohesion: 0.09
Nodes (22): PendingIntent, ActionState, BLOCKED, COMPENSATED, COMPENSATION_FAILED, FAILED, IN_PROGRESS, NOT_STARTED (+14 more)

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
Cohesion: 0.11
Nodes (17): BroadcastReceiver, android, BootReceiver, Context, Intent, DeadlineReceiver, Context, Intent (+9 more)

### Community 131 - "Fact"
Cohesion: 0.27
Nodes (3): MemoryScreen(), Fact, FactStore

### Community 132 - "InferenceBackend"
Cohesion: 0.11
Nodes (20): BenchmarkFields, Cold, Failed, FileKey, Generating, com, Result, StateFlow (+12 more)

### Community 133 - "PlanDraft"
Cohesion: 0.15
Nodes (10): Else, OnSignal, OnStepEnd, OnTimeout, PlanDraft, PlanStep, PlanTransition, PlanValidator (+2 more)

### Community 134 - "CueService.kt"
Cohesion: 0.12
Nodes (10): ControlKind, PAUSE, RESUME, SKIP_TODAY, STOP, UnsupportedRoute, NONE, SYSTEM_AGENT (+2 more)

### Community 135 - "Working in this repository"
Cohesion: 0.33
Nodes (6): Claims, Commands, graphify, Rules that are not style preferences, Two modules, and the line between them, Working in this repository

### Community 136 - "Working in this repository"
Cohesion: 0.33
Nodes (6): Claims, Commands, graphify, Rules that are not style preferences, Two modules, and the line between them, Working in this repository

### Community 137 - "UnknownReason"
Cohesion: 0.13
Nodes (8): T, UnknownReason, ADAPTER_UNAVAILABLE, NEVER_OBSERVED, PERMISSION_DENIED, REDACTED_BY_OS, STALE, CalendarKitTest

### Community 138 - "Demo script (Task 20)"
Cohesion: 0.33
Nodes (6): Backup evidence, Before the demo (setup, not part of the story), Cut from the live demo until their rows pass, Demo script (Task 20), Ground rules, Live script (phone)

### Community 140 - "Conversation.kt"
Cohesion: 0.13
Nodes (13): Applied, Choice, CommandResult, Confirm, Handoff, MissingTarget, PendingCommand, Rejected (+5 more)

### Community 141 - "InsightsMain.kt"
Cohesion: 0.20
Nodes (11): ChatMain, at(), connect(), hm(), main(), print(), remedyText(), run() (+3 more)

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

### Community 152 - "PersonalIndex"
Cohesion: 0.33
Nodes (7): FloatArray, NeedsClarification, NeedsConfirmation, NotFound, PersonalIndex, ReferenceResolution, Resolved

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

### Community 162 - "OriginOS 7 experience pass"
Cohesion: 0.50
Nodes (3): Delivery checklist, Design contract, OriginOS 7 experience pass

### Community 163 - "SarvamReadback"
Cohesion: 0.48
Nodes (3): ByteArray, SarvamReadback, MediaPlayer

### Community 164 - "Cues Brain execution checklist"
Cohesion: 0.50
Nodes (3): Checkpoint — Typed actions, detail (24 Sep 2026), Cues Brain execution checklist, Standing verification after each applicable slice

### Community 165 - "DragAndDrop.kt"
Cohesion: 0.83
Nodes (3): dragAndDropTextSource(), Modifier, View

### Community 166 - "Measured results"
Cohesion: 0.29
Nodes (6): Developer Gemma surface (CLEANUP.md CL-38, `docs/DEVICE_MATRIX.md` M10), How to record a result, Measured results, Model provisioning (CLEANUP.md CL-36), Office Kit transport (CLEANUP.md CL-22, `docs/DEVICE_MATRIX.md` M4), On-device inference (CLEANUP.md CL-18, `docs/DEVICE_MATRIX.md` M2)

### Community 167 - "SnapshotBuilder"
Cohesion: 0.13
Nodes (8): Context, WifiReadings, SnapshotBuilder, WifiState, AudioKind, ANY, BLUETOOTH, WIRED

### Community 172 - "Corpus"
Cohesion: 0.16
Nodes (7): CaseResult, Corpus, CorpusCase, CorpusReport, BakeOffTest, RoutineDrafter, RoutineDrafter

### Community 173 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 178 - "Fixtures"
Cohesion: 0.13
Nodes (12): ContextSource, AUDIO_MANAGER, BATTERY_MANAGER, BLUETOOTH_ADAPTER, CALENDAR_PROVIDER, LOCATION_MANAGER, REHEARSAL, SYSTEM_CLOCK (+4 more)

### Community 181 - "Workstreams"
Cohesion: 0.12
Nodes (16): Acceptance matrix, Boundaries, Delivery order and cut line, Outcome, Required decisions before W2/W3/W5 implementation, Sprint 9 — Visible Intelligence, Sarvam, and Declared Sensing, Verification cadence, W0 — Integrate and stabilize Sprint 8 (+8 more)

### Community 182 - "Remedy"
Cohesion: 0.19
Nodes (11): GrantCapability, KeepCuesRunning, OsWithholds, Radio, BLUETOOTH, LOCATION, WIFI, Remedy (+3 more)

### Community 183 - "Contexts"
Cohesion: 0.16
Nodes (3): Contexts, ContextualKitsTest, Places

### Community 184 - "EngineResult"
Cohesion: 0.21
Nodes (7): Ended, EngineResult, ExitCancelled, ExitScheduled, Ignored, Skipped, Started

### Community 185 - "Day"
Cohesion: 0.19
Nodes (11): Day, FRI, MON, SAT, SUN, THU, TUE, WED (+3 more)

### Community 186 - "StatusChip"
Cohesion: 0.24
Nodes (11): Modifier, StatusChip(), Tone, AMBER, GO, STOP, toTone(), ForecastCard() (+3 more)

### Community 187 - "UtilityRestoreTest"
Cohesion: 0.31
Nodes (3): ActionExecutor, StatelessUtilityExecutor, UtilityRestoreTest

### Community 188 - "SarvamClient"
Cohesion: 0.33
Nodes (4): ByteArray, SarvamClient, Transcript, Translation

### Community 189 - "PatchSentence.kt"
Cohesion: 0.27
Nodes (5): Inexpressible, KClass, PatchResult, Sentence, SignalReadback

### Community 191 - "UiStep"
Cohesion: 0.22
Nodes (8): Click, Scroll, ScrollDirection, BACKWARD, FORWARD, SetText, UiExpectation, UiStep

### Community 193 - "GraceScheduler"
Cohesion: 0.57
Nodes (3): GraceScheduler, Context, PendingIntent

### Community 194 - "ReplySource"
Cohesion: 0.25
Nodes (7): ReplySource, FORECAST, ON_DEVICE_LLM, PARSER, RECEIPTS, ROUTINE_STORE, SARVAM_CLOUD

### Community 197 - "ModelCatalog"
Cohesion: 0.38
Nodes (5): ModelArtifact, ModelCatalog, ModelExecution, NPU_SOC_SPECIFIC, PORTABLE

### Community 198 - "FaceDownClassifier"
Cohesion: 0.48
Nodes (3): FaceDownClassifier, Reading, State

### Community 200 - ".captureScreenText"
Cohesion: 0.53
Nodes (4): Refused, ScreenCapture, Success, Unavailable

### Community 204 - ".performPlayback"
Cohesion: 0.70
Nodes (3): Blocked, MacroRunOutcome, Succeeded

### Community 206 - "UtilityMechanism"
Cohesion: 0.50
Nodes (4): UtilityMechanism, ACCESSIBILITY_BINDING, PUBLIC_INTENT, SETTINGS_PANEL

## Knowledge Gaps
- **749 isolated node(s):** `GemmaCallProtocol`, `object@L41`, `Cold`, `Unavailable`, `Succeeded` (+744 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 1076 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **41 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `Routine` connect `Routine` to `Trigger`, `ListenerHealth`, `JsonFileStore`, `CueService.kt`, `Session`, `RecordingExecutor`, `ActionSpec`, `Conversation.kt`, `CueService`, `ActionRegistry`, `InferenceReport`, `MainActivity.kt`, `CuesType`, `ReviewScreenV2.kt`, `PersonalIndex`, `GrammarParser`, `InsightsTest`, `CueCard.kt`, `NowScreen.kt`, `SignalRegistry`, `ArmResult`, `Patch`, `Finding`, `Suggestion`, `DraftResult`, `Ports.kt`, `Rehearsal`, `ContextSnapshot`, `EngineResult`, `UtilityRestoreTest`, `AudioOutputAdapter.kt`, `SimMain.kt`, `Receipt.kt`, `Truth`, `AppShortcuts.kt`, `Main.kt`, `Normalizer`, `Insights.kt`, `WifiKits.kt`, `AskScreen.kt`, `ReviewCopy`, `.run`, `SignalKitTest`, `Capability`, `DraftSourceId`, `Context.kt`, `forecastToday`, `Routine.kt`, `JsonFileStore.kt`?**
  _High betweenness centrality (0.097) - this node is a cross-community bridge._
- **Why does `CuesApplication` connect `CuesApplication` to `ListenerHealth`, `BroadcastReceiver`, `JsonFileStore`, `CueService`, `InsightsMain.kt`, `BluetoothReceiver.kt`, `ActionOutcome`, `GrammarParser`, `DiagnosticsScreen.kt`, `UtilityId`, `DraftResult`, `CuesAppFunctionService.kt`, `InferenceOutput`, `SarvamClient`, `AudioOutputAdapter.kt`, `AndroidCapabilityProvider`, `DifferentialDrafter`, `SarvamChatDrafter.kt`, `AppShortcuts.kt`, `CueTileService.kt`, `EventKind`, `.run`, `NowNextWidget.kt`, `CuesGemmaProvider`, `ExternalGemmaGate`, `DraftSourceId`, `Context.kt`, `JsonFileStore.kt`?**
  _High betweenness centrality (0.076) - this node is a cross-community bridge._
- **Why does `JsonFileStore` connect `JsonFileStore` to `Fact`, `Session`, `Routine`, `InsightsMain.kt`, `CapabilityProvider`, `GrammarParser`, `CuesApplication`, `Patch`, `Ports.kt`, `CueServiceTest`, `NamedContext`, `UtilityRestoreTest`, `UiMacro`, `SimMain.kt`, `Place`, `JsonFileStoreTest`, `SessionWindowTest`, `ReceiptRecordTest`, `JsonFileStore.kt`?**
  _High betweenness centrality (0.058) - this node is a cross-community bridge._
- **Are the 2 inferred relationships involving `Routine` (e.g. with `.parse()` and `.heroRoutine()`) actually correct?**
  _`Routine` has 2 INFERRED edges - model-reasoned connections that need verification._
- **What connects `GemmaCallProtocol`, `object@L41`, `Cold` to the rest of the system?**
  _749 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Trigger` be split into smaller, more focused modules?**
  _Cohesion score 0.04985994397759104 - nodes in this community are weakly interconnected._
- **Should `ListenerHealth` be split into smaller, more focused modules?**
  _Cohesion score 0.09269162210338681 - nodes in this community are weakly interconnected._