# Phase 1 idea submission

Open Innovation. Hyderabad City Battle, September 26–27, as supplied in the event brief.

Status: proposed app. No application implementation or performance results yet.

## Idea title

Cues: Context You Declare, Behaviour That Ends

## Description

Cues lets you declare the contexts that matter to you, and the behaviour that belongs to each one. The unit is a **cue**: a context you name, and behaviour with an explicit beginning and an explicit end.

Cues is not an automation or workflow builder, and it does not try to develop a deep understanding of you. Context here is declared, not inferred — a cue uses only the signals you attach to it and knows nothing else.

Say: “When my earbuds connect after 6 PM on weekdays, start a 45-minute focus timer and quiet notifications. End it if I disconnect.” The app turns the request into a readable WHEN / IF / DO / UNTIL / RESTORE plan. It shows the selected device, required permissions and any suggested defaults before you approve the rule.

The focus is trust in unattended behavior. A routine should not leave quiet mode behind after a workout, create duplicate timers during reconnects, or override another mode when it ends. Cues models the routine as a bounded session. Its exit releases only changes it owns, and a receipt explains why it started, skipped or stopped.

Speech-to-text and a small language model run on the phone. Natural language is untrusted input: the model only proposes structured data. A deterministic validator checks an allowlisted vocabulary, arguments, permissions and unresolved entities. After approval, ordinary Android code handles the events and actions. No model or network participates in runtime decisions for the demonstrated routines.

Before activation, the user can rehearse the rule on clearly labeled sample events. Later, optional local logging can enable replay against actually observed history. A new install never pretends to know last week’s behavior.

Natural-language automation already has precedents, including Tasker’s AI generator and Apple’s description-based shortcut creation. We are proposing a focused iQOO experience that combines offline authoring, explicit session endings, rehearsal and understandable execution records. We will demonstrate that combination instead of claiming to be the first voice automation tool.

Phone assistants are also becoming far more contextual. Apple’s Siri AI in iOS 27 infers context from a broad personal index of messages, emails, photos and onscreen content. That capability is real and we do not claim to exceed it. Cues takes a different position on purpose. Context is declared rather than inferred: a routine uses only the signals the user explicitly attaches, each shown with its source and age, and anything else stays unknown. The runtime carries no network dependency and no usage budget, because a rule expected to fire every evening for months has different requirements from an assistant you invoke. And recognising that a moment has begun is the well-covered half of the problem; this proposal concentrates on the ending — releasing only what the routine owns, respecting a later user choice, and not firing twice on a reconnect.

The solo 30-hour core supports Bluetooth and charging triggers, an app-owned timer, an app-owned DND contribution, review, cleanup and receipts. Wi-Fi and schedule adapters follow if the core is stable. One stretch idea checks whether the phone is charging at a deadline and reminds the user if it is not. Longer-term ideas include tasks that wait for an approved opportunity, named contexts and temporary changes such as “just this week.”

The demo enables airplane mode, explicitly disables Wi-Fi and keeps Bluetooth on. A spoken routine is reviewed and approved. Connecting the earbuds starts the focus session; disconnecting ends it and releases the app’s DND contribution. Android permission, scheduling or background limits are visible outcomes, never hidden successes. All application work is planned for the event.

## Short version

Cues turns spoken intentions into approved offline routines on iQOO. Users review not only WHEN / IF / DO, but also UNTIL / RESTORE. A local model drafts the rule; deterministic Android code executes it later. The proposed experience combines bounded sessions, sample-event rehearsal and receipts explaining starts, skips and exits. The live demo creates a routine with networks disabled, starts a focus timer and DND contribution when earbuds connect, then releases its own effects when they disconnect. The 30-hour build prioritizes Bluetooth, charging and reliable lifecycle behavior. Wi-Fi, time triggers and deadline reminders follow if stable.

## What makes the entry stand out?

The project treats automation as a complete lifecycle. It makes the end condition and supported cleanup as visible as the trigger. The model cannot directly control the device, and the user approves the exact compiled behavior. The prototype aims to prove this on real iQOO events while offline, including skipped cases and a truthful record of failures. We recognize existing AI automation products and focus the differentiation on a narrow experience that we can test and demonstrate.

## Attachments and remaining form fields

- Deck: [Cues_Deck.pptx](Cues_Deck.pptx), eleven slides with speaker notes. Slide 8 positions the proposal against contextual assistants such as Siri AI in iOS 27.
- Video URL: not recorded yet. Record the real start-and-stop demonstration after implementation.
- Prototype URL: not available yet. Do not represent these design documents as a working prototype.
- Android and LLM proficiency: select the actual proficiency level.
- Prior builds and placements: provide only verifiable personal history.

This copy does not assume form character limits. Shorten to the actual field limits when entering it. Registration and submission are separate user actions and have not been performed.

## Demo preparation

The event dates fall on a weekend. Use a visibly approved weekend rule or an appropriate current-day condition during the live demo. Never secretly bypass the weekday check. A short timer must be an explicit review edit, not a hidden execution shortcut.

Research and claims guidance: the private positioning notes (kept locally, outside this repository). Requirements: [PRS.md](PRS.md). Design: [FDD.md](FDD.md).
