# Sprint 9 — Visible Intelligence, Sarvam, and Declared Sensing

**Status:** proposed implementation plan  
**Target device:** vivo/iQOO I2501, Snapdragon SM8850, Android 16  
**Prerequisite:** land Sprint 8 (`619a7fd` → `58e8247` → `a724b30`) on the actual `main` branch before work begins. At plan time, those commits are reachable from `feat/gemma-ui-sprint8`, not current `main`.

## Outcome

Cues will make each authoring decision visible and honest: whether Gemma was installed, enabled, attempted, cancelled, rejected, agreed with grammar, or escalated—with the backend and timings from the run, never from device eligibility. Sarvam becomes a separately consented Indian-language assist. New phone sensors become reviewed, declared condition kits rather than background profiling. The approved runtime remains deterministic and model-free.

```text
Ask input
  ├─ Grammar parser (always available, deterministic)
  ├─ Gemma (only installed + enabled) ── NPU* → GPU → CPU
  └─ Sarvam (only user opt-in, only after local failure/disagreement)
         ↓
DraftTrace → validation → user Review → approved cue
                                      ↓
                         deterministic sensors/actions/receipts

* only after a compatible SM8850 model artifact has loaded successfully
```

## Boundaries

- No model, network call, or inference result participates after approval.
- A streamed response is display-only until its final text passes the existing parser and validator.
- `UNKNOWN` signal/model state never authorizes a cue or reads as ready.
- Sarvam never silently receives a request. A production app must not embed a reusable Sarvam API key; use a user-managed key or authenticated proxy.
- A new sensor must have: a closed core condition kit, a clear permission story, an Android adapter, a reason-code receipt, a user-visible source/age, and a device-matrix test.

## Workstreams

### W0 — Integrate and stabilize Sprint 8

1. Rebase or merge the three Sprint 8 commits in order, resolving against the current app changes only after preserving the working tree.
2. Run `./dev t`, `./dev b`, and `./dev perms`; install the debug APK on I2501.
3. Confirm model-off behavior first: every authoring surface says **Grammar only · model off/not installed**, never “on-device model”.
4. Retain the new `DraftTrace`, `DraftCredit`, `ModelAvailability`, `LocalModelRunner`, warm-engine lifecycle, cancellation, and cloud-through-`CueService` contracts as the data foundation for the UI below.

**Exit:** Sprint 8 APIs compile on `main`; model-off UI and parser drafting work on the phone.

### W1 — Visualization layer (the missing Sprint 8 deliverable)

Build an observable `ModelController` in `:app` and an `AskViewModel` that own the drafting job, live partial output, cancel action, selected disagreement candidate, conversation, and picker state. The controller combines provision state, enabled state, `LocalModelRunner` state, real `DraftTrace`, and NPU eligibility without treating eligibility as evidence of use.

Create `ui/components/ModelReadouts.kt`:

| Component | Truth shown |
|---|---|
| `ModelStatusBar` | grammar-only / loading / warm state; installed/enabled state; actual tier only after a runner/report says it |
| `DraftPipelineStrip` | input → Gemma → grammar → compare → validate → user, with skipped/success/failure/cancel/timing state |
| `ProvenanceBadge` | `DraftCredit`, actual backend, total time, token count and estimated/measured marker |
| `StreamingPreview` | partial text labelled “not yet checked”, plus Cancel |
| `BackendPill` | reusable factual backend/readout |

Wire them into:

- **Ask:** status bar, live strip, streaming preview, all conversation turns, and a responsive two-candidate disagreement card. The card highlights the differing clause and forces an explicit choice, rephrase, or opted-in cloud request.
- **Review:** compact pipeline and provenance badge. A user-selected candidate says that it was selected by the user.
- **Now:** compact authoring-only status while keeping “No model at runtime” and “No network at runtime”. Restore the evidence-backed coach card; narration is labelled optional Gemma wording.
- **Insights:** aggregate actual attempt outcomes and backend latencies from the inference ledger.
- **Checks:** live runner state, warm/test actions, last five attempts, and a separate NPU-eligibility line.

**Exit:** no visual surface derives its label from `DraftSourceId.ON_DEVICE_LLM` alone; every model claim has a trace/report backing it. Verify light/dark and narrow/wide layouts.

### W2 — Gemma provisioning, routing, and measurement

1. Import the downloaded Gemma `.litertlm` through the existing picker/side-load route; identify the exact filename, checksum, quantization, and LiteRT-LM compatibility before enabling it.
2. Keep the runtime policy: compatible, verified NPU → GPU → CPU → grammar-only. The I2501 reports `SM8850`, which is not currently in Cues’ verified NPU allowlist; only a successful, deliberate probe with a matching model can add evidence for that tier.
3. Complete prompt/sampler quality: one canonical grammar output, deterministic drafting settings, bounded tokens, corpus-held-out evaluation, and a prompt id in every trace.
4. Measure cold/warm load, TTFT, generation, tokens/sec, memory, cancellation, and corpus correctness on-device. Record evidence in `docs/MEASUREMENTS.md` and `docs/DEVICE_MATRIX.md` M2; do not claim a tier beforehand.

**Exit:** the Checks test prompt, corpus score, and cancellation demonstrate actual behavior on I2501, and second warm drafts are measured rather than assumed faster.

### W3 — Sarvam as explicit language assist

1. Keep Sarvam off by default and visible only when configured.
2. Support user-invoked regional speech-to-text, translation/transliteration, exact translated read-back, and one-shot chat drafting. Preserve original and translated text side by side.
3. Route every Sarvam draft through `CueService` so its `Turn`, `DraftTrace`, ledger entry, consent state, and Review provenance are visible alongside local attempts.
4. Enable “Try Sarvam” only after grammar/Gemma failure or a user sees a disagreement; it never silently replaces a local candidate.
5. Add live-key integration tests using a non-production test credential or proxy, error/redaction handling, network-off fallback, and Hindi-first language selection with an extensible picker.

**Exit:** a user can identify exactly what text left the phone, why, which Sarvam capability ran, and that its final proposal still passed deterministic validation.

### W4 — Receipts and end-to-end evidence

The structured Receipts UI is already wired to prefer `ReceiptRecord` with legacy-text fallback. Finish it by creating and exercising real records on I2501:

1. Run a safe charging or manual cue through start, skip, and end.
2. Verify lifecycle kind, provenance, reason codes, action outcome, cleanup obligations, exact read-aloud text, and newest-first refresh.
3. Add filtering by lifecycle (`Started`, `Skipped`, `Ended`, `Blocked`) only if the real history proves it improves scanability; never filter away evidence by default.
4. Record a device proof in the matrix and retain the text fallback for pre-v2 history.

**Exit:** Receipts proves what the device observed and did; it does not tell an AI-generated story about it.

### W5 — Declared sensor kits

Start with the two already-created pure classifiers, then complete their product contracts rather than adding a broad sensor grab:

| Candidate | Current state | Sprint 9 work |
|---|---|---|
| Face down | Pure gravity + proximity classifier exists | condition kit, Android adapter, 3-second debounce/staleness tests, sensor-absence → `UNKNOWN`, source/age in Review and Receipts |
| Ambient light | Pure dark/dim/bright classifier exists | condition kit, `TYPE_LIGHT` adapter, 2-second debounce/staleness, source/age and no raw-lux retention |
| Bluetooth / charging / Wi-Fi / calendar | Existing declared kits | device verification, freshness/read-failure receipts, and Ask chips that explain required grants |
| Activity / location | Not planned by default | only start after a specific user story and explicit permission/retention decision; no passive profiling |

For each admitted sensor: add core tests first, derive capability from the kit, request the Android permission only at user attachment time, expose the sensor’s read age in Review, and record `UNKNOWN` with a remediation in Receipts.

**Exit:** a cue can explicitly use face-down or light-band context; missing/stale hardware data safely prevents execution and says why.

### W6 — External local-Gemma surface and safety

Finish Sprint 8’s provider hardening before demonstrating it: package-plus-signing-digest consent, visible pending/approved/blocked callers, per-caller rate limit, authoring-priority busy handling, shared warm-load gate, and a user-visible audit trail. Run `DEVICE_MATRIX.md` M10 for disabled, unapproved, approved, rate-limited, and busy cases.

**Exit:** no other app can silently consume a local model or masquerade as an approved caller.

### W7 — Documentation, polish, and release gate

Update `CLEANUP.md`, `docs/API_VERIFICATION.md`, `docs/MEASUREMENTS.md`, `docs/DEVICE_MATRIX.md`, `docs/FDD.md`, `docs/DEMO.md`, and `README.md` from measured evidence. Finish visual polish for Receipts/Checks in both themes and reduced-motion mode. Remove no legacy surface until its replacement is tested.

## Delivery order and cut line

**Must ship:** W0, W1 Ask/Review components, W2 safe routing/measurement, W3 consent/provenance, W4 device Receipts proof, W5 face-down/light correctness, and W6 provider safety.

**Cut first if time is tight:** streaming preview (keep the final trace), corpus-score UI (keep CLI/device test), Insights aggregation, coach narration, and advanced Sarvam language picker. Never cut the parser/validator/approval boundary, model-off truthfulness, disagreement choice, or device measurement.

## Acceptance matrix

| Scenario | Expected proof |
|---|---|
| No model | All surfaces say grammar-only; model stage is skipped, no timeout spent |
| Model off | Same as no model, with an explicit enable action |
| Gemma runs | Actual backend/timings/tokens come from report; trace appears in Ask, Review, Insights, Checks |
| Drafters agree | Parser proposal is credited as confirmed by Gemma; no warning |
| Drafters disagree | Side-by-side candidates; user selects or rephrases; no silent winner |
| Gemma fails/cancels | Trace explains failure/cancel; parser candidate remains reviewable if valid |
| Sarvam assist | Consent and provenance visible; request is ledgered; network failure returns to local drafting |
| Sensor unavailable/stale | `UNKNOWN`, no start, receipt names source and remediation |
| Session lifecycle | Structured Receipt records start/skip/end/cleanup on phone |

## Required decisions before W2/W3/W5 implementation

1. Provide the Gemma artifact filename, source, SHA-256, and whether it is a LiteRT-LM `.litertlm` build intended for SM8850/QNN.
2. Choose Sarvam credentials: user-provided key for development, or an authenticated proxy for a distributable app.
3. Confirm the first sensor priority: **face-down + ambient light** is the recommended pair because both classifiers already exist and require no location/activity profiling.

## Verification cadence

For each core change: `./dev t` and targeted CLI scenario.  
For each Android change: `./dev b` and `./dev perms` (`INTERNET` is intentional only for the opt-in Sarvam path).  
For every device claim: run the matching `DEVICE_MATRIX.md` row, save evidence, and update the measurement/disclosure documents.
