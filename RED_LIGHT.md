# Building during Red Light

Roughly ten and a half of the nineteen build hours are Red Light: the iQOO
phone only, with the laptop reachable through Office Kit. Green Light is both
devices, and covers the opening sprint, mentor rounds, the overnight window and
demo polish.

The constraint is real and worth planning around rather than fighting.
Office Kit route confirmed working on the loaner, 26 Sep 2026 — see CL-01
(closed) in [CLEANUP.md](CLEANUP.md).

---

## Office Kit drives the laptop

The laptop keeps compiling; the phone becomes the keyboard and screen. Office
Kit mirrors the laptop, forwards input, shares a clipboard and moves files
without a cable.

**Set up during the Saturday teach-in, while it is still Green Light:**

1. Pair the loaner with the laptop in Office Kit and confirm screen mirroring
   and remote control both work.
2. Turn on Wireless debugging on the phone (Settings → Developer options).
3. Pair adb once over the network: `adb pair <host>:<pair-port>`.
4. Connect: `./dev w <host>:<connect-port>`.

Step 4 is the one that matters. With adb over Wi-Fi, the APK installs onto the
same phone that is mirroring the laptop — no cable competing for the port, and
no need to stop mirroring to deploy.

**The loop, entirely from the phone:**

```
./dev t      run the core tests
./dev d "…"  compile a sentence, read the review and rehearsal
./dev r      install and launch on this phone
./dev l      watch the Cues log
```

Every verb is one or two characters because a phone keyboard makes every
character cost something. `./dev d` matters most: it exercises the whole
decision path — parse, normalize, validate, evaluate, rehearse — in about a
second, with no install and no device required. Most core work can be done
without deploying at all.

**Office Kit usage is 10% of the rubric**, read off HackTracker device
telemetry rather than self-reported. Working this way is not a compliance
exercise; it is how the hours get spent anyway.

---

## What does not depend on either route

`:core` builds and tests anywhere a JDK 21 exists — laptop, phone, cloud
container, CI. `settings.gradle.kts` drops `:app` entirely when no Android SDK
is present, so a machine without one still runs the full test suite and the
demo CLI without a word of configuration.

Whatever happens to the build route, the core suite keeps running. As of 24 Sep 2026 it has
267 tests covering evaluation, session admission, reconnect grace, cleanup,
recovery, drafting and the Sprint 7 additions.
