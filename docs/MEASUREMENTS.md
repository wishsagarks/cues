# Measured results

CLAUDE.md's rule: "No number ships until it has been measured on the device,
and the source of each measurement gets recorded with it. Targets and
results are different things and the documents say which is which." This is
the *results* document. `CLEANUP.md` holds the assumptions that haven't been
retired yet; this file holds the real numbers once they exist.

The first on-device authoring row below was recorded from the mirrored iQOO
I2501 session on 26 Sep 2026. Do not add a row from a simulator, an estimate,
or a number carried over from a different build — only from an observation on
the loaner, with the evidence it came from.

## How to record a result

Same discipline as `docs/DEVICE_MATRIX.md`: date, device model, OS build
(`ro.build.fingerprint`), app commit, the metric, the value, the method it
was read by (Diagnostics screen, `./dev probe`, a stopwatch, logcat
timestamps), and the evidence file (`evidence/probe-<utc>-<serial>.txt` or
similar) it traces to. A row with no evidence reference is not a
measurement.

## On-device inference (CLEANUP.md CL-18, `docs/DEVICE_MATRIX.md` M2)

| Date | Device | OS build | Commit | Backend | Load (ms) | Generation (ms) | Tokens/sec | Model file | Evidence |
|---|---|---|---|---|---|---|---|---|---|
| 2026-09-26 | vivo/iQOO I2501 (SM8850) | Android 16 / BP2A.250605.031.A3_V000L1 | 7d6a9a0 + working tree ABI fix | GPU | 1856 | 1601 | n/a (36 estimated tokens) | 584,417,280 B; SHA-256 `1325ae36…98be` | [Gemma E2E evidence](/Users/wish/Documents/DailyCodes/iqoo+/evidence/gemma-e2e-20260926T122838Z-10BFBN2C30001KN.txt) |

Backend must be read from `CueService.Diagnostics.lastInferenceReport` after
a real draft call on the loaner — never asserted from the NPU SoC allowlist
alone (`LiteRtLmSession.npuSocEligible()` names *eligibility*, not a claim
that NPU ran).

## Model provisioning (CLEANUP.md CL-36)

| Date | Device | Source (URL / side-load / BYOM picker) | Size (MB) | Download time | Checksum result | Evidence |
|---|---|---|---|---|---|---|
| | | | | | | |

## Developer Gemma surface (CLEANUP.md CL-38, `docs/DEVICE_MATRIX.md` M10)

| Date | Device | Caller (adb / second app, package) | Backend | Tokens | Latency (ms) | Model identity | Evidence |
|---|---|---|---|---|---|---|---|
| | | | | | | | |

## Office Kit transport (CLEANUP.md CL-22, `docs/DEVICE_MATRIX.md` M4)

| Date | File size sent | File size received | Intact? | Evidence |
|---|---|---|---|---|
| | | | | |
