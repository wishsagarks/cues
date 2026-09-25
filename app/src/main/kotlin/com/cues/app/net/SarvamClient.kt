package com.cues.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import java.util.UUID

/**
 * Endpoints and payload shapes confirmed against https://docs.sarvam.ai on
 * 25 Sep 2026 (translate, speech-to-text, text-to-speech, chat completions).
 * Not yet exercised against a live key on a device — see CLEANUP.md CL-35 and
 * docs/DISCLOSURE.md.
 *
 * Uses [HttpURLConnection] rather than Java 11's `java.net.http.HttpClient`
 * on purpose: that newer client is only available on Android API 34+, and
 * this app's minSdk is 29 (`app/build.gradle.kts`) — `HttpURLConnection` has
 * been present since API 1.
 *
 * Every call here is a plain, one-shot REST request. This class knows nothing
 * about Cues' drafting or review model — it only turns Sarvam's HTTP API into
 * Kotlin data, exactly the way [com.cues.app.drafting.LiteRtLmSession] turns
 * LiteRT-LM into [com.cues.app.drafting.InferenceOutput]. Callers (
 * [com.cues.app.drafting.SarvamChatDrafter], the regional-voice input path,
 * the translated read-back) decide what any of this means for a cue.
 */
class SarvamClient(private val apiKey: String) {

    init {
        check(apiKey.isNotBlank()) { "Sarvam API key is blank; construct SarvamClient only when one is configured." }
    }

    data class Translation(val translatedText: String, val sourceLanguageCode: String)
    data class Transcript(val transcript: String, val languageCode: String?)

    /** [sourceLanguageCode] may be `"auto"` to let Sarvam detect it; the response echoes what it resolved. */
    suspend fun translate(
        text: String,
        sourceLanguageCode: String = "auto",
        targetLanguageCode: String = "en-IN",
    ): Translation = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("input", text)
            put("source_language_code", sourceLanguageCode)
            put("target_language_code", targetLanguageCode)
        }
        val response = postJson("https://api.sarvam.ai/translate", body.toString())
        val json = Json.parseToJsonElement(response).jsonObject
        Translation(
            translatedText = json.getValue("translated_text").jsonPrimitive.content,
            sourceLanguageCode = json["source_language_code"]?.jsonPrimitive?.content ?: sourceLanguageCode,
        )
    }

    /** One user-turn chat completion. Cues never sends conversation history or system context Sarvam didn't need. */
    suspend fun chat(prompt: String, model: String = "sarvam-105b"): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("model", model)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("temperature", 0.2)
        }
        val response = postJson("https://api.sarvam.ai/v1/chat/completions", body.toString())
        val json = Json.parseToJsonElement(response).jsonObject
        val choices = json.getValue("choices").jsonArray
        choices.first().jsonObject.getValue("message").jsonObject.getValue("content").jsonPrimitive.content
    }

    /**
     * [wavBytes] must already be a valid WAV file (see the regional-voice
     * capture path, which records 16kHz mono PCM and writes a WAV header).
     * [languageCode] of `"unknown"` lets Saaras auto-detect it.
     */
    suspend fun transcribe(
        wavBytes: ByteArray,
        languageCode: String = "unknown",
        model: String = "saaras:v4",
    ): Transcript = withContext(Dispatchers.IO) {
        val boundary = "cues-${UUID.randomUUID()}"
        val body = multipartBody(
            boundary = boundary,
            fields = mapOf("model" to model, "language_code" to languageCode),
            fileFieldName = "file",
            fileName = "cue.wav",
            fileContentType = "audio/wav",
            fileBytes = wavBytes,
        )
        val response = send(
            url = "https://api.sarvam.ai/speech-to-text",
            contentType = "multipart/form-data; boundary=$boundary",
            body = body,
        )
        val json = Json.parseToJsonElement(response).jsonObject
        Transcript(
            transcript = json.getValue("transcript").jsonPrimitive.content,
            languageCode = json["language_code"]?.jsonPrimitive?.content,
        )
    }

    /** Returns decoded WAV bytes, ready to write to a temp file and hand to [android.media.MediaPlayer]. */
    suspend fun textToSpeech(
        text: String,
        languageCode: String,
        speaker: String = "anushka",
        model: String = "bulbul:v2",
    ): ByteArray = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("text", text)
            put("language_code", languageCode)
            put("speaker", speaker)
            put("model", model)
        }
        val response = postJson("https://api.sarvam.ai/text-to-speech", body.toString())
        val json = Json.parseToJsonElement(response).jsonObject
        val audios = json.getValue("audios").jsonArray
        check(audios.isNotEmpty()) { "Sarvam returned no audio." }
        Base64.getDecoder().decode(audios.first().jsonPrimitive.content)
    }

    private fun postJson(url: String, jsonBody: String): String =
        send(url, contentType = "application/json", body = jsonBody.toByteArray(Charsets.UTF_8))

    private fun send(url: String, contentType: String, body: ByteArray): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 10_000
            readTimeout = 30_000
            setRequestProperty("api-subscription-key", apiKey)
            setRequestProperty("Content-Type", contentType)
        }
        try {
            connection.outputStream.use { it.write(body) }
            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() } ?: ""
            check(statusCode in 200..299) { "Sarvam returned HTTP $statusCode: ${responseText.take(200)}" }
            return responseText
        } finally {
            connection.disconnect()
        }
    }

    private fun multipartBody(
        boundary: String,
        fields: Map<String, String>,
        fileFieldName: String,
        fileName: String,
        fileContentType: String,
        fileBytes: ByteArray,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        fun writeText(text: String) = out.write(text.toByteArray(Charsets.UTF_8))
        fields.forEach { (name, value) ->
            writeText("--$boundary\r\n")
            writeText("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
            writeText("$value\r\n")
        }
        writeText("--$boundary\r\n")
        writeText("Content-Disposition: form-data; name=\"$fileFieldName\"; filename=\"$fileName\"\r\n")
        writeText("Content-Type: $fileContentType\r\n\r\n")
        out.write(fileBytes)
        writeText("\r\n--$boundary--\r\n")
        return out.toByteArray()
    }
}
