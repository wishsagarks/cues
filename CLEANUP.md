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

## CL-02 — Android stubs that report BLOCKED

**Status:** open · **Raised:** 22 Sep 2026

`AndroidActionExecutor` returns `BLOCKED` with an explanation for every action.
`Stubs.kt` logs events without acting on them. `OnDeviceLlmDrafter` returns a
clean failure. `SampleRoutine` supplies a hard-coded cue so the scaffold screen
renders something.

These are deliberately unable to succeed. An executor that returned
`SUCCEEDED` without doing anything would make the app look finished and the
receipts lie, which is the precise failure the product is built to avoid.

**Remove when:** each is replaced by a real implementation verified on the
device. `SampleRoutine` goes when the Review surface can display a drafted cue.

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

Assumed without verification: that the loaner runs OriginOS 6 on Android 15 or
later; that `AutomaticZenRule` behaves as documented there; that exact alarms
can be scheduled; that manifest-declared Bluetooth and power receivers actually
deliver while the app is backgrounded under the OEM battery policy.

The FDD is explicit that a receiver declaration is not proof of delivery. None
of this is knowable from a container.

**Remove when:** each is tested on the loaner and the result recorded — as a
working implementation, or as a documented limitation. A limitation that is
written down is closed; one that is quietly hoped about is not.
