# Sprint 3: Offline authoring, review and approval

**Duration:** 6 hours (hours 10–16)
**Goal:** With the network off, a spoken or typed request becomes a checked, versioned rule. The review shows **WHEN / IF / DO / UNTIL / RESTORE**; approval stores and arms that rule.

OriginOS 7 already describes Jovi agent tasks, action confirmation, security visibility and scheduled tasks. Sprint 3 therefore puts the rule's ending and the trust boundary first: Cues obtains approval once for the whole lifetime of a rule, including its cleanup; it starts from declared device events; and, after approval, no model or network participates in runtime decisions.

| # | Task | Est. | Requirement IDs | Change from v1 |
|---|---|---:|---|---|
| 3.0 | **Check OriginOS 7 on the event phone.** Confirm the OS version. Check `SpeechRecognizer.isOnDeviceRecognitionAvailable` and the locally available English (India) language pack. Check whether Jovi takes over the mic or assist gesture. Record all results in diagnostics. Also observe whether the OS permission-usage monitor flags Cues' Bluetooth and charging listeners. | 0.25h | IN-01, TR-01 | New |
| 3.1 | Typed text becomes a draft rule over the closed vocabulary. Label the path that produced it in diagnostics. | 1.25h | IN-02, IN-03, CP-01 | −0.25h |
| 3.2 | Keep the checker separate from the model. Return a question for unsupported clauses; never drop them. | 1.5h | CP-02, CP-03 | — |
| 3.3 | Resolve “my earbuds” against paired devices, with a picker. | 0.5h | CP-02, TR-01 | — |
| 3.4 | **Review screen, ending first.** Give UNTIL and RESTORE equal weight with WHEN and DO. Add the fixed line: “After approval: no model, no network.” Show each action's registry risk class; all core actions must be owned and reversible. Explain why each permission is needed and how often it is used, so OS-monitor suggestions are unsurprising. Any edit requires approval again. | 1.75h | RV-01, RV-03, AC-01, AC-02, LC-01, LC-04 | +0.25h |
| 3.5 | Use local speech with transcript correction and no silent network fallback. If task 3.0 fails, disclose typed input as the fallback. | 0.5h | IN-01, IN-02 | −0.25h |
| 3.6 | Approve, store the exact version, then arm it. | 0.25h | CP-01, RV-03 | — |

## Test cases

- A typed or corrected request produces a reviewable proposal over only the supported vocabulary, and diagnostics identify the parser path.
- Unsupported or ambiguous clauses return a readable question; no clause is silently discarded.
- “My earbuds” opens the paired-device picker and binds the chosen device rather than a guessed device.
- The review shows WHEN, IF, DO, UNTIL and RESTORE without scrolling, including the fixed no-model/no-network boundary.
- Editing any reviewed behavior invalidates the prior approval and requires approval of the new version.
- With the network off, local speech either produces a correctable transcript or visibly offers typed input; it never silently switches to a network recognizer.
- Approval stores the displayed version and arms only that version.
- A request Jovi handles naturally, such as “every morning at 8 compile the news,” gets the readable answer: “Time-scheduled content tasks aren't supported.” It must not be squeezed into a trigger rule.

## Exit criteria

- A spoken or typed supported request becomes a checked, versioned rule with a visible parser-path diagnostic.
- The review states the ending and the no-model boundary without scrolling.
- Unsupported clauses, edits and ambiguous devices remain visible and require resolution or reapproval.
- The OriginOS 7 results from task 3.0 are recorded, including local-speech availability and the listener-monitoring result.

## What to cut, in order

1. Reduce the rewordings covered.
2. Use a picker even when there is one paired device.
3. Fall back to typed input.

## Not in this sprint

- Integrating with Jovi or its custom skills. There is no verified developer path, so the Office Kit rule applies.
- Time triggers. They are more clearly deferred now that Jovi offers scheduled tasks.
