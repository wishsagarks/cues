# iQOO device verification matrix (Task 19)

Prepared 24 September 2026. **No row below has been run.** This matrix was
written in an environment with no loaner phone, no Android SDK and no access
to Google's Maven, so it is a protocol with empty result cells, not a record.
Fill each **Result** cell only from an observation on the loaner, and cite the
evidence file or log line it came from.

## How to record a result

1. Connect the loaner (`./dev w <host>:<port>`) and install the build under
   test (`./dev r`).
2. Run `./dev probe` before the session and again after any row that changes
   state. It is read-only and writes raw output to
   `evidence/probe-<utc>-<serial>.txt`: device and OS build, SoC, radio state,
   Cues' grants, accessibility state, what `ACTION_ASSIST` resolves to, OEM
   packages, GPU native libraries, the side-loaded model, and any zen rules,
   alarms or services Cues owns at that moment.
3. Keep `./dev l` running in a second terminal for the `CuesSession` log.
4. Write the result as **Pass**, **Fail** or **Limited**, then the observed value, the
   evidence file, and the caveat. A timing needs its method (stopwatch,
   logcat timestamps) next to the number.
5. When a row retires a CLEANUP entry, strike that entry through with the
   commit that recorded the result. A row that fails its fallback is
   still closed once the limitation is written into the named document.

Every row records the same context: date, device model, OS build
(`ro.build.fingerprint`), app commit, and radio state (airplane, Wi-Fi,
Bluetooth). `./dev probe` prints all of these.

## Hero loop gate (carried from Sprint 4/5)

These run before anything below. They decide whether the demo shows the hero
loop live. Method and pass conditions are in
[SPRINT_4_5.md](SPRINT_4_5.md#rd-register); they are not repeated here.

| ID | Check | Retires | Result | Evidence |
|---|---|---|---|---|
| R1 | Bluetooth connect/disconnect reaches the receiver: backgrounded, swiped away, and with the screen off for 10+ min | CL-06 (part) | Not run | — |
| R2 | The owned zen rule applies and releases without touching user DND or a vivo mode | CL-06 (part) | Not run | — |
| R3 | Exact alarm plus `specialUse` FGS fire on time with the screen off | CL-06 (part) | Not run | — |
| R6 | A disconnect while the process is dead shows up as a coverage gap | CL-10 | Not run | — |
| R8 | At-time callback with the screen off | CL-06 (part) | Not run | — |
| Build | `./dev b` and a cold launch on the loaner; note any edge-to-edge change under targetSdk 36 | CL-04, CL-14 | Not run | — |

## Task 19 rows

| ID | Check | Method | Pass | If it fails | Retires |
|---|---|---|---|---|---|
| M1 | **Package discovery** | `./dev probe`, sections *OEM package discovery* and *System assistant*. Record the raw package list; do not guess a package from its name. | The OriginOS packages that host Eye protection, Ultra saver, Game Mode and Jovi are identified from the device itself | `utility_bindings_accessibility.xml` stays unscoped (any package) and the disclosure says so | CL-23 item 5 |
| M2 | **NPU backend and drafting latency** | Read `ro.soc.model` from the probe. Install the model either way and confirm both paths land the same file: (a) side-load — `adb push model.litertlm /data/local/tmp/` then `adb shell run-as com.cues.android.debug sh -c 'mkdir -p files/models && cp /data/local/tmp/model.litertlm files/models/'`; (b) open Diagnostics, tap the "Cues Brain" card's Download, confirm it completes over real Wi-Fi and the checksum passes (CL-36). Relaunch, open Diagnostics, read the last inference report, and run the in-app bake-off. | The inference report names a backend, the SoC is in the NPU allowlist if that backend is NPU, p50/p95 are recorded with the model file name, and the tile's eligibility line matches the SoC read from the probe | Show only the tier the report names. With no model, or if no tier starts, the drafter is labelled "phrase grammar" everywhere, including on stage (CL-03). The tile never claims a running-state tier before a report names it | CL-18 items 2, 3, 5; CL-36; CL-03 |
| M3 | **Camera import in airplane mode** | Airplane mode on and Wi-Fi off, with the probe confirming both. Capture a printed timetable, review the rows, open one in Review and approve it. Repeat with the Photo Picker. | Rows extract, uncertain rows are flagged, nothing arms from the import screen, and the probe after the run shows the radios still off | Keep the typed and `./dev d` path, and disclose camera import as unverified | CL-20 |
| M4 | **Office Kit transfer** | Export the Cue Console from the phone, send it through Office Kit, then open the received file on the laptop from wherever Office Kit put it. | The file arrives intact (compare sizes) and every section renders from `file://` | Say "share sheet to any app" and drop the Office Kit transport claim | CL-22 items 1, 2 |
| M5 | **Origin Island rendering** | Start a session with the screen on, then with it locked. Photograph the status bar, the lock screen and Origin Island. | Record exactly where the ongoing session notification appears | No Live Update or Origin Island claim. The standard foreground notification is the stated surface | CL-15 (as a recorded decision) |
| M6 | **Accessibility utility binding** | Enable "Cues: iQOO utility bindings" and teach Eye protection on and off. Run Test on and Test off, flip the toggle by hand, then try a password field and a denylisted app. | On and off both read back. The password field and denylisted app are refused. Record whether a manual flip gets overridden | Utility bindings shown as "test only". Item 4's override risk is disclosed on the binding screen | CL-23 items 1, 3, 4, 6 |
| M7 | **AppFunctions and Jovi handoff** | AppFunctions: `./dev b` first — the provider is written but has never compiled (CL-24). Then `adb shell cmd app_function list-app-functions \| grep -A10 com.cues.android.debug`, then `adb shell cmd app_function execute-app-function --package com.cues.android.debug --function 'com.cues.app.appfunctions.CuesAppFunctionService#draftCue' --parameters '{"params":{"request":"when charging starts pin a note saying test"}}'`. Jovi: type an unsupported request, tap the handoff card, record what opens, and compare it with the probe's `ACTION_ASSIST` resolution. | `list-app-functions` shows `draftCue`/`startCue`/`stopCue`/`forecastToday`/`currentContext`; `execute-app-function` produces a real REVIEWABLE draft visible in the app. The handoff opens the phone's system assistant, and the probe names which one | If the build fails, record the compile error here rather than skipping the row. The card says "system assistant", not "Jovi", unless the probe resolves to Jovi | CL-24 |
| M8 | **Screen-off pending action** | Arm a cue whose action needs the user (for example "open Spotify when charging"), lock the screen, and trigger it. Then unlock. | The action is `PENDING` while locked, and `retryPendingActions()` runs it once `ACTION_USER_PRESENT` fires on unlock. If it expires first, it is `BLOCKED` and the session is `PARTIAL` | The wiring exists now (CL-25) but is uncompiled. Confirm `PowerManager.isInteractive()` reads false while locked on this OEM, that the receiver actually fires on unlock, and that Android's activity-launch restrictions don't still block the retried action | CL-25 |
| M9 | **QR between devices** | Show a Cue Card QR on the loaner and scan it on a second phone, and the other way round. Repeat with text share. Try one scan with a device missing from the receiver. | The card reimports to the receiver's own device id. The missing entity is refused by name. Nothing arrives armed | Text share remains the supported path, and QR is disclosed as unverified | CL-21 |

## Results

| ID | Date | Result | Observed value | Evidence | Caveat |
|---|---|---|---|---|---|
| M1 | | Not run | | | |
| M2 | | Not run | | | |
| M3 | | Not run | | | |
| M4 | | Not run | | | |
| M5 | | Not run | | | |
| M6 | | Not run | | | |
| M7 | | Not run | | | |
| M8 | | Not run | | | |
| M9 | | Not run | | | |

Timings from M2 (and R3 above) also go into `docs/MEASUREMENTS.md`, which
is created only when the first real measurement exists (see
[DEVICE_RUNBOOK.md](DEVICE_RUNBOOK.md)).
