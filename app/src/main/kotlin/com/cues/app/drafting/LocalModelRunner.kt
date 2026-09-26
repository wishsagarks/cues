package com.cues.app.drafting

import android.content.Context
import android.os.Build
import com.cues.core.drafting.InferenceOutput
import com.cues.core.inference.InferenceBackend
import com.cues.core.inference.InferenceReport
import com.cues.core.inference.TokenCountSource
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * What this runner's one [Engine], if any, is doing right now. `:app`-only —
 * never leaks into `:core`, since it describes a local runtime detail, not a
 * drafting decision.
 */
sealed interface RunnerState {
    /** No model file, or the engine has never been asked to load. */
    data object Cold : RunnerState

    data class Loading(val tier: InferenceBackend) : RunnerState

    /** Loaded and idle — the next call reuses this engine rather than reloading. */
    data class Warm(val tier: InferenceBackend, val loadMs: Long) : RunnerState

    data class Generating(val tier: InferenceBackend, val startedAtMillis: Long) : RunnerState

    data class Failed(val reasonCode: String) : RunnerState
}

/**
 * Owns exactly one [Engine] for one model file and keeps it warm across
 * calls, rather than the pre-Sprint-8 [LiteRtLmSession] behaviour of
 * building and closing a fresh [Engine] — a full model load — on every
 * single call.
 *
 * WRITTEN AGAINST REAL APIs, VERIFIED ON NOTHING, the same discipline every
 * class in `app/.../drafting/` already carries (CLEANUP.md CL-18/CL-36):
 * this cannot be exercised against a real `.litertlm` file or a real device
 * in the environment that wrote it.
 *
 * **Correctness this fixes (CLEANUP.md CL-18's runtime item):**
 * - *Engine reuse.* [engine] is rebuilt only when [modelFile]'s own identity
 *   ([FileKey]: path, length, `lastModified()`) changes since the last call —
 *   which is exactly the moment [ModelDownloader]'s atomic rename could have
 *   swapped it. A fresh [com.google.ai.edge.litertlm.Conversation] is still
 *   created per call, so no two drafts ever share conversational context.
 * - *Safe completion.* [generate] deliberately uses the blocking
 *   `sendMessage` API.  LiteRT-LM 0.16.1's async callback path calls a
 *   Kotlin synthetic (`SendChannel.close$default`) that is absent from the
 *   coroutines ABI shipped by this app; on a real device that killed the
 *   process exactly when a generation finished.  The synchronous API is the
 *   supported path that completes without crossing that broken callback ABI.
 *   Cancellation still calls `cancelProcess()` when the native call reports
 *   cancellation; the higher-level drafter timeout remains an honest
 *   timeout, never a fabricated model result.
 * - *Real token counts.* [InferenceReport.estimatedTokens] comes from
 *   `Conversation.getBenchmarkInfo()`'s own prefill+decode counts
 *   ([TokenCountSource.MEASURED]) when the runtime provides one, falling
 *   back to the whitespace-split guess only when it doesn't.
 * - *One engine load at a time.* [loadGate] is shared by every
 *   [LocalModelRunner] the process constructs (the authoring model and the
 *   developer-surface model are two separate files, two separate runners),
 *   so two multi-hundred-MB loads never race each other for memory.
 */
class LocalModelRunner(
    private val context: Context,
    private val modelFile: File,
    private val supportedNpuSocModels: Set<String> = LiteRtLmSession.DEFAULT_NPU_SOC_MODELS,
    private val loadGate: Mutex = sharedLoadGate,
) {
    private val _state = MutableStateFlow<RunnerState>(RunnerState.Cold)
    val state: StateFlow<RunnerState> = _state

    /** Serializes every load and generate call against this one engine — one caller at a time, authoring or external. */
    private val callMutex = Mutex()

    private var engine: Engine? = null
    private var engineTier: InferenceBackend? = null
    private var engineKey: FileKey? = null
    private var engineLoadMs: Long = 0

    private data class FileKey(val path: String, val length: Long, val lastModifiedMillis: Long)

    private fun currentKey(): FileKey? =
        if (modelFile.isFile) FileKey(modelFile.path, modelFile.length(), modelFile.lastModified()) else null

    /** Loads the engine now, if it isn't already warm for the model file's current identity — otherwise a no-op. Lets the UI show "loading" before the user's first draft rather than folding load time into it. */
    suspend fun warm(): Result<InferenceBackend> = callMutex.withLock {
        try {
            Result.success(ensureEngineLocked())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = RunnerState.Failed(e.message ?: e::class.simpleName ?: "load failed")
            Result.failure(e)
        }
    }

    /** Closes the engine, if one is open. Called on toggle-off, model remove/swap, and low-memory — never leaves a stale engine holding native memory for a model that no longer applies. */
    suspend fun release() {
        callMutex.withLock {
            engine?.close()
            engine = null
            engineTier = null
            engineKey = null
            _state.value = RunnerState.Cold
        }
    }

    suspend fun generate(prompt: String): InferenceOutput = callMutex.withLock {
        val tier = ensureEngineLocked()
        val eng = checkNotNull(engine) { "ensureEngineLocked() did not leave an engine in place." }
        val loadMsForTier = engineLoadMs
        _state.value = RunnerState.Generating(tier, System.currentTimeMillis())
        try {
            withContext(Dispatchers.IO) {
                val conversation = eng.createConversation()
                try {
                    val genStart = System.nanoTime()
                    val response = try {
                        // Do not switch this back to sendMessageAsync until
                        // the library fixes its SendChannel.close$default ABI
                        // mismatch (see CLEANUP.md CL-18).  The async path
                        // crashes the whole app on the first real completion.
                        conversation.sendMessage(prompt)
                    } catch (e: CancellationException) {
                        conversation.cancelProcess()
                        throw e
                    }
                    val lastText = response.contents.contents
                        .filterIsInstance<Content.Text>()
                        .joinToString("") { it.text }
                    val genMs = ((System.nanoTime() - genStart) / 1_000_000).coerceAtLeast(1)
                    InferenceOutput(text = lastText, report = reportFor(tier, loadMsForTier, genMs, lastText, conversation))
                } finally {
                    conversation.close()
                }
            }
        } finally {
            _state.value = RunnerState.Warm(tier, loadMsForTier)
        }
    }

    @OptIn(com.google.ai.edge.litertlm.ExperimentalApi::class)
    private fun reportFor(
        tier: InferenceBackend,
        loadMs: Long,
        generationMs: Long,
        text: String,
        conversation: com.google.ai.edge.litertlm.Conversation,
    ): InferenceReport {
        // getBenchmarkInfo() is marked @ExperimentalApi by the library
        // itself — "may change or be removed without notice" — opted into
        // deliberately: it's the only documented read-back this API offers
        // for real token counts, and the pre-Sprint-8 whitespace-split
        // guess it replaces was never more reliable, only less honest about
        // being a guess.
        val benchmark = try {
            conversation.getBenchmarkInfo()
        } catch (e: Exception) {
            null
        }
        val measured = benchmark?.let(::readBenchmarkFields)
        return if (measured != null) {
            InferenceReport(
                backend = tier,
                loadMs = loadMs,
                generationMs = generationMs,
                estimatedTokens = (measured.prefillTokens + measured.decodeTokens).coerceAtLeast(1),
                tokenCountSource = TokenCountSource.MEASURED,
                timeToFirstTokenMs = (measured.timeToFirstTokenSeconds * 1_000).toLong(),
                prefillTokens = measured.prefillTokens,
                decodeTokens = measured.decodeTokens,
                decodeTokensPerSecond = measured.decodeTokensPerSecond,
            )
        } else {
            val tokens = text.trim().split(Regex("\\s+")).count { it.isNotBlank() }.coerceAtLeast(1)
            InferenceReport(backend = tier, loadMs = loadMs, generationMs = generationMs, estimatedTokens = tokens)
        }
    }

    private data class BenchmarkFields(
        val prefillTokens: Int,
        val decodeTokens: Int,
        val timeToFirstTokenSeconds: Double,
        val decodeTokensPerSecond: Double,
    )

    /**
     * Reads [com.google.ai.edge.litertlm.BenchmarkInfo]'s own getters via
     * reflection rather than a normal Kotlin call.
     *
     * The JVM bytecode is public and stable (confirmed with `javap`, see
     * `docs/API_VERIFICATION.md`) — the getters simply don't *compile*
     * against this project's Kotlin compiler, because the class carries
     * `kotlin.Metadata(mv=[2,3,0])` (Kotlin 2.3's binary metadata format),
     * newer than this project's pinned Kotlin compiler (2.2.21). Kotlin
     * treats a class whose metadata version it can't read as having no
     * resolvable members at all, even though the same members are ordinary
     * public JVM methods a Java caller — or reflection — reaches fine. See
     * CLEANUP.md CL-42.
     */
    private fun readBenchmarkFields(benchmark: Any): BenchmarkFields? = try {
        val cls = benchmark::class.java
        BenchmarkFields(
            prefillTokens = cls.getMethod("getLastPrefillTokenCount").invoke(benchmark) as Int,
            decodeTokens = cls.getMethod("getLastDecodeTokenCount").invoke(benchmark) as Int,
            timeToFirstTokenSeconds = cls.getMethod("getTimeToFirstTokenInSecond").invoke(benchmark) as Double,
            decodeTokensPerSecond = cls.getMethod("getLastDecodeTokensPerSecond").invoke(benchmark) as Double,
        )
    } catch (e: Exception) {
        null
    }

    /** Must be called under [callMutex]. Reuses [engine] when [modelFile]'s identity is unchanged; otherwise closes it and loads fresh, under [loadGate] so only one engine in this process loads at a time. */
    private suspend fun ensureEngineLocked(): InferenceBackend {
        val key = currentKey() ?: throw IllegalStateException("No side-loaded model at ${modelFile.path}.")
        engineTier?.let { warmTier ->
            if (engineKey == key) return warmTier
        }
        // The file changed underneath us (a swap, or this is the first
        // call) — the old engine, if any, no longer matches what's on disk.
        engine?.close()
        engine = null
        engineTier = null
        engineKey = null

        val tiers = buildList {
            if (npuSocMatches()) add(InferenceBackend.NPU)
            add(InferenceBackend.GPU)
            add(InferenceBackend.CPU)
        }

        var lastError: Throwable? = null
        for (tier in tiers) {
            _state.value = RunnerState.Loading(tier)
            try {
                loadGate.withLock {
                    val backend = when (tier) {
                        InferenceBackend.NPU -> Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)
                        InferenceBackend.GPU -> Backend.GPU()
                        InferenceBackend.CPU -> Backend.CPU()
                        InferenceBackend.CLOUD -> error("CLOUD is not a local inference tier.")
                    }
                    val config = EngineConfig(modelPath = key.path, backend = backend, cacheDir = context.cacheDir.path)
                    val loadStart = System.nanoTime()
                    val newEngine = Engine(config)
                    newEngine.initialize()
                    engineLoadMs = (System.nanoTime() - loadStart) / 1_000_000
                    engine = newEngine
                    engineTier = tier
                    engineKey = key
                }
                _state.value = RunnerState.Warm(tier, engineLoadMs)
                return tier
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Falls through to the next, less capable tier — every step
                // down is a fact about *this* load, never a tier this run
                // silently pretended to use.
                lastError = e
            }
        }
        val failure = lastError ?: IllegalStateException("No inference backend could load the model.")
        _state.value = RunnerState.Failed(failure.message ?: failure::class.simpleName ?: "load failed")
        throw failure
    }

    private fun npuSocMatches(): Boolean = LiteRtLmSession.npuSocEligible(supportedNpuSocModels)

    companion object {
        /**
         * Shared across every [LocalModelRunner] the process constructs by
         * default — the authoring model and the developer-surface model are
         * two different files, two different runners, but only one of them
         * should ever be mid-load at a time (a multi-hundred-MB model load
         * is exactly the moment two concurrent loads would compete hardest
         * for memory).
         */
        private val sharedLoadGate = Mutex()
    }
}
