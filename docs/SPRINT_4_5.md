# Sprints 4 and 5: The cue meets the phone, then earns its claims

**Duration:** 14 hours (hours 16–30), run as one mega-sprint with a hard gate at hour 23.
**Entry condition:** Sprint 3's exit criteria hold. A supported request becomes a checked, versioned and armed rule, and the parser path is visible in diagnostics. If Sprint 3 is not closed, finish it before starting here. Nothing below depends on a better draft.

| Sprint | Hours | Goal |
|---|---|---|
| **4: Unattended proof** | 16–23 | With the app backgrounded and networks off, a real earbud connection starts exactly one session. A disconnect, deadline or manual stop ends it, and it releases only what Cues owns. The receipt says what happened, including anything that failed. |
| **5: Earned claims** | 23–30 | Decide the drafting path from measurements, not impressions. Ship the four features that make Cues more than a rule engine. Record every number with its source, then rehearse the demo against both the physical device and the backup footage. |

The ordering is deliberate. Sprint 4 proves the promise that matters when nobody is watching. Sprint 5 adds what makes the promise *visible*. If hour 23 arrives with the hero loop still unreliable, Sprint 5 shrinks to 5.7 and 5.8 only (see *What to cut*).

## Pre-event Sprint 3.5: signal framework (Sep 23–25)

The core now has a closed `SignalRegistry` of typed trigger, condition and end
kits. Bluetooth, charging and manual behavior dispatch through the same kit
interfaces as any-Wi-Fi, device-connected and at-time signals. This is generic
source code, not a runtime plugin system: every subtype is sealed, registered
and covered by an exhaustiveness check. Existing semantic forms retain the
approved digest, and `PINNED_NOTE` is an app-owned, reversible action.

The event still proves delivery on the phone. No Android adapter is considered
healthy until it reports its listener status and its result is recorded.

---

## R&D register

Every open question here has a method, a pass condition, and a fallback that the plan already accepts. We are not researching to find a nicer answer. We are researching to find out *which already-written plan* applies. Record the results in the Diagnostics screen and in [CLEANUP.md](../CLEANUP.md). A limitation that is written down closes its entry.

| ID | Question | Method (time-box) | Pass | If it fails |
|---|---|---|---|---|
| **R1** | Does `ACL_CONNECTED`/`ACL_DISCONNECTED` reach a manifest receiver on the loaner in three states: backgrounded, swiped from recents, and screen off for 10+ min? | Connect and disconnect the earbuds 5× in each state. Log the receipt time against a stopwatch. (30 min) | 5/5 in the first two states | Spike **R1b**: `CompanionDeviceManager` association plus `startObservingDevicePresence`. This path is designed to wake an app for an associated device. If R1b also fails, the review states "while Cues is open or recently used" and monitoring health reports it. Nothing is hidden. |
| **R2** | Does an app-owned `AutomaticZenRule` apply and release on OriginOS without affecting a user-set DND or another mode? | Four cases: our rule alone; our rule then user DND on; user DND then our rule; our rule plus a vivo mode. After each release, read `getCurrentInterruptionFilter` and `automaticZenRules`. (30 min) | We release only our rule, and the other quiet state remains | Narrow AC-02 to what was observed and write the exact limitation into the RESTORE line. Never claim "DND off". |
| **R3** | Are exact alarms and the `specialUse` foreground service allowed on the loaner, and does the timer fire on time with the screen off? | Set 2-minute sessions 3× with the screen off. Record the delay from deadline to cleanup. (20 min) | Median delay under 5 s, *measured* | Review says "ends within about N minutes", using the measured N. Do not claim precision. |
| **R4** | Does the merged APK request **no `INTERNET` permission**? | `./dev perms` (new, task 4.0) dumps the merged manifest, because MediaPipe and other libraries can merge `INTERNET` in. (10 min) | `INTERNET` absent, or removed with `tools:node="remove"` while the app still works | If a required library needs it, drop the structural claim and fall back to an observed claim: the receipt records connectivity state. |
| **R5** | Which on-device runtime drafts a cue fastest and most correctly on this phone? | The bake-off in task 5.0. It runs over `corpus/paraphrases.txt` plus 15 unseen paraphrases written by a teammate who has not read the grammar. (60 min, Sprint 5) | Beats the parser on unseen items, has zero *wrong-meaning* accepts, and p50 latency is at most 4 s | Keep the parser only and say so plainly (CL-03). A parser shown as AI is the one outcome that is ruled out. |
| **R6** | Can a missed event be *detected*, even if it cannot be prevented? | On resume and on every received event, compare the adapter's current connected set with the last recorded transition. (Inside task 4.6) | A disconnect made while the process was dead appears as a coverage gap on the next resume | Monitoring health says "not checked" rather than "healthy". |
| **R7** | Does OriginOS expose a usable Wi-Fi name with and without location access? | Compare any-network and named-network callbacks with location granted and revoked. (30 min) | Named matching is stable without an unjustified permission claim | Keep any-Wi-Fi only; named Wi-Fi remains a readable needs-location answer. |
| **R8** | Does an at-time callback arrive with the screen off? | Schedule three bounded sessions, screen off, and compare delivery with the persisted zone/deadline. (20 min) | All three callbacks arrive within the measured tolerance | Keep at-time semantics in core, report delivery as unverified and cut the Android adapter. |

**R4 — closed, 23 Sep 2026.** Manifest-permission merging is a build-time
fact, not a device fact, so this ran for real: `./dev perms` (now added)
built the debug APK and dumped the merged manifest with `aapt dump
permissions`. First run showed `android.permission.INTERNET`, sourced (per
`app/build/outputs/logs/manifest-merger-debug-report.txt`) from
`com.google.android.datatransport:transport-backend-cct`, a telemetry
dependency pulled in transitively through MediaPipe's GenAI library — not
anything Cues calls. Removed with `tools:node="remove"` in
`AndroidManifest.xml`; `./dev perms` now reports no `INTERNET` permission
and `./dev b` still succeeds. **R1, R2, R3, R6 and R8 remain open** — each
needs the physical loaner and is not run from this environment.

Candidate runtimes for R5, in the order to try them:
1. MediaPipe LLM Inference, already in the catalogue but unverified.
2. Its successor, LiteRT-LM, if MediaPipe's pin does not resolve.
3. llama.cpp with a GBNF grammar, whose constraint is closest to what the FDD asks for.

Model files are side-loaded, never downloaded by the app (see R4). No NPU claims unless the runtime reports that it used the NPU.

---

## Sprint 4: Unattended proof (hours 16–23)

| # | Task | Est. | Requirement IDs |
|---|---|---:|---|
| 4.0 | **Run spikes R1–R4 first**, before writing adapter code, because their answers pick the code path. Add `./dev perms` to print the merged-manifest permissions. Record each result in Diagnostics and in the matching CLEANUP entry (CL-02, CL-06). | 1.5h | TR-01, AC-02, LC-01 |
| 4.1 | **Bluetooth adapter, end to end.** Match the event against the *bound* device address from the Sprint 3 picker, never a name guess. Key admission on device + connection transition + rule version. A repeated callback attaches to the existing session and is recorded as `DUPLICATE_EVENT_SAME_CONNECTION`. Use the CDM path instead if R1 chose it. | 1.25h | TR-01, LC-02 |
| 4.2 | **Charging adapter.** Use `ACTION_POWER_*` plus a read of current `BatteryManager` state. Keep "is charging now" separate from "was plugged in". If the state can't be read, it's `Unknown`, not `false`. | 0.5h | TR-01, TR-02 |
| 4.3 | **Acquire, then verify.** Start the timer and the owned zen rule, then *read each one back* (alarm scheduled, rule present and enabled) before marking it `SUCCEEDED`. If the read-back fails, the action is `BLOCKED` with the reason, and the session is `PARTIAL`. | 1h | AC-01, AC-02, EX-01 |
| 4.4 | **Exits.** Three paths share one idempotent cleanup: disconnect with the visible reconnect grace, deadline, and manual stop from the notification. A reconnect inside the grace window cancels the pending exit *without* restarting the timer. Attempt every obligation even if one fails. | 1.25h | LC-01, LC-02, LC-05 |
| 4.5 | **Reconcile after death.** Replace the in-memory `zenRuleIds` map by reconciling against `NotificationManager.automaticZenRules`, matched by owner and rule name, on process start and on boot. If an expired session is found, cleanup runs and nothing restarts. Retires the first CL-02 bullet. | 0.75h | LC-03, LC-05 |
| 4.6 | **Monitoring health and coverage gaps (R6).** Home shows three things: when the last event was received, whether each armed trigger's listener is live, and any *coverage gap*. A gap means the device state changed while Cues was not listening, and it gets its own receipt line from a new reason code `COVERAGE_GAP`. A missed event is never recorded as "nothing happened". | 0.5h | LC-05, EX-02 |
| 4.7 | **Live receipts and permission re-check on resume.** The receipts list observes the store instead of `remember { … }`. The review and arm preflight re-read capabilities in `onResume`. Retires the other two CL-02 bullets. | 0.25h | AC-03, RV-01 |
| 5.A | **Wi-Fi and time adapters plus `AdapterSupervisor`.** Register only adapters required by armed kits, feed listener health to MH-01, and route callbacks through `CueService` with unknown/redacted readings preserved. | 1.75h | TR-03, MH-01, EX-02 |

### Sprint 4 test cases (on the loaner, not in :core)

- Airplane mode on, Wi-Fi explicitly off, Bluetooth on. The app is swiped from recents. Connecting the bound earbuds starts one session, and a notification shows the deadline.
- Toggle the connection off and on inside the grace window. The existing timer keeps running, no second session starts, and the receipt shows the reconnect.
- A disconnect ends the session. Our zen rule is gone, and the receipt says "released our quiet rule".
- Turn on user DND during the session, then end it. DND stays on, and the receipt does not claim DND is off (R2).
- Deadline expiry with the screen off ends the session. The measured delay is recorded (R3).
- Revoke notification-policy access mid-session. Cleanup reports `BLOCKED` for the release, the obligation remains visible, and a recovery action exists.
- Force-stop mid-session, then relaunch. Reconciliation finds and releases the rule without restarting the timer.
- Disconnect while the process is dead. On relaunch, a coverage-gap receipt appears.
- Connecting a non-bound Bluetooth device does nothing, and the receipt says `TRIGGER_DEVICE_MISMATCH`.

### Gate at hour 23

The hero loop passes the first four test cases three times running, with the app backgrounded. If it does not, Sprint 5 is reduced to 5.7 and 5.8.

---

## Sprint 5: Earned claims (hours 23–30)

The features in this sprint are the reason Cues is its own kind of app, not a rule engine with a voice front-end. Each one follows directly from a rule in [CLAUDE.md](../CLAUDE.md), so none of them is decoration.

| # | Task | Est. | Requirement IDs |
|---|---|---:|---|
| 5.0 | **Drafter bake-off (R5).** Build a harness that feeds every corpus line to each available drafter and scores *meaning assertions*, not JSON equality. It records per-item pass/fail, wrong-meaning accepts, p50/p95 latency, the model file, the runtime, the backend and whether the network was on. Output goes to `docs/MEASUREMENTS.md`. The result decides CL-03. | 1.5h | IN-03, CP-01, CP-02 |
| 5.1 | **Wire the winning model path**, but only if 5.0 passes. `OnDeviceLlmDrafter` asks for typed data over the closed vocabulary, constrained where the runtime allows. Its output goes through the same `Validator`. If 5.0 fails, the app says "drafted by the phrase grammar" everywhere and the deck says the same. | 1h | IN-03, CP-01 |
| 5.2 | **Differential drafting.** When both drafters answer, compare their `Normalizer.digest`. If they agree, the review shows "two independent drafters agree". If they disagree, the review shows the differing field as a question ("Weekdays, or every day?") and never picks one silently. This lives in `:core`, next to `CompositeDrafter`. | 0.75h | CP-02, CP-03, IN-03 |
| 5.3 | **Clause accounting.** Every word of the request is classified as *mapped* (to a WHEN/IF/DO/UNTIL/RESTORE field), *filler* (from a closed list of words like "please" and "the"), or *unaccounted*. The review underlines the source text by class, and anything unaccounted blocks approval until it is resolved or deleted by the user. This mechanically enforces "never drop a clause". It starts with the grammar parser, which knows its spans. For the model path the accounting is recomputed deterministically and never taken from the model. | 1h | CP-03, RV-01 |
| 5.4 | **"What would happen right now?"** A one-tap dry run of an armed cue against the *live* context snapshot, using the same pure evaluator with the mock executor that rehearsal uses. The result is labeled "Dry run: nothing was changed". This is a single evaluation, not shadow mode, and it structurally cannot touch the phone (RH-01). | 0.5h | RH-01, RH-02, EX-02 |
| 5.5 | **Nearest-miss receipts.** When a cue is skipped, the receipt names the *one* failing condition with the observed value that failed it, for example: "Skipped: 17:52 is before 18:00. Everything else matched." The line is rendered from reason codes plus observed values, never generated. | 0.5h | EX-02 |
| 5.6 | **Composition demo (CT-01).** Compose an `AtTime` trigger, `ChargingState(false)` condition and `NOTIFY_RESULT` action. At the approved deadline, false notifies once; true and unknown produce truthful receipts. | 0.75h | CT-01 |
| 5.7 | **Measurement run.** Run the hero cue 5× (match), 3× (nonmatch) and 2× (exit by deadline), and measure authoring latency, event-to-start delay and exit-to-release delay. Each row in `docs/MEASUREMENTS.md` records the date, the device, OS build, the method and any caveat. Update README, SUBMISSION and the deck **only** from this file. | 1h | All "Proposed evaluation" items in PRS |
| 5.8 | **Demo lock.** The demo script uses the *visibly approved* weekend rule. The short timer is an explicit edit in the review, not a hidden shortcut. Record backup footage and label it "recorded" on screen. Rehearse the demo twice, once from the physical device and once from the footage. Close or re-date every CLEANUP entry. | 1h | — |

### Sprint 5 test cases

- The bake-off harness gives the same score twice on the same inputs. Its results file names the drafter, runtime and backend for every row.
- A request where the drafters disagree ("after 6 on weekdays" vs a model reading "every day") gets a question, not a winner.
- "When my earbuds connect start a timer and text Mum" shows "text Mum" as unaccounted, and approval stays disabled until it's resolved.
- The dry run on an armed cue never changes zen rules, alarms or services. Verify by reading all three before and after.
- A skip at 17:52 yields the nearest-miss line with the observed time, and the line matches the reason code.
- Every figure in README, SUBMISSION and the deck traces to a row in `MEASUREMENTS.md`.

### Exit criteria (end of event)

- A judge speaks or types a supported paraphrase, sees the drafter named and any drafter disagreement, approves, and then triggers the cue physically with networks off. They see the start and the exit, and read a receipt that explains both.
- A duplicate event does not create a second timer, and ending the session never claims DND is off when something else still holds it.
- The drafting path shown on stage is the one the measurements support.
- No number appears anywhere that is not in `MEASUREMENTS.md`.

---

## Proposed requirement additions

These would join [PRS.md](PRS.md) once the corresponding task lands, and not before.

| ID | Requirement | Task |
|---|---|---|
| MH-01 | Show whether each armed trigger's listener is live, and report detected coverage gaps as receipts | 4.6 |
| AC-04 | Mark an action `SUCCEEDED` only after reading back the resulting platform state, where the platform exposes it | 4.3 |
| DD-01 | When two drafters disagree on normalized semantics, ask about the differing field rather than choosing | 5.2 |
| CP-04 | Account for every word of the request as mapped, filler or unaccounted, and block approval while anything is unaccounted | 5.3 |
| RH-03 | Offer a labeled, non-executing evaluation against the live context of an armed cue | 5.4 |

## What to cut, in order

1. 5.2 (differential drafting). It disappears anyway if 5.0 drops the model.
2. 5.4 (dry run). Rehearsal already covers the logic; this only makes it live.
3. 5.6 (CT-01 composition demo).
4. Clause accounting for the model path. Keep it for the parser, which knows its spans.
5. The CDM spike R1b. Accept the documented limitation instead.
6. The charging adapter (4.2). Keep Bluetooth as the only physical trigger.

**Never cut:** 4.3's read-back, 4.4's independent cleanup, 4.6's coverage gaps, 5.7's measurement file, or the drafter label. Each one is what keeps a claim true.

## Not in these sprints

- Integrating with Jovi or its skills. There is still no verified developer path.
- Time-triggered content tasks. Jovi's scheduled tasks cover them, and the answer stays "not supported".
- Shadow mode, history replay, named contexts, the opportunity queue, temporary patches and timetable import. These stay on the FDD roadmap. Task 5.4 is a single evaluation, not a step toward any of them.
- Any network path, including model download from inside the app.
