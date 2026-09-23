# OriginOS 7 experience pass

## Design contract

Cues remains its own product: a focused, verifiable automation app. OriginOS 7
is reference material for motion quality and material treatment, not a source
of iQOO branding, copy, artwork, or screen layouts.

- **Visual language:** quiet light/dark foundations, an electric-blue cue,
  rounded-but-not-bubbly surfaces, and a restrained translucent ambient glow.
- **Interaction:** user-initiated actions acknowledge immediately with the
  system haptic engine; cards and surfaces use short spring-like size and
  color transitions rather than decorative animation.
- **Trust:** status remains semantic (green / amber / red), never conflated
  with the brand accent. Permission, network, and approval claims stay explicit.
- **Launch:** use the AndroidX system-splash handoff. Never add a second
  splash Activity or delay first useful content for branding.

## Delivery checklist

- [x] Sync `main` with `origin/main` and create `codex/originos7-experience`.
- [x] Establish shared color, type, shape, and material tokens.
- [x] Add an accessible system splash experience for Android 10+.
- [x] Add haptic acknowledgment to the primary create and navigation paths.
- [x] Refresh the Home surface with ambient depth and clear action hierarchy.
- [x] Validate the debug APK on the Android emulator: cold launch, splash
  handoff, Home viewport, and no fatal exception.
- [ ] Run a physical-device pass for haptic strength, dark mode, and splash exit.
- [ ] Capture before/after screenshots on the target device before release.
