# Product Requirements Specification

Revision: 22 September 2026. This specification describes intended behavior. Some of it is
implemented and tested offline; the parts that touch a phone are not. See the repository
[README](../README.md) for what exists today.

## Product and user

Cues is an Android app for iQOO users who want recurring phone behavior without programming an automation tool. Voice is the primary authoring path, with typed input and transcript correction available. The app runs supported routines locally after explicit approval.

The primary promise is: **Speak the routine. Approve its context and ending. Understand what happened.**

The first target experience is a focus session triggered by selected earbuds, with a timer and an owned DND contribution. Charging provides a second physical trigger. The user can inspect entry, skipped conditions and exit in one place.

## Principles

- Natural language is untrusted input. Models can draft but cannot execute actions.
- The review describes the complete lifetime of supported effects.
- Unknown context remains unknown. A device connection does not establish location or intent.
- User edits require validation and reapproval of the changed version.
- Cleanup releases the app’s own effects and respects later user decisions.
- Claims about latency, offline support and background reliability require actual-device evidence.

## Scope

| Tier | Included |
|---|---|
| Core, 30-hour target | Offline speech and local rule drafting; device resolution; review; Bluetooth and charging; app timer and supported DND rule; exits; receipts; synthetic rehearsal |
| Add only after core stability | Wi-Fi and time triggers, one charging-deadline reminder |
| Later | Opt-in event history and shadow mode, named contexts, opportunity queue, temporary patches, timetable import, geofencing, Office Kit integration |

Do not build arbitrary app control, screen clicking, continuous ambient listening, automatic messaging or broad passive personal-data collection.

Work completed before the event covers the offline decision core — the routine model, evaluator, compiler, action registry, grammar drafting path, session engine, receipts and rehearsal — with unit tests, plus an Android runtime (action executor, Bluetooth and power adapters, timer service, review and other Compose screens) written against real device APIs but never compiled or run, since this work happened without an Android SDK. No on-device model and no measured results were produced beforehand; verifying the runtime on real hardware and adding the model belong to the event.

## Core journey

1. Press the mic and speak. Show the transcript and allow correction.
2. Draft a structured routine locally. Resolve “my earbuds” with a device picker if ambiguous.
3. Show WHEN / IF / DO / UNTIL / RESTORE, required access and any proposed defaults.
4. Offer a clearly labeled rehearsal with a match, a nonmatch and an exit.
5. Validate and approve an immutable rule version. Arm it only when required capabilities and permissions pass preflight.
6. On a real event, evaluate conditions without a model and create one session if eligible.
7. Run supported actions and record individual outcomes.
8. End on disconnect, timeout or user stop, cleaning up only supported owned effects.
9. Show the reason and actual outcome. A failed cleanup stays visible.

## Requirements

| ID | Requirement | Priority |
|---|---|---|
| IN-01 | Capture speech only after a user gesture and run recognition locally. Verify recognizer support or use bundled local assets. | Core |
| IN-02 | Allow transcript correction and typed authoring. Do not silently substitute a network recognizer. | Core |
| IN-03 | Label the actual inference/parser path in diagnostics. A canonical parser must not masquerade as general language understanding. | Core |
| CP-01 | Produce only a versioned proposal over the closed trigger, condition, action and exit vocabulary. | Core |
| CP-02 | Validate meaning-sensitive fields, numeric bounds, entities, supported semantics and permissions independently of schema generation. | Core |
| CP-03 | Ask about unresolved conditions or unsupported requests. Never silently drop a requested action or clause. | Core |
| RV-01 | Show start, conditions, actions, ending, cleanup scope and permissions before approval. | Core |
| RV-02 | Display suggested defaults, including duration and disconnect behavior, as proposals. | Core |
| RV-03 | Bind approval to the exact normalized version. Any behavior change requires reapproval. | Core |
| TR-01 | Support a selected Bluetooth device and charging transitions on the actual target phone. | Core |
| TR-02 | Support day and local-time conditions. Show time zone and overnight interpretation if relevant. | Core |
| TR-03 | Add Wi-Fi and schedule adapters only after their permissions and background behavior pass device checks. | Target addition |
| AC-01 | Start an app-owned focus timer with a visible deadline and cancellation state. | Core |
| AC-02 | Apply and release only a supported app-owned DND contribution after required access. | Core |
| AC-03 | Show local outcomes in-app and through notifications when permitted. Notification denial is itself a visible condition. | Core |
| LC-01 | End the session on the approved disconnect, duration limit or manual stop. Make cleanup idempotent. | Core |
| LC-02 | Prevent duplicate starts within one connection session. Reconnect debounce must not restart the same timer during a short signal flap. | Core |
| LC-03 | Persist active sessions and cleanup obligations. Reconcile after process restart without replaying completed side effects. | Core |
| LC-04 | Preserve another mode’s quiet contribution and an explicit user override. Do not claim global DND is off merely because our rule ended. | Core |
| LC-05 | If lifecycle delivery fails, show delayed or failed cleanup and provide a stop/recovery path. | Core |
| EX-01 | Record observed input, condition result, start decision, action outcomes and exit reason. | Core |
| EX-02 | Give a readable explanation for a nonmatch, unknown context, duplicate, blocked action or cleanup failure. | Core |
| RH-01 | Use the same pure evaluator for synthetic rehearsal and runtime decisions. Simulations must never invoke action adapters. | Core |
| RH-02 | Label sample events as synthetic. Do not imply access to historical events before installation or consent. | Core |
| CT-01 | At one approved deadline, read current charging state and notify once if false. Unknown must not become “not charging.” | Stretch |

## Review example

Request: “When my earbuds connect after 6 PM on weekdays, start a 45-minute focus timer and quiet notifications. End it if I disconnect.”

| Field | Review |
|---|---|
| WHEN | The selected paired earbuds connect |
| IF | Monday–Friday, local time at or after 18:00 |
| DO | Begin the app’s 45-minute focus timer and request the approved DND policy |
| UNTIL | Timer reaches its deadline, earbuds disconnect or user stops the session |
| RESTORE | End/cancel our timer as appropriate and release only our DND contribution |
| Repeat behavior | One session per connection, subject to a visible reconnect debounce |
| Access | Actual OS requirements for Bluetooth, notifications, DND and scheduling |

“After 6 PM” is normalized visibly. The prototype can define this as at-or-after 18:00 with seconds omitted; the user sees that interpretation. No geofence is implied.

## UX surfaces

- Home: primary mic, text alternative, active session, monitoring health and recent result.
- Review: complete routine, editable fields, permission preflight, rehearsal and approval.
- Routine detail: approved version, active/paused status, next relevant deadline and manual stop.
- Receipt: trigger facts, matched or missing conditions, outcomes and cleanup state.

A receipt should read like “Skipped: Saturday is outside Monday–Friday.” It should not invent a natural-language justification using an LLM after the fact.

## Proposed evaluation

These are acceptance targets to test during the event, not achieved metrics:

1. Several unseen paraphrases of supported routines preserve device, condition and exit semantics.
2. Voice and compilation work with verified network isolation after required assets are present.
3. Two physical triggers work while the app is backgrounded on the target iQOO.
4. A matching event starts one timer, and a repeated callback does not start another.
5. Disconnect and timeout each end a session and release the app-owned DND contribution.
6. A separate quiet mode remains respected when our session ends.
7. Permission denial, a skipped condition and a blocked cleanup show truthful outcomes.
8. Process recreation does not silently duplicate work. Reboot behavior is explicitly tested or listed as unverified.
9. Synthetic rehearsal cannot mutate phone state.
10. A user can explain both entry and exit after reading the review, without reading JSON.

Measure compilation latency, event delivery delay and cleanup delay on the actual phone, recording model, backend, OS and prompt. Retain previous 5-second planning and 3-second reaction figures only as aspirations until measured; they are not submission claims.

## Risks and scope decisions

| Risk | Response |
|---|---|
| Local model too slow or inaccurate | Early feasibility spike, smaller vocabulary/model and visible unsupported cases |
| Speech fails in a loud venue | Transcript correction, another speaker in rehearsal, disclosed typed fallback |
| Background delivery or power management fails | Actual-device spike, monitoring health, explicit limitation and recovery |
| Timer completion or cleanup requires precise scheduling | Preflight access and show timing limits; do not promise exact delivery without evidence |
| DND platform behavior differs | Test ownership and overlapping modes first; reduce claim if unsupported |
| Novelty challenged | Acknowledge precedents, including OriginOS 7 Jovi Security's visible actions and confirmation before sensitive actions; demonstrate upfront approval of the whole lifetime, cleanup on exit and no model at runtime |
| OS permission monitor flags background listeners | Explain each permission in the permission check, keep Bluetooth and charging listeners minimal, and track monitoring health |
| Too many contextual ideas | Keep rich context as roadmap and preserve the core start-and-exit proof |

Research, comparison and pitch wording live in the private positioning notes (kept locally, outside this repository). Technical behavior lives in [FDD.md](FDD.md). The revised authoring, review and approval plan is in [SPRINT_3.md](SPRINT_3.md); runtime proof, R&D spikes and measurement are in [SPRINT_4_5.md](SPRINT_4_5.md).
