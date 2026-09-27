# Verification walkthrough: place arrival, WhatsApp, regional language

One executable checklist for the three things that are still marked
unverified in CLEANUP.md and genuinely need the loaner and/or you, not
adb automation alone:

- **CL-13** — place-arrival trigger, specifically **R13**: delivery under
  the OEM battery policy, screen off.
- **CL-42** — regional-language authoring quality (Gemma on-device
  translation) and whether the phone's speech recognizer actually
  supports a non-English locale.
- **WhatsApp handoff** (`ActionId.COMPOSE_WHATSAPP`) — not in CLEANUP.md
  yet because it hasn't been run on a device at all.

Each step says who does it: **[YOU]** needs your hands on the phone or a
real walk outside; **[ME]** I drive over adb; **[EITHER]** doesn't matter.
Do the parts in order — Part A gates on a real place, Part B and Part C
don't depend on each other.

## Before starting

1. **[EITHER]** Confirm the phone's on `adb devices` and unlocked.
2. **[YOU]** Open Google Maps, find a place you can actually go stand
   near for the arrival test (your office, home, anywhere reachable) —
   or one you're already at. Long-press the pin and read out (or note)
   the coordinates Maps shows at the bottom (e.g. `12.9716, 77.5946`).
   This is what makes the saved Place real instead of made-up numbers —
   Cues' own radius floor is 100 m, so anywhere Maps gives you a pin for
   is precise enough.
3. **[ME]** Install the current build: `./dev i` or the adb equivalent.

## Part A — place arrival → WhatsApp (CL-13 / R13, WhatsApp handoff)

| # | Who | Step | What should happen |
|---|---|---|---|
| A1 | ME | Open Cues, Workbench → Contexts & places → New place. Fill Name = a short label for your Maps pin, Latitude/Longitude = the coordinates from step 2, radius 150m. Save. | Place appears under PLACES. |
| A2 | ME | Workbench → Patch bay. WHEN = "Enter a place" (auto-selects your saved place — this is the picker-default bug fixed on 2026-09-26; it should now genuinely commit, not just look selected). DO = "Draft a WhatsApp message". Fill Message in **lowercase** (the grammar reads it case-sensitively) — e.g. `i am home`. Contact optional. | Patch Wire shows "GENERATED SENTENCE", not "Pick a WHEN...". |
| A3 | ME | Tap Compile to Review. | Lands on Review: WHEN "enter <place>", DO "Draft a WhatsApp message", risk badge HANDOFF. |
| A4 | YOU | Tap the location "Allow" grant button. Approve the OS permission dialog (**While using the app** is enough — this is `ACCESS_FINE_LOCATION`, no background permission needed, see CL-13's note on why). | "Grant access first" clears; Approve & Arm becomes enabled. |
| A5 | YOU | Tap Approve & Arm. | Status ARMED. Now screen shows the cue as armed. |
| A6 | YOU | **Leave the phone alone, screen off, for at least 10–15 minutes** while it is *not* at the saved place (simulates the OEM battery policy actually letting the foreground service keep running unattended — this is R13's real question). Then walk to the saved place (or however you can trigger a real arrival at those coordinates) and stay within the radius for at least ~90 seconds (the dwell window is 60s). | A notification from "Cues is watching for <place>" should still be visible the whole time you had the screen off — if it's gone, the OS killed the foreground service and that's the R13 finding right there. |
| A7 | YOU | Once you're confident you dwelled inside the radius for 60s+, check: did WhatsApp open with your drafted text pre-filled? | If yes: note the actual delay between arrival and the WhatsApp draft appearing (want vs. actual, for the "within a few minutes" honesty claim). If no: check Receipts (next step) before concluding it failed. |
| A8 | EITHER | Open Cues → Receipts. Look for a start receipt referencing this cue. | If a receipt exists but WhatsApp didn't open, that's a `composeWhatsApp` execution bug, not a trigger-delivery bug — different thing to fix. If no receipt at all, the arrival was never detected — that's the real R13 failure mode. |
| A9 | EITHER | Record the outcome in `docs/DEVICE_MATRIX.md`'s R13 row (pass / limited / fail, with what you actually observed) and update `CLEANUP.md` CL-13 — don't close it, just fill in what's now known. | Paper trail matches the project's own "no number ships until measured" rule. |

**If A6/A7 fails (no delivery after screen-off):** that's a legitimate, expected-possible R13 outcome given `PlaceWatcherService` is a plain foreground service, not a wakelock-holding one — the OEM battery policy is exactly what's being tested. Don't treat it as a regression; it's the answer to a question nobody had run yet. The honest fallback (already in `docs/DEMO.md`) is to demo the armed state live and show the WhatsApp handoff as a recorded clip.

## Part B — regional language authoring (CL-42)

adb's `input text` cannot type Devanagari/Bengali/etc. (confirmed — it
throws `NullPointerException`, no key mapping exists for non-Latin
scripts), so this part needs your hands on the keyboard, not mine on adb.

| # | Who | Step | What should happen |
|---|---|---|---|
| B1 | ME | Open Cues → Ask. Confirm "GEMMA WARM · on-device" is showing (toggle it on if not) and Sarvam cloud assist is **off** — this isolates the on-device path, not Sarvam's. | Routing line reads "Gemma normalizes → local parser validates." |
| B2 | YOU | Tap the Hindi chip (or whichever language you're fluent enough in to judge the output). Type or speak a real request in that language — something with a clear trigger/action, e.g. the Hindi equivalent of "when my charger connects, silence notifications for 10 minutes". | Field shows your text in that script. |
| B3 | YOU | Tap Ask Cues. | Draft pipeline shows Gemma ran; either a drafted cue appears in Review, or a clarification question. |
| B4 | YOU | **Judge the result yourself** — does the drafted cue (or clarification question) actually match what you asked for? Note anything mistranslated, dropped, or invented. | This is the actual CL-42 gate: a fluent speaker's judgment, which I can't substitute for. |
| B5 | YOU | Now test voice: tap Speak, say the same request in the same language. | Either a transcript appears in your language, or an error. If it's `ERROR_LANGUAGE_NOT_SUPPORTED` or similar, that's the answer to CL-42's speech-recognition-locale question. |
| B6 | EITHER | Update `CLEANUP.md` CL-42 with what you found — translation quality verdict, and whether speech recognition worked for that locale. | Closes the loop CL-42 left open. |

## Part C — optional, quick: plain SMS/message handoff sanity check

Lower priority (COMPOSE_MESSAGE already has some prior coverage per
CLEANUP.md), but cheap to confirm alongside WhatsApp since you'll already
be in Patch Bay:

| # | Who | Step | What should happen |
|---|---|---|---|
| C1 | ME | Patch bay: WHEN = "Run by hand" (no picker, no waiting), DO = "Pre-fill a message", lowercase text, optional contact. Compile → Review → Approve & Arm (no permission needed for this one). | Arms immediately. |
| C2 | YOU | Now screen → run the shortcut/manual cue. | Default SMS app opens with the text pre-filled — confirms the non-WhatsApp handoff path still works. |

## When you're ready

Tell me which parts you want to run together — I can drive every ME/EITHER
step the moment the phone shows up on `adb devices` again, and pause at
each YOU step for you to do your part and tell me what you saw.
