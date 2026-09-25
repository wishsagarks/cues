# Cues

**Context you declare. Behaviour that ends.**

A proposed Android app for iQOO smartphones. The unit is a **cue**: a context you name, and the behaviour that begins and ends with it. A user speaks a cue, reviews its complete behaviour — including how it ends — and approves a persistent local rule. Speech recognition and rule drafting run on the device. After approval, ordinary Android code responds to device events; no model and no network participate in runtime decisions.

Cues is deliberately not an automation or workflow builder, and does not try to develop a deep understanding of its user. Context is **declared, not inferred** — a cue uses only the signals you attach to it and knows nothing else.

> “When my earbuds connect after 6 PM on weekdays, start a 45-minute focus timer and quiet notifications. End it if I disconnect.”

The app presents this as **WHEN / IF / DO / UNTIL / RESTORE** and asks for approval before anything is armed.

## Status

**Offline decision core implemented and tested. `:app` builds and has run on an Android emulator — a full Kinetic Obsidian redesign, exercised end to end from a Workbench patch bay through Review. Never installed on an iQOO. No measured results.**

As of 25 September 2026 (the full record is [DISCLOSURE.md](docs/DISCLOSURE.md)):

- **`:core`** is pure Kotlin/JVM and passes 357 tests. It covers the routine model, the
  three-valued evaluator, compiler and approval digest, the closed action
  and signal registries (including a calendar condition kit — `Condition.CalendarBusy`/
  `CalendarNotBusy`), grammar drafting with clause accounting, the
  session engine and receipts, Ask Cues, declared memory, the coach,
  Cue Cards, the Console export, the macro validator and the utility
  catalog, plus a redesign pass's structured receipts (`ReceiptRecord`),
  the `Insights` aggregator, live-gate readouts (`GateReadout`), an
  unreadable-input-to-remedy map (`UnknownRemedy`) and a Workbench
  patch-bay sentence builder (`PatchSentence`) that round-trips every
  kit through the grammar parser. `./dev t` runs the suite on any machine
  with a JDK.
- **`:app`** carries a full UI/UX redesign ("Kinetic Obsidian", from the
  Stitch prototype in `docs/design/stitch/`): a navigation-compose shell
  with 5 tabs (Now/Insights/Ask/Receipts/Workbench), a shared component
  library, disk-is-truth store reactivity (`ObservableStore`/
  `StoreGeneration`, so a session a background receiver started still
  shows up correctly after a process kill), rebuilt Now/Ask/Review/Insights
  screens wired to the real `:core` API above, a touch-to-patch Workbench
  patch bay whose selections are checked live through `PatchSentence`
  before compiling, a restyled session notification/widget/quick-settings
  tile, and a new adaptive launcher icon and splash. On the machine this
  redesign was built on, `./gradlew :app:assembleDebug` now succeeds and
  the app has been installed and run on an Android emulator (see
  CLEANUP.md CL-33) — the first time any part of `:app` has been observed
  running in this repository's recorded history, though only on that
  emulator, only in light theme, and only for the screens that pass
  actually navigated to (Now, Ask, Workbench, the patch bay, Review). A
  physical iQOO remains untouched. Receipts is still the old screen with
  the new theme only — structured `ReceiptRecord` cards aren't built,
  though the `:core` support and the live `ReceiptLog` wiring are. It
  includes a LiteRT-LM integration
  with no model side-loaded yet. With no model the app falls back to the
  grammar parser, and it says so on every draft.
- **Nothing has run on an iQOO.** Background delivery, the owned quiet rule,
  timing, camera, QR, Office Kit, accessibility, the utility-bindings
  macro replay, model backends and the whole redesigned UI are all listed
  in [DEVICE_MATRIX.md](docs/DEVICE_MATRIX.md), and every row is "Not run".
- **Known software gaps**, each with its retirement condition in
  [CLEANUP.md](CLEANUP.md) (34 entries as of this pass): the AppFunctions
  provider exists (`draftCue`/`startCue`/`stopCue`/`forecastToday`/
  `currentContext`) but is uncompiled on the loaner, and the "Jovi" handoff
  still opens whatever the system assistant is rather than routing through
  it (CL-24). Actions that need the user, three capabilities' in-app grant
  buttons, and accepting a coach suggestion into a draft are all fixed in
  code (CL-25, CL-26, CL-27). A promoted Live Update notification was
  deliberately not attempted — recorded as a decision, not a gap, in CL-15.

**There are no measured performance results.** Every latency, delivery and
reliability figure in the design documents is a target until it is recorded
in `docs/MEASUREMENTS.md`, which does not exist yet.

Submitted to the iQOO Hackathon 2026 City Battles (Open Innovation track).

## Documents

| Document | Purpose |
|---|---|
| [FINAL_PROBLEM_STATEMENT.md](docs/FINAL_PROBLEM_STATEMENT.md) | The problem, the proposal and its boundaries |
| [PRS.md](docs/PRS.md) | Product requirements, scope tiers and proposed evaluation |
| [FDD.md](docs/FDD.md) | Functional design: architecture, session semantics, action registry |
| [SPRINT_3.md](docs/SPRINT_3.md) | Revised six-hour plan for offline authoring, review and approval |
| [SPRINT_4_5.md](docs/SPRINT_4_5.md) | Hours 16–30: unattended device proof, R&D spikes, measured drafting choice and demo |
| [SPRINT_6.md](docs/SPRINT_6.md) | Declared named contexts, temporary patches, forecast and contextual signal kits |
| [tasks/plan.md](tasks/plan.md) | Sprint 7 ("Cues Brain") plan: Tasks 0–20; progress in [tasks/todo.md](tasks/todo.md) |
| [API_VERIFICATION.md](docs/API_VERIFICATION.md) | Official-source API and dependency gate, with every pin explained |
| [PERMISSIONS.md](docs/PERMISSIONS.md) | Every declared permission, what uses it and when it is requested |
| [DEVICE_MATRIX.md](docs/DEVICE_MATRIX.md) | The iQOO verification matrix and `./dev probe` protocol |
| [DEMO.md](docs/DEMO.md) | Demo script, its device gates, what is cut, and backup evidence |
| [DISCLOSURE.md](docs/DISCLOSURE.md) | What was verified, where, and what may not be claimed yet |
| [SUBMISSION.md](docs/SUBMISSION.md) | Phase 1 submission copy |
| [Cues_Deck.pptx](docs/Cues_Deck.pptx) | Eleven-slide deck with speaker notes |
| [CLAUDE.md](CLAUDE.md) | Repository conventions and the rules that are not style preferences |
| [RED_LIGHT.md](RED_LIGHT.md) | Building when the laptop is only reachable through Office Kit |
| [CLEANUP.md](CLEANUP.md) | Provisional choices, each with the condition that retires it |
| [docs/design/stitch/DESIGN_SYSTEM.md](docs/design/stitch/DESIGN_SYSTEM.md) | Kinetic Obsidian/Daylight design system and the Stitch screen prototypes the `:app` redesign is built from |
| [DEVELOPER_SETUP.md](docs/DEVELOPER_SETUP.md) | Kotlin, Android SDK and shared Claude/Codex skill setup |

Earlier versions are in the git history.

## Design principles

- **Natural language is untrusted input.** A local model may draft a routine; it never executes anything. Android capabilities sit behind a closed, allowlisted registry.
- **A valid schema is not a correct understanding.** Structural validity does not mean the model understood the request, so the user approves the exact compiled behavior.
- **Unknown context stays unknown.** A device connection does not establish location or intent. Missing data never grants permission to act.
- **Context is declared, not inferred.** A routine uses only the signals a user explicitly attaches, each shown with its source and age.
- **A routine has an ending.** Cleanup releases only the app's own effects and respects a later user choice or another active mode.
- **Signals are closed and composable.** A new signal is a reviewed core kit plus an app adapter; runtime plugins and model-created capabilities are not part of the vocabulary.

## Claim discipline

Natural-language automation, persistent routines and end-of-routine actions all have established precedents, including Apple Shortcuts and Siri AI in iOS 27, Tasker's AI generator, Samsung's Modes and Routines, and vivo/iQOO Jovi agent tasks with action confirmation in OriginOS 7. This proposal does not claim to have invented them, nor to exceed a contextual assistant at inference.

The proposed contribution is a focused combination — offline authoring, bounded sessions with approved endings, rehearsal before activation, and receipts explaining each run — demonstrated on real device events. Claims withdrawn during research are tracked in private working notes that are not published here.

On OriginOS, Cues positions itself as the "when-and-until" layer next to Jovi,
not a replacement for it. Requests that are not about a cue's lifetime are
handed to the system assistant. There is no Office Kit SDK integration: Office
Kit is used only as a file and clipboard transport. No OriginOS API is assumed.
OEM packages, Origin Island rendering and utility toggles are discovered on
the device and recorded before anything depends on them. The OriginOS 7
visual pass borrows motion and material quality, not iQOO branding.

## Building

`:core` needs only a JDK 21. `:app` is included in the build only when an
Android SDK is present, so the core suite runs anywhere.

```sh
./dev t                                  # core tests
./dev d "when my earbuds connect ..."    # compile a cue, print review + rehearsal
./dev demo                               # scripted offline demo, transcript to evidence/
./dev probe                              # read-only device facts for the matrix (adb)
./dev h                                  # everything else
```

## License

Not yet specified.
