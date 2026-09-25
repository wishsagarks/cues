package com.cues.app.drafting

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.cues.core.inference.ModelProvisionState
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Downloads and installs the on-device model the "Cues Brain" tile offers —
 * pure I/O, no decision content, so (like [LiteRtLmSession]) it stays
 * entirely in `:app`; nothing in `:core`'s drafting logic needs to know a
 * download is in progress, only whether [LlmSession.generate] can succeed
 * right now.
 *
 * Targets exactly the path `CuesApplication.modelFile` and [LiteRtLmSession]
 * already expect (`filesDir/models/model.litertlm`), so neither needs to
 * change: [LiteRtLmSession.generate] re-checks that file on every call, so
 * an install here is picked up automatically on the very next draft — no
 * restart, no re-wiring, exactly like a manual `adb push` already works.
 *
 * Wi-Fi-gated with no cellular override: a Gemma3 `.litertlm` asset is
 * plausibly hundreds of MB, unlike Sarvam's small per-call payloads — the
 * exact scenario CLEANUP.md CL-35's "no network except an explicit, opt-in
 * call" posture was protecting an event venue's cellular data from.
 */
class ModelDownloader(
    private val context: Context,
    private val targetFile: File,
    private val sourceUrl: String,
    private val expectedSha256: String,
) {
    /** Cheap: reads only file presence/size, never re-hashes an already-installed file. */
    fun currentState(): ModelProvisionState =
        if (targetFile.isFile) ModelProvisionState.Installed(sizeBytes = targetFile.length(), sha256 = null)
        else ModelProvisionState.NotInstalled

    fun canDownload(): Boolean = sourceUrl.isNotBlank() && expectedSha256.isNotBlank()

    fun isOnWifi(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /**
     * Streams into a same-directory temp file, verifies its SHA-256 while
     * streaming, then atomically renames into place — an interrupted or
     * corrupt download can never be mistaken for an installed model, and
     * [targetFile] only ever holds a file this method already checked.
     */
    suspend fun download(onProgress: (bytesDownloaded: Long, totalBytes: Long?) -> Unit): ModelProvisionState =
        withContext(Dispatchers.IO) {
            if (!canDownload()) {
                return@withContext ModelProvisionState.Failed("Not configured — no model source is set.", retryable = false)
            }
            if (!isOnWifi()) {
                return@withContext ModelProvisionState.Failed("Connect to Wi-Fi to download the model.", retryable = true)
            }

            val partFile = File(targetFile.parentFile, "${targetFile.name}.part")
            targetFile.parentFile?.mkdirs()

            var connection: HttpURLConnection? = null
            try {
                connection = (URL(sourceUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                }
                if (connection.responseCode !in 200..299) {
                    return@withContext ModelProvisionState.Failed("Download failed: HTTP ${connection.responseCode}", retryable = true)
                }
                val totalBytes = connection.contentLengthLong.takeIf { it > 0 }
                val digest = MessageDigest.getInstance("SHA-256")
                var bytesDownloaded = 0L

                connection.inputStream.use { input ->
                    partFile.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            // A tapped "Cancel" cancels this coroutine's Job;
                            // checking here (rather than only at the next
                            // suspend point, since the reads/writes below are
                            // plain blocking I/O, not suspending) is what
                            // makes that cancellation actually stop the loop
                            // instead of running to completion regardless.
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            bytesDownloaded += read
                            onProgress(bytesDownloaded, totalBytes)
                        }
                    }
                }

                val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                    return@withContext ModelProvisionState.Failed(
                        "Checksum did not match the pinned model — discarded, nothing installed.", retryable = true,
                    )
                }

                Files.move(partFile.toPath(), targetFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                ModelProvisionState.Installed(sizeBytes = targetFile.length(), sha256 = actualSha256)
            } catch (e: IOException) {
                ModelProvisionState.Failed(e.message ?: "Download failed.", retryable = true)
            } finally {
                connection?.disconnect()
                // Covers every non-success exit above, including a cancelled
                // download: a half-written `.part` file must never be
                // mistaken for an install. A no-op once the move above has
                // already renamed it away.
                partFile.delete()
            }
        }

    /** Frees the space back up; [LiteRtLmSession] falls back to the parser on the very next call. */
    fun remove() {
        targetFile.delete()
    }
}
