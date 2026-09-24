# Implementation Plan: Cues Brain for iQOO

## Overview

Deliver the cumulative A-F plan as an iQOO-native “when-and-until” layer: users can ask Cues about cues, refine and approve typed routines, understand why they did or did not run, receive evidence-backed suggestions, import context from camera/files, and invoke safe cross-app and OriginOS utility actions. Models may propose authoring data, but approved runtime behavior remains deterministic, capability-derived, reversible where claimed, and locally auditable.

The implementation starts from the existing two-module architecture: `:core` remains pure Kotlin/JVM and owns every decision; `:app` owns Android, Compose, inference runtimes, device discovery, notifications, camera, sharing, and accessibility. Device/OEM claims are not considered complete until recorded from the loaner phone in `CLEANUP.md`.

## Existing State and Gap Summary

- Present and green: closed routine model, deterministic parser/validator, review/approval, signal kits, four owned actions, session lifecycle, receipts, JSON persistence, Compose shell, local speech input, Android adapters, tile/widget, diagnostics.
- Baseline verification on 2026-09-24: 180 core tests pass; debug APK builds; merged manifest contains no `INTERNET`.
- Stubbed: the current on-device model path always fails cleanly and falls back to the parser.
- Missing: conversation/refinement/explanation, personal index, declared memory, usage ledger/coach, typed cross-app actions, presence/PENDING behavior, calendar condition, share/shortcuts/AppFunctions, camera/timetable import, cue cards, TTS, static Console export/import, accessibility macros, OriginOS utility discovery/bindings, inference reports and embeddings.

## Architecture Decisions

- Preserve the trust boundary: inference and imported/screen text can only propose typed data; no model or imported content can execute, grant permissions, or cross approval.
- Extend closed registries rather than accept package names, intents, selectors, capabilities, or commands from a model.
- Store explicit facts with source and dependency references. Editing/deleting a referenced fact invalidates approval.
- Represent actions that require attention as `PENDING`; expiry is `BLOCKED`, and any blocked action makes a session `PARTIAL`.
- Treat Accessibility as a narrowly scoped “iQOO utility bindings” fallback. User-taught third-party macros are secondary, version-pinned, denylisted, and require presence.
- Keep inference backends honest through `InferenceReport`; only runtime-reported backends may be displayed or claimed.
- Use Office Kit only as an explicitly named file/clipboard transport. No direct Office Kit SDK claim.
- Gate OEM-specific behavior behind discovery/read-back and record unverified behavior in `CLEANUP.md`.

## Dependency Spine

```text
Core contracts and persistence
  -> conversation + personal index + facts
  -> coach ledger and evidence
  -> typed action/session semantics
  -> import/card/console formats
  -> Android adapters and UI
  -> inference/camera/accessibility/AppFunctions
  -> device verification and demo hardening
```

## Task List

### Phase 0: Contracts and Baseline

- [x] Task 0: Audit repository, requirements, safety invariants, and baseline checks.
- [ ] Task 1: Verify current official Android/LiteRT/AppFunctions/CameraX/ML Kit APIs and pin compatible dependencies; document any unavailable or experimental surface.

### Phase 1: Ask Cues and Declared Context

- [ ] Task 2: Add conversation contracts, closed intent routing, deterministic reply copy, personal reference resolution, refiner operations, explanation, and pending command confirmation.
- [ ] Task 3: Add Declared Memory (`Fact`, `FactStore`, fact-aware references, approval invalidation) and lexical/optional embedding ranking.
- [ ] Task 4: Add `./dev chat`, conversation fixtures, injection tests, and the Compose Assistant/Memory screens with source footers and Jovi handoff cards.

### Checkpoint: Conversation

- [ ] `./dev t`, `./dev b`, and `./dev perms` pass.
- [ ] Create -> refine -> explain -> confirm-control works without a device.
- [ ] Shared/injected text cannot arm or execute anything.

### Phase 2: Evidence-Backed Coach

- [ ] Task 5: Add bounded `UsageLedger`, coverage gaps, mutes, six pure detectors, policy limits, and receipt/session integration.
- [ ] Task 6: Add `./dev coach`, fixture ledgers, Learning settings/log wipe, Today/Assistant suggestion cards, and opt-in notification behavior.

### Checkpoint: Coach

- [ ] Every detector threshold, coverage-gap rule, daily limit, dismiss, and permanent mute has a test.
- [ ] Accepting a suggestion only seeds authoring and still requires Review/Approve.

### Phase 3: Typed Cross-App Actions and OriginOS Surfaces

- [ ] Task 7: Extend the action registry/model with explicit risk and presence, typed handoffs, alarms/media/ringer, utility actions, owned restoration, and deterministic receipt codes.
- [ ] Task 8: Implement session `PENDING` behavior, attention checks, expiry to `BLOCKED`/`PARTIAL`, and safe user-override restoration.
- [ ] Task 9: Implement Android executors, calendar condition, installed-app picker, share target, shortcuts, live session notification, Workbench drag/drop, package/intent discovery, and Jovi routing.

### Checkpoint: Typed Actions

- [ ] Core tests prove risk, validation, presence, restoration, UNKNOWN, and PARTIAL behavior.
- [ ] Review surfaces every non-owned effect and never accepts model-supplied packages/intents.
- [ ] APK builds and `INTERNET` remains absent.

### Phase 4: Local Brain, Voice, Camera, and Imports

- [ ] Task 10: Introduce inference ports/reports and a side-loaded LiteRT-LM session with explicit NPU -> GPU -> CPU -> parser-only fallback; add optional on-device embedder.
- [ ] Task 11: Add exact-copy TTS for replies/receipts; voice can author but never approve or confirm a pending command.
- [ ] Task 12: Add pure timetable extraction, multi-cue import proposals, uncertainty flags, injection/size limits, CameraX + bundled ML Kit capture, and Snap Note/Gallery image sharing.

### Checkpoint: Brain and Camera

- [ ] Parser-only operation remains complete when models are unavailable.
- [ ] Diagnostics and each turn show actual inference source/backend.
- [ ] Camera/import path works in airplane mode and cannot arm directly.

### Phase 5: Desk Bridge and Offline Sharing

- [ ] Task 13: Add versioned Cue Card encode/decode/digest validation, local re-binding/capability derivation, QR import/export, and tamper tests.
- [ ] Task 14: Add versioned export schema, `./dev console` single-file HTML, phone share/export/import for `.cue.txt`, `.cuecard`, and `cues-export.json`, plus Desk context template.

### Checkpoint: Bridge

- [ ] Cue Card round-trip passes; tampering fails; permissions/approval never transfer.
- [ ] Console renders receipts, forecast, coach evidence, inference reports, and ledger without a server.

### Phase 6: Utility Bindings and Ecosystem

- [ ] Task 15: Add macro model/validator/store, denylist tests, package/version pinning, selectors/postconditions, and bounded retry rules.
- [ ] Task 16: Add consent/teaching UI, narrowly scoped AccessibilityService, one-shot “Cue this screen,” floating stop, and iQOO utility on/off bindings with read-back and restoration.
- [ ] Task 17: Add AppFunctions provider for draft/start/stop/forecast/current-context, retaining in-app approval for drafts and manual-only start constraints.

### Checkpoint: Ecosystem

- [ ] Secure/password windows and denylisted apps/actions are refused.
- [ ] Version mismatch and unreadable state become `BLOCKED`, never guessed success.
- [ ] System-agent calls cannot bypass routine validation or approval.

### Phase 7: Documentation, Device Proof, and Demo

- [x] Task 18: Update FDD, README claim discipline/OriginOS positioning, runbooks, permissions, and `CLEANUP.md` retirement conditions.
- [ ] Task 19 (matrix and probe prepared in docs/DEVICE_MATRIX.md; not executed, needs the loaner): Execute device matrix: package discovery, NPU backend measurement, Camera airplane-mode import, Office Kit transfer, Origin Island rendering, Accessibility utility binding, AppFunctions/Jovi handoff, screen-off pending action, QR between devices.
- [ ] Task 20 (software regression, offline demo and docs/DISCLOSURE.md done; APK re-check and device rehearsal pending): Run final full verification, produce scripted offline demo and backup evidence, and disclose any device-only item that could not be proven.

## Task Definitions and Acceptance Criteria

### Task 1: Official API and Dependency Gate

**Acceptance criteria:** exact versions and official API references are recorded; experimental/OEM-only paths have adapters and fallbacks; no dependency silently adds a network requirement.

**Verification:** dependency resolution; APK build; merged permission dump; official-source links in implementation notes/docs.

### Tasks 2-4: Conversation and Memory Slice

**Acceptance criteria:** every turn has typed intent and source; ambiguous references yield chips; semantic refinements clear approval; commands require confirmation; facts are explicit/source-labelled/deletable; fact edits invalidate dependent approvals.

**Verification:** focused unit tests first, then `./dev t`; scripted `./dev chat` flow; APK/UI build; manual Compose flow.

### Tasks 5-6: Coach Slice

**Acceptance criteria:** 14-day bounded local history; signal recording is opt-in; >30% coverage gaps suppress conclusions; one suggestion/day; dismiss/never mutes; suggestions never arm.

**Verification:** detector/policy/store tests; fixture replay through `./dev coach`; UI build.

### Tasks 7-9: Cross-App Slice

**Acceptance criteria:** registry owns intent construction and validation; attention-required actions wait; handoff success means opened/user-finishes; ringer/utility cleanup respects user changes; unsupported general requests route to Jovi.

**Verification:** registry/session/receipt tests; parser/review CLI examples; Android build and manifest check; device actions recorded as unverified until run.

### Tasks 10-12: Brain/Voice/Camera Slice

**Acceptance criteria:** model assets are side-loaded; fallback is labelled; backend metrics come from runtime; embedding only ranks; TTS is exact copy; imports remain proposals; uncertain timetable rows require review.

**Verification:** fake-runtime tests; parser fallback tests; timetable fixtures including prompt injection; APK/permission check; physical device matrix.

### Tasks 13-14: Sharing/Desk Slice

**Acceptance criteria:** exported cards contain normalized behavior/schema/digest only; imports revalidate and rebind locally; static Console needs no server; Office Kit is described only as transport.

**Verification:** card tamper and schema tests; deterministic Console snapshot test; open generated HTML locally; physical transfer test.

### Tasks 15-17: Accessibility/AppFunctions Slice

**Acceptance criteria:** max eight macro steps; package/version pin; denylisted targets and password text blocked; postconditions required; one retry; user presence; AppFunctions cannot bypass normal gates.

**Verification:** validator/session tests; manifest inspection; Android build; on-device teach/update/refusal/AppFunctions tests.

### Tasks 18-20: Release Gate

**Acceptance criteria:** all claims match measured evidence; every unresolved OEM behavior is disclosed with a retirement condition; all demo paths are green or cut cleanly.

**Verification:** `./dev t`, `./dev b`, `./dev perms`, CLI scenarios, device runbook, offline demo rehearsal.

## Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| LiteRT-LM/QNN APIs or artifacts differ from the proposal | High | Verify official artifacts first; keep runtime behind a port; ship labelled parser-only fallback rather than a fake NPU claim. |
| OriginOS packages/intents and Island rendering are undocumented | High | Discover on device; never hard-code guessed packages; record exact build/device evidence in `CLEANUP.md`. |
| AppFunctions support differs on OriginOS 7 | High | Keep provider additive/experimental; Jovi launch handoff remains functional fallback. |
| Accessibility policy/scope | High | Narrow package list, prominent disclosure, hard denylists, user-taught steps only, no background screen capture. |
| New action semantics destabilize existing session cleanup | High | Contract-first model extension and TDD around `PENDING`, `PARTIAL`, ownership, and overrides before Android wiring. |
| Plan breadth exceeds the available device window | High | Every task is independently demoable; device-dependent features stay hidden until their checkpoint; failed spikes are cut and disclosed. |
| New dependencies reintroduce `INTERNET` | Medium | Run `./dev perms` after every Android dependency/manifest increment. |
| Existing AGP/compileSdk and duplicate Kotlin-plugin warnings worsen | Medium | Track separately; only update pins when required by verified dependencies, then rebuild both modules. |

## Definition of Complete

“Complete end to end” means every software path above is implemented and locally verified, and every claim that depends on an iQOO loaner is either proven on that device and recorded or explicitly marked unverified/cut. It does not mean inventing OEM APIs or claiming an NPU/Office Kit/Origin Island result that the available hardware did not report.
