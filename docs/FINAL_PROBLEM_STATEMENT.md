# Cues

## Final problem statement

**Cues: Context You Declare, Behaviour That Ends**

Track: Open Innovation. Intended platform: an Android app on iQOO. Status: idea and design only. All application implementation is planned for the event.

The unit of the product is a **cue**: a context the user names, and the behaviour that begins and ends with it. The app is Cues; the thing a person creates is a cue.

People repeat the same phone setup when a familiar context returns, but describing the start is only part of the problem. A cue also needs to know when to end, what to do when context is uncertain and how to avoid overriding a later user choice.

Cues is not positioned as an automation or workflow tool, and not as a system that builds a deep understanding of its user. Context here is **declared, not inferred**: a cue uses the signals a person explicitly attaches to it, and knows nothing else.

A user speaks a cue, inspects its complete behavior and approves a persistent local rule. A typical request is:

> “When my earbuds connect after 6 PM on weekdays, start a 45-minute focus timer and quiet notifications. End it if I disconnect.”

The phone presents **WHEN / IF / DO / UNTIL / RESTORE**. It resolves the paired device, normalizes the time, shows permissions and makes suggested defaults explicit. The user approves the exact version before activation.

Speech recognition and rule drafting run locally. A small language model proposes typed data over a limited action vocabulary. A deterministic validator checks structure, values, capabilities and unresolved meanings. A valid schema is necessary but does not guarantee that the model understood the user.

After approval, ordinary Android code responds to device events and deadlines. The runtime never asks a model to decide what to do. A focus session ends on disconnect or timeout. It releases only the app-owned DND contribution and handles its own timer. It does not blindly restore old global settings over other routines or user changes.

## Proposed differentiation

The product focuses on understandable unattended behavior:

- **Bounded sessions:** start, exit and cleanup appear in one review.
- **Rehearsal:** clearly labeled sample events show matching and skipped cases before activation.
- **Reason receipts:** every run, skip and cleanup explains the relevant facts and outcome.
- **Explicit context:** missing or stale information remains unknown rather than becoming an invented assumption.

Persistent automations, natural-language authoring and routine end actions already exist in adjacent products. Our claim is a focused combination and user experience to demonstrate, not that we invented those capabilities. The competitive evidence and proposed evaluation are in the private positioning notes (kept locally, outside this repository).

## Relation to contextual assistants

Contemporary phone assistants are becoming markedly more contextual. Apple's Siri AI in iOS 27 derives context from a broad personal index — messages, emails, photos, onscreen content — and can create automations from a description. That capability is real, and this proposal does not claim to exceed it.

Cues takes a deliberately different position on three points:

- **Context is declared, not inferred.** The routine uses only the signals a user explicitly attaches, each shown with its source and observation age. Anything else stays unknown. A phone does not need to read messages to know that a selected pair of earbuds connected.
- **The runtime carries no network or usage budget.** An assistant can acceptably be unavailable or rate-limited; a rule expected to fire every evening for months cannot. Apple documents that Siri AI uses Private Cloud Compute with usage limits, which is a reasonable trade for an assistant and a poor one for a standing rule.
- **Being contextual about starting is half the problem.** Recognising that a moment has begun is well covered across the industry. Modelling the moment ending — releasing only owned changes, not overriding a later user choice, not firing twice on a reconnect — is where this proposal concentrates.

These are positioning choices to demonstrate, not measured advantages.

## Creative extensions

After the core is reliable, an expected-event reminder can ask whether the phone is charging at a chosen deadline. Later, an opportunity queue can hold approved tasks until specified conditions hold, then expire stale work. Named contexts, temporary rule changes and user-shared timetables can make the experience more contextual without unrestricted phone observation.

## Build boundary

The 30-hour core uses Bluetooth and charging events, an app-owned focus timer, a supported DND rule, local voice and compilation, review, synthetic rehearsal and execution history. Wi-Fi and time triggers remain target additions after core stability. Geofencing is optional later work. Rich personal content and Office Kit integration require separate feasibility checks.

Actions that Android cannot complete silently must produce an honest pending or blocked result and a user-action path where available. Permissions, OEM power management and precise scheduling require validation on the actual device.

## Success

A judge can speak a supported paraphrase, inspect and approve the complete routine, trigger it with a real device event while networks are disabled, observe both start and exit, and understand the receipt. A duplicate event must not create another timer. If another mode still requires quiet, releasing Cues must not claim that all DND has ended.

**One-line pitch:** Cues turns spoken routines into offline phone behavior you can rehearse, understand and stop correctly.
