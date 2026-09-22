// Intentionally almost empty.
//
// Do NOT declare the Android or Compose plugins here, not even with
// `apply false`. Gradle resolves plugin markers at configuration time, so a
// root-level declaration would reach for Google's repository on every build —
// including the :core-only builds that have to work without an Android SDK.
// Android plugins are declared in app/build.gradle.kts, which is only ever
// configured when settings.gradle.kts found an SDK.

tasks.register("cleanAll") {
    group = "build"
    description = "Deletes the root build directory."
    val buildDir = layout.buildDirectory
    doLast { buildDir.get().asFile.deleteRecursively() }
}
