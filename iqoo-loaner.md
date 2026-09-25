# The loaner runbook

Written 25 Sep 2026, the night before first device contact. This is the
execution order for the first Red Light session with the physical iQOO —
everything in it is already written and tested in `:app`/`:core`; nothing new
should get built during this session unless a row below fails and needs a
small, targeted fix. The scarce resource tomorrow is device time, not code.

Every method, pass condition and retirement mapping below is copied from
[docs/DEVICE_MATRIX.md](docs/DEVICE_MATRIX.md) — that file is still the
source of truth; this one is just it reordered into "do this, then this" for
a phone that will only be in hand for limited windows.

## Tonight, before the phone arrives

None of this needs the device. Do it now so the first hour tomorrow isn't
spent on prep instead of proof.

- [ ] Confirm `./dev t` and `./dev b` are green on the current commit —
      today's baseline, so any failure tomorrow is a device finding, not a
      regression you shipped tonight.
- [ ] Re-read [RED_LIGHT.md](RED_LIGHT.md) Route A steps 1–4 (Office Kit
      pairing, wireless debugging, `adb pair`, `./dev w`) so it's muscle
      memory — this cannot be rehearsed without the device, only read.
- [ ] Print a timetable with a clear day/time/label/room grid for **M3**
      (camera import). Without a printed sheet, M3 either gets skipped or
      run against a screen, which isn't the airplane-mode-camera claim.
- [ ] Decide the **M2** model question now: either locate/stage a
      `model.litertlm` file to side-load, or accept going in that no model
      exists yet and M2 will simply confirm the labelled parser-only
      fallback (CL-03, CL-18). Don't discover this decision live.
- [ ] Line up a second Android phone for **M9** (Cue Card QR round trip) —
      borrow one tonight if needed.
- [ ] Skim [docs/DEMO.md](docs/DEMO.md)'s live script once. Steps 4–10 there
      are literally gated on rows R1/R2/CL-26/M7/M9 below — know which live
      step each row unlocks.

## Session order

Do these in order. Each has a rough time budget — treat it as a stop
condition, not a target: if a row is dragging, record **Limited** with what
you observed and its stated fallback, and move on. A skipped row is
recoverable in a later Red Light window; a whole session spent on one row
is not.

### 0. Connect and baseline — ~10 min

```sh
# Office Kit paired, wireless debugging on, then:
adb pair <host>:<pair-port>
./dev w <host>:<connect-port>
./dev r          # install the build under test
./dev probe      # baseline evidence -> evidence/probe-<utc>-<serial>.txt
```

Keep `./dev l` running in a second terminal for the rest of the session.

**Build gate**: confirm the cold launch works and note any visual change
under `targetSdk 36` edge-to-edge. Retires CL-04, CL-14.

### 1. Hero loop gate — ~30–45 min, do this before anything else

**R1** (Bluetooth connect/disconnect reaches the receiver — backgrounded,
swiped away, screen off 10+ min), **R2** (the owned zen rule applies and
releases without touching user DND), **R3** (exact alarm + `specialUse` FGS
fire on time, screen off), **R6** (a disconnect while the process is dead
shows as a coverage gap), **R8** (at-time callback, screen off). Methods are
in [SPRINT_4_5.md](docs/SPRINT_4_5.md#rd-register).

This is the single highest-priority block. Without R1/R2, [DEMO.md](docs/DEMO.md)
steps 4–6 (the entire live "connect earbuds → session starts → disconnect →
grace → ends" loop) cannot be shown live and default to recorded footage —
everything after this point in the session is lower-stakes than this row.
Retires CL-06 (all parts) and CL-10.

### 2. M8 — screen-off pending action — ~10 min, same setup as above

Arm a cue whose action needs the user present (e.g. "open Spotify when
charging"), lock the screen, trigger it, then unlock. Pass: the action shows
`PENDING` while locked and fires once on unlock; if it expires first it's
`BLOCKED` and the session is `PARTIAL`. Confirm `PowerManager.isInteractive()`
actually reads false while locked on this OEM. Retires CL-25.

### 3. M1 — package discovery — ~5 min, cheap, read-only

`./dev probe`, sections *OEM package discovery* and *System assistant*.
Record the raw package list — never guess a package name from its label.
Retires CL-23 item 5.

### 4. M7 — AppFunctions + Jovi handoff — ~15 min, do early while there's time to fix

AppFunctions has never compiled anywhere (CL-24) — this row is real compile
risk, not just a device check, so don't leave it for the end.

```sh
adb shell cmd app_function list-app-functions | grep -A10 com.cues.android.debug
adb shell cmd app_function execute-app-function \
  --package com.cues.android.debug \
  --function 'com.cues.app.appfunctions.CuesAppFunctionService#draftCue' \
  --parameters '{"params":{"request":"when charging starts pin a note saying test"}}'
```

Pass: `list-app-functions` shows all five functions; `execute-app-function`
produces a real reviewable draft visible in the app. Then type an unsupported
request in Ask Cues, tap the handoff card, record what actually opens, and
compare against the probe's `ACTION_ASSIST` resolution — the card must say
"system assistant" unless the probe confirms it resolved to Jovi. If the
build fails, record the compile error here rather than skipping the row.
Retires CL-24.

### 5. M2 — NPU backend + drafting latency — ~20 min if a model is staged, else ~5 min to confirm the fallback

```sh
adb push model.litertlm /data/local/tmp/
adb shell run-as com.cues.android.debug sh -c \
  'mkdir -p files/models && cp /data/local/tmp/model.litertlm files/models/'
```

Relaunch, open Diagnostics, read the last inference report, run the in-app
bake-off. Pass: the report names a backend, the SoC is in the NPU allowlist
if that backend is NPU, p50/p95 recorded with the model file name. With no
model staged, this row still matters: confirm the drafter is labelled
"phrase grammar" everywhere, including what would be said on stage (CL-03).
Retires CL-18 items 2/3/5 and, if it comes out parser-only, closes CL-03's
measurement question either way.

### 6. M6 — accessibility utility binding — ~15 min

Enable "Cues: iQOO utility bindings", teach Eye protection on and off, run
Test on/off, flip the toggle by hand, then try a password field and a
denylisted app. Pass: on and off both read back; the password field and
denylisted app are refused; record whether a manual flip gets silently
overridden. Retires CL-23 items 1, 3, 4, 6.

### 7. M3 — camera import, airplane mode — ~15 min

Airplane mode on, Wi-Fi off, confirmed via probe. Capture the printed
timetable from tonight, review the extracted rows, open one in Review and
approve it. Repeat with the Photo Picker. Pass: rows extract, uncertain rows
are flagged, nothing arms from the import screen itself, and the probe
afterward still shows both radios off. Retires CL-20.

### 8. M5 — Origin Island rendering — ~5 min, cheap

Start a session with the screen on, then locked. Photograph the status bar,
lock screen and Origin Island. Record exactly where the notification
appears — no Live Update / Origin Island claim regardless of the result; the
standard foreground notification is the stated surface either way (CL-15).

### 9. M4 — Office Kit transfer — ~10 min

Export the Cue Console from the phone, send it through Office Kit, open the
received file on the laptop from wherever Office Kit put it. Pass: file
arrives intact (compare sizes), every section renders from `file://`. If it
fails, the claim downgrades to "share sheet to any app" — say so in
DISCLOSURE.md rather than dropping it silently. Retires CL-22 items 1, 2.

### 10. M9 — QR between devices — ~10 min, needs the second phone

Show a Cue Card QR on the loaner, scan it on the second phone, then reverse.
Repeat with text share. Try one scan with a device missing from the
receiver. Pass: the card reimports to the receiver's own device id, the
missing entity is refused by name, nothing arrives armed. Retires CL-21.

## After each row

- Write the result into [DEVICE_MATRIX.md](docs/DEVICE_MATRIX.md)'s Results
  table — Pass/Fail/Limited, observed value, evidence file, caveat.
- Strike through the CLEANUP.md entry the row retires, with the commit that
  recorded it. A row that fails its fallback is still closed once the
  limitation is written down — "unverified" and "silently hoped about" are
  not the same thing (CL-06's own framing).
- Any real timing from M2 or R3 goes into `docs/MEASUREMENTS.md` — create it
  the moment the first genuine number exists; no figure is spoken on stage
  before it's in that file.

## End of session

- If R1/R2 passed: record phone footage of DEMO.md steps 1–7, "Recorded"
  burned into the video, per that doc's Backup evidence section.
- Update the README status paragraph with what actually ran on physical
  hardware — this is the point where "never installed on an iQOO" stops
  being true, so say exactly what changed and nothing more.
- Rehearse the live script once from the phone, once from the recorded
  footage.

## If something fails

Don't chase a failing row past its time budget. Record Fail/Limited with
what you actually observed and the fallback the row already names in
DEVICE_MATRIX.md, then move to the next row. A finding that needs a real
code fix becomes a new or updated CLEANUP.md entry with a retirement
condition — not a silent patch mid-session and not something fixed from
memory without the device in front of you to confirm it.
