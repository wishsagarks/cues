# Cues

**Context you declare. Behaviour that ends.**

A proposed Android app for iQOO smartphones. The unit is a **cue**: a context you name, and the behaviour that begins and ends with it. A user speaks a cue, reviews its complete behaviour — including how it ends — and approves a persistent local rule. Speech recognition and rule drafting run on the device. After approval, ordinary Android code responds to device events; no model and no network participate in runtime decisions.

Cues is deliberately not an automation or workflow builder, and does not try to develop a deep understanding of its user. Context is **declared, not inferred** — a cue uses only the signals you attach to it and knows nothing else.

> “When my earbuds connect after 6 PM on weekdays, start a 45-minute focus timer and quiet notifications. End it if I disconnect.”

The app presents this as **WHEN / IF / DO / UNTIL / RESTORE** and asks for approval before anything is armed.

## Status

**Offline generic signal core implemented and tested; Android runtime written but unverified; no measured results.**

The decision core is built and tested: the routine model, the three-valued
evaluator, the compiler and approval digest, the closed action registry, the
grammar-based drafting path, the closed signal-kit registry, any-Wi-Fi and
bounded at-time sessions, device-connected conditions, pinned notes, the session
engine and the receipts. It is pure Kotlin/JVM with no Android dependency, so
`./dev t` runs its suite on any machine with a JDK.

The Android runtime — the action executor, Bluetooth/power adapters, the
signal-adapter port for Wi-Fi/time, the owned Do Not Disturb rule, the timer
service and the Compose screens — is
written against real Android APIs and wired end to end, but none of it has
compiled or run anywhere: this environment has no Android SDK (see
[CLEANUP.md](CLEANUP.md) CL-04). Proving each call against the actual OS, on
the loaner hardware, is the event's first job. The on-device language model
is not yet wired in.

**There are no measured performance results.** Nothing in this repository has
run on an iQOO. Every latency, delivery and reliability figure in the design
documents remains a target. Assumptions awaiting verification are tracked in
[CLEANUP.md](CLEANUP.md).

Submitted to the iQOO Hackathon 2026 City Battles (Open Innovation track).

## Documents

| Document | Purpose |
|---|---|
| [FINAL_PROBLEM_STATEMENT.md](docs/FINAL_PROBLEM_STATEMENT.md) | The problem, the proposal and its boundaries |
| [PRS.md](docs/PRS.md) | Product requirements, scope tiers and proposed evaluation |
| [FDD.md](docs/FDD.md) | Functional design: architecture, session semantics, action registry |
| [SPRINT_3.md](docs/SPRINT_3.md) | Revised six-hour plan for offline authoring, review and approval |
| [SPRINT_4_5.md](docs/SPRINT_4_5.md) | Hours 16–30: unattended device proof, R&D spikes, measured drafting choice and demo |
| [SUBMISSION.md](docs/SUBMISSION.md) | Phase 1 submission copy |
| [Cues_Deck.pptx](docs/Cues_Deck.pptx) | Eleven-slide deck with speaker notes |
| [CLAUDE.md](CLAUDE.md) | Repository conventions and the rules that are not style preferences |
| [RED_LIGHT.md](RED_LIGHT.md) | Building when the laptop is only reachable through Office Kit |
| [CLEANUP.md](CLEANUP.md) | Provisional choices, each with the condition that retires it |

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

## Building

`:core` needs only a JDK 21. `:app` is included in the build only when an
Android SDK is present, so the core suite runs anywhere.

```sh
./dev t                                  # core tests
./dev d "when my earbuds connect ..."    # compile a cue, print review + rehearsal
./dev h                                  # everything else
```

## License

Not yet specified.
