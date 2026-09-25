# Permissions

What `app/src/main/AndroidManifest.xml` declares, why, and when the user is
asked. Checked against the source manifest on 24 September 2026, updated 25
September 2026 for CL-35's `INTERNET` permission. The **merged** manifest
(the source plus what dependencies merge in) is checked by `./dev perms`,
which now fails if `INTERNET` is *missing* rather than if it appears — see
CLEANUP.md CL-35. That check was not re-run for this revision; see
[DISCLOSURE.md](DISCLOSURE.md).

The rule: every permission belongs to a named action, adapter or screen.
Capabilities a cue needs are derived from its actions and signals by
`ActionRegistry`/`SignalRegistry`, never taken from a draft. The Review screen
shows them before approval and names any that are missing. It re-reads them
when the app resumes, and arming re-checks them live.

## Declared permissions

| Permission | Kind | Used by | When it is requested |
|---|---|---|---|
| `BLUETOOTH_CONNECT` | Runtime | Bluetooth trigger/condition: resolving the bound device and reading its connection | Prompted from Home before the paired-device picker |
| `RECORD_AUDIO` | Runtime | Speech input, only after a mic tap | Prompted on the first mic tap (Home) |
| `POST_NOTIFICATIONS` | Runtime (API 33+) | Session notification, `NOTIFY_RESULT`, pinned notes | Review's "Required access" row has an "Allow" button when missing (CL-26, uncompiled) |
| `ACCESS_NETWORK_STATE` | Normal | Any-Wi-Fi signal. No SSID, so no location | Install time |
| `ACCESS_NOTIFICATION_POLICY` | Special access | The app-owned `AutomaticZenRule` (`REQUEST_DND`) and ringer mode (`RINGER_MODE`) | Review's "Required access" row has an "Open settings" button (`ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS`) when missing (CL-26, uncompiled) |
| `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM` | Special / normal | Timer deadline, reconnect grace, at-time trigger | Review's "Required access" row has an "Open settings" button (`ACTION_REQUEST_SCHEDULE_EXACT_ALARM`) where the OS hasn't granted it (CL-26, uncompiled) |
| `RECEIVE_BOOT_COMPLETED` | Normal | Reconciling sessions after a restart | Install time |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Normal | `SessionService`, the visible running session | Install time |
| `CAMERA` | Runtime | Timetable capture and Cue Card QR scan only, released when the screen closes | On opening either capture screen. The Photo Picker route needs no permission |
| `com.android.alarm.permission.SET_ALARM` | Normal | `SET_ALARM` handoff to the clock app, which the platform requires to invoke `ACTION_SET_ALARM` | Install time. The clock app still shows its own confirm screen |
| `INTERNET` | Normal | Two features, both opt-in: cloud language assist (CLEANUP.md CL-35) — Sarvam AI's translate, speech-to-text, text-to-speech and chat completion calls; and the "Cues Brain" on-device model download (CLEANUP.md CL-36) — a one-time, user-initiated fetch of the `.litertlm` model file. No other feature calls out | Install time (a normal permission, granted automatically). Cloud assist stays off until the user turns it on in Ask, and only if `BuildConfig.SARVAM_API_KEY` is configured. The model download runs only on an explicit tap of Diagnostics' "Cues Brain" card, only over Wi-Fi (no cellular override), and only if `BuildConfig.GEMMA_MODEL_URL`/`_SHA256` are configured |

`INTERNET` was `tools:node="remove"`'d from 4.5 through this sprint (R4/CL-06)
because nothing used it. It is declared again, deliberately, for CL-35 — not
a dependency-merged accident this time. `./dev perms` now expects it present
and fails if it is *missing*, the inverse of what it checked before.

`USE_EXACT_ALARM` is granted without a prompt, but Google Play restricts it to
alarm-clock and calendar apps. That doesn't affect a side-loaded event build.
It would need revisiting before any store release.

## Bound services and special access (not `<uses-permission>`)

| Surface | Protection | What the user grants |
|---|---|---|
| `CuesAccessibilityService` ("Cues: iQOO utility bindings") | `BIND_ACCESSIBILITY_SERVICE` | The user turns it on in Accessibility settings. It replays taught macros for `USE_UTILITY` and does one-shot "Cue this screen" reads. `packageNames` is currently unscoped (CL-23 item 5) |
| `CueTileService`, `ScreenTile` | `BIND_QUICK_SETTINGS_TILE` | The user adds the tile |
| `FileProvider` | Not exported. Per-URI grant | Nothing standing. Each Console export is shared as a single `content://` URI |
| Bluetooth, power and boot receivers | `exported="true"` for protected system broadcasts | Nothing; see the manifest comment |

## Package visibility

`<queries>` lists launcher apps (the `OPEN_APP` picker) and one intent each for
`SENDTO smsto:`, calendar `INSERT`, `SET_ALARM`, and `VIEW https:`/`tel:`.
There is no `QUERY_ALL_PACKAGES`. Cues can see that these apps exist and how
they launch, and nothing about their data.

## Deliberately absent

No location (fine or coarse), no background microphone, no notification-listener
access, no `QUERY_ALL_PACKAGES`, no storage/media permission, and no
`SYSTEM_ALERT_WINDOW`. Adding any of them needs a CLEANUP entry and a line in
this file first — as CL-35 did for `INTERNET` above, the one exception to
"no network" the app now has.
