# Release gate: verification and disclosure (Task 20)

Revision of 24 September 2026. This is what was verified before the event,
where, and what was not. It is the document to read before any claim goes
into the deck, the submission copy or a spoken pitch.

## What this pass verified, and where

Environment: a cloud container with JDK 21, **no Android SDK**, and
`dl.google.com` denied by network policy. Maven Central rate-limited
the first four Gradle attempts (HTTP 429); the fifth resolved.

| Check | Result | Source |
|---|---|---|
| `./dev t` (all `:core` tests) | **267 tests, 0 failures** | Gradle test reports from this run |
| `./dev d` hero cue, weekend form | Compiles. Labelled "drafted by grammar parser". Six rehearsal rows | `evidence/demo-*.txt` step 1 |
| `./dev d` with an unmappable clause ("text Mum") | Blocked: "Cues could not account for: text, Mum" (after the fix below) | step 2 |
| `./dev sim` | Start, duplicate skipped, grace reconnect kept, timeout releases timer and quiet rule | step 3 |
| `./dev chat` sequence | Create, then refine (approval cleared), then injection refused, then handoff offered | step 4 |
| `./dev card` | Rebinds to the receiver's device id. Tampered card refused | step 5 |
| `./dev today`, `./dev coach` | Fixture forecast and evidence print | steps 6–7 |
| `./dev console` | Single HTML file. Every section rendered in headless Chromium | step 8 |
| `./dev bo` | Grammar parser 14/26, 0 wrong-meaning accepts, network=false. **This container's JVM, not a phone.** Latency here is not a claim | step 9 |
| `./dev b`, `./dev perms` | **Not run.** No SDK, and Google's Maven is blocked here | — |

**`./dev b` and `./dev perms` must be re-run on the laptop before the
demo.** This pass changed `:app`: a manifest permission (`SET_ALARM`), a
`catch` in `AndroidActionExecutor`, and a comment. None of that has been
compiled. The last green APK build and no-`INTERNET` check are the ones
`tasks/todo.md` records from the session that wrote Tasks 12–14, on another
machine, before these edits.

## Fixed in this pass

- `./dev d` called the parser directly and skipped clause accounting. A
  request with an unmapped clause printed as a clean draft, with no error,
  while the app blocked it. The accounting now lives in one function,
  `ClauseAccounting.stamp`, used by both `CueService` and the CLI, with a
  regression test.
- `./dev console` showed fixture inference figures ("812ms load, 14.2 tok/s")
  under a heading describing a phone's data. The CLI page now carries a
  sample-data banner. Real phone exports are unchanged.
- `SET_ALARM` could not work: the platform requires
  `com.android.alarm.permission.SET_ALARM` to invoke `ACTION_SET_ALARM`, and
  a refusal would have thrown an uncaught `SecurityException`. The permission
  is now declared, and a refusal becomes `BLOCKED`.
- `./dev r` launched `com.cues.android`, but debug builds install as
  `com.cues.android.debug`. It also discarded `am start`'s error, so it
  printed "running" either way.
- The model side-load comment said to `adb push` into private storage,
  which adb cannot write to. The working `run-as` route is now in
  DEVICE_MATRIX M2.

## Software gaps found and left open

These are not device unknowns. The code does not do what a document or
screen implies. Each has a CLEANUP entry with its retirement condition.

| Gap | Effect | Entry |
|---|---|---|
| The attention port was never wired | Fixed in `:core` and tested (268 tests); `:app` wiring written, uncompiled | CL-25 |
| No in-app grant for notifications, DND access or exact alarms | Fixed in `:app`, uncompiled — Review's rows now have grant buttons. The demo keeps the adb fallback until this runs on the loaner | CL-26 |
| Coach Accept seeds text the parser cannot draft | Fixed in `:core`, tested (272 tests); `:app` wiring written, uncompiled | CL-27 |
| "Hand off to Jovi" fires the generic `ACTION_ASSIST`. AppFunctions (Task 17) written but uncompiled | The label promises Jovi; a system agent's call to Cues has never actually run | CL-24 |
| A screen capture reaches `CuesApp` and is ignored | "Cue this screen" does nothing visible yet | CL-23 item 2 |
| ~~Utility bindings reachable only from their test buttons~~ | Fixed 24 Sep 2026: `GrammarParser` now drafts `USE_UTILITY` from "turn on/off eye protection/ultra saver/game mode". Unverified on the loaner (the toggle itself, not the drafting) | CL-23 item 7 |
| Declared-memory facts can't end a cue from speech | "until my exam" is unsupported | CL-16 |
| Review has no field editor | PRS lists "editable fields". Today a change goes through Ask Cues refine or a new sentence | — |
| Task 9 leftovers: calendar condition, share target, shortcuts, Workbench drag/drop | Not built | tasks/todo.md |

## Device-only items not proven

Nothing has run on an iQOO. Every row of
[DEVICE_MATRIX.md](DEVICE_MATRIX.md) is **Not run**. Until a row has a
result there:

| Do not claim | Until |
|---|---|
| Any latency, delivery delay or cleanup delay | R3/M2 recorded in `docs/MEASUREMENTS.md` |
| Background Bluetooth/charging delivery, or survival after a swipe or screen-off | R1, R8 |
| "Releases only our quiet rule" as observed on OriginOS | R2 |
| NPU, GPU or any model drafting on the phone | M2 (CL-18). Until then the drafter is the grammar parser |
| Camera or QR import works offline | M3, M9 |
| Office Kit carries the Console | M4 |
| Origin Island shows the session | M5 (not built: CL-15) |
| Utility bindings toggle iQOO settings | M6 |
| Jovi integration of any kind | M7 |
| Coverage-gap detection catches a real missed event | R6 (CL-10) |
| Emulator results stand for the phone | Never. The 23 Sep AVD run (commit `a9f9c09`) proves launch, draft and arm on stock Android only |
| Any Sarvam AI call (translate, speech-to-text, text-to-speech, chat completion) succeeding against a live key | CLEANUP.md CL-35 item 1: a real call against `SarvamClient.kt` is exercised and confirmed — `sarvam/test_sarvam.py`'s Python-SDK smoke test does not count |
| Cloud voice input (regional-language speech-to-text) working end to end | CLEANUP.md CL-35 items 1–2: a live key succeeds and `:app` compiles at all, which is currently blocked on CL-24's AGP/compileSdk gap |
| A translated read-back of the assistant's reply playing end to end | CLEANUP.md CL-35 items 1–2, same as above |
| The cloud chat drafter's (`SarvamChatDrafter`) disagreement-tie-breaker path resolving as designed | CLEANUP.md CL-35 items 1–2, same as above |

## What can be said today

- The decision core is implemented and passes 267 unit tests on a JVM.
- After approval, no model is consulted. This is structural: there is no
  path from a drafter to an executor, and review copy is rendered from reason
  codes.
- The source manifest removes `INTERNET`. The merged-manifest check passed
  when last run and must be re-run for this revision.
- The app has been built and launched on an Android emulator, not on an
  iQOO.
- Named precedents (Shortcuts, Tasker, Modes and Routines, Jovi) are
  acknowledged. The claim is the combination, demonstrated, not invention.
