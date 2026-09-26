# Graph Report - iqoo+  (2026-09-26)

## Corpus Check
- 274 files · ~273,706 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 3493 nodes · 8106 edges · 183 communities (149 shown, 30 thin omitted)
- Extraction: 97% EXTRACTED · 3% INFERRED · 0% AMBIGUOUS · INFERRED: 225 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `7d6a9a03`
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
- PatchBayScreen.kt
- CuesAccessibilityService
- GrammarParserTest
- CalendarKit.kt
- ActionOutcome
- TimetableExtractorTest
- CueService
- ActionId
- ActionRegistry
- DetectorsTest
- InferenceReport
- MainActivity.kt
- CuesTokens.kt
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
- ReviewScreen.kt
- AssistantIntent
- Patch
- Finding
- RoutineDrafter.kt
- DraftTrace
- CueServiceTest
- Cleanup register
- Task List
- PatchBayEditors.kt
- LedgerEvent
- Rehearsal
- RecognitionListener
- ReplyCode
- OwnedResource
- NamedContext
- Reason
- PlaceSupport
- CuesAppFunctionService.kt
- AndroidActionExecutor.kt
- LocalTimeOfDay
- Functional Design Document
- InferenceOutput
- AudioOutputAdapter.kt
- README.md
- Capability
- KineticButton
- SimMain.kt
- Session order
- Receipt.kt
- SkipFamily
- ModelNote
- EvaluatorTest
- DoKind
- IfKind
- DifferentialDrafter
- NavBars.kt
- MainActivity
- Turn
- ActionState
- Insights.kt
- EventKind
- Ports.kt
- ContextSnapshot
- RoutineDrafter
- AttemptOutcome
- JsonFileStoreTest
- Main.kt
- Sprints 4 and 5: The cue meets the phone, then earns its claims
- AskScreen.kt
- FakeAdapter
- WifiAdapter.kt
- CueService.kt
- CleanupObligation
- MonitoringRepository
- TimetableExtractor.kt
- ReceiptScreen.kt
- SignalKitTest
- Refiner
- NormalizerTest
- ReceiptRecordTest
- CuesGemmaProvider
- Platform API verification — 24 September 2026
- BlockCause
- Product Requirements Specification
- FactKind
- UnknownRemedyTest
- Normalizer
- dev
- CompositeDrafter
- ExternalGemmaGate
- test_sarvam.py
- RoutineStatus
- BatteryThresholdKit
- DraftSourceId
- RehearsalTest
- RearmPolicy
- SignalAdapter
- Day
- Cues
- Phase 1 idea submission
- Cues
- Cues Android development
- Routine.kt
- InsightsScreen.kt
- ObservableStore.kt
- EndReason
- OnImageSavedCallback
- QrCode.kt
- OnImageSavedCallback
- BroadcastReceiver
- Fact
- InferenceBackend
- ActionRisk
- IntentRouterTest
- Working in this repository
- Working in this repository
- Demo script (Task 20)
- GateReadoutTest
- Conversation.kt
- InsightsMain.kt
- SuggestionKind
- Release gate: verification and disclosure (Task 20)
- ActionRegistryTest
- CuesMotion.kt
- ReplySpeaker
- Working in this repository
- TimetableOcr.kt
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
- OriginOS 7 experience pass
- Cues Brain execution checklist
- DragAndDrop.kt
- Measured results
- UtilityCatalogTest
- SuggestionNarrator
- gradlew
- Fixtures
- GemmaCallProtocol.kt
- Sprint 9 — Visible Intelligence, Sarvam, and Declared Sensing
- SessionEngine
- ForecastStatus
- UtilityRestoreTest
- AndroidCapabilityProvider
- ReplySource
- HubPolicyTest
- FaceDownClassifierTest
- ModelCatalog
- FaceDownClassifier
- CueTileService.kt
- ClauseAccounting
- InsightsBenchmarkTest
- ModelCatalogTest

## God Nodes (most connected - your core abstractions)
1. `Routine` - 163 edges
2. `JsonFileStore` - 135 edges
3. `Trigger` - 122 edges
4. `Condition` - 117 edges
5. `CueService` - 96 edges
6. `TriggerEvent` - 89 edges
7. `GrammarParser` - 82 edges
8. `Session` - 81 edges
9. `ReasonCode` - 75 edges
10. `CuesApplication` - 57 edges

## Surprising Connections (you probably didn't know these)
- `CuesApplication` --calls--> `OnDeviceRefinePhraser`  [EXTRACTED]
  app/src/main/kotlin/com/cues/app/CuesApplication.kt → core/src/main/kotlin/com/cues/core/assistant/OnDeviceRefinePhraser.kt
- `CuesApplication` --calls--> `CueService`  [EXTRACTED]
  app/src/main/kotlin/com/cues/app/CuesApplication.kt → core/src/main/kotlin/com/cues/core/CueService.kt
- `CuesApplication` --calls--> `DifferentialDrafter`  [EXTRACTED]
  app/src/main/kotlin/com/cues/app/CuesApplication.kt → core/src/main/kotlin/com/cues/core/drafting/DifferentialDrafter.kt
- `CuesApplication` --calls--> `GrammarParser`  [EXTRACTED]
  app/src/main/kotlin/com/cues/app/CuesApplication.kt → core/src/main/kotlin/com/cues/core/drafting/GrammarParser.kt
- `CuesApplication` --calls--> `Clock`  [EXTRACTED]
  app/src/main/kotlin/com/cues/app/CuesApplication.kt → core/src/main/kotlin/com/cues/core/ports/Ports.kt

## Import Cycles
- None detected.

## Communities (183 total, 30 thin omitted)

### Community 0 - "Trigger"
Cohesion: 0.05
Nodes (18): TriggerEvent, AudioOutput, BluetoothConnection, Charging, Manual, PlaceTransition, Trigger, WifiConnection (+10 more)

### Community 1 - "ListenerHealth"
Cohesion: 0.20
Nodes (3): ListenerHealth, AdapterSupervisor, FakeSignalAdapter

### Community 2 - "ReasonCode"
Cohesion: 0.04
Nodes (54): ReasonCode, AUDIO_KIND_MISMATCH, AUDIO_OUTPUT_ACTIVE, AUDIO_OUTPUT_CHANGED, AUDIO_OUTPUT_INACTIVE, AUDIO_OUTPUT_UNKNOWN, BATTERY_AS_REQUIRED, BATTERY_NOT_AS_REQUIRED (+46 more)

### Community 3 - "PatchSentence"
Cohesion: 0.11
Nodes (12): Inexpressible, KClass, No, PatchResult, PatchSelection, PatchSentence, Phrase, Sentence (+4 more)

### Community 4 - "JsonFileStore"
Cohesion: 0.07
Nodes (3): JsonFileStore, T, UsageLedgerTest

### Community 5 - "WhenKind"
Cohesion: 0.08
Nodes (20): DoSelection, IfSelection, UntilExtra, UntilExtraKind, AT_TIME, HALF_HOUR, HOUR, WhenKind (+12 more)

### Community 6 - "Condition"
Cohesion: 0.05
Nodes (23): AtPlace, AudioOutputActive, BatteryAtLeast, BatteryBelow, CalendarBusy, CalendarNotBusy, ChargingState, Condition (+15 more)

### Community 7 - "Session"
Cohesion: 0.08
Nodes (5): Session, SessionStore, ScratchStore, ApprovalsTest, InMemorySessionStore

### Community 8 - "RecordingExecutor"
Cohesion: 0.12
Nodes (4): ActionExecutor, RecordingExecutor, ToggleAttention, SessionEngineTest

### Community 9 - "Routine"
Cohesion: 0.07
Nodes (11): CueCardShareScreen(), Routine, Embedder, FloatArray, RoutineStore, PermissionCheckCopy, ReviewCopy, EmbeddingPersonalIndexTest (+3 more)

### Community 10 - "ActionSpec"
Cohesion: 0.13
Nodes (3): ActionSpec, CleanupPolicy, ValidatorTest

### Community 11 - "EndCondition"
Cohesion: 0.12
Nodes (14): AtTime, Duration, EndCondition, ManualStop, TriggerReversed, AtTimeEndKit, dayFrom(), DurationEndKit (+6 more)

### Community 12 - "PatchBayScreen.kt"
Cohesion: 0.17
Nodes (12): CuesShape, CuesType, variableFont(), Chip(), ChipGrid(), androidx, com, Modifier (+4 more)

### Community 13 - "CuesAccessibilityService"
Cohesion: 0.07
Nodes (24): AccessibilityEvent, AccessibilityNodeInfo, AccessibilityService, Blocked, CuesAccessibilityService, StateFlow, MacroRunOutcome, Refused (+16 more)

### Community 14 - "GrammarParserTest"
Cohesion: 0.12
Nodes (4): corpusText(), object@L360, GrammarParserTest, parser()

### Community 15 - "CalendarKit.kt"
Cohesion: 0.20
Nodes (5): CalendarBusyKit, CalendarConditionKit, CalendarNotBusyKit, C, KClass

### Community 16 - "ActionOutcome"
Cohesion: 0.11
Nodes (17): AndroidActionExecutor, ActionExecutor, ActionArgs, Alarm, CalendarEvent, ComposeMessage, Dnd, FocusTimer (+9 more)

### Community 18 - "CueService"
Cohesion: 0.08
Nodes (20): Conversation, ChatMain, CueService, Diagnostics, DrafterSetup, com, FactReference, ActionExecutor (+12 more)

### Community 19 - "ActionId"
Cohesion: 0.07
Nodes (19): FixtureExecutor, ActionExecutor, ActionId, ADD_CALENDAR_EVENT, COMPOSE_MESSAGE, MEDIA_CONTROL, NOTIFY_RESULT, OPEN_APP (+11 more)

### Community 20 - "ActionRegistry"
Cohesion: 0.21
Nodes (8): ActionDefinition, ActionRegistry, ArgResult, Invalid, Presence, NEEDS_USER, UNATTENDED_OK, Valid

### Community 22 - "InferenceReport"
Cohesion: 0.19
Nodes (9): ConsoleHtml, ConsoleMain, CuesExporter, InferenceReport, ForecastItem, ReceiptEntry, CuesExporterTest, com (+1 more)

### Community 23 - "MainActivity.kt"
Cohesion: 0.15
Nodes (18): ExportImport, Context, Intent, Uri, ObservableStore, availableSignalsNow(), CuesApp(), friendlyLabel() (+10 more)

### Community 24 - "CuesTokens.kt"
Cohesion: 0.33
Nodes (7): ClauseBlock(), ClauseRow(), ClauseKind, Modifier, CuesColorTokens, CuesPalette, Color

### Community 25 - "ReviewScreenV2.kt"
Cohesion: 0.14
Nodes (27): cuesGridBackground(), Color, Modifier, SlabCard(), SlabTier, ONE, THREE, TWO (+19 more)

### Community 26 - "GrammarParser"
Cohesion: 0.17
Nodes (10): main(), object@L9, AmbiguousDevice, GrammarParser, IntRange, RoutineDrafter, ResolvedApp, ResolvedTrigger (+2 more)

### Community 27 - "InsightsTest"
Cohesion: 0.24
Nodes (3): LedgerView, ActionRecord, InsightsTest

### Community 28 - "CuesApplication"
Cohesion: 0.06
Nodes (26): CuesApplication, com, AppBakeOff, object@L41, FakeSarvamChatSession, RoutineDrafter, SarvamChatDrafter, SarvamChatSession (+18 more)

### Community 29 - "DiagnosticsScreen.kt"
Cohesion: 0.07
Nodes (37): Uri, ModelDownloader, DeviceDiagnostics, DeviceDiagnosticsRepository, RecognitionSupportCallback, ManualObservation, ISSUE_OBSERVED, NO_ISSUE_OBSERVED (+29 more)

### Community 30 - "Badges.kt"
Cohesion: 0.14
Nodes (23): ClauseBadge(), clauseColors(), ClauseKind, DO, IF, RESTORE, UNTIL, WHEN (+15 more)

### Community 31 - "CueCard.kt"
Cohesion: 0.21
Nodes (13): CardDecodeResult, CueCard, CueCards, T, Malformed, Missing, MissingEntity, Ok (+5 more)

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
Cohesion: 0.17
Nodes (6): EndKit, com, E, KClass, T, TriggerKit

### Community 36 - "SignalRegistry"
Cohesion: 0.18
Nodes (5): C, com, E, T, SignalRegistry

### Community 37 - "UtilityId"
Cohesion: 0.10
Nodes (16): UtilityBindingScreen(), UtilityBinding, UtilityId, EYE_PROTECTION, GAME_MODE, ULTRA_SAVER, UtilityState, OFF (+8 more)

### Community 38 - "ReviewScreen.kt"
Cohesion: 0.10
Nodes (22): ActionRiskRow(), grantLabel(), com, Intent, PermissionCheckRow(), RehearsalRowView(), ReviewRow(), ReviewScreen() (+14 more)

### Community 39 - "AssistantIntent"
Cohesion: 0.20
Nodes (10): AssistantIntent, Capabilities, Control, Create, Explain, Forecast, ListCues, Refine (+2 more)

### Community 40 - "Patch"
Cohesion: 0.15
Nodes (11): describe(), DetailRow(), RoutineDetailScreen(), TemporaryPatchCard(), tomorrowMorningMillis(), Patch, PatchKind, SkipOccurrence (+3 more)

### Community 41 - "Finding"
Cohesion: 0.16
Nodes (6): Finding, ValidationResult, Validator, ConditionKit, C, SignalText

### Community 42 - "RoutineDrafter.kt"
Cohesion: 0.12
Nodes (14): RoutineDrafter, ClauseKind, FILLER, MAPPED, UNACCOUNTED, ClauseSpan, DifferingClause, ACTIONS (+6 more)

### Community 43 - "DraftTrace"
Cohesion: 0.29
Nodes (3): DrafterAttempt, DraftTrace, DraftCreditTest

### Community 44 - "CueServiceTest"
Cohesion: 0.22
Nodes (3): CueServiceTest, RoutineDrafter, RoutineDrafter

### Community 45 - "Cleanup register"
Cohesion: 0.05
Nodes (42): ~~CL-01 — Termux on-device build route~~, CL-02 — Android runtime written against real APIs, verified on none of them, CL-03 — Two drafting paths at equal weight, CL-04 — Unverified Android dependency versions, CL-05 — Pre-event code and what the documents claim, CL-06 — Device and OS assumptions, CL-07 — Named Wi-Fi requires a device decision, CL-08 — Pre-event signal framework disclosure (+34 more)

### Community 46 - "Task List"
Cohesion: 0.06
Nodes (31): Architecture Decisions, Checkpoint: Brain and Camera, Checkpoint: Bridge, Checkpoint: Coach, Checkpoint: Conversation, Checkpoint: Ecosystem, Checkpoint: Typed Actions, Definition of Complete (+23 more)

### Community 47 - "PatchBayEditors.kt"
Cohesion: 0.26
Nodes (17): DayToggleRow(), EnumChipRow(), androidx, IntRange, Modifier, T, NumberStepper(), PatchTextField() (+9 more)

### Community 48 - "LedgerEvent"
Cohesion: 0.17
Nodes (17): LearningSettings(), CoachMain, Detection, Detectors, EvidenceLine, NoPattern, NotEnoughData, Suggest (+9 more)

### Community 49 - "Rehearsal"
Cohesion: 0.34
Nodes (4): MutableClock, Rehearsal, RehearsalReport, RehearsalRow

### Community 50 - "RecognitionListener"
Cohesion: 0.16
Nodes (5): android, ByteArray, LocalSpeechInput, RecognitionListener, SpeechRecognizer

### Community 51 - "ReplyCode"
Cohesion: 0.11
Nodes (17): ReplyCode, CAPABILITIES, CONFIRM_COMMAND, CUES_LIST, DRAFT_FAILED, DRAFT_READY, DRAFT_REFINED, EXPLANATION (+9 more)

### Community 52 - "OwnedResource"
Cohesion: 0.14
Nodes (8): OwnedResource, DND_CONTRIBUTION, FOCUS_TIMER, PINNED_NOTE, RINGER_MODE, UTILITY_CONTRIBUTION, ActionExecutor, ActionExecutor

### Community 53 - "NamedContext"
Cohesion: 0.09
Nodes (8): CliContexts, NamedContext, NamedContextStore, ContextualStores, EmptyContexts, Contexts, ContextualKitsTest, Places

### Community 54 - "Reason"
Cohesion: 0.13
Nodes (14): Reason, EventProvenance, MANUAL, PHYSICAL, REHEARSAL, ReceiptKind, ENDED, EXIT_CANCELLED (+6 more)

### Community 56 - "CuesAppFunctionService.kt"
Cohesion: 0.06
Nodes (29): AskLocalGemmaParams, AskLocalGemmaResult, BaseCuesAppFunctionService, CurrentContextResult, DraftCueParams, DraftCueResult, ForecastItemResult, ForecastTodayResult (+21 more)

### Community 57 - "AndroidActionExecutor.kt"
Cohesion: 0.17
Nodes (10): PendingIntent, Uri, MediaCommand, NEXT, PAUSE, PLAY, PREVIOUS, RingerModeKind (+2 more)

### Community 58 - "LocalTimeOfDay"
Cohesion: 0.19
Nodes (3): Comparable, LocalTimeOfDay, SnapshotBuilderTest

### Community 59 - "Functional Design Document"
Cohesion: 0.09
Nodes (22): Action registry, Architecture, Cues Brain additions (Sprint 7), DND ownership, Event and context adapters, Expected state at a deadline (stretch), Functional Design Document, Implementation order (+14 more)

### Community 60 - "InferenceOutput"
Cohesion: 0.10
Nodes (10): GatedLlmSession, LiteRtLmSession, OnDeviceRefinePhraser, RefinePhrasing, OllamaLlmSession, FakeLlmSession, InferenceOutput, LlmSession (+2 more)

### Community 61 - "AudioOutputAdapter.kt"
Cohesion: 0.22
Nodes (9): AudioOutputAdapter, AudioDeviceCallback, Context, AudioDeviceInfo, AudioManager, AudioKind, ANY, BLUETOOTH (+1 more)

### Community 62 - "README.md"
Cohesion: 0.21
Nodes (4): Sprint 5 device runbook, Building during Red Light, Office Kit drives the laptop, What does not depend on either route

### Community 63 - "Capability"
Cohesion: 0.04
Nodes (34): CalendarReadings, Context, Context, Readings, Context, WifiReadings, SnapshotBuilder, UnreadableInputs (+26 more)

### Community 64 - "KineticButton"
Cohesion: 0.26
Nodes (10): clickableNoIndication(), GhostButton(), HaltButton(), KineticButton(), KineticSwitch(), Modifier, ShortcutDeck(), CuesHaptics (+2 more)

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

### Community 69 - "ModelNote"
Cohesion: 0.18
Nodes (9): DraftCredit, ModelNote, CHOSEN_FROM_DISAGREEMENT, MODEL_FAILED, MODEL_INVALID, MODEL_NOT_INSTALLED, MODEL_OFF, MODEL_TIMED_OUT (+1 more)

### Community 71 - "DoKind"
Cohesion: 0.17
Nodes (12): DoKind, ALARM, CALENDAR_EVENT, COMPOSE_MESSAGE, FOCUS_TIMER, MEDIA, NOTIFY, OPEN_APP (+4 more)

### Community 72 - "IfKind"
Cohesion: 0.17
Nodes (12): IfKind, AT_PLACE, AUDIO_ACTIVE, BATTERY_AT_LEAST, BATTERY_BELOW, CALENDAR_BUSY, CALENDAR_FREE, CHARGING (+4 more)

### Community 73 - "DifferentialDrafter"
Cohesion: 0.43
Nodes (3): DifferentialDrafter, DifferentialDrafterTest, RoutineDrafter

### Community 74 - "NavBars.kt"
Cohesion: 0.29
Nodes (8): CuesBottomNav(), CuesTopBar(), Composable, ImageVector, Modifier, TabItem(), TabSpec, CuesRoutes

### Community 75 - "MainActivity"
Cohesion: 0.18
Nodes (7): android, Bundle, MainActivity, TileService, ScreenTile, CuesTheme(), ComponentActivity

### Community 77 - "ActionState"
Cohesion: 0.22
Nodes (9): ActionState, BLOCKED, COMPENSATED, COMPENSATION_FAILED, FAILED, IN_PROGRESS, NOT_STARTED, PENDING (+1 more)

### Community 78 - "Insights.kt"
Cohesion: 0.13
Nodes (15): BlockedGroup, CleanupLedger, Coverage, InferenceLedgerView, InferenceUsage, InsightCounts, Insights, OutstandingObligation (+7 more)

### Community 79 - "EventKind"
Cohesion: 0.09
Nodes (21): Context, Intent, PowerReceiver, Context, Intent, TimeTriggerReceiver, EventKind, AUDIO_OUTPUT_ADDED (+13 more)

### Community 80 - "Ports.kt"
Cohesion: 0.12
Nodes (7): Place, com, MacroStore, PlaceStore, ReceiptLog, ReceiptSink, EmptyPlaces

### Community 81 - "ContextSnapshot"
Cohesion: 0.09
Nodes (21): chargingWord(), contains(), Decision, describe(), Evaluator, freshened(), FreshnessPolicy, full() (+13 more)

### Community 82 - "RoutineDrafter"
Cohesion: 0.22
Nodes (5): RoutineDrafter, RoutineDrafter, RoutineDrafter, RoutineDrafter, RoutineDrafter

### Community 83 - "AttemptOutcome"
Cohesion: 0.12
Nodes (16): AttemptOutcome, CANCELLED, CLARIFY, DRAFTED, FAILED, INVALID, SKIPPED_UNAVAILABLE, TIMED_OUT (+8 more)

### Community 85 - "Main.kt"
Cohesion: 0.06
Nodes (32): ByteArray, SarvamClient, Transcript, Translation, ByteArray, SarvamReadback, ByteArray, SarvamSpeechInput (+24 more)

### Community 86 - "Sprints 4 and 5: The cue meets the phone, then earns its claims"
Cohesion: 0.17
Nodes (12): Exit criteria (end of event), Gate at hour 23, Not in these sprints, Pre-event Sprint 3.5: signal framework (Sep 23–25), Proposed requirement additions, R&D register, Sprint 4 test cases (on the loaner, not in :core), Sprint 4: Unattended proof (hours 16–23) (+4 more)

### Community 87 - "AskScreen.kt"
Cohesion: 0.10
Nodes (33): Cold, RunnerState, InstalledApp, installedApps(), Context, rankInstalledApps(), AdapterStatus, AskScreen() (+25 more)

### Community 88 - "FakeAdapter"
Cohesion: 0.31
Nodes (3): AdapterSupervisorTest, FakeAdapter, com

### Community 89 - "WifiAdapter.kt"
Cohesion: 0.29
Nodes (4): WifiAdapter, NetworkCallback, ConnectivityManager, Network

### Community 90 - "CueService.kt"
Cohesion: 0.29
Nodes (5): Choice, Confirm, Handoff, PendingCommand, ReplyChip

### Community 93 - "TimetableExtractor.kt"
Cohesion: 0.28
Nodes (6): ImportEntryCard(), ImportReviewScreen(), fullName(), TimetableEntry, TimetableExtractionResult, TimetableExtractor

### Community 94 - "ReceiptScreen.kt"
Cohesion: 0.83
Nodes (3): LegacyReceiptCard(), ReceiptScreen(), StructuredReceiptCard()

### Community 99 - "CuesGemmaProvider"
Cohesion: 0.24
Nodes (5): CuesGemmaProvider, Bundle, Uri, ContentProvider, ContentValues

### Community 100 - "Platform API verification — 24 September 2026"
Cohesion: 0.14
Nodes (13): AppFunctions, Camera and offline text recognition, Cue Cards and QR, Dependency policy, Desk Bridge and the Cue Console, Gemma model distribution (on-device "brain" download), LiteRT-LM and Qualcomm NPU, Live Updates (+5 more)

### Community 101 - "BlockCause"
Cohesion: 0.50
Nodes (4): BlockCause, ExpiredWaitingForYou, NeedsCapabilities, Unattributed

### Community 102 - "Product Requirements Specification"
Cohesion: 0.20
Nodes (10): Core journey, Principles, Product and user, Product Requirements Specification, Proposed evaluation, Requirements, Review example, Risks and scope decisions (+2 more)

### Community 103 - "FactKind"
Cohesion: 0.17
Nodes (11): FactKind, DATE, DAYS, DEVICE_ALIAS, PLACE_ALIAS, TEXT, FactSource, CAMERA (+3 more)

### Community 105 - "Normalizer"
Cohesion: 0.07
Nodes (5): Normalizer, E, ReviewCopyLinesTest, TemplatesTest, CueCardTest

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
Cohesion: 0.18
Nodes (9): AppShortcuts, Context, RoutineStatus, ARMED, DISABLED, DRAFT, INVALID, PAUSED (+1 more)

### Community 111 - "BatteryThresholdKit"
Cohesion: 0.24
Nodes (3): BatteryAtLeastKit, BatteryThresholdKit, C

### Community 112 - "DraftSourceId"
Cohesion: 0.06
Nodes (22): Result, ExternalCallEntry, ExternalCallerLedger, ModelDraftGuard, RoutineDrafter, InferenceCost, CostBasis, CLOUD_METERED (+14 more)

### Community 113 - "RehearsalTest"
Cohesion: 0.12
Nodes (3): ActionExecutor, RehearsalTest, ActionExecutor

### Community 115 - "SignalAdapter"
Cohesion: 0.23
Nodes (3): AlarmManager, TimeAdapter, SignalAdapter

### Community 117 - "Day"
Cohesion: 0.07
Nodes (27): AddAction, AddCondition, AddDays, ControlKind, PAUSE, RESUME, SKIP_TODAY, STOP (+19 more)

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
Cohesion: 0.12
Nodes (15): Any, AudioTransition, ADDED, REMOVED, DeviceTransition, CONNECTED, DISCONNECTED, Named (+7 more)

### Community 123 - "InsightsScreen.kt"
Cohesion: 0.24
Nodes (15): BackendPill(), clickable2(), costLine(), InsightsScreen(), items2(), KpiTile(), androidx, Modifier (+7 more)

### Community 124 - "ObservableStore.kt"
Cohesion: 0.11
Nodes (7): StateFlow, StoreGeneration, CoachState, CoachStateStore, UsageLedger, InferenceLedger, InMemoryCoachState

### Community 125 - "EndReason"
Cohesion: 0.15
Nodes (12): EndReason, COVERAGE_GAP, DEADLINE_REACHED, MANUAL_STOP, RECONCILED_EXPIRED, ROUTINE_PAUSED, START_FAILED, TRIGGER_REVERSED (+4 more)

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
Nodes (17): BroadcastReceiver, android, BluetoothReceiver, Context, Intent, BootReceiver, Context, Intent (+9 more)

### Community 131 - "Fact"
Cohesion: 0.27
Nodes (3): MemoryScreen(), Fact, FactStore

### Community 132 - "InferenceBackend"
Cohesion: 0.11
Nodes (19): BenchmarkFields, Failed, FileKey, Generating, com, Result, StateFlow, Loading (+11 more)

### Community 133 - "ActionRisk"
Cohesion: 0.09
Nodes (19): Severity, ERROR, WARNING, Else, OnSignal, OnStepEnd, OnTimeout, PlanDraft (+11 more)

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
Cohesion: 0.22
Nodes (8): Applied, CommandResult, MissingTarget, Rejected, SystemAgent, GOOGLE_ASSISTANT, JOVI, SYSTEM

### Community 141 - "InsightsMain.kt"
Cohesion: 0.31
Nodes (9): at(), connect(), hm(), main(), print(), remedyText(), run(), sign() (+1 more)

### Community 142 - "SuggestionKind"
Cohesion: 0.29
Nodes (7): SuggestionKind, ADD_SIGNAL_CUE, ADD_TIME_TRIGGER, CHECK_OR_REMOVE_ACTION, FIX_PERMISSION, REMOVE_DAY, SHORTER_DURATION

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

### Community 164 - "Cues Brain execution checklist"
Cohesion: 0.50
Nodes (3): Checkpoint — Typed actions, detail (24 Sep 2026), Cues Brain execution checklist, Standing verification after each applicable slice

### Community 165 - "DragAndDrop.kt"
Cohesion: 0.83
Nodes (3): dragAndDropTextSource(), Modifier, View

### Community 166 - "Measured results"
Cohesion: 0.29
Nodes (6): Developer Gemma surface (CLEANUP.md CL-38, `docs/DEVICE_MATRIX.md` M10), How to record a result, Measured results, Model provisioning (CLEANUP.md CL-36), Office Kit transport (CLEANUP.md CL-22, `docs/DEVICE_MATRIX.md` M4), On-device inference (CLEANUP.md CL-18, `docs/DEVICE_MATRIX.md` M2)

### Community 173 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 178 - "Fixtures"
Cohesion: 0.07
Nodes (23): GrantCapability, KeepCuesRunning, OsWithholds, Radio, BLUETOOTH, LOCATION, WIFI, Remedy (+15 more)

### Community 181 - "Sprint 9 — Visible Intelligence, Sarvam, and Declared Sensing"
Cohesion: 0.11
Nodes (17): Acceptance matrix, Boundaries, Delivery order and cut line, Implementation status — 2026-09-26, Outcome, Required decisions before W2/W3/W5 implementation, Sprint 9 — Visible Intelligence, Sarvam, and Declared Sensing, Verification cadence (+9 more)

### Community 184 - "SessionEngine"
Cohesion: 0.10
Nodes (9): Ended, EngineResult, ExitCancelled, ExitScheduled, Ignored, isLive(), SessionEngine, Skipped (+1 more)

### Community 186 - "ForecastStatus"
Cohesion: 0.15
Nodes (16): Modifier, StatusChip(), Tone, AMBER, GO, STOP, toTone(), ForecastCard() (+8 more)

### Community 187 - "UtilityRestoreTest"
Cohesion: 0.27
Nodes (3): ActionExecutor, StatelessUtilityExecutor, UtilityRestoreTest

### Community 194 - "ReplySource"
Cohesion: 0.25
Nodes (7): ReplySource, FORECAST, ON_DEVICE_LLM, PARSER, RECEIPTS, ROUTINE_STORE, SARVAM_CLOUD

### Community 197 - "ModelCatalog"
Cohesion: 0.33
Nodes (6): KnownNpuDispatchFailure, ModelArtifact, ModelCatalog, ModelExecution, NPU_SOC_SPECIFIC, PORTABLE

### Community 198 - "FaceDownClassifier"
Cohesion: 0.48
Nodes (3): FaceDownClassifier, Reading, State

## Knowledge Gaps
- **750 isolated node(s):** `GemmaCallProtocol`, `object@L41`, `Cold`, `Unavailable`, `Succeeded` (+745 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 1078 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **30 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `Routine` connect `Routine` to `ListenerHealth`, `JsonFileStore`, `RecordingExecutor`, `ActionSpec`, `Conversation.kt`, `InferenceReport`, `MainActivity.kt`, `CuesTokens.kt`, `ReviewScreenV2.kt`, `PersonalIndex`, `GrammarParser`, `InsightsTest`, `CueCard.kt`, `NowScreen.kt`, `SignalRegistry`, `ReviewScreen.kt`, `Patch`, `Finding`, `RoutineDrafter.kt`, `Rehearsal`, `Reason`, `SessionEngine`, `UtilityRestoreTest`, `AudioOutputAdapter.kt`, `Capability`, `KineticButton`, `SimMain.kt`, `Receipt.kt`, `Insights.kt`, `Ports.kt`, `ContextSnapshot`, `Main.kt`, `AskScreen.kt`, `WifiAdapter.kt`, `CueService.kt`, `SignalKitTest`, `Refiner`, `Normalizer`, `RoutineStatus`, `SignalAdapter`, `Routine.kt`, `ObservableStore.kt`?**
  _High betweenness centrality (0.098) - this node is a cross-community bridge._
- **Why does `JsonFileStore` connect `JsonFileStore` to `SimMain.kt`, `ReceiptRecordTest`, `Fact`, `UtilityId`, `Session`, `Patch`, `Routine`, `RecordingExecutor`, `CleanupObligation`, `CueServiceTest`, `CuesApplication`, `InsightsMain.kt`, `Ports.kt`, `CueService`, `JsonFileStoreTest`, `NamedContext`, `UtilityRestoreTest`, `ObservableStore.kt`?**
  _High betweenness centrality (0.064) - this node is a cross-community bridge._
- **Why does `CuesApplication` connect `CuesApplication` to `ListenerHealth`, `BroadcastReceiver`, `JsonFileStore`, `ActionOutcome`, `CueService`, `MainActivity.kt`, `GrammarParser`, `DiagnosticsScreen.kt`, `UtilityId`, `CuesAppFunctionService.kt`, `InferenceOutput`, `AudioOutputAdapter.kt`, `AndroidCapabilityProvider`, `DifferentialDrafter`, `CueTileService.kt`, `EventKind`, `Main.kt`, `AskScreen.kt`, `WifiAdapter.kt`, `MonitoringRepository`, `CuesGemmaProvider`, `ExternalGemmaGate`, `DraftSourceId`, `SignalAdapter`, `ObservableStore.kt`?**
  _High betweenness centrality (0.061) - this node is a cross-community bridge._
- **Are the 2 inferred relationships involving `Routine` (e.g. with `.parse()` and `.heroRoutine()`) actually correct?**
  _`Routine` has 2 INFERRED edges - model-reasoned connections that need verification._
- **What connects `GemmaCallProtocol`, `object@L41`, `Cold` to the rest of the system?**
  _750 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Trigger` be split into smaller, more focused modules?**
  _Cohesion score 0.051061388410786 - nodes in this community are weakly interconnected._
- **Should `ReasonCode` be split into smaller, more focused modules?**
  _Cohesion score 0.037037037037037035 - nodes in this community are weakly interconnected._