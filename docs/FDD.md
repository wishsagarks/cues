# Functional Design Document

Revision: 22 September 2026. Design only. This document is not application implementation.

## Architecture

```text
  AUTHORING  ── model participates ──────────────┐
                                                 │
   ┌──────────┐   ┌──────────┐   ┌────────────┐  │
   │ Mic/text │──▶│ Local    │──▶│ Local LM   │  │
   │ input    │   │ speech   │   │ proposes   │  │
   └──────────┘   │ to text  │   │ typed data │  │
                  └──────────┘   └─────┬──────┘  │
                                       │         │
                             ┌─────────▼──────┐  │
                             │ Deterministic  │  │   Rejects unsupported
                             │ validator      │  │   or unresolved input.
                             └─────────┬──────┘  │   Schema validity is
                                       │         │   not understanding.
                             ┌─────────▼──────┐  │
                             │ Review:        │  │
                             │ WHEN IF DO     │  │
                             │ UNTIL RESTORE  │  │
                             │ + permissions  │  │
                             └─────────┬──────┘  │
                                       │         │
                             ┌─────────▼──────┐  │
                             │ User approves  │  │
                             │ a version      │  │
                             └─────────┬──────┘  │
                                       │         │
═══════════════════════════════════════╪═════════╪══════════════════
  TRUST BOUNDARY                       │         │  No model beyond
  Only approved, validated data crosses│         └─ this line, ever.
═══════════════════════════════════════╪════════════════════════════
                                       │
                             ┌─────────▼──────┐
                             │ Local store    │
                             │ armed cues     │
                             └─────────┬──────┘
                                       │
  RUNTIME  ── deterministic Android code only ──────────────────┐
                                       │                        │
   ┌──────────────┐          ┌─────────▼──────┐                 │
   │ Event        │─────────▶│ Context        │                 │
   │ adapters     │  signal  │ snapshot       │                 │
   │ BT, charging │          │ + freshness    │                 │
   └──────────────┘          └─────────┬──────┘                 │
                                       │                        │
                             ┌─────────▼──────┐   UNKNOWN never │
                             │ Pure predicate │──▶ grants        │
                             │ evaluator      │   permission     │
                             └─────────┬──────┘   to act         │
                                       │ MATCH                  │
                             ┌─────────▼──────┐                 │
                             │ Session        │  One session    │
                             │ admission      │  per connection │
                             └─────────┬──────┘                 │
                                       │                        │
                             ┌─────────▼──────┐                 │
                             │ Action         │  Closed         │
                             │ registry       │  allowlist      │
                             └─────────┬──────┘                 │
                                       │                        │
                  ┌────────────────────┼────────────────────┐   │
                  │                    │                    │   │
         ┌────────▼───────┐   ┌────────▼───────┐   ┌────────▼───────┐
         │ Exit scheduler │   │ Cleanup:       │   │ Reason receipt │
         │ deadline or    │──▶│ release only   │──▶│ started/skipped│
         │ disconnect     │   │ owned effects  │   │ /ended + why   │
         └────────────────┘   └────────────────┘   └────────────────┘
```

The language model sits entirely above the trust boundary. It cannot reach an executor, and the runtime never consults it. What crosses the line is approved, validated data — not model text.

Authoring: microphone or text input, local speech recognition, local structured proposal, deterministic validation, complete review, permission preflight, versioned approval and local storage.

Runtime: public Android event adapters, fresh context snapshot, pure predicate evaluation, session admission, action registry, exit scheduling, cleanup and reason receipts.

The language model appears only in the authoring path. It has no execution tools. Android capabilities remain behind a closed registry. The runtime accepts only approved, validated definitions, not model text.

## Routine definition and session records

A routine is a persistent policy. A session is one occurrence of that policy. Keep their states separate: completing a workout must not disable tomorrow’s routine.

Proposed routine fields:

| Field | Meaning |
|---|---|
| id / version / schemaVersion | Stable identity and explicit revision |
| sourceText | Correctable original transcript or typed request |
| trigger | One supported event, with a resolved entity identifier |
| conditions | Typed day, time and supported state predicates |
| actions | Registry identifiers with validated bounded arguments |
| endConditions | Explicit disconnect, duration deadline and/or manual stop semantics |
| cleanupPolicy | Supported owned effects to release, with limitations |
| rearmPolicy | New connection session, debounce and cooldown rules |
| requiredCapabilities | Deterministically derived requirements, never trusted from the model |
| approvedDigest | Digest of normalized executable semantics and approved defaults |
| status | Draft, invalid, reviewable, armed, paused or disabled |

Proposed session fields: session ID, rule/version, event identity, observed inputs, admitted timestamp, persisted deadline, per-action status, owned resources, cleanup obligations, user override marker, terminal state and receipt.

The digest detects an accidental mismatch with approved data; it is not a security boundary against compromise of the app itself. Storage remains private to the app.

## Input and compilation

Use a verified offline speech engine. Android exposes an on-device availability check; availability and downloaded language support must be tested on the phone. The local language model can be Gemma/Phi class or another suitable small model, but choose it based on actual latency, memory and correctness. Do not assume NPU support for either model.

Grammar constraints, if supported by the chosen runtime, limit output shape. Independent validation still checks supported types, numeric ranges, resolved entities, contradictory predicates and semantic completeness. A correctly shaped rule can misunderstand “unless”, negate a condition or attach the wrong device.

Never discard an unsupported clause to make a request fit. Return a clarification or a readable limitation. User-visible review derives from the normalized rule, not a fresh model-generated paraphrase.

Speech reference: [Android SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer).

## Event and context adapters

Register only what armed rules need. Use version-appropriate public APIs. The target-device spike decides which background delivery paths actually work; a receiver declaration alone is not proof.

| Adapter | Proposed input | Design constraint |
|---|---|---|
| Bluetooth | Selected paired device connection/disconnection | Resolve real identity, filter duplicates, test permissions and background delivery |
| Charging | Connect/disconnect plus current charging state | Separate current state from history of having charged |
| Time | Approved deadline or schedule | Select scheduling mechanism appropriate to accuracy and access |
| Wi-Fi | Network change and permitted network identity | Unavailable or redacted identity is unknown, not a match |
| Manual | Explicit user test/run | Record manual provenance separately from physical triggers |

Each context value carries a source, observation time and known/unknown status. Each adapter defines its freshness rule. The evaluator returns MATCH, NO_MATCH or UNKNOWN with structured reasons. Missing data never grants permission to act.

No background microphone, global clipboard surveillance, unrestricted notifications access or inferred location belongs in the core. Geofencing and richer personal context need their own permission and reliability design.

## Session admission and duplicate prevention

Serialize admission for a routine. Record a durable session identity before side effects. For connection rules, key admission by normalized device plus connection transition/session identity and rule version, rather than only a clock bucket.

Allow at most one active session per routine in the core. Repeated callbacks attach to or skip the same session. On a short disconnect, apply the approved debounce before terminating. Cancel that pending exit if the device reconnects within the grace window, without restarting the timer. The grace period must be visible in the review.

After a confirmed exit, a future connection may create a new session under the approved rearm policy. A new day or time condition becoming true while earbuds are already connected does not silently create a connection event. If that behavior is desired, it needs a separate approved trigger.

## Action registry

Core actions: start app focus timer, request owned DND contribution, record/show local result. Cleanup actions: complete/cancel the owned timer and release the owned DND contribution.

Each adapter declares its required access, argument validator, execution method, capability limits, outcome verifier where possible, idempotency behavior and supported compensation. No model-supplied shell commands, code, packages or arbitrary intents reach an executor.

The registry builds any allowed intent itself from approved typed arguments. Broad app launching is deferred from the hero flow. If added, use a tap-to-open notification when Android requires interaction. A notification cannot be a universal fallback if notification permission is denied.

Background reference: [Android activity security](https://developer.android.com/guide/components/activities/secure-bal).

## DND ownership

Use a supported app-owned AutomaticZenRule strategy after notification policy access. Apps targeting Android 15 and later cannot simply change the global DND state through the older setters; those calls affect app-associated rules. Validate the exact implementation against the event phone’s OS and target SDK.

Cues’ session engine tracks which sessions require its quiet contribution. Ending one session releases its ownership and recomputes that contribution. Do not claim this overrides or fully models other apps’ policies. A user or another rule may legitimately keep the effective phone state quiet.

Respect an explicit user override. If the platform does not expose enough provenance to restore a global setting safely, do not guess. In that situation use only owned resources or ask for recovery. Arbitrary system-setting restoration is outside the core.

Reference: [Android NotificationManager](https://developer.android.com/reference/android/app/NotificationManager).

## Timer and exit semantics

The app owns its focus timer so cancellation and receipts are under its control. Avoid depending on a third-party clock app silently launching from the background.

Store the approved duration and deadline. Use a monotonic clock for elapsed time within a boot, with an explicitly defined persisted wall-clock deadline for recovery after reboot. Show local time-zone semantics for calendar schedules. Clock changes, reboot and missed delivery require reconciliation; never silently restart the full duration.

At the duration deadline, complete the timer and begin cleanup. At a confirmed disconnect, cancel the remaining timer and begin cleanup. Manual stop follows the same idempotent path. If cleanup or scheduling cannot run on time, record the delay and show unresolved obligations when the app can resume.

Preflight exact-alarm capability if precise notification or exit timing is required. Do not substitute periodic WorkManager execution and still claim a precise countdown completion or exact DND release.

Reference: [Android alarm scheduling](https://developer.android.com/develop/background-work/services/alarms).

## State, failure and recovery

Routine state: DRAFT, INVALID, REVIEWABLE, ARMED, PAUSED, DISABLED.

Session state: STARTING, ACTIVE, ENDING, COMPLETED, CANCELLED, PARTIAL, CLEANUP_PENDING, FAILED.

Define action records separately: NOT_STARTED, IN_PROGRESS, SUCCEEDED, BLOCKED, FAILED, COMPENSATED, COMPENSATION_FAILED. A blocked action is never success.

Persist ownership and recovery intent around each side effect. After a crash between an effect and its receipt, query actual resource state where possible rather than blindly repeating. Exactly-once delivery across all Android effects is not a claim. Favor idempotent operations and honest uncertainty.

If the timer starts but DND fails, the session records a partial start and performs the reviewed safe cleanup policy for the timer. Never assume every completed effect can be undone. Attempt all independent cleanup obligations even if one fails, so one failed release does not strand another resource.

Pausing prevents new sessions. For an active session, default the UI action to “Pause future runs and end current session” and describe that behavior. Editing never retroactively rewrites an active session. Deletion must resolve or visibly retain cleanup obligations before deleting the required records.

On process start and permitted reboot delivery, reconcile active sessions against current time and context. An expired session must attempt cleanup, not restart its actions. A force-stopped app cannot promise reliable unattended recovery. Surface monitoring health and state what was tested.

## Rehearsal and explanations

The pure evaluator accepts a rule and an event/context record. Runtime and rehearsal call the same function. Rehearsal must use mock action outcomes and have no reference to real action adapters.

Core scenarios: eligible connection, wrong weekday, early connection, duplicate callback, disconnect and timeout. Label all synthetic data. The preview demonstrates logical decisions, not proof that a future OS call will succeed.

Build receipts from deterministic reason codes and observed values. Example: “Skipped: Saturday is outside Monday–Friday.” Another: “Ended: earbuds disconnected. Our quiet rule released. Another quiet rule remains active,” only if the final state supports that observation; do not invent the owner of an external mode.

History replay is later work. Collection begins only after consent, retains a bounded local window and records coverage gaps. Missing events cannot be treated as proof that nothing happened. Shadow mode evaluates without executing and is also outside the core.

## New context primitives

### Expected state at a deadline (stretch)

Register an approved check at 23:00. Read current charging state. If false, send one reminder. If true, record no reminder needed. If unknown, record that no reliable check was possible. Deduplicate by rule version plus scheduled occurrence.

This supports “if not charging at 11 PM.” “If I have never charged today” requires historical coverage and is not an equivalent condition.

### Opportunity queue (roadmap)

An item has user-approved eligibility predicates, earliest execution time, expiry, confirmation requirements and attempt limits. Re-evaluate before action. Unknown conditions retain the pending state until expiry. Do not issue stale work after the deadline. Calendar-based availability is optional access, not an assumed capability.

### Named context (roadmap)

A name refers to an explicit predicate set. Capture selected current signals only after permission. Show sources and let the user remove them. Context revisions invalidate affected approval where behavior changes.

### Temporary patch (roadmap)

Store base version, changed fields, approved start and expiry. At expiry remove the patch only if compatible with the current version. If a subsequent edit conflicts, present a resolution rather than overwriting it.

### Shared timetable (roadmap)

Receive explicitly shared content, extract proposed facts locally if feasible, review uncertain times and normalize dates. Treat document instructions as data. No imported artifact can activate rules or change permissions.

## Security and privacy

Keep transcripts, rules and receipts in private local storage with bounded retention settings. Raw audio need not persist after transcription. Model assets may require an initial download, but no network request is allowed in the demonstrated authoring or runtime paths after provisioning.

Sharing typed data removes a need to transfer executable code; it does not make arbitrary routines safe. Future imports require size/depth limits, schema validation, entity rebinding, permission preflight and user approval. Disable imports that request unsupported behavior. Review external effects separately if they are ever introduced.

## Test plan for the event

Meaningful tests cover normalization, unsupported clauses, immutable approval, three-valued evaluation, duplicate callbacks, reconnect grace, timer expiry, independent cleanup and crash recovery.

On the actual iQOO, test voice with another speaker and venue-like noise, end-to-end network isolation, background Bluetooth and charging, DND overlap, manual override, permission revocation, process recreation and timer delivery. Document untested reboot, force-stop or battery-policy behavior instead of generalizing.

Run the hero routine several times with match and nonmatch cases. Collect actual timing and correctness outcomes; all quantities remain targets until measured. Keep simulated events, physical events and prerecorded backup footage visibly distinguishable.

## Implementation order

1. During the event, spike local inference and the target phone’s event, DND and scheduling capabilities.
2. Define the closed routine/session model and pure evaluator.
3. Implement approved-version storage and deterministic owned actions.
4. Add review and offline text compilation, then local speech.
5. Complete exits, receipts and crash reconciliation.
6. Add synthetic rehearsal and validate real background behavior.
7. Add Wi-Fi/time and a charging deadline reminder only if the core passes.

There is no claim of direct Office Kit SDK access, unrestricted OriginOS APIs or universal background execution. The complete creative direction and comparison are in the private positioning notes (kept locally, outside this repository).
