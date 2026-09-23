# Sprint 6: Contextual, declared

Sprint 6 extends Cues with context the user can name, inspect and bound. It
does not infer habits, read screen content, guess places, or turn `UNKNOWN`
into false.

| Track | Delivered | Verification |
|---|---|---|
| A | Named-context model, resolution store, semantic inlining, capability union and temporary patches | `ContextualKitsTest`, core suite |
| B | Audio-output trigger/condition and battery threshold conditions; Android audio callback and sticky battery reading | `./dev t`, `./dev b` |
| C | Pure Today forecast, fixed disclosure label, checked-in starter templates and `./dev today` | `./dev today` |
| D/F | API 36 target, monochrome notification icon, scoped service stop, and real result notifications with read-back | `./dev b`, `./dev perms` |
| H | Declared-place model, conditions/triggers, bounds, semantic coordinates and derived foreground/background access | `ContextualKitsTest` |

## Risk register

| ID | Open device check | Fallback / disclosure |
|---|---|---|
| R9 | Audio callback delivery while backgrounded | Health reports it is live only while the process is alive. |
| R10 | `BATTERY_LOW` manifest delivery | No battery threshold trigger is shipped; thresholds remain snapshot conditions. |
| R11 | API 36 promoted session presentation on OriginOS | Keep the standard foreground notification. |
| R12 | Quick Settings tile stop with the process killed | Treat notification stop as the supported stop surface until measured. |
| R13 | Geofence delivery under OriginOS battery policy | Place semantics stay declared; delivery must be measured before an arrival claim. |

## Cut order

If the loaner forces scope reduction: widget, place trigger, battery kit UI,
template gallery UI, then Today UI. Never cut approval invalidation after a
context/place edit, patch expiry/version checks, unknown-member reasons, or
the grammar-drafter label.

## Enhancement pass (24 Sep 2026)

The first pass built the whole `:core` domain — named contexts, patches,
audio/battery kits, the forecast, declared places — plus a glance widget, a
Quick Settings tile and a visual refresh, but left most of it unreachable
from the app: no screen created a `NamedContext` or `Place`, no screen showed
`forecastToday`'s output, `RoutineDetailScreen` had no skip/pause controls
even though `CueService.skipToday`/`pauseUntil` already existed, and one
checked-in template (`Bedtime charge check`) never actually compiled clean.

Closed in this pass:

- `TemplatesTest` compiles every checked-in template through `GrammarParser`
  and fails the build if any leaves an unsupported or unaccounted clause —
  the broken template is fixed, not exempted.
- `ContextsScreen`: lists and creates named contexts (from signals actually
  readable right now — charging, Wi-Fi, a connected device, audio output;
  never a signal reading `Unknown`) and places (typed coordinates, asking for
  no location permission).
- `TodayScreen`: renders `forecastToday` with `FORECAST_LABEL` shown
  verbatim.
- `RoutineDetailScreen`: Skip today / Pause 3h / Pause until tomorrow, backed
  by a new `CueService.currentPatch` reader so an active patch is visible and
  clearable rather than fire-and-forget.
- A template gallery on Home, `BackHandler` on every non-Home screen, and the
  stray "iQOO Design System" comment in `ui/Theme.kt` removed.

Left open, tracked as CL-11 through CL-15: audio-callback delivery while
backgrounded (R9), the missing battery-threshold trigger (R10), the missing
place delivery adapter (R13), targetSdk 36 behavior on the loaner, and the
promoted Live Update notification that was planned but not built (R11).
