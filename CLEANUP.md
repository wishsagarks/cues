# Cleanup register

Provisional choices, each with the condition that retires it.

Everything here was decided without the loaner iQOO in hand, and several
entries are placeholders that will look like working code to anyone who does
not read them closely. That is the risk this file exists to manage: a stub that
returns a plausible answer is worse than no stub at all, and the way to keep
that honest is to write down, at the moment of writing it, what would prove it
should go.

Status: `open` until its remove-when condition is met, then struck out with the
commit that resolved it.

---

## CL-01 — Termux on-device build route

**Status:** open · **Raised:** 22 Sep 2026

`RED_LIGHT.md` documents two ways to compile during Red Light: Office Kit
driving the laptop (primary) and Gradle running natively on the phone under
Termux (fallback). Only one of them is needed.

The Termux route has never been tried on this hardware. It is written down
because discovering on Saturday afternoon that the Office Kit route does not
work, with no alternative prepared, would cost more than the hour it takes to
document a fallback now.

**Remove when:** the Office Kit route is confirmed working on the loaner during
Saturday's opening Green Light window. Delete the Termux section and this
entry. If Office Kit turns out not to work, delete the *other* section instead
and promote Termux.

---

## CL-02 — Android runtime written against real APIs, verified on none of them

**Status:** open · **Raised:** 22 Sep 2026 · **Updated:** 22 Sep 2026

`Stubs.kt` and the always-`BLOCKED` executor are gone. `AndroidActionExecutor`
now calls the real APIs — `AutomaticZenRule` for the owned quiet rule,
`AlarmManager.setExactAndAllowWhileIdle` plus a foreground `SessionService`
for the timer — and `AndroidCapabilityProvider` reads live permission state.
`BluetoothReceiver`, `PowerReceiver`, `BootReceiver` and `DeadlineReceiver`
call into `CueService` for real. The Compose screens (Home, Review, Routine
detail, Receipts) are wired to `CueService` and render real drafted/armed
routines — `SampleRoutine` is deleted, its job now done by an actual draft.

None of it has compiled anywhere. `:app` cannot build in this container (see
CL-04), so every one of these calls is a considered reading of the platform
docs, not a tested result. What's left honestly `BLOCKED` is now specific
refusals — policy access not granted, exact-alarm not granted, result
notifications not wired up — not a blanket "not implemented".

**Found and fixed in Sprint 4 task 4.3 (23 Sep 2026):** none of
`AndroidActionExecutor`'s three owning handlers (`startFocusTimer`,
`requestDnd`, `pinnedNote`) ever set `ActionOutcome.acquired`. Every fake,
rehearsal and sim `ActionExecutor` set it correctly; the one real
implementation did not. `SessionEngine.start()` only records a
`CleanupObligation` when `acquired` is non-null, so on a real device this
silently meant no session ever owed a release — the timer, the zen rule and
the pinned note would all have been left running forever, which is exactly
the failure "cleanup releases only what Cues owns" exists to rule out. Fixed
by deriving `acquired` from `ActionRegistry.definition(actionId)?.owns` in
`execute()` rather than re-typing it per handler, and by having each handler
read its effect back (scheduled alarm, enabled zen rule, active notification)
before returning `SUCCEEDED` — task 4.3's read-back requirement and this bug
turned out to be the same fix. Unverified on the loaner; the alarm read-back
in particular depends on `PendingIntent` matching behaving as documented.

**Resolved in Sprint 4 task 4.5 (23 Sep 2026).** `AndroidActionExecutor` now
has `reconcileZenRules()`, which rebuilds `zenRuleIds` from
`NotificationManager.getAutomaticZenRules()` — filtered to rules this app
owns, then matched to a session id decoded from the rule's own `conditionId`
URI (`zenRuleConditionUri` already encoded it: `condition://com.cues.android/session/<id>`),
not guessed from "the most recently orphaned rule". `CuesApplication.onCreate()`
calls it, then `CueService.onBoot()`, before anything else can touch the
executor — on every process start, not only `BootReceiver`'s
`ACTION_BOOT_COMPLETED`, since an OEM-killed-and-relaunched process needs the
same reconciliation a device reboot does. `zenRuleIds` moved from a
companion-object (process-wide static) map to an instance property, since the
static map was itself part of what made this bug easy to miss. Unverified on
the loaner.

**Resolved in Sprint 4 task 4.7 (23 Sep 2026).** Both remaining bullets are
fixed:

- `CueService.missingCapabilities(routine)` exposes the same live
  `requiredCapabilities - capabilities.granted()` read `Approvals.arm` already
  does at arm time. `ReviewFlow` now calls it from a `LifecycleEventObserver`
  on `ON_RESUME`, so leaving the app to grant a permission and coming back
  updates the Review screen's rows without needing to re-approve.
- `ReceiptScreen` takes `loadReceipts: () -> List<ReceiptEntry>` instead of a
  `List<ReceiptEntry>`, and polls it every 1.5s via `LaunchedEffect` while the
  screen is in composition — the store is a plain directory of files with no
  change notification of its own, so "observing" it means asking again, not
  computing it once with `remember`. Polling stops the moment the screen
  leaves composition, since `LaunchedEffect` is cancelled with its call site.

Unverified on the loaner: the resume re-check depends on `ON_RESUME` actually
firing when the user returns from the system permission settings, which is
ordinary Android lifecycle behaviour but has not been observed on this
hardware.

---

## CL-03 — Two drafting paths at equal weight

**Status:** open · **Raised:** 22 Sep 2026

`CompositeDrafter` runs the on-device model first and falls back to
`GrammarParser`. Both are carried deliberately: the model covers phrasing the
grammar cannot anticipate, the parser is the path that still works when the
model is cold, slow or throttled.

Carrying both costs real time. It may turn out that one of them should be the
only path.

**Remove when:** both have been scored against `core/src/main/resources/corpus/paraphrases.txt`
on the loaner phone, with latency measured. Then either drop the model (and say
so plainly in the deck — a parser presented as AI understanding is a lie to a
judge), or keep both and record the measured numbers here. Do not decide this
from impressions.

---

## CL-04 — Unverified Android dependency versions

**Status:** open · **Raised:** 22 Sep 2026

In `gradle/libs.versions.toml`, everything below the "unverified" marker —
`agp`, `composeBom`, `androidxCore`, `lifecycle`, `activityCompose`,
`datastore`, `mediapipeGenai` — was pinned in an environment where
`dl.google.com` is blocked by network policy, so none of it could be resolved,
let alone compiled.

They are considered guesses. `compileSdk`/`targetSdk` 35, `minSdk` 29 and the
Java 17 toolchain in `app/build.gradle.kts` are in the same position.

**Remove when:** the first successful `./dev b` on the laptop. Bump whatever
was wrong, then delete the "unverified" markers from the catalogue and this
entry.

---

## CL-05 — Pre-event code and what the documents claim

**Status:** open · **Raised:** 22 Sep 2026

The repository previously stated in four places that no application code
existed before the event. That is no longer true, and the wording was corrected
in `README.md`, `docs/PRS.md`, `docs/SUBMISSION.md` and
`docs/FINAL_PROBLEM_STATEMENT.md` rather than left to rot.

The related claim — that there are no measured performance results — is still
true and was left exactly as it was.

**Remove when:** the corrected wording has been checked against the actual
event rulebook on what may be built beforehand, and the Phase 1 submission copy
reflects whatever that says. This is a disclosure question, not a code
question, and it is the one entry here that cannot be closed by writing
software.

---

## CL-06 — Device and OS assumptions

**Status:** open · **Raised:** 22 Sep 2026

Assumed without verification: that the loaner runs OriginOS 7 on Android 15 or
later; that `AutomaticZenRule` behaves as documented there; that exact alarms
can be scheduled; that manifest-declared Bluetooth and power receivers actually
deliver while the app is backgrounded under the OEM battery policy.

The FDD is explicit that a receiver declaration is not proof of delivery. None
of this is knowable from a container.

**Remove when:** each is tested on the loaner and the result recorded — as a
working implementation, or as a documented limitation. A limitation that is
written down is closed; one that is quietly hoped about is not.

---

## CL-07 — Named Wi-Fi requires a device decision

**Status:** open · **Raised:** 22 Sep 2026

The core accepts `WifiNetwork.Named` as a typed value so the vocabulary does
not need to change after the device spike, but the validator rejects it with a
needs-location answer until R7 establishes whether OriginOS exposes the SSID
without location access. Any-Wi-Fi is the supported generic path now.

**Remove when:** R7 is run with location granted and revoked, and the result is
recorded in Diagnostics and the Sprint 4/5 register.

---

## CL-08 — Pre-event signal framework disclosure

**Status:** open · **Raised:** 22 Sep 2026

The sealed signal kits and the `PINNED_NOTE` action were added before the event
to keep the core generic. They do not enable runtime plugins or model-created
capabilities; the registry is closed and compile-time checked. The Android
Wi-Fi and time adapters remain unverified until R7/R8.

**Remove when:** the rulebook disclosure is checked against the final deck and
submission copy, and the device adapter results are recorded.

---

## CL-09 — Signal-framework merge went to `main` without a local build or test run

**Status:** struck out · **Raised:** 22 Sep 2026 · **Closed:** 23 Sep 2026

The commit that merged the `SignalRegistry` refactor was pushed to `main` from an
environment with no working JDK, so `./dev t` could not be run locally. CI
(`.github/workflows/core.yml`, which does have a real JDK) caught two real bugs
that a local run would also have caught:

- `GrammarParser.kt`: `trim('"', '\\'')` was a malformed character literal
  (`'\''` was intended) — a compile error.
- `Evaluator.kt`: lost its wildcard model import when the trigger/condition
  `when`-switches moved to `SignalRegistry`, leaving `ContextValue`, `Condition`,
  `LocalTimeOfDay` and `Day` unresolved for the helper extensions it still
  defines — also a compile error, and its cascade produced misleading
  "ambiguous overload" errors in the new `signals/` files that were not
  actually at fault.
- One corpus assertion (`dnd=false` on the CT-01 composition line) exercised a
  case `Corpus.kt`'s `"dnd"` check has never supported — it only ever checks
  presence of `REQUEST_DND`, never absence. Not a signal-framework bug; the
  assertion itself was wrong and was removed.

Fixed in `a5f8f08` and `b393282`. CI run
[35814893487](https://github.com/wishsagarks/Origin-Flow/actions/runs/35814893487)
is green: 156/156 core tests pass, `:core:compileKotlin` and `:core:test` both
succeed on Temurin 21.

**Lesson kept for CL-04 and future merges:** a container without Google's Maven
can still often run `./dev t` — this one specifically had no JDK at all, which
is a different and rarer failure than CL-04's network block. Don't conflate the
two; check for a working `java`/`javac` before assuming `./dev t` is available.

**Addendum, 23 Sep 2026 (relates to CL-04):** this Claude Code session installed
a JDK 17/21 and the Android SDK locally (`brew install openjdk@17 openjdk@21
android-commandlinetools`, `sdkmanager` for `platform-tools`,
`platforms;android-35`, `build-tools;35.0.0`) and ran `./dev b` successfully for
the first time — this is a different machine from the event laptop, so it does
not retire CL-04 (whose condition is specifically the laptop, via Office Kit),
but it is the first real evidence the pinned dependency versions in
`gradle/libs.versions.toml` actually resolve and compile together.

---

## CL-10 — Bluetooth coverage-gap detection only sees GATT-connected devices

**Status:** open · **Raised:** 23 Sep 2026

Task 4.6's `BluetoothCoverage.currentlyConnectedDeviceIds` (R6) reads
`BluetoothManager.getConnectedDevices(BluetoothProfile.GATT)`, the one profile
constant that call is documented to accept. Classic-audio-only earbuds that
never expose a GATT service (no battery or ANC characteristic over BLE) would
not appear in that list even while genuinely connected, which would make
`CueService.checkBluetoothCoverage` end a session that is actually still
running and file it as `EndReason.COVERAGE_GAP` — a false gap, not a missed
one. `BluetoothAdapter.getProfileConnectionState(BluetoothProfile.A2DP)` was
considered instead, but it reports the adapter's connection state for *any*
device on that profile, not the specific bound device, which is worse for a
check whose whole point is per-device confidence.

**Remove when:** R6 is run on the loaner with the actual paired earbuds —
confirm whether they expose a GATT service while connected (most modern TWS
earbuds do, for battery reporting) and record a false-positive rate, or
switch to a `BluetoothA2dp`/`BluetoothHeadset` profile-proxy read if GATT
alone proves insufficient.

---

## CL-11 — Audio-output signal is only observed while the process is alive

**Status:** open · **Raised:** 24 Sep 2026

`AudioOutputAdapter` registers `AudioManager.registerAudioDeviceCallback`,
which — like the Wi-Fi callback it mirrors — has no delivery guarantee once
the process is killed. `docs/SPRINT_6.md`'s R9 tracks this; monitoring health
reports it as live only while the process is alive rather than claiming
background delivery that has not been observed.

**Remove when:** R9 is run on the loaner (connect/disconnect wired and
Bluetooth audio 5× each, app backgrounded and swiped from recents) and the
result is recorded here and in Diagnostics.

---

## CL-12 — Battery-threshold conditions have no matching trigger

**Status:** open · **Raised:** 24 Sep 2026

`Condition.BatteryBelow`/`BatteryAtLeast` are snapshot-time gates only. R10
(does `ACTION_BATTERY_LOW`/`BATTERY_OKAY` reach a manifest receiver on this
OEM) was never run, so no `Trigger` crosses that threshold — a routine can
require "battery below 20%" but nothing starts a session the moment it drops
below 20%. This is a deliberate scope cut (`docs/SPRINT_6.md`'s cut order),
not an oversight, and the vocabulary stays smaller than the corpus this
implies until R10 says otherwise.

**Remove when:** R10 is run on the loaner and either a `Trigger.BatteryLevel`
kit is added, or the limitation is confirmed permanent and this entry is
replaced with that finding.

---

## CL-13 — Declared places have no delivery adapter

**Status:** open · **Raised:** 24 Sep 2026

`Place` and `Condition.AtPlace`/`Trigger.PlaceTransition` are typed and
validated in `:core`, and the app lets a user save a place by typing its
coordinates (no location permission requested, matching the manifest's own
"no location" line). There is no `PlaceAdapter`, no `GeofencingClient` and no
location permission in `:app` — a place can be referenced by a cue's
condition, evaluated against `ContextSnapshot.insidePlaces` if that field is
ever populated, but nothing populates it today, and no cue can currently be
*triggered* by entering or leaving a place. This is the first item in
`docs/SPRINT_6.md`'s cut order, cut deliberately rather than shipped
unverified: `docs/FDD.md` still lists geofencing as needing its own
permission and reliability design, and R13 (delivery under the OEM battery
policy with the screen off) has not been run.

**Remove when:** a `PlaceAdapter` is built behind `ports/Ports.kt`, R13 is run
on the loaner, and the result — working, or a documented delivery limitation
— is recorded here.

---

## CL-14 — targetSdk 36 is unverified on the loaner

**Status:** open · **Raised:** 24 Sep 2026

`app/build.gradle.kts` targets and compiles against API 36 (raised from 35 in
this sprint, ahead of AGP 8.7.3's tested range — the build prints a warning
about this on every run). The FDD's other targetSdk-sensitive claims —
exact-alarm scheduling, the app-owned zen rule, foreground-service behavior —
were written and reasoned about against 35's rules; 36 may enforce
edge-to-edge display or other behavior changes that have not been checked
against `Theme.Cues` or `MainActivity`.

**Remove when:** `./dev b` and a cold launch are confirmed on the loaner
running OriginOS 7, with any edge-to-edge or notification-behavior changes
recorded here.

---

## CL-15 — No promoted Live Update; the session notification is the old style (recorded decision: keep it, for now)

**Status:** open · **Raised:** 24 Sep 2026 · **Revisited:** 24 Sep 2026

The original Sprint 6 plan called for a promoted `ProgressStyle` Live Update
for the running session, matching iOS Live Activities' visibility. That was
not built — `SessionService`'s notification is unchanged from Sprint 4/5.
`docs/SPRINT_6.md`'s R11 (would a promoted session even be shown on the
OriginOS 7 lock screen or Origin Island) was never run, and building the
richer notification before knowing the answer risked shipping a claim this
repo could not measure.

**Revisited while continuing Task 9's unfinished pieces:** `compileSdk`/
`targetSdk` are both 36, so `Notification.ProgressStyle` is technically
reachable, and it would have been possible to write speculative code against
it the way `CuesAccessibilityService` and the other unverified `:app` code
in this repo already does. Decided not to. The difference is verifiability
of the *shape*, not just the *behavior*: every other "written against real
APIs, verified on nothing" class here (`AndroidActionExecutor`,
`CuesAccessibilityService`, `CalendarReadings`, ...) is built against APIs
with years of public documentation, sample code and Stack Overflow history
to check the call shapes against, even without a compiler. `ProgressStyle`
is an API-36-era addition with thin public documentation as of this pass —
writing against it blind risks a *wrong* builder shape, not just an
*unverified* one, which is a different and worse failure mode than the rest
of this file discloses. R11 not having been run is exactly the condition
that already argued against attempting this; nothing has changed that.

**Decision:** keep the current `NotificationCompat.Builder`-based
notification (`SessionService.buildNotification`) until R11 is actually run
on the loaner. It is correct, already covered by the existing manual
verification the rest of `SessionService` has, and does not claim visibility
it cannot back up.

**Remove when:** R11 is run on the loaner and either confirms the current
notification is what OriginOS 7 shows (closing this with no further code
needed), or shows a gap a `ProgressStyle` upgrade should close — at which
point it can be built with a real device to check the builder shape against,
not blind.

---

## CL-16 — Declared Memory facts cannot yet be referenced from a spoken end condition

**Status:** open · **Raised:** 24 Sep 2026

`Fact`, `FactStore`, "remember …" and approval-invalidation on edit/delete are
built and tested (`FactTest`). `Routine.factDependencies` exists and is proven
to invalidate approval when a referenced fact changes or is deleted. What is
not built: a natural-language path from `GrammarParser` (or the `Refiner`) to
actually attach a `FactReference` while drafting — "quiet my phone every
evening until my exam" still returns "Time-scheduled content tasks aren't
supported" rather than resolving "my exam" against Declared Memory and adding
an end condition for it. There is also no `EndCondition` variant for "until an
absolute date," which a fact like `Oct 12` would need — today's
`EndCondition.AtTime` is a recurring time-of-day, not a calendar date.

Today a fact reference can only be attached programmatically (as the tests
do); no user-facing flow produces one.

**Remove when:** either a dated `EndCondition` is added with matching
evaluator/session support and `GrammarParser`/`Refiner` can resolve "my
`<label>`" against `FactStore` into a `FactReference`, or this is explicitly
descoped for the event and the assistant states plainly that it cannot end a
cue on a remembered date yet.

---

## CL-17 — Ringer-mode restore does not survive a process death

**Status:** open · **Raised:** 24 Sep 2026 · **Updated:** 24 Sep 2026

`AndroidActionExecutor.ringerModeBefore` remembers what ringer mode a
session replaced, the same way `zenRuleIds` remembers a session's zen rule
id — but unlike a zen rule, a ringer mode carries no marker of who set it,
so there is nothing in live system state for a `reconcileZenRules()`-style
method to rebuild this map from after a process restart. A session that
dies mid-run with the ringer silenced will not have it restored on cleanup
retry; the resource stays as Cues left it rather than guessing at a value
it can no longer know.

**Closed as of this update:** all seven Phase C actions now have
`GrammarParser` phrasing, including the five that name an external entity.
`OPEN_APP` — the one genuinely needing a picker, since the parser has no
installed-app list to resolve a name against — asks
`NeedsClarification(about = "action.app", appQuery = "spotify")`; the
Android layer queries `PackageManager` for launcher-visible apps (declared
in `<queries>`, not the `QUERY_ALL_PACKAGES` permission), ranks them against
the query, and the picked app re-drafts through a machine-written
`(selected app: pkg|Label)` marker the parser resolves deterministically —
never from surrounding prose, the same discipline the existing paired-device
picker already follows. `COMPOSE_MESSAGE`, `ADD_CALENDAR_EVENT`, `SET_ALARM`
and `OPEN_LINK` needed no picker at all: a contact hint, an event title, a
clock time and a URL are all things the phrasing itself already states, and
none of them names something Cues has to look up on the device first.

**Remove when:** a durable ringer-mode record is added (e.g. alongside
`CleanupObligation`) and confirmed to survive a restart on the loaner, or
this is accepted as a standing limitation and stated as such in the Review
screen.

---

## CL-18 — LiteRT-LM is real and wired, but its NPU claim and its version pin are both unverified on a device

**Status:** open · **Raised:** 24 Sep 2026

`OnDeviceLlmDrafter` no longer wraps a stub. `LiteRtLmSession`
(`app/.../drafting/LiteRtLmSession.kt`) is a genuine integration against
`com.google.ai.edge.litertlm:litertlm-android`, compiled and resolved in this
sprint — not merely read from documentation the way the rest of
`docs/API_VERIFICATION.md` was gathered. It tries `Backend.NPU`, then
`Backend.GPU`, then `Backend.CPU`, and reports whichever tier's
`Engine.initialize()` returned without throwing as an `InferenceReport`,
surfaced in Diagnostics and on each Assistant turn ("answered from …").
Several things about it are still unverified or disclosed as limited by
design, rather than untested oversights:

1. **The version pin exists because of a real compiler incompatibility, not
   caution.** `litertlm-android` releases 0.17.0 and 0.17.1 depend on
   `kotlin-reflect:2.4.0`; that artifact's own `.class` files carry Kotlin
   2.4.0 metadata, which this project's 2.2.21 compiler cannot read at all —
   `-Xskip-metadata-version-check` would not have been enough, since the
   library's own bytecode is unreadable, not just a transitive version
   conflict. Every release through 0.16.1 depends on `kotlin-reflect:2.2.21`,
   matching this project exactly, so the pin is `litertlm = "0.16.1"`.
   Re-verify this by hand (check that release's POM for its `kotlin-reflect`
   version) before ever bumping it.
2. **No model has been side-loaded or run.** `LiteRtLmSession` expects a
   `.litertlm` file at `filesDir/models/model.litertlm`; nothing in the app
   places one there. Every build without one falls back to the parser,
   exactly as the old stub did — this sprint changed the failure's *cause*
   (a missing file, not an unconditional throw) but not its outcome.
3. **The NPU allowlist (`SM8750`, `SM8650`, `SM8550`) is copied from
   `docs/API_VERIFICATION.md`'s published table, not from a reading of the
   loaner's own `Build.SOC_MODEL`.** If the event phone's SoC is not in that
   published table — plausible for a newer iQOO 15-class chip, per the same
   doc — NPU is correctly treated as unavailable and the session falls to
   GPU, but that fallback itself has never run.
4. **"Backend" is a request that succeeded, not an independent read-back.**
   Unlike `AndroidActionExecutor`'s acquire-then-verify checks,
   `litertlm-android`'s public Kotlin API exposes no separate "which backend
   actually executed" query. `InferenceReport.backend` is the tier whose
   `Engine.initialize()` didn't throw — the strongest signal the library
   offers, and weaker than every other "verified" claim in this codebase.
   Say so plainly if this ships in a demo.
5. **The GPU backend's required `<uses-native-library>` entries
   (`libvndksupport.so`, `libOpenCL.so`) are added per
   `docs/API_VERIFICATION.md` but never confirmed present at those exact
   names on the loaner.**
6. **`Embedder` (for `PersonalIndex`'s fuzzy "my deep work thing" resolution)
   is now threaded all the way from `CueService` through to `PersonalIndex`,
   but `CuesApplication` still passes none** — no on-device embedding model
   or API has been identified or verified, unlike the chat path above. Fuzzy
   resolution in the real app therefore still falls back to lexical matching
   only, same as before this sprint's `CueService` constructor gained the
   parameter.

**Remove when:** each numbered item above has a device result — a
side-loaded model that actually ran, a confirmed or corrected NPU allowlist
entry for the loaner's real `Build.SOC_MODEL`, and a measured GPU fallback —
recorded here or in `docs/MEASUREMENTS.md`, with the source of each number
noted. Item 6 retires separately, when an embedding runtime is chosen and
verified the same way this chat path was.

---

## CL-19 — Text-to-speech is wired but never heard on a device

**Status:** open · **Raised:** 24 Sep 2026

`app/.../voice/ReplySpeaker.kt` wraps `android.speech.tts.TextToSpeech`
against the official API (`docs/API_VERIFICATION.md`'s "Text to speech"
entry): it speaks exactly a reply's or a receipt's own rendered string,
never a paraphrase, and it cannot approve a draft or confirm a
`PendingCommand` — those calls are nowhere near it. "Speak replies" (Home,
off by default) and "Read aloud" (each Receipts card) both call it.

Unverified: whether the device has a usable TTS engine and voice data
installed at all — `TextToSpeech`'s init callback can report failure, which
`ReplySpeaker` treats as "never ready" and silently no-ops rather than
crashing, but that fallback path has not been exercised on a phone with a
missing or misconfigured engine. Locale is set to `Locale.getDefault()`
rather than the fixed `en-IN` `LocalSpeechInput` uses for recognition — not
yet decided whether replies should also be pinned to en-IN for consistency.

**Remove when:** confirmed on the loaner that speech synthesis actually
plays, and a decision is recorded on whether the reply voice's locale should
match the recognizer's fixed en-IN.

---

## CL-20 — Timetable import: a new camera permission, and a real but unverified CameraX/ML Kit integration

**Status:** open · **Raised:** 24 Sep 2026

Task 12 adds a genuinely new permission — `android.permission.CAMERA` — for
exactly one feature: "point at your timetable" (`app/.../camera/TimetableCaptureScreen.kt`).
This is a deliberate, disclosed addition, not scope creep; it is requested
only from that one screen, never in the background, and the screen releases
the camera (`ProcessCameraProvider.unbindAll()`) the moment it leaves
composition.

**What is genuinely built and tested:**
- `core/.../imports/TimetableExtractor.kt` is pure Kotlin, fully tested
  (`TimetableExtractorTest.kt`), including that a line shaped like an
  attempted instruction rather than a timetable row produces no entry and no
  proposal — the same trust boundary the rest of this app already holds
  imported/shared text to.
- A clean row's generated sentence was verified to round-trip through the
  *real* `GrammarParser` (not mocked) into a `PINNED_NOTE` action bounded by
  `EndCondition.AtTime`, with the day expressed as a `Condition.DaysOfWeek` —
  confirmed empirically via `./dev d`, not assumed from reading the grammar.
- `app/.../camera/TimetableCaptureScreen.kt` and `TimetableOcr.kt` compile
  against the real CameraX 1.5.3 and ML Kit 16.0.1 APIs — pinned down from
  1.6.2 after its AAR metadata demanded a newer AGP than this project uses
  (see `docs/API_VERIFICATION.md`).

**What is not verified, because `:app` cannot be exercised in this
environment:**
1. The CameraX preview, capture and lifecycle binding have never run against
   a real camera. `ImageCapture.takePicture` writing to `context.cacheDir`
   and its callback wiring is read from official samples, not tested here.
2. ML Kit's bundled recognizer has never actually run OCR on a photographed
   or picked timetable image — accuracy, lighting sensitivity and rotation
   handling are all unverified.
3. `TimetableEntry.proposedSentence()` lowercases a label's casing (see its
   own doc comment) because the grammar parser lowercases its entire input;
   "Math — Room 204" becomes a pinned note reading "math - room 204". Cosmetic,
   not a safety issue, but worth fixing if it reads poorly in practice.
4. The Photo Picker path (`ActivityResultContracts.PickVisualMedia`) needs no
   runtime permission on modern Android, but has not been confirmed on
   OriginOS 7, which sometimes substitutes its own gallery/picker UI.
5. `<uses-native-library>`-style GPU-only concerns don't apply here, but the
   camera's autofocus/back-camera assumption (`CameraSelector.DEFAULT_BACK_CAMERA`)
   has not been checked against the loaner's actual camera set.

**Remove when:** a real capture → OCR → import review round trip has run on
the loaner phone, with results (recognition accuracy, capture latency,
whether the label-casing loss is worth fixing) recorded here or in
`docs/MEASUREMENTS.md`.

---

## CL-21 — Cue Cards: the format is fully tested, the QR camera path is not

**Status:** open · **Raised:** 24 Sep 2026

**What is genuinely built and tested, entirely in `:core`:**
- `core/.../share/CueCard.kt` / `CueCards`: export, tamper-checked decode, and
  reimport with device/place/context re-resolution by label — never by the
  sender's own id or version. `CueCardTest.kt` covers a clean round trip,
  approval/status/capability fields being structurally absent from the wire
  format (not just empty), a missing device on the receiving phone refusing
  the import, a same-label-different-address device correctly rebinding to
  the *receiver's* id, a named context resolving the same way, a tampered
  card being refused before any resolution is attempted, malformed input,
  and a from-the-future card schema being refused. `./dev card` runs a full
  phone-to-phone demo (different device addresses on each side) and prints
  every step, including the caught tamper.
- Two real, disclosed simplifications: (1) `Trigger`/`Condition` variants
  with no device/place/context reference (times, days, durations, action
  args) pass through the card unresolved and unvalidated beyond the digest
  check — they were already closed, already-compiled data before export, so
  this is the same trust the rest of the app already gives a normalized
  routine. (2) A missing entity refuses the *whole* import rather than
  offering a picker to substitute one — simple and safe, but a friend
  missing one named place currently can't import the other 90% of a more
  complex cue.

**What is not verified, because `:app` cannot be exercised here:**
1. `app/.../camera/CueCardScanScreen.kt` and `QrCode`/`CueCardScanner` compile
   against real CameraX 1.5.3 and ML Kit barcode-scanning 17.3.0 APIs, but
   neither a QR render nor a QR scan has run on a device. Print size,
   scanning distance, and glare/lighting sensitivity are all unknown.
2. `app/.../ui/CueCardShareScreen.kt`'s QR bitmap has never been displayed or
   photographed by a second phone.
3. The "share as text" fallback uses `ACTION_SEND text/plain`; whether
   OriginOS's own share sheet or Office Kit (Phase E) mangles a ~1KB JSON
   payload in transit is unconfirmed.
4. `MissingEntity`'s message names the device/place/context, but there is no
   in-app flow yet to add that entity and retry the same scan — the user has
   to close the scan, add it via Contexts/pairing, and scan again.

**Remove when:** a real phone-to-phone QR share and scan has run on the
loaner (or two loaners), and the text-share fallback has been confirmed over
at least one real share target, with results recorded here or in
`docs/MEASUREMENTS.md`.

---

## CL-22 — Cue Console: the export and page are verified, Office Kit transport is not

**Status:** open · **Raised:** 24 Sep 2026

**What is genuinely built and verified:**
- `core/.../export/CuesExporter.kt` builds a read-only JSON snapshot from a
  phone's routines, receipts, forecast, coach evidence, ledger and last
  inference report — every field is the same rendered text the phone's own
  screens already show (`ReviewCopy`, receipt text, forecast reasons), never
  a re-derived summary. `CuesExporterTest.kt` (6 tests) checks every section
  is present, an armed routine's status travels as-is, a missing inference
  report is shown as absent rather than fabricated, and an empty phone
  exports empty sections rather than erroring.
- `core/src/main/resources/console/template.html` is a single, dependency-free
  static page (no CDN, no fetch — the export JSON is embedded inline so a
  `file://`-opened page needs no server and hits no local-file CORS
  restriction). `./dev console`'s fixture output was opened in a real browser
  and every section (cues, forecast, coach evidence, receipts, diagnostics,
  ledger) confirmed rendering correctly — not just assumed from reading the
  template.
- `app/.../bridge/ExportImport.kt` builds the same Console from a phone's live
  `CueService`/`JsonFileStore` state and shares it through a `FileProvider`
  (a new, scoped `<provider>` and `res/xml/file_paths.xml`, restricted to one
  cache subdirectory) — `content://`, never a raw `file://` a receiving app
  could not open.

**What is not verified, because this needs a real Office Kit connection:**
1. Whether Office Kit's transfer path accepts an arbitrary shared file at all,
   and whether the ~9KB (fixture size; a real phone's history will be larger)
   HTML survives the transfer intact.
2. Whether the exported page, once on the laptop, actually opens correctly
   from Windows/Mac Office Kit's own file landing location — a `file://`
   double-click has only been tested from this repo's own build output, not
   from wherever Office Kit deposits a received file.
3. The `.cue.txt`/`.cuecard` *drop-onto-the-phone* half of the Desk Bridge
   (author on the laptop, import as data) is not built this pass — Cue Cards
   already have an import path (`CueCards.reimport`, Task 13), but nothing
   yet routes an Office-Kit-delivered file into it automatically; today it
   would have to go through the existing QR-scan or manual text-share paths.
4. The "Desk" named-context quick-fill (`ContextsScreen.kt`'s "Suggest: Desk"
   button, pre-filling charging + a connected device) has not been exercised
   with a real paired laptop.

**Remove when:** a real Office Kit transfer of an exported Console has been
confirmed end to end (phone export → Office Kit → laptop open), and either a
`.cue.txt`/`.cuecard` file-drop import path is built or this is explicitly
descoped with the QR/text-share paths named as the supported alternative.

---

## CL-23 — Utility Bindings: the macro contract is fully tested, the accessibility replay is not

**Status:** open · **Raised:** 24 Sep 2026

Tasks 15/16 add the last-resort mechanism the FDD's "Utility Bindings"
section describes: a taught macro replayed through an accessibility service,
used only for `USE_UTILITY` (Eye protection, Ultra saver, Game Mode today —
`core/.../registry/UtilityCatalog.kt`'s closed list).

**What is genuinely built and tested, entirely in `:core`:**
- `model/UiMacro.kt`, `compile/MacroValidator.kt`: the step budget (8), the
  password-field refusal, the package denylist and the click-word/currency
  denylist are all covered in `MacroValidatorTest.kt`, including the
  deliberate distinction that a macro may *scroll past* a denylisted label
  but may never *tap* one, and that "send"/"pay"/etc. only match as whole
  words (no false positive on "sender").
- `registry/UtilityCatalog.kt`: every `UtilityId` has a definition (enforced
  at class-init time, not just by convention), and `isKnownRawId` correctly
  rejects a raw string that isn't in the closed list — `UtilityCatalogTest.kt`.
- `ActionId.USE_UTILITY`'s registry entry (`UI_AUTOMATION` risk, `NEEDS_USER`
  presence, derives `Capability.ACCESSIBILITY_SERVICE`) — `ActionRegistryTest.kt`.

**What is not verified, because `:app` cannot be exercised in this
environment (no Android SDK here — see CLAUDE.md):**
1. `app/.../runtime/CuesAccessibilityService.kt`'s node-tree walk
   (`findNode`), step replay (`performAction` for click/set-text/scroll) and
   the accessibility-event-to-`UiStep` recording path have never run against
   a real screen. Selector matching (`viewIdResourceName`/`text`/
   `contentDescription`), the 2s-per-step/one-retry timing, and whether
   `AccessibilityWindowInfo.root` genuinely comes back null for a
   `FLAG_SECURE` window on OriginOS 7 are all unconfirmed.
2. The one-shot "Cue this screen" read (`captureScreenText`) has never
   captured a real window's text. **Closed 24 Sep 2026:** the wiring gap the
   Task 18 audit found — `CuesApp` accepted `incomingScreenText` but never
   read it — is fixed. `CuesApp` now navigates to Home the moment a capture
   arrives (`LaunchedEffect(incomingScreenText)`), and `HomeScreen` lands it
   in the draft text field as plain, editable data (`LaunchedEffect(incomingText)`),
   the same trust boundary a share-target text already gets — never
   auto-submitted, never arming anything by itself. What remains unverified
   is everything upstream of that: whether a real accessibility-service
   capture ever reaches `EXTRA_SCREEN_CAPTURE` with real screen text in the
   first place (see item 1 and item 3).
3. `ScreenTile.kt`'s ordering — capture the screen, *then* collapse the QS
   panel and navigate — assumes the accessibility tree still reports the app
   *behind* Quick Settings as active at tap time. This is a real device
   question, not something this build can assert.
4. `USE_UTILITY`'s release (`AndroidActionExecutor.releaseUtility`) cannot
   cheaply verify the toggle is still in the state Cues set before restoring
   it, unlike `RINGER_MODE` (CL-17's neighbour): a live check needs the
   target app's own screen in the foreground, which release does not force
   open just to look. It replays the opposite macro unconditionally instead.
   A user who already flipped the toggle back by hand before the session
   ended may see it flipped again — the acceptance test in the Cues Brain
   plan ("flip it manually mid-session: Cues does not override it") is not
   met by this pass.
5. `res/xml/utility_bindings_accessibility.xml` leaves `packageNames` unset
   (any package), because which OriginOS package actually hosts each
   cataloged utility's toggle is exactly the device-discovery question Spike
   U0 exists for. Narrowing it to the discovered packages plus whatever the
   user has taught a macro against is a real, disclosed gap, not an
   oversight — see the FDD's utility-catalog mechanism list.
6. Whether "Cues: iQOO utility bindings" actually appears and can be enabled
   in OriginOS 7's Accessibility settings, and what its consent screen looks
   like there, is unconfirmed.
7. **Closed 24 Sep 2026.** `GrammarParser` now has a phrase for `USE_UTILITY`:
   "turn on/off" or "enable/disable" plus one of the catalog's own labels
   ("eye protection", "ultra saver", "game mode") — a closed vocabulary over
   a closed action, the same discipline every other action keeps. Verified
   with `./dev d "when charging turn on eye protection"` (not assumed):
   drafts, reviews and rehearses a `USE_UTILITY` action end to end. New test
   `GrammarParserTest` ("utility phrasing drafts USE_UTILITY..."), 275 core
   tests green. Chat/`IntentRouter` support is still separate — "turn on eye
   protection" as a chat request routes through `CUE_PATTERNS`
   (`IntentRouter.kt`) only if it happens to also mention a trigger word;
   there is no dedicated intent for a bare utility toggle request. Teaching
   and testing a binding from the Utility Bindings screen was already
   end-to-end; now attaching one to a cue is too, in `:core`.
8. **Restore intent survives process death — `:core` half done, `:app` half
   open (25 Sep 2026, plan §10.4).** `releaseUtility` kept its restore target
   only in memory, so after a process death it returned `SUCCEEDED "No prior
   utility state was held."` while Game Mode stayed on. `:core` now persists
   the approved `ActionArgs` on each `CleanupObligation` at acquisition,
   hands release the whole obligation and session
   (`ActionExecutor.release(obligation, session)`, defaulting to the old
   overload), and carries `Verification` (`STEPS_CONFIRMED` renders as
   "assumed" in receipts). `UtilityRestoreTest` proves the data is present
   and sufficient after a simulated process death, a mid-session edit, and
   two utilities in one cue. **Not done:** `AndroidActionExecutor` still
   uses only the resource-only overload, so on a phone the bug is unchanged
   until it overrides the new one (`TODO(app)` in `Ports.kt`). Same shape as
   CL-17's ringer restore: a release must never report success for state it
   cannot account for.

**Remove when:** a taught Eye protection on/off pair has actually run on the
loaner — teach, test on, test off, and a real cue session that arms, fires
`USE_UTILITY`, and releases it — with results recorded here or in
`docs/MEASUREMENTS.md`.

---

## CL-24 — "Hand off to Jovi" opens whatever the system assistant is, and there is no AppFunctions provider

**Status:** open · **Raised:** 24 Sep 2026

The Assistant's handoff card (`MainActivity.kt`, `onHandoffToJovi`) fires
`Intent.ACTION_ASSIST` and nothing more specific. On a phone where Jovi is the
default assistant that opens Jovi. Anywhere else it opens whatever the user
chose, and if nothing resolves the card says so. No OriginOS package or Jovi
intent is hard-coded, which is correct, but the button's name makes a claim
the code does not check. `./dev probe` records what `ACTION_ASSIST` resolves
to on the device under test (docs/DEVICE_MATRIX.md, M7).

**Task 17 (AppFunctions), updated 24 Sep 2026: written, not compiled.**
`app/.../appfunctions/CuesAppFunctionService.kt` implements `draftCue`,
`forecastToday`, `currentContext` and `stopCue` against
`androidx.appfunctions:appfunctions:1.0.0-alpha12` — every annotation,
exception type and manifest shape checked against the live reference pages
on developer.android.com on 24 Sep 2026 (not the stale "alpha11" guess
`docs/API_VERIFICATION.md` previously recorded), not merely copied from an
old note. `gradle/libs.versions.toml` and `app/build.gradle.kts` gained the
dependency, the KSP compiler and the `com.google.devtools.ksp` plugin
(version `2.2.21-2.0.5`, confirmed against Maven Central's own metadata —
reachable even where `dl.google.com` is blocked). The manifest declares the
`<service>` and the app-level `app_metadata` property the KSP processor is
supposed to generate.

None of this has run. This environment has no Android SDK, so KSP has never
executed once — meaning the exact generated service class name, the
`app_functions_schema.xsd` asset, the `cues_app_function_service.xml` asset
and the `@xml/app_metadata` resource this manifest entry references have
never actually been produced and confirmed to match. The manifest entry is
built by careful analogy to the official guide's own example, substituting
this project's names, not by reading real generated output.

**Deliberately not exposed: a "start this cue now" function.** Building it
surfaced a real, separate, pre-existing gap: `EventKind.MANUAL_RUN` (what
`Trigger.Manual` matches) carries no routine identifier, so
`CueService.onDeviceEvent(TriggerEvent(EventKind.MANUAL_RUN, ...))` would
start *every* armed manual-triggered routine at once, not just the one an
agent named. Nothing in `:app` calls this path today either — there is no
"run this cue now" button anywhere yet — so the gap was latent, not
introduced here. Tracked separately as CL-28 rather than fixed in this
pass, since fixing it means deciding how a manual trigger identifies its
target routine, a design question bigger than this file.

**Remove when:** M7 records what the handoff resolves to on the loaner, and
the card's label matches that result ("Jovi" only if the probe resolves to
Jovi, otherwise "system assistant"). The AppFunctions half retires
separately: `./dev b`/`./dev perms` compile this on the laptop, `adb shell
cmd app_function list-app-functions` on the loaner shows all four
functions, and `adb shell cmd app_function execute-app-function` actually
runs `draftCue` and produces a real reviewable draft in the app.

---

## CL-25 — Actions that need the user never wait on a real phone

**Status:** open (core fixed and tested; `:app` half written, uncompiled) · **Raised:** 24 Sep 2026 · **Updated:** 24 Sep 2026

Task 8's `PENDING` semantics are built and tested in `:core`:
`SessionEngine` holds a `Presence.NEEDS_USER` action while
`DeviceAttention.isUserPresent()` is false, `retryPendingActions` runs it
later, and expiry makes it `BLOCKED` and the session `PARTIAL`. Found during
the Task 19 matrix preparation (M8): the running app never used any of it.

**Fixed in `:core`, this update:**

- `CueService` now takes an `attention: DeviceAttention` parameter (default
  `DeviceAttention { true }`, so every existing caller — the CLI, every
  test — is unaffected) and passes it to `SessionEngine`.
- A new `CueService.retryPendingActions()` finds every unfinished session
  with a `PENDING` action, checks `attention.isUserPresent()` itself (it does
  not trust the caller to have already checked — `SessionEngine`'s own
  method does trust its caller, by design, so the check has to live at this
  layer), retries each one through the engine, and records a "Resumed"
  receipt (`Receipts.resumed`, sharing its per-action line rendering with
  `started` rather than duplicating it).
- New test: `CueServiceTest` — a `NEEDS_USER` action stays `PENDING` and
  unattempted while `attention` says nobody is present; calling
  `retryPendingActions()` in that state is a no-op (catches the version of
  this fix that forgot to gate on `attention` itself); flipping `attention`
  to present and calling again executes it and returns the session; a
  second call afterward is a no-op, not a re-execution. 268 core tests
  green.

**Written but not compiled (`:app`, no Android SDK in the environment that
wrote this):**

- `AndroidDeviceAttention` (`app/.../runtime/`) reads `PowerManager.isInteractive()`,
  not `KeyguardManager.isKeyguardLocked()` — see its own doc comment for why.
- `CuesApplication` passes it to `CueService`'s new `attention` parameter, and
  registers a context-registered `ACTION_USER_PRESENT` receiver in
  `onCreate()` (`ContextCompat.registerReceiver(..., RECEIVER_NOT_EXPORTED)`,
  required at targetSdk 36) that calls `retryPendingActions()`.
- `MainActivity`'s existing `ON_RESUME` observer (the same one that re-checks
  Bluetooth coverage) also calls `retryPendingActions()`, since reopening the
  app from recents on an already-unlocked phone never fires
  `ACTION_USER_PRESENT`.

**Remove when:** `./dev b` compiles this, and M8 is run on the loaner —
does `isInteractive()` behave as expected, does the receiver actually fire,
does a `NEEDS_USER` action that ran from the background get blocked by
Android's activity-launch restrictions before the retry ever gets a chance —
with the result recorded here and in `docs/DEVICE_MATRIX.md`.

---

## CL-26 — Three capabilities Review asks for have no way to grant them in-app

**Status:** open (fixed in `:app`, uncompiled) · **Raised:** 24 Sep 2026 · **Updated:** 24 Sep 2026

Review lists every derived capability, marks the missing ones ("Missing:
Do Not Disturb access, notifications, exact alarms") and re-reads them on
resume (CL-02, task 4.7). Before this update, only Bluetooth and the
microphone (Home), the camera (both capture screens) and Accessibility
(Utility Bindings) had a button that led to the grant — `POST_NOTIFICATIONS`
was never requested at runtime, and notification-policy access and exact
alarms had no deep link to their settings pages. A first-time user saw
"Missing: …" under an Approve button that stayed disabled, with no way
forward from inside the app.

**Fixed, this update (`app/.../ui/ReviewScreen.kt`), not compiled here (no
Android SDK in this environment):**

Each missing capability's row in "Required access" now shows a button where
one applies:

- `POST_NOTIFICATIONS` and `BLUETOOTH_CONNECT` — "Allow", an
  `ActivityResultContracts.RequestPermission()` launcher, the same pattern
  Home already uses for the mic and Bluetooth prompts. Its callback calls a
  new `onCapabilitiesChanged` (wired from `ReviewFlow` to the same
  `cueService.missingCapabilities(...)` read the `ON_RESUME` observer
  already used), so the row updates immediately rather than waiting for the
  user to background and foreground the app.
- `NOTIFICATION_POLICY_ACCESS` — "Open settings",
  `Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS`.
- `EXACT_ALARM` — "Open settings", `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM`.
  Only ever missing on API 31+, since `AndroidCapabilityProvider` reports it
  granted unconditionally below that, so there is no older-API branch.

These two leave the app, so they rely on the existing `ON_RESUME` re-check
(CL-02) to update the row on return, not on `onCapabilitiesChanged`.

Left as `null` (no button, same as before): `LOCATION_*`, `BATTERY_STATE`,
`NETWORK_STATE` (none is ever actually missing — see
`AndroidCapabilityProvider`), and `ACCESSIBILITY_SERVICE` (its own consent
flow already exists on the Utility Bindings screen; a second deep link here
would just be a second path to the same toggle).

For the event, until this is confirmed on the loaner, still grant these
before the demo with adb (docs/DEMO.md, "Before the demo") as a fallback.

**Remove when:** `./dev b` compiles this, and a run on the loaner confirms
each button's flow — including that the `POST_NOTIFICATIONS` and
`BLUETOOTH_CONNECT` rows update without needing to leave the app, and that
returning from each settings screen re-enables Approve through the existing
resume re-check.

---

## CL-27 — Accepting a coach suggestion never produces a draft

**Status:** open (fixed in `:core`, tested; `:app` wiring written, uncompiled) · **Raised:** 24 Sep 2026 · **Updated:** 24 Sep 2026

The six detectors, the policy limits and the ledger are tested, and
`./dev coach` shows correct evidence. The hand-off after detection was
broken: `Suggestion.proposal` was a sentence like `"make ${routineId} N
minutes"`, and Home's Accept passed it straight to `draft()`. No drafter
could ever parse that — it describes an edit, not a cue — so Accept always
returned a clarifying question instead of a reviewable draft.

**Fixed, this update:**

- `Suggestion` (`core/.../coach/Detectors.kt`) gained `routineId: String?`
  and `operation: RefineOperation?` — a structured edit, not prose. `null`
  for `FIX_PERMISSION` (no routine in the ledger to point at) and
  `ADD_SIGNAL_CUE` (proposes a *new* cue, not an edit).
- `earlyStop`, `recurringSkip`, `manualRoutine` and `blockedAction` populate
  both, but only when every event behind the suggestion agrees on one
  routine — `distinct().singleOrNull()`, never a guess. This also fixed a
  latent bug the four detectors already had: each grouped its evidence
  globally across every cue (e.g. "the last five sessions" regardless of
  which cue they belonged to), so a suggestion could already have been
  silently misattributed on a phone with more than one cue. `unknownBlocker`
  is unaffected: `LedgerEvent.Skipped.capability` is never populated anywhere
  in the app, a separate, pre-existing dead path this pass found but did not
  fix (worth its own entry if this file gets a CL-28).
- `LedgerEvent.PatchCreated`/`ActionBlocked`/`ManualStart` gained a
  `routineId: String? = null` field (default `null`, so a ledger file
  written before this change still decodes), filled in at their one call
  site each in `CueService`, which already had the routine in scope.
- New `CueService.acceptSuggestion(suggestion)`: looks up `routineId`,
  applies `operation` through the existing `Refiner` — the same path chat's
  "make it 30 minutes" already uses — and returns the refined, unapproved
  routine, or `null` if there's nothing to act on or the routine is gone.
  Nothing is persisted or armed; the caller takes the result to Review, same
  as `draft()`.
- Tests: `DetectorsTest` proves each of the four detectors both populates
  and correctly withholds `routineId`/`operation`; `CueServiceTest` proves
  `acceptSuggestion` applies the edit, clears approval, and refuses (rather
  than guessing) for `FIX_PERMISSION` and a deleted routine. 272 core tests
  green.
- `./dev coach`'s fixture now prints "accept would apply: …" so the fix is
  demonstrated, not just tested.

**Written, not compiled here (no Android SDK in this environment):**
`MainActivity.onAcceptSuggestion` calls `cueService.acceptSuggestion` and
opens Review on the result, instead of re-drafting `suggestion.proposal`.
`HomeScreen`'s suggestion card names the target cue by its own title
(looked up from the same `routines` list Home already renders from) and
only shows "Review idea" when `suggestion.operation` is non-null.

**Remove when:** `./dev b` compiles this, and a run on the loaner confirms
Accept opens Review on the edited routine with the right field changed, for
at least one of `earlyStop`/`recurringSkip`/`manualRoutine`/`blockedAction`.

---

## CL-28 — A manual trigger has no way to name which cue it starts

**Status:** open · **Raised:** 24 Sep 2026

`Trigger.Manual`/`ManualKit` match any `TriggerEvent(EventKind.MANUAL_RUN,
...)` unconditionally — `ManualKit.match` returns `MATCH` for that event
kind with no reference to which routine asked for it, because
`TriggerEvent` itself carries no routine identifier (its disambiguating
fields — `deviceId`, `connectionSessionId`, `networkLabel` — are all
signal-specific, and manual runs have none of their own). `CueService.
onDeviceEvent` evaluates one event against every armed routine
(`routines.armed().flatMap { ... event.couldStart(routine) ... }`), so a
single `MANUAL_RUN` event would start *every* armed `Trigger.Manual`
routine whose other conditions also hold, not just the one a caller meant.

Nothing in `:app` has ever hit this: there is no "run this cue now" UI
anywhere in the app, and `EventKind.MANUAL_RUN` is otherwise only used from
`:core` tests, the CLI's `./dev sim`/`./dev d`-style rehearsal path, and
`ManualKit.rehearsalEvents` (rehearsal never calls `onDeviceEvent` for
real). Found while building Task 17's AppFunctions provider, which needed a
per-cue "start" action and couldn't safely use this path — see CL-24.

**Remove when:** `TriggerEvent` (or a new, Manual-specific companion) can
name which routine a manual run targets — reusing `routineId`, the same
field this pass already added to several `LedgerEvent` variants for CL-27,
is one option — and `event.couldStart(routine)` for `Trigger.Manual` checks
it. Add a test proving two armed manual cues don't both start from one
targeted event, then Task 17's `startCue` (or any future "run now" UI) can
use it.

---

## CL-29 — Share target: a new intent-filter, unverified on a device

**Status:** open · **Raised:** 24 Sep 2026

Task 9 listed "share target" among the not-done Android surfaces. Added: a
second `<intent-filter>` on `MainActivity` for `ACTION_SEND` / `text/plain`,
and `MainActivity.sharedOrCapturedText` — the same function now also used
for `EXTRA_SCREEN_CAPTURE` — reads `EXTRA_TEXT` and feeds it into
`incomingScreenText`. It reaches the app through the exact path CL-23 item 2
just closed: `CuesApp` navigates to Home and `HomeScreen` lands the text in
the draft field as plain, editable data. This applies the FDD's "Shared
timetable" rule (shared text is data, never executed, never auto-armed) to
any share-sheet text, not only a photographed timetable.

**What is not verified, because `:app` cannot be exercised in this
environment (no Android SDK — see CLAUDE.md):**
1. Whether the share sheet actually lists Cues for a `text/plain` share on
   OriginOS 7 — the `<category android:name="android.intent.category.DEFAULT" />`
   requirement for an implicit-intent target is textbook, but unconfirmed here.
2. Whether `singleTop` launch mode correctly routes a share while Cues is
   already open through `onNewIntent` rather than a fresh `onCreate`, and
   whether a share while Cues is backgrounded behaves the same as a cold
   launch — both are asserted from platform docs, not observed.
3. No injection-style test exists for this specific path the way
   `TimetableExtractorTest` has one for camera import — shared text reaching
   the draft box untouched, with no way to auto-submit it, is currently
   argued from reading the code, not demonstrated by a test that tries to
   break it.

**Remove when:** a real share from another app (Chrome, Messages, a notes
app) has landed in Cues' draft box on the loaner, and either a test proves
shared text cannot reach `onDraft`/arm anything without the user pressing
submit and going through Review, or that guarantee is judged self-evident
enough from `HomeScreen`'s existing `submitDraft` gate to skip.

---

## CL-30 — Calendar condition kit: fully tested in `:core`, a new permission unverified on a device

**Status:** open · **Raised:** 24 Sep 2026

Task 9 listed the calendar condition kit among the not-done items. Added:
`Condition.CalendarBusy`/`CalendarNotBusy`, `Capability.READ_CALENDAR`,
`signals/CalendarKit.kt` (mirrors `BatteryThresholdKit`'s shared-evaluation
shape), and a `GrammarParser` phrase ("my calendar is busy"/"free"/"clear"/
"not busy").

**What is genuinely built and tested, entirely in `:core`:**
- `CalendarKitTest.kt`: busy/free matching, the inverse relationship between
  the two conditions, and — the one that matters — a denied `READ_CALENDAR`
  reads as `Unknown`, never as "not busy". `SignalRegistry`'s own init-block
  check enforces every `Condition` subtype has exactly one kit, so this
  can't silently go unregistered.
- `GrammarParserTest.kt`: the phrase drafts the matching condition and never
  both at once, passes `Validator`, and derives `Capability.READ_CALENDAR`.
- Verified with `./dev d "when my earbuds connect, start a 25 minute focus
  timer if my calendar is busy"` (not assumed): drafts, reviews (`ACCESS`
  row correctly lists "calendar read access"), and rehearses to a correctly
  `Unknown`-because-never-observed skip — there is no CLI adapter feeding it
  a value, which is the honest behaviour for an unwired signal.

**What is not verified, because `:app` cannot be exercised in this
environment (no Android SDK — see CLAUDE.md):**
1. `app/.../runtime/CalendarReadings.kt`'s `CalendarContract.Instances`
   query has never run against a real calendar provider — whether an
   all-day or a `AVAILABILITY_FREE`-marked event should count as "busy" is
   an open product question this pass answers with "any instance exists,
   full stop," disclosed as a simplification in the code, not resolved.
2. `android.permission.READ_CALENDAR` is a genuinely new, dangerous runtime
   permission — added to the manifest, but there is no in-app grant button
   for it yet the way CL-26 added for notifications/DND/exact-alarm. A cue
   using either calendar condition will show `Unknown` → skipped until the
   user is prompted through the OS's own permission dialog some other way
   (today, that means the system permission screen directly, not a Cues
   button).
3. `./dev perms` has not been run against this revision (no Android SDK
   here) to confirm `READ_CALENDAR` is the only new line and `INTERNET` is
   still absent.

**Remove when:** a real device confirms `READ_CALENDAR` can be granted and
denied in-app, a real calendar event drives `Condition.CalendarBusy` through
an actual armed cue, and the all-day/free-marked-event question above is
either answered or explicitly deferred with a tracking entry of its own.

---

## CL-31 — App shortcuts: written, unverified on a device, no new permission

**Status:** open · **Raised:** 24 Sep 2026

Task 9 listed app shortcuts among the not-done items. Added
`app/.../runtime/AppShortcuts.kt` (`ShortcutManagerCompat.setDynamicShortcuts`)
with a "New cue" entry plus one "Start ‹cue›" entry per armed
`Trigger.Manual` cue, refreshed on process start (`CuesApplication.onCreate`)
and on every `ON_RESUME` (`MainActivity`'s existing coverage-check
observer). Tapping "Start ‹cue›" fires the exact same event
`CuesAppFunctionService.startCue` already does — `EventKind.MANUAL_RUN`
scoped to that one routine id (CL-28), never an unscoped run.

**A real, disclosed decision, not an oversight:** every shortcut is
*dynamic*, none are static (`res/xml`). A static shortcut's `<intent>` needs
a hardcoded target package, and this app's debug build changes its package
id (`applicationIdSuffix = ".debug"` in `app/build.gradle.kts`) — a static
XML resource has no way to reference `${applicationId}` the way
`AndroidManifest.xml` can, so a hardcoded `com.cues.android` would silently
point a debug build's shortcut at the wrong package. Building every intent
as `Intent(context, MainActivity::class.java)` in Kotlin is always correct
for whichever build is actually installed; the cost is that shortcuts only
exist after the app has run at least once, not immediately after install.

**What is not verified, because `:app` cannot be exercised in this
environment (no Android SDK — see CLAUDE.md):**
1. None of `ShortcutManagerCompat`'s calls have run on a device — whether
   the shortcuts actually appear on a long-press of the launcher icon, and
   whether OriginOS 7's launcher honors dynamic shortcuts the way stock
   Android does, are both unconfirmed.
2. `ShortcutManagerCompat.getMaxShortcutCountPerActivity` is trusted as
   returning a sane positive number; the `?: 4` fallback for a non-positive
   result has never been exercised.
3. No test proves a "Start ‹cue›" tap can't reach a paused, disabled or
   non-manual routine — the guard clauses in the `MainActivity` handler
   mirror `CuesAppFunctionService.startCue`'s checks, but unlike that
   class's own logic (which lives partly in tested `:core` code via
   `onDeviceEvent`/`couldStart`), the shortcut-specific routing itself has
   no test.

**Remove when:** the shortcut set has actually appeared on the loaner's
launcher, "Start ‹cue›" has started a real manual cue from a long-press
menu, and item 3's routing has either a test or is judged thin enough to
skip one.

---

## CL-32 — Workbench drag/drop: written against the stable View API, unverified on a device

**Status:** open · **Raised:** 24 Sep 2026

Task 9 listed Workbench drag/drop among the not-done items. Added
`android:resizeableActivity="true"` on `MainActivity`, and
`ui/DragAndDrop.kt`'s `Modifier.dragAndDropTextSource` — long-press starts a
system drag carrying plain text, wired onto a receipt card (`ReceiptScreen`,
drags `entry.text` — the exact rendered receipt, nothing summarized) and the
"Share as a Cue Card" button (`RoutineDetailScreen`, drags `CueCards.encode`'s
output — the identical payload `CueCardShareScreen`'s own "Share as text"
button already sends).

**A real, disclosed decision:** this uses `View.startDragAndDrop` (the
platform API, stable and unchanged since API 24) rather than Compose's own
`Modifier.dragAndDropSource`. That modifier's callback shape has changed
across Compose Foundation releases, and this environment has no compiler to
confirm which shape the pinned `composeBom` version (2024.12.01) expects —
the same reasoning CL-15 used to decline building a `ProgressStyle`
notification blind, applied here to a case where a well-documented,
version-stable alternative actually exists, so the feature could still be
attempted rather than deferred outright.

**What is not verified, because `:app` cannot be exercised in this
environment (no Android SDK — see CLAUDE.md):**
1. None of this has run on a device: whether OriginOS 7's Atomic Workbench
   (or stock Android split-screen) actually offers Cues as a drag source or
   accepts a drop into it, and whether `resizeableActivity="true"` alone is
   sufficient for the app to appear as a Workbench pane at all, are both
   unconfirmed.
2. Layering a `pointerInput` long-press detector on top of `OutlinedButton`'s
   own internal `clickable` (on the "Share as a Cue Card" button) is a real,
   disclosed interaction risk — Compose's gesture arbitration between two
   independent detectors on the same node has not been exercised here, and
   it's plausible a long-press could suppress the button's ordinary tap, or
   the reverse.
3. `View.DragShadowBuilder(view)` shadows the *entire* Compose host view,
   not just the dragged card or button — visually wrong (a full-screen
   shadow instead of a small card), though not functionally wrong. A
   correctly-scoped shadow needs a custom `DragShadowBuilder` this pass
   didn't build.

**Remove when:** a real long-press-and-drop between Cues and another app (or
Workbench pane) has been observed on the loaner, the button/long-press
interaction in item 2 is confirmed not to break ordinary taps, and either
item 3's shadow is fixed or judged acceptable to ship as-is.

## CL-33 — Kinetic Obsidian redesign: `:app` compiles for the first time in this environment, still unverified on a device

**Status:** open · **Raised:** 25 Sep 2026

The UI/UX redesign (design tokens in `ui/theme/`, a shared component library
in `ui/components/`, navigation-compose replacing the old sealed-`Screen`
router, `ObservableStore`/`StoreGeneration` for disk-is-truth reactivity, and
rebuilt Now/Ask/Insights/Workbench screens) is implemented against the real
`:core` API. Three genuinely new things were confirmed, not assumed, in this
environment:

1. **`:app:compileDebugKotlin` was made to actually run here for the first
   time.** Every earlier statement in this repository that "the Android
   runtime was written against real APIs but never compiled" (CL-02, the
   PRS) was literally true until this pass — Google's Maven was unreachable
   at scaffold time. This machine *does* have `ANDROID_HOME` set locally
   (outside `local.properties`'s committed placeholder), so the Kotlin
   compiler could actually run once two unrelated blockers were routed
   around (below). Every file this redesign touched now compiles clean.
2. **`androidx.appfunctions:appfunctions:1.0.0-alpha12` and
   `androidx.appsearch:*:1.1.0` require AGP ≥ 8.9.1 (appfunctions wants
   9.1.0), and this project pins AGP 8.7.3 with `compileSdk = 36`.**
   `:app:checkDebugAarMetadata` fails outright on this combination —
   confirmed by running it against `main` before any redesign change, so
   this is pre-existing, not something the redesign introduced. Bumping AGP
   is a real, separate decision (Gradle-version and plugin-compat
   consequences of its own) and was deliberately left alone here rather
   than folded into a UI pass. The Kotlin-compile check above ran with
   `-x :app:checkDebugAarMetadata`, which only skips that one metadata gate,
   not actual compilation.
3. **`res/xml/app_metadata.xml`, referenced by `AndroidManifest.xml`'s
   `android.app.appfunctions.app_metadata` `<property>`, is normally
   generated by the appfunctions KSP processor** — which, per the existing
   comment at that manifest line, "has never run" for the same
   Maven-reachability reason. A resource-linking failure over the missing
   file was worked around locally with a throwaway stub file, deleted again
   once Kotlin compilation was confirmed; nothing generated is committed.
   `kspDebugKotlin` *did* run successfully once the AAR-metadata gate was
   skipped, which is itself new information: KSP's annotation processing
   over this codebase has now been exercised, even though the file it needs
   for a full resource-link/package/install still doesn't exist here.

**Superseded by the update below:** a full `assembleDebug` and a run on an
emulator both happened later the same day, once the guava/listenablefuture
conflict (the very next item) got a real fix instead of a workaround. See
that update for what was actually confirmed and what — dark theme,
`StoreGeneration` under a real process kill, the bundled fonts' per-weight
`FontVariation` rendering, a physical device — still isn't.
- Two pre-existing files, untouched by this redesign and confirmed
  unaffected by it (no diff against `main`), still fail to compile in this
  environment: `camera/CueCardScanScreen.kt` and
  `camera/TimetableCaptureScreen.kt`, both on
  `Cannot access class 'ListenableFuture'`. Root cause, confirmed rather than
  guessed: `androidx.appsearch:appsearch` (pulled in by appfunctions) forces
  dependency resolution to `com.google.guava:guava:32.0.1-android`, which
  Gradle's Maven-conflict rule then substitutes in place of the real
  `com.google.guava:listenablefuture:1.0` jar camera-core's
  `ProcessCameraProvider.getInstance()` needs — but only guava's **`.pom`**
  is present in this machine's Gradle cache, never its `.jar`
  (`~/.gradle/caches/modules-2/files-2.1/com.google.guava/guava/32.0.1-android/`
  has no jar file), so the substitution resolves to an empty stub with no
  usable `ListenableFuture` class at all. `./gradlew --refresh-dependencies`
  was tried and still fails the same way after ~90s of real network
  activity, even though a plain `curl` to `repo1.maven.org` for that exact
  jar path returns 200 — so *some* path to Maven Central works from this
  machine, but Gradle's own resolution for this specific artifact doesn't,
  for a reason this pass didn't chase further (a proxy/allowlist specific to
  the Gradle daemon's HTTP client is the leading guess). Not investigated
  past that as part of this redesign; the camera/OCR/QR features are
  unaffected in scope (their Kotlin source is unchanged) but currently
  cannot be exercised even at compile time in this environment.
- **Update, same day:** the parallel `:core` addition landed (CL-34) and was
  merged and wired in. `InsightsScreen` now renders the real `InsightsReport`
  from `CueService.insights(window)`, not a ledger-only summary.
  `AndroidActionExecutor` adopted `ActionExecutor.release(obligation,
  session)`, closing the utility-restore bug this entry and CL-23 item 8
  both flagged (`CleanupObligation.args` now carries the restore target,
  not an in-memory map that died with the process). Session notification,
  widget and the quick-settings tile were also restyled. A Workbench patch
  bay was added (touch-to-patch controls compiled live through
  `PatchSentence`, then the same `CueService.draft` path).
- **Update, same day: `assembleDebug` succeeded, and the app ran on an
  emulator.** The guava/listenablefuture conflict above turned out to have
  a real, permanent fix, not just a local workaround: CameraX 1.5.3 expects
  the *consuming app* to add a `ListenableFuture` provider itself (its own
  setup docs say so) rather than declaring one as a dependency, so nothing
  on the compile classpath satisfied `ProcessCameraProvider.getInstance()`'s
  return type. `app/build.gradle.kts` now depends on
  `com.google.guava:guava` directly (pinned in `libs.versions.toml` to the
  version `androidx.appsearch` already resolves to) and excludes the
  standalone `listenablefuture` module project-wide, so guava's own bundled
  copy of the class is the only one on any classpath — no version force,
  no duplicate-class conflict. `res/xml/app_metadata.xml` is committed as a
  documented placeholder (see the comment in that file) so the
  `android.app.appfunctions.app_metadata` manifest reference resolves;
  KSP still does not generate a real one in this environment. With both of
  those, `./gradlew :app:assembleDebug -x :app:checkDebugAarMetadata`
  (that one flag still needed — see item 2 above, unchanged) produced a
  real, installable APK.
  - Installed and launched on the `Cues_Pixel_9` AVD already present on
    this machine (`emulator -avd Cues_Pixel_9`). No crash, no fatal
    exception in `logcat`. This is the first time any part of `:app` has
    been observed running, anywhere, in this project's recorded history.
  - Screenshots surfaced four real bugs, all fixed and reverified by
    reinstalling: (1) the Now cockpit's three trust chips sat in a plain
    `Row`, whose non-weighted children are still measured with the *row's
    own* loose maxWidth as their upper bound — on a phone-width screen the
    third chip's text wrapped into a tall single-character column,
    ballooning the whole row (and the visible gap above "SIGNAL GATES")
    to roughly 320dp; made the row `horizontalScroll` instead, which also
    matches the plan's "horizontal chip row" description better than a
    row that silently overflowed. (2) `CuesTopBar`/`CuesBottomNav` are
    plain `Row`s, not Material3's `TopAppBar`/`NavigationBar`, so
    `enableEdgeToEdge()` let the status bar draw directly over the logo
    and the gesture bar crowd the tab labels; both now take
    `Modifier.windowInsetsPadding(WindowInsets.statusBars /
    .navigationBars)`. (3) `AskScreen` rendered "TRY A TEMPLATE" twice —
    once itself, once again inside the reused `TemplateGallery` — and
    `TemplateGallery`'s chips still used the *old* `com.cues.app.ui.Theme`
    (`cuesColors`, uninstantiated in the new `CuesTheme`, so it silently
    fell back to `DarkSemantic`), rendering dark chips on a light screen;
    fixed the duplicate and restyled `TemplateGallery` onto the new
    tokens. (4) `SectionHeader`'s meta text had no `maxLines`, so a long
    one (Workbench's "touch-to-patch, checked live against the grammar")
    wrapped onto a second line that visually sat *above* the title instead
    of beside it; now single-line with ellipsis, both title and meta given
    a shared `weight(1f, fill = false)` so long text truncates instead of
    wrapping.
  - The Workbench patch bay was exercised end to end on the emulator:
    selected "Charger plugged in" (WHEN) and "Request quiet (DND)" (DO),
    watched the live preview compile "When I plug in the charger, quiet
    notifications." through the real `PatchSentence`/`GrammarParser`
    round-trip, tapped "Compile to Review", and landed on Review showing
    "drafted by grammar parser", clause-accounted source text ("Every word
    accounted for."), the WHEN/IF/DO/UNTIL/RESTORE cards with correct
    badge colors, the DND action's honest restore copy ("release our
    quiet rule — and nothing else. Another quiet mode stays as it is."),
    and Approve correctly disabled with "Grant access first" pending the
    notification-policy permission. This is the first time the full
    authoring path — draft, clause accounting, review, permission gating —
    has been seen running rather than only unit-tested.
- Receipts is still the old screen restyled by inheriting the new theme
  only — no structured `ReceiptRecord` cards yet, though `ReceiptLog` is
  wired (`CuesApplication` passes `receiptLog = store`) and every session
  now has one waiting to be rendered.
- Encountered and worked around three times in this pass, not yet
  understood: importing `androidx.compose.foundation.layout.weight`
  explicitly in a file (rather than relying on the implicit `RowScope`/
  `ColumnScope` receiver already in scope inside a `Row { }`/`Column { }`
  lambda) resolves to an unrelated **internal** `RowColumnParentData.weight`
  property in this pinned Compose Foundation version and fails to compile
  ("it is internal in file"). Every file that hit this
  (`ui/components/Readouts.kt`, `ui/insights/InsightsScreen.kt`,
  `ui/workbench/WorkbenchScreen.kt`) was fixed by simply deleting the
  import — `.weight(...)` still resolves correctly via the implicit
  receiver with no import at all. Worth a real explanation before adding
  another `Row`/`Column` file that needs `weight`.

- **Update, same day: dark theme checked too.** `cmd uimode night yes` on
  the same AVD, reinstalled, and rescreenshotted Now, Ask, Workbench, the
  patch bay and Review. All render correctly and consistently — the
  obsidian surfaces, clause-badge colors (including DO's filled
  yellow-on-black treatment, which only applies in dark theme) and status
  colors all read as intended. Found and fixed one real issue:
  `KineticButton`'s disabled state was `t.doYellow.copy(alpha = 0.35f)`,
  which renders as a legible soft peach over the light theme's near-white
  but as a murky, low-contrast olive over the dark theme's near-black —
  alpha-blending a saturated color doesn't desaturate the same way against
  a light background as a dark one. Replaced with the standard disabled
  treatment (neutral raised surface, hairline border, slate text), which
  is unambiguous in both themes; confirmed by rescreenshotting both. Also
  hit one transient, non-reproducible blank-screen artifact from toggling
  `cmd uimode` rapidly while mid-navigation, which forces an Activity
  relaunch (`MainActivity` declares no `configChanges` for `uiMode`) —
  gone on the next clean launch, and not something a real user's Settings
  toggle would race with taps the way scripted `adb` commands did.

**What is still not verified:**
- A physical iQOO, or any physical device — everything above ran on the
  `Cues_Pixel_9` AVD only.
- Touch-target sizing, TalkBack, font-scale, and every screen this pass
  didn't specifically navigate to and screenshot (Insights, Receipts,
  Review's permission-grant buttons actually granting, Cue Detail, Checks,
  the remaining Workbench editors, camera/QR capture).
- Live surfaces (the session notification's chronometer/progress bar, the
  widget, the quick-settings tile) — none has an armed, running session to
  render against yet in any environment.
- `android.app.appfunctions.app_metadata`'s real content — the committed
  file is a documented empty placeholder, not what KSP would generate.
- Whether the guava/listenablefuture fix holds under `--offline` or on a
  machine that has never resolved `androidx.appsearch` at all (this
  machine already had `guava-32.0.1-android.jar` cached from an earlier
  `--refresh-dependencies` attempt in this same session).

**Remove when:** the app has run on a physical iQOO (or any physical
device), the screens this pass didn't reach have been screenshotted, and
a real (KSP-generated) `app_metadata.xml` replaces the placeholder.

---

## CL-34 — Insights: computed and benchmarked on the JVM, never measured on a phone

**Status:** open · **Raised:** 25 Sep 2026

`core/.../insights/Insights.kt` computes the Insights report from sessions,
structured receipts (`receipts-v2/`) and the usage ledger.
`InsightsBenchmarkTest` runs plan §10.2's synthetic worst case (1,200
sessions, 200 receipts, 14,000 ledger events): a median of 2–3 ms against a
150 ms budget. That figure is **the pure computation on an Apple M4 laptop
JVM**, with every input already in memory.

**What is not measured:**
1. The device cost. The reads that gather the inputs are what §10.2 expects
   to dominate: `SessionStore.recent` and `allUnfinished` decode one file per
   session, and `ledgerEvents()` decodes one file per ledger event (up to
   ~14,000). None of that has run on the loaner.
2. Whether pruning (sessions ended over 30 days ago, owing nothing, deleted
   on the next ended save) keeps `allUnfinished()`'s full scan cheap enough
   in practice. It bounds the file count; the time per file is unknown.

**Consequence worth knowing now:** pruning at 30 days means the 30-day
window's *previous*-window delta (days 30–60) will almost always read "No
earlier data". That is honest, not a bug, but the UI should expect it.

**Remove when:** the debug "Insights computed in N ms (N events)" line in
Checks has been read on the loaner with a realistic ledger, and the figure
is recorded in `docs/MEASUREMENTS.md` with its source. If it exceeds 300 ms,
or file reads dominate, first move the ledger to a single append-only file
(plan §10.2) before considering SQLite/Room.
