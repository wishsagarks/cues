---
name: cues-android
description: Build, test, and evolve the Cues Android/Compose app without weakening its declared-context safety model.
---

# Cues Android development

Use this skill for Android, Kotlin, Compose, widget, tile, notification, and
device-adapter changes in this repository.

## Architecture boundary

- `:core` is Kotlin/JVM only. Never add an Android dependency or import.
- `:app` owns Android APIs, Compose, adapters, notifications, tiles and widgets.
- Device effects must cross a `core/.../ports/Ports.kt` interface; use fakes in
  core tests.

## Required checks

Run the narrowest relevant checks while changing code, then before handoff run:

```sh
./dev t       # all pure Kotlin core tests
./dev b       # debug APK
./dev perms   # merged manifest must not include INTERNET
```

For parser or forecast work, also run `./dev d "<cue>"` and `./dev today`.

## Safety invariants

- Keep unknown context as `ContextValue.Unknown`; it is never a non-match or
  permission to act.
- Derive capabilities from signal/action kits. Never persist a model-provided
  capability set as authority.
- Semantic edits to a declared context or place invalidate approval.
- Runtime code after approval must be deterministic and must not consult a
  drafting model.
- Actions read back platform state when that is possible; a failed read-back is
  `BLOCKED`, not success.
- Receipts originate from reason codes and observed values.
- Cleanup only releases effects Cues owns.

## Compose interaction quality

- Let animation explain user action: navigation, expanding content, listening
  and drafting state. Avoid decorative looping motion.
- Use light tap haptics for ordinary actions; reserve `LongPress` feedback for
  destructive actions.
- Keep motion short and interruptible, use content-size transitions for live
  result areas, and preserve readable dark/light contrast.
- Add any new app capability, receiver, service or provider to the manifest
  deliberately and re-run `./dev perms`.

## Device truth

An APK build proves type/API integration, not OEM delivery behavior. Record
loaner-only behavior in `CLEANUP.md` and the matching row of
`docs/DEVICE_MATRIX.md` (evidence from `./dev probe`), instead of claiming it
was verified locally. Debug builds install as `com.cues.android.debug`.
