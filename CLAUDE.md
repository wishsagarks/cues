# Working in this repository

Cues is an Android app for the iQOO Hackathon 2026. Its unit is a *cue*: a
context the user declares, and behaviour with an explicit beginning and an
explicit end.

Read [docs/FDD.md](docs/FDD.md) before changing anything in `:core`. The design
decisions there are load-bearing and most of them are about what the app
deliberately will not do.

## Two modules, and the line between them

```
:core   Kotlin/JVM, no Android types.  Every decision the app makes.
:app    Android + Compose.             Everything the app actually does.
```

`:core` holds the routine model, the evaluator, the compiler, the drafting
grammar, the session engine and the receipts. It has no Android dependency, so
it builds and tests in about twenty seconds on any machine with a JDK — which
is what keeps the work moving during Red Light and in cloud containers where
Google's Maven is unreachable.

Anything that touches the device goes behind an interface in
`core/.../ports/Ports.kt`, implemented by Android in `:app` and by fakes in
tests. **Do not add an Android import to `:core`.** The moment that happens,
the fast test loop and the phone-only workflow both stop working.

`settings.gradle.kts` includes `:app` only when an Android SDK is present.
Declare Android plugins in `app/build.gradle.kts`, never in the root build
file: root-level plugin declarations resolve against Google's repository at
configuration time and break every SDK-free build.

## Rules that are not style preferences

These come from the product's central claim, which is that its behaviour can be
trusted when nobody is watching.

- **UNKNOWN is not false.** A signal that cannot be read stays
  `ContextValue.Unknown` through evaluation and into the receipt. It never
  grants permission to act, and it never quietly reads as "no".
- **Nothing downstream of approval consults a model.** The drafting model
  proposes typed data; the validator checks it independently; the runtime
  follows the approved rule. There is no path from a model to an executor.
- **Capabilities are derived, never trusted.** `ActionRegistry.capabilitiesFor`
  computes them from the actions. A draft claiming it needs no permissions is a
  claim about our code, not about the world.
- **Cleanup releases only what Cues owns.** `OwnedResource` has no member for
  global Do Not Disturb, because Cues did not set it and cannot know who did.
- **A blocked action is not a success.** It is `BLOCKED`, the session is
  `PARTIAL`, and the receipt says so.
- **Receipts are rendered from reason codes**, never generated as prose. A
  fluent explanation that does not match what the code did is worse than none.
- **Never drop a clause to make a request fit.** Report it as unsupported or
  ask. A rule that works perfectly and does the wrong thing is the failure mode
  that matters.
- **Every draft names the drafter that produced it.** A canonical parser
  presented as language understanding is a lie told to a judge; a model that
  silently fell back to the parser is the same lie told by accident.

## Claims

No number ships until it has been measured on the device, and the source of
each measurement gets recorded with it. Targets and results are different
things and the documents say which is which. Assumptions that have not been
verified belong in [CLEANUP.md](CLEANUP.md) with the condition that would
retire them.

## Commands

For Android, Compose, widget, notification or adapter changes, follow the
versioned [Cues Android skill](.agents/skills/cues-android/SKILL.md).

`./dev` is the task runner; verbs are short because much of the event is spent
typing on a phone. `./dev h` lists them. `./dev t` runs the core suite and
`./dev d "<sentence>"` compiles a cue and prints its review and rehearsal
without needing a device.

[RED_LIGHT.md](RED_LIGHT.md) covers building when the laptop is only reachable
through Office Kit.
