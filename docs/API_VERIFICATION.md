# Platform API verification — 24 September 2026

This note records the official-source gate for the Cues Brain plan. It is not evidence of behavior on an iQOO loaner; device results belong in `CLEANUP.md`.

## LiteRT-LM and Qualcomm NPU

- Kotlin/Android artifact: `com.google.ai.edge.litertlm:litertlm-android`. **Pinned to `0.16.1`, integrated and compiled this sprint** — see `app/.../drafting/LiteRtLmSession.kt`. Releases 0.17.0+ depend on `kotlin-reflect:2.4.0`, whose own compiled classes carry Kotlin 2.4.0 metadata that this project's 2.2.21 compiler cannot read at all (a hard incompatibility, not a resolvable version conflict); every release through 0.16.1 depends on `kotlin-reflect:2.2.21`, matching this project. Re-check this before ever bumping the pin.
- Public Kotlin configuration supports `Backend.CPU()`, `Backend.GPU()`, and `Backend.NPU(nativeLibraryDir = ...)`. `Message` carries no `.text`; plain text is `message.contents.contents.filterIsInstance<Content.Text>()`.
- **No embeddings API.** Checked 25 Sep 2026 against the same getting-started doc this section already cites: the public Kotlin surface is text generation only (`Engine`, `Conversation.sendMessage`/`sendMessageAsync`, `Message`/`Content`, tool-calling types) — there is no embed-text call. This settles a question CLEANUP.md CL-18/CL-36 left open: wiring `core/.../ports/Ports.kt`'s `Embedder` port to LiteRT-LM as a real vector embedder is not buildable against the pinned `0.16.1` release. A future attempt needs either a different embedding-capable runtime or the slower, honest fallback of one yes/no `generate()` call per candidate — never a fabricated embedding.
- Qualcomm NPU requires a SoC-specific `.litertlm` model plus QAIRT runtime/dispatch libraries. The official table currently lists SM8750, SM8650, and SM8550 Gemma3-1B int4 models.
- The plan's likely iQOO 15 hardware is newer than that published table. Cues inspects `Build.SOC_MODEL` (API 31+) and treats NPU as unavailable unless it matches that published table; GPU, then CPU, then parser-only are the labelled fallbacks — see CLEANUP.md CL-18 for what remains unverified (no model has been side-loaded, no SoC has been read on a real device, and the library exposes no independent "which backend ran" query, only whether `Engine.initialize()` threw).
- The GPU tier needs `<uses-native-library android:name="libvndksupport.so" android:required="false"/>` and the same for `libOpenCL.so` inside `<application>`.
- Source: https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md
- Source: https://developers.google.com/edge/litert/next/litert_lm_npu
- Source: https://maven.google.com/web/index.html#com.google.ai.edge.litertlm:litertlm-android (version/POM history used to find the 0.16.1 pin)
- **`Conversation` surface, verified with `javap` on the resolved 0.16.1 AAR's runtime jar (26 Sep 2026), for Sprint 8's warm-runtime work:**
  `sendMessageAsync(prompt: String): Flow<Message>` (a cancellable, coroutine-native alternative to the blocking `sendMessage`), `cancelProcess()`, `getBenchmarkInfo(): BenchmarkInfo` (marked `@ExperimentalApi`), `getTokenCount(): Int`, `isAlive()`. `BenchmarkInfo(initTimeInSecond, timeToFirstTokenInSecond, lastPrefillTokenCount, lastDecodeTokenCount, lastPrefillTokensPerSecond, lastDecodeTokensPerSecond)`. `EngineConfig`/`ConversationConfig` also expose `SamplerConfig(topK, topP, temperature, seed)` and `ResponseFormat.regex(...)`/`.json(...)` for constrained decoding — neither used yet.
- **A second, narrower metadata incompatibility inside the pinned 0.16.1 release itself:** `BenchmarkInfo`'s own class file carries `kotlin.Metadata(mv=[2,3,0])` (Kotlin 2.3's binary metadata format) even though the rest of this dependency's `kotlin-reflect` requirement matches this project's 2.2.21 (the bullet above). This project's Kotlin compiler cannot read that metadata version and treats the class as having no resolvable members — `benchmark.lastPrefillTokenCount` and even the explicit Java-style `benchmark.getLastPrefillTokenCount()` both fail with "unresolved reference", despite `javap` confirming every getter is an ordinary public JVM method. `app/.../drafting/LocalModelRunner.kt` reads these fields with plain reflection instead. See CLEANUP.md CL-40.

## AppFunctions

**Compiled, not confirmed on a device.** Task 17 (`app/.../appfunctions/CuesAppFunctionService.kt`) implements `draftCue`, `forecastToday`, `currentContext`, `startCue` and `stopCue`, with the dependency, KSP plugin and manifest service declared. As of 25 Sep 2026 (CL-24's update), `kspDebugKotlin` has run for the first time — an AGP 9.1.1/compileSdk 37/KSP 2.3.12 bump (CL-35 needed `:app` to build at all) got past the AAR-metadata block that had prevented any Kotlin here from compiling. That confirms the generated code compiles; it does not confirm the generated service class name, `app_functions_schema.xsd`, `cues_app_function_service.xml` or `@xml/app_metadata` actually match what the manifest references, or that `adb shell cmd app_function` can see or call any of it — those still need a real device check. See CLEANUP.md CL-24.

- Re-verified 24 Sep 2026 (superseding this file's earlier alpha11 note, which had never been checked against an actual snippet): Android platform App Functions require API 36 and remain a preview. **Current Jetpack release is `1.0.0-alpha12`** — confirmed both from the release-notes page and from every relevant reference page (`AppFunctionService`, `AppFunction`, `AppFunctionSerializable`, `AppFunctionServiceEntryPoint`, `AppFunctionElementNotFoundException`, `AppFunctionInvalidArgumentException`) independently saying "Added in 1.0.0-alpha12".
- The architecture changed since this file's first pass: alpha10 introduced `@AppFunctionServiceEntryPoint`, which now generates the concrete service class and consolidates what used to be a separate `appfunctions-service` artifact and an `AppFunctionConfiguration.Provider`. Only two dependencies are needed: `androidx.appfunctions:appfunctions` (`implementation`) and `androidx.appfunctions:appfunctions-compiler` (`ksp`) — there is no current `appfunctions-service` artifact to add separately.
- Functions are annotated `@AppFunction(isDescribedByKDoc = true)` inside an `@AppFunctionServiceEntryPoint`-annotated `abstract class ... : AppFunctionService()`; parameter/return types are `@AppFunctionSerializable` data classes. `AppFunctionInvalidArgumentException`/`AppFunctionElementNotFoundException` are the predefined ways to fail a call.
- The manifest declares the KSP-generated `<service>` (`BIND_APP_FUNCTION_SERVICE`, an `AppFunctionService` intent-filter, and `schema`/`v2` `<property>` entries naming KSP-generated XML assets) plus one app-wide `app_metadata` `<property>`, also KSP-generated — none of these four generated artifacts have actually been produced here to confirm the manifest's exact naming matches.
- KSP was pinned to `2.2.21-2.0.5` (matching this project's exact Kotlin version) until 25 Sep 2026, when CL-24/CL-35's AGP 9.1.1 bump required KSP `2.3.12` instead — the old pin's paired Kotlin-version scheme could not apply under AGP 9's built-in Kotlin compilation. Confirmed from Maven Central's own `maven-metadata.xml` — reachable even where `dl.google.com` is blocked, since KSP resolves from Central/the Gradle Plugin Portal rather than Google's Maven.
- Kept from the original plan: retain ordinary in-app/Jovi-launch handoff (`IntentRouter`/`ACTION_ASSIST`) when OriginOS does not discover this provider.
- Source: https://developer.android.com/jetpack/androidx/releases/appfunctions
- Source: https://developer.android.com/ai/appfunctions/add-appfunctions
- Source: https://developer.android.com/ai/appfunctions (overview, the `@AppFunctionSerializable` example)
- Source: https://developer.android.com/reference/kotlin/androidx/appfunctions/AppFunctionService
- Source: https://repo.maven.apache.org/maven2/com/google/devtools/ksp/com.google.devtools.ksp.gradle.plugin/maven-metadata.xml

## Camera and offline text recognition

- **Integrated and compiled this sprint** — `app/.../camera/TimetableCaptureScreen.kt` (CameraX preview + capture, plus a Photo Picker fallback needing no storage permission) and `app/.../camera/TimetableOcr.kt` (ML Kit). CameraX's newest stable is 1.6.2, but its own AAR metadata requires Android Gradle Plugin 8.9.1+; this project uses AGP 8.7.3, so **CameraX is pinned to 1.5.3** (requires AGP 8.6.0+, confirmed via each release's `aar-metadata.properties`). `ImageCapture` supports in-memory or file capture and lifecycle binding.
- Bundled ML Kit Text Recognition v2 uses `com.google.mlkit:text-recognition:16.0.1`; the model is statically linked and available immediately. Confirmed via `./dev perms` that this dependency, despite its POM listing `play-services-base`/`play-services-basement` as compile dependencies, does **not** add `INTERNET` to the merged manifest — unlike MediaPipe's GenAI library (CL-06). Do not use the Play Services artifact, which downloads the model.
- Camera permission (`android.permission.CAMERA`) is now declared, deliberately, for this one feature — see CLEANUP.md CL-20.
- Captured/shared text remains untrusted data (`core/.../imports/TimetableExtractor.kt`) and only produces review proposals — nothing is armed without the ordinary Review screen.
- Source: https://developer.android.com/jetpack/androidx/releases/camera
- Source: https://developer.android.com/media/camera/camerax/take-photo
- Source: https://developers.google.com/ml-kit/vision/text-recognition/v2/android

## Cue Cards and QR

- **Integrated and compiled this sprint.** `com.google.zxing:core:3.5.3` renders the QR bitmap — pure Java, no Android or network dependency at all (its POM has only a `junit` test dependency). `com.google.mlkit:barcode-scanning:17.3.0` reads one back offline from a captured or picked image, the same bundled-vs-Play-Services split as text recognition; confirmed via `./dev perms` that it adds no `INTERNET` permission either.
- `core/.../share/CueCard.kt` is the portable format: normalized behaviour, schema version and a digest, never an approval, a status or a capability claim (those fields simply do not exist on the type). Device/place/context references travel as the labels `Trigger`/`Condition` already carry alongside their ids; import re-resolves each by label against the receiving phone's own stores and refuses the import if one is missing, rather than trusting the sender's id.
- Source: https://github.com/zxing/zxing
- Source: https://developers.google.com/ml-kit/vision/barcode-scanning/android
- Source: https://maven.google.com/web/index.html#com.google.mlkit:barcode-scanning

## Desk Bridge and the Cue Console

- **Integrated and compiled this sprint.** `core/.../export/CuesExporter.kt` builds a read-only JSON snapshot (routines' own review text, receipts, forecast, coach evidence, the ledger, and the last inference report) using `kotlinx.serialization.json`'s `buildJsonObject`/`putJsonArray` builders — note that `add`/`put`/`int`/`long`/`double` on `JsonArrayBuilder`/`JsonPrimitive` are extension functions/properties in `kotlinx.serialization.json` that need their own explicit imports; omitting them fails with "unresolved reference" or a type mismatch, not a missing-dependency error.
- `core/src/main/resources/console/template.html` is a single, dependency-free HTML/CSS/vanilla-JS file. The export JSON is embedded inline between `/*__CUES_EXPORT__*/`/`/*__END_CUES_EXPORT__*/` markers rather than fetched separately, because a `file://`-opened page's `fetch()` of a sibling file is commonly blocked by the browser's own local-file CORS policy — embedding means the page just needs to be double-clicked, with nothing else running.
- `./dev console` (fixture data) confirmed by rendering the output in a real browser: every section (cues, forecast, coach evidence, receipts, diagnostics, ledger) displays correctly.
- The phone-side path (`app/.../bridge/ExportImport.kt`) shares the file via Android's `FileProvider` (`androidx.core.content.FileProvider`, already part of `androidx.core-ktx`) — a raw `file://` Uri cannot be shared to another app since Android N. No new dependency; a `<provider>` entry and `res/xml/file_paths.xml` were added.
- Source: https://developer.android.com/reference/androidx/core/content/FileProvider
- Source: https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/json.md

## Live Updates

**Not integrated.** The session notification is the standard foreground style (CLEANUP.md CL-15). What follows is the official-source reading only.

- A promoted ongoing notification requires `android.permission.POST_PROMOTED_NOTIFICATIONS`, an ongoing standard/big-text/progress/metric style notification, a title, and an explicit promotion request.
- OEMs can add eligibility rules, so Origin Island rendering remains a loaner-phone verification item.
- Source: https://developer.android.com/develop/ui/views/notifications/live-update

## Text to speech and shortcuts

TTS is integrated (`ReplySpeaker`, CL-19). Shortcuts are **not built** (Task 9 leftovers in `tasks/todo.md`).

- Use `TextToSpeech.speak(CharSequence, Int, Bundle, String)` and always release with `shutdown()`. Cues passes the exact deterministic reply/receipt text.
- Static and dynamic shortcuts launch app-owned intents; dynamic routine shortcuts remain bounded by the device launcher limit.
- Source: https://developer.android.com/reference/android/speech/tts/TextToSpeech
- Source: https://developer.android.com/develop/ui/compose/system/shortcuts/creating-shortcuts

## Sarvam AI (cloud language assist)

**Written, not compiled or called with a real key.** CLEANUP.md CL-35: the
app's first network dependency, opt-in only. No official Kotlin/Java SDK
exists — Sarvam publishes a Python SDK only (`sarvamai`, used by
`sarvam/test_sarvam.py`'s six-call smoke test) — so `app/.../net/SarvamClient.kt`
calls the REST API directly with `java.net.HttpURLConnection`, adding no new
HTTP dependency. Deliberately not Java 11's `java.net.http.HttpClient`: that
newer client is only available on Android API 34+, while this app's minSdk
is 29 — `HttpURLConnection` has been present since API 1.
`kotlinx-serialization-json` is reused for parsing (already `:core`'s JSON
library via `CuesExporter`; used here through its `JsonElement`/
`buildJsonObject` API, not `@Serializable` classes, so no new Gradle plugin
is needed either).

- Endpoints, headers and payload shapes below were read from
  https://docs.sarvam.ai on 25 Sep 2026 — not yet confirmed against a live
  response; see CLEANUP.md CL-35.
- Auth: header `api-subscription-key: <key>` on every call (matches
  `sarvam/test_sarvam.py`'s `SarvamAI(api_subscription_key=...)`).
- Translate — `POST https://api.sarvam.ai/translate`, JSON body
  (`input`, `source_language_code`, `target_language_code`), JSON response
  (`translated_text`, `source_language_code`). `source_language_code: "auto"`
  is used throughout rather than a separate language-identification call.
- Speech-to-text (Saaras) — `POST https://api.sarvam.ai/speech-to-text`,
  `multipart/form-data` with a `file` field (Cues sends a 16kHz mono PCM WAV
  it records itself via `AudioRecord` — see `SarvamSpeechInput.kt`), JSON
  response with `transcript`/`language_code`.
- Text-to-speech (Bulbul) — `POST https://api.sarvam.ai/text-to-speech`, JSON
  body (`text`, `language_code`, `speaker`, `model`), JSON response with
  `audios: [base64 WAV]` — decoded and played via `android.media.MediaPlayer`
  from a cache-dir temp file (`SarvamReadback.kt`).
- Chat completion (`sarvam-105b`) — `POST https://api.sarvam.ai/v1/chat/completions`,
  same auth header, OpenAI-shaped `messages`/`choices` JSON. Used only by
  `SarvamChatDrafter`, and only as an explicit, opt-in third opinion after the
  offline grammar/on-device-model pair disagrees or both fail — never in the
  default pipeline. Its output is re-parsed by `GrammarParser` and
  independently validated exactly like `OnDeviceLlmDrafter`'s output; see
  `docs/FDD.md`'s "Optional cloud assist" section.
- Source: https://docs.sarvam.ai/api-reference-docs/translate/translate-text
- Source: https://docs.sarvam.ai/api-reference-docs/speech-to-text/transcribe
- Source: https://docs.sarvam.ai/api-reference-docs/text-to-speech/convert
- Source: https://docs.sarvam.ai/api-reference-docs/chat/chat-completions

## Ollama (laptop dev-only)

**Written, not exercised against a live server.** `core/.../cli/OllamaLlmSession.kt`
backs `./dev llm "<sentence>"` — a way to draft through a Gemma-class model
without the event phone or a side-loaded `.litertlm` file, for iterating on
prompts/grammar from a laptop. It is CLI-only: no `:app` production code path
constructs it, and it never ships in the APK.

- Endpoint: `POST http://localhost:11434/api/generate`, JSON body
  (`model`, `prompt`, `stream: false`), JSON response expected to carry
  `response` (the generated text) and `done` (bool). This is Ollama's
  documented non-streaming generate contract, read from its own docs, not
  exercised here — **confirm the exact field names against a real
  `ollama serve` response before trusting this class**, the same "verify
  before you claim it" rule as every other entry in this file.
- No backend/device claim: `OllamaLlmSession` always reports
  `InferenceBackend.CPU`, deliberately conservative rather than a guess at
  what device Ollama actually used — nothing in the documented response says.
- Suggested local setup: `ollama pull gemma3:1b`, `ollama serve`, then
  `./dev llm "..."`. With the server unreachable, `DifferentialDrafter`'s
  existing guarded-timeout/catch already degrades to the grammar parser's own
  answer — verified locally (see CLEANUP.md).
- Source: https://github.com/ollama/ollama/blob/main/docs/api.md

## Gemma model distribution (on-device "brain" download)

**Not yet sourced.** The download tile (`app/.../drafting/ModelDownloader.kt`,
CLEANUP.md CL-36) needs a real `.litertlm` asset URL, SHA-256 and license
terms for a Gemma3-1B int4 build — the same model class this file's LiteRT-LM
section already cites for the NPU path — read from Google's own LiteRT-LM/
model-distribution channel, the way the `litertlm` version pin and the NPU
SoC table above were sourced. Until an entry with a real URL/hash lands here,
`BuildConfig.GEMMA_MODEL_URL`/`GEMMA_MODEL_SHA256` stay blank and the tile
honestly shows "not configured" — never a guessed or placeholder URL.

## Sarvam AI pricing (for the analytics tab's cost figures)

**Not yet sourced.** `core/.../inference/InferenceCost.kt`'s
`sarvamCostPer1kTokensUsd` stays `null` — every Sarvam call in the usage/cost
ledger shows `CostBasis.UNVERIFIED` ("cost not verified"), never a fabricated
`$0.00` or an invented rate — until a real per-token or per-call price is
read from https://docs.sarvam.ai's billing pages and recorded here with the
date it was checked.

## Dependency policy

- Add dependencies only in the slice that uses them.
- Run `./dev b` and `./dev perms` after each dependency increment.
- A resolved artifact proves build integration, not device/OEM behavior.
