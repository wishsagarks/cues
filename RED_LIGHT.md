# Building during Red Light

Roughly ten and a half of the nineteen build hours are Red Light: the iQOO
phone only, with the laptop reachable through Office Kit. Green Light is both
devices, and covers the opening sprint, mentor rounds, the overnight window and
demo polish.

The constraint is real and worth planning around rather than fighting. Two
routes are prepared. One of them gets deleted once the other is proven — see
CL-01 in [CLEANUP.md](CLEANUP.md).

---

## Route A — Office Kit drives the laptop (primary)

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

## Route B — Termux on the phone (fallback, unproven)

Gradle and the Android SDK running natively on the iQOO, no laptop involved.

**This has not been tested on this hardware.** It is written down so that a
failure of Route A on Saturday afternoon is an inconvenience rather than a
disaster. Try it during the opening Green Light window, not at the moment it is
needed.

```sh
pkg install openjdk-21 gradle git
# The Android SDK command-line tools need aarch64 builds; the standard
# x86 build-tools binaries will not run. This is the step most likely to fail.
```

What would work even if the SDK does not: `:core` is pure Kotlin/JVM with no
Android dependency at all. `./dev t` and `./dev d` need nothing but a JDK and
Gradle, so the entire decision core stays workable on the phone under Termux
regardless of whether an APK can be built there.

That is not an accident of the design. It is the reason `:core` has no Android
types in it.

---

## What does not depend on either route

`:core` builds and tests anywhere a JDK 21 exists — laptop, phone, cloud
container, CI. `settings.gradle.kts` drops `:app` entirely when no Android SDK
is present, so a machine without one still runs the full test suite and the
demo CLI without a word of configuration.

Whatever happens to the build route, the 92 tests covering evaluation, session
admission, reconnect grace, cleanup and recovery keep running.
