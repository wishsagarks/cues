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

Two things flagged for the first Saturday pass, not yet true bugs:

- `AndroidActionExecutor.zenRuleIds` is an in-memory map from session id to
  the platform's zen-rule id. It does not survive process death, so a
  `DND_CONTRIBUTION` obligation left over from a killed process falls back to
  "no rule was held" on release rather than actually finding and removing it.
  A real fix reconciles against `NotificationManager.automaticZenRules`,
  matched by owner and name, on boot.
- The Review screen's rehearsal and required-access rows never account for a
  capability that becomes available only after the user leaves the app to
  grant a permission and comes back — there is no re-check on resume.
- The Receipts screen loads once via `remember { store.receipts() }` and does
  not refresh while open. Fine for a demo; a live session ending while the
  screen is on-screen won't show up without leaving and reopening it.

**Remove when:** each path is exercised on the loaner and the result recorded
— a working implementation, or a documented limitation with its own entry
here.

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
