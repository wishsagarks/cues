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
