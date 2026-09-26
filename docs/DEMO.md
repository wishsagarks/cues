# Demo script (Task 20)

Written 24 September 2026, before any run on the loaner. Every live step
below is **gated on a device-matrix result**
([DEVICE_MATRIX.md](DEVICE_MATRIX.md)). Where a gate has not passed, the
step is cut or replaced by its stated fallback, and the replacement is said
out loud rather than smoothed over.

## Ground rules

- **Live and recorded look different.** Anything not happening on the phone in
  front of the judge carries "Recorded" on screen. The offline CLI
  transcript is labelled a recorded CLI run in its own header.
- **The rule on screen is the rule that runs.** The event is on a weekend, so
  the cue says "on weekends". A short timer is spoken into the sentence, so it
  shows in the DO line. The time window is one that holds at demo time. No
  check is bypassed out of sight.
- **Name the drafter.** Review shows "drafted by grammar parser" unless M2
  passed and the model actually drafted that cue. Say whichever it shows.
- **No number that isn't in `docs/MEASUREMENTS.md`.** That file does not exist
  yet, so no latency, delivery or battery figure is spoken until it does.

## Before the demo (setup, not part of the story)

1. Install the build under test with `./dev r`, and pair the demo earbuds.
2. Review's "Required access" rows now have grant buttons for these (CL-26,
   written but not compiled in the environment that wrote it — confirm on the
   loaner before relying on this step). If a button is missing or doesn't
   work, fall back to adb as setup, not shown on stage:

   ```sh
   adb shell pm grant com.cues.android.debug android.permission.POST_NOTIFICATIONS
   adb shell cmd notification allow_dnd com.cues.android.debug
   adb shell appops set com.cues.android.debug SCHEDULE_EXACT_ALARM allow
   ```

3. Airplane mode on, Wi-Fi explicitly off, Bluetooth on. Run `./dev probe` and
   keep the file. It records the radio state the "offline" claim rests on.
4. Clear old cues and receipts if the judge should see a clean Home.

## Live script (phone)

| # | Do | The judge sees | Gate |
|---|---|---|---|
| 1 | Tap the mic and say: "When my earbuds connect on weekends, start a 2-minute focus timer and quiet notifications. End it if I disconnect." Type it instead if the venue is loud, and say so. | The transcript, then Review: WHEN / IF / DO / UNTIL / RESTORE, the drafter label and the access list | — |
| 2 | Scroll the rehearsal rows | Started, skipped on a weekday, duplicate skipped, grace, timer ends. All labelled as sample events | — |
| 3 | Approve and arm | Status ARMED | CL-26 setup done |
| 4 | Put the phone down. Connect the earbuds | One session starts. The notification shows the deadline. The receipt starts with "TWS … connected" | R1, R2 |
| 5 | Disconnect, then reconnect within 20 s | "Kept going". No second timer | R1 |
| 6 | Disconnect and wait | The session ends after the grace. The receipt says "Released our quiet rule". Any user DND stays on | R2 |
| 7 | Open Receipts and read one aloud | The start and end reasons, rendered from reason codes | — |
| 7b | Draft "When I arrive at Office, text Mum I'm home" | Review: WHEN a place arrival, DO a drafted message handed to the user's own messaging app (never auto-sent), and a new "foreground location" row in Required access with its own grant button | CL-13 |
| 8 | Draft "When my earbuds connect start a timer and text Mum" | "text, Mum" flagged as unaccounted. Approve stays disabled | — |
| 9 | Ask Cues: "book me a cab home" | "That is a Jovi task", plus a handoff card. Tap it and say what opened | CL-24 / M7 |
| 10 | Share the cue as a Cue Card and scan it on a second phone | It imports as a DRAFT bound to that phone's own earbuds, not armed | M9 |

If step 4 fails live, stop the live section, say that background delivery
didn't arrive this time, and switch to the recorded footage (below).
Don't retry on stage more than once.

## Cut from the live demo until their rows pass

| Feature | Why it's cut | Tracked |
|---|---|---|
| Place arrival trigger fires live (walking into the geofence on stage) | R13 (delivery under the OEM battery policy with the screen off) has not been run on the loaner. Draft, review, and arm the cue live — that part is real and verified the moment CL-13 ships — but the live-fire step stays cut until R13 passes; show the recorded clip (below) instead and say so | CL-13, R13 |
| Accepting a coach suggestion | Fixed and tested in `:core`; the `:app` wiring is written but uncompiled, so confirm one Accept end to end on the loaner before relying on it live — show the evidence card only until then | CL-27 |
| Actions that wait until you're at the phone | The attention port is not wired, so they don't wait | CL-25, M8 |
| A utility binding inside a cue | Drafting works now ("turn on eye protection" — CL-23 item 7). Still cut live: the accessibility replay itself is unverified on any device | CL-23 |
| AppFunctions / "Jovi can call Cues" | Not built | CL-24 |
| Origin Island / Live Update | Not built. Only the standard notification exists | CL-15, M5 |
| "Runs on the NPU" | Only if M2's inference report says NPU | CL-18 |
| Office Kit transfer of the Console | Only if M4 passed. Otherwise "share to any app" | CL-22 |
| Camera timetable import | Only if M3 passed in airplane mode | CL-20 |

## Backup evidence

- **Offline CLI transcript.** `./dev demo` runs the scripted sequence
  (hero cue, unaccounted clause, full lifecycle, Ask Cues create, refine,
  injection refusal and handoff, Cue Card rebind and tamper, forecast, coach
  evidence, Console, corpus score). It writes
  `evidence/demo-<utc>.txt` and `evidence/console-<utc>.html`. Every event
  in it is synthetic and the executor is fake. It proves the decision
  logic, not the phone. The committed copy was produced on 24 September 2026
  from the commit named in its header.
- **Phone footage.** Not yet recorded. Record steps 1–7 on the loaner once
  R1/R2 pass, with "Recorded" burned into the video, and rehearse once from
  the phone and once from the footage (Sprint 5, task 5.8). Once R13 passes,
  record a second clip of an actual place arrival — screen off, app
  backgrounded — firing the drafted-message action and landing a Receipts
  entry; burn "Recorded" into it the same way. Until R13 passes, step 7b
  stays live only through "armed" and the arrival itself is not claimed.
