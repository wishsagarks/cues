# Developer setup

## Toolchain

- JDK 21 is required by `:core`.
- Kotlin is installed with Homebrew: `brew install kotlin`.
- Android SDK platform 36 and build tools 36.0.0 are required for `:app`.

Confirm the standalone compiler with:

```sh
kotlin -version
kotlinc -version
```

The Gradle wrapper remains the build authority; use `./dev t`, `./dev b`, and
`./dev perms` for project verification.

## Agent skills

The project Android/Compose skill is versioned at
`.agents/skills/cues-android/SKILL.md`. It is linked into both
`~/.codex/skills/cues-android` and `~/.claude/skills/cues-android` on this
machine, so either agent follows the same architecture and verification rules.

Recreate the links after a new checkout:

```sh
ln -sfn "$PWD/.agents/skills/cues-android" ~/.codex/skills/cues-android
ln -sfn "$PWD/.agents/skills/cues-android" ~/.claude/skills/cues-android
```
