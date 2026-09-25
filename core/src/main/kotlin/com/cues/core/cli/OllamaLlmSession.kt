package com.cues.core.cli

import com.cues.core.drafting.InferenceOutput
import com.cues.core.drafting.LlmSession
import com.cues.core.inference.InferenceBackend
import com.cues.core.inference.InferenceReport
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * A laptop-only [LlmSession]: talks to a local Ollama server instead of
 * LiteRT-LM, so a developer without the event phone can still exercise
 * Gemma-backed drafting from `./dev llm`. See `docs/API_VERIFICATION.md`'s
 * "Ollama (laptop dev-only)" entry — the request/response shape below is
 * Ollama's documented `/api/generate` contract, not yet exercised against a
 * live server from this environment; confirm field names there before
 * trusting this class on a machine you haven't checked.
 *
 * Uses the JDK's built-in [HttpClient] rather than [java.net.HttpURLConnection]
 * on purpose: unlike `app/.../net/SarvamClient.kt`, this class runs only on
 * `:core`'s plain `jvmToolchain(21)`, with no Android API-level floor forcing
 * the older client — see `core/build.gradle.kts`.
 *
 * On any failure — server not running, model not pulled, malformed response —
 * this throws a specific, actionable message. Never a fabricated answer: the
 * same honesty contract [com.cues.core.drafting.UnconfiguredLlmSession]
 * already sets for "no model configured" applies here to "no model reachable".
 */
class OllamaLlmSession(
    private val baseUrl: String = "http://localhost:11434",
    private val model: String = "gemma3:1b",
    private val timeout: Duration = Duration.ofSeconds(60),
) : LlmSession {

    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    override suspend fun generate(prompt: String): InferenceOutput = withContext(Dispatchers.IO) {
        val requestBody = buildJsonObject {
            put("model", model)
            put("prompt", prompt)
            put("stream", false)
        }.toString()

        val request = HttpRequest.newBuilder(URI.create("$baseUrl/api/generate"))
            .timeout(timeout)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()

        val response = try {
            client.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: java.net.ConnectException) {
            throw IllegalStateException(
                "Could not reach Ollama at $baseUrl — run `ollama serve` and `ollama pull $model` first.", e,
            )
        }
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("Ollama returned HTTP ${response.statusCode()}: ${response.body().take(200)}")
        }

        val start = System.nanoTime()
        val json = Json.parseToJsonElement(response.body()).jsonObject
        val done = json["done"]?.jsonPrimitive?.boolean ?: true
        if (!done) throw IllegalStateException("Ollama response was not marked done (streaming was disabled).")
        val text = json["response"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("Ollama response had no \"response\" field: ${response.body().take(200)}")
        val elapsedMs = ((System.nanoTime() - start) / 1_000_000).coerceAtLeast(1)
        val tokens = text.trim().split(Regex("\\s+")).count { it.isNotBlank() }.coerceAtLeast(1)

        InferenceOutput(
            text = text,
            // CPU is deliberately conservative rather than a guess at what
            // device Ollama actually used for this run — no field in the
            // documented response says, so this codebase's rule is to
            // understate rather than overclaim (the same reason LiteRT-LM's
            // own InferenceReport.backend doc comment exists).
            report = InferenceReport(InferenceBackend.CPU, loadMs = 0, generationMs = elapsedMs, estimatedTokens = tokens),
        )
    }
}
