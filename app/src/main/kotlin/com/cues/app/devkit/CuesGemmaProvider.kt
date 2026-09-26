package com.cues.app.devkit

import android.content.ContentProvider
import android.content.ContentValues
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import com.cues.app.CuesApplication
import kotlinx.coroutines.runBlocking

/**
 * A call-only `ContentProvider` — the developer-facing local Gemma surface
 * (CLEANUP.md CL-38). Implements [call] only; every cursor/mutation method
 * below refuses, because this shares one function, not a table.
 *
 * `content://<applicationId>.gemma` is reachable from `adb shell content
 * call` with zero client code, and from any other installed app's
 * `ContentResolver` — chosen over a bound AIDL service specifically so the
 * zero-code `adb` proof this surface needs (docs/MEASUREMENTS.md's
 * dev-surface entry) never requires a second app to exist. See
 * [GemmaCallProtocol] for the wire contract and
 * [CuesApplication.generateForExternalCaller] for what actually runs a
 * request — this class does no drafting itself, only caller identification
 * and Bundle marshalling.
 *
 * `exported="true"` with no `<uses-permission>` gate (see AndroidManifest.xml):
 * any installed app can attempt a call, but it receives no completion unless
 * Cues is enabled *and* the user has approved that exact caller package.
 * The gate also rate-limits every approved caller and logs every outcome.
 * Consent is still in-memory for this prototype; a signing-certificate-bound,
 * persistent consent record is the remaining CL-38 hardening work.
 */
class CuesGemmaProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val app = context?.applicationContext as? CuesApplication
            ?: return Bundle().apply { putString(GemmaCallProtocol.KEY_ERROR, "Cues is not running.") }
        val callerPackage = callingPackage()

        return when (method) {
            GemmaCallProtocol.METHOD_MODEL_INFO -> Bundle().apply {
                putString(GemmaCallProtocol.KEY_MODEL_IDENTITY, app.externalModelIdentity())
            }
            GemmaCallProtocol.METHOD_GENERATE -> {
                val prompt = arg ?: extras?.getString(GemmaCallProtocol.KEY_PROMPT)
                if (prompt.isNullOrBlank()) {
                    Bundle().apply { putString(GemmaCallProtocol.KEY_ERROR, "No prompt given.") }
                } else {
                    runBlocking {
                        app.generateForExternalCaller(prompt, callerPackage, signingDigest(callerPackage)).fold(
                            onSuccess = { output ->
                                Bundle().apply {
                                    putString(GemmaCallProtocol.KEY_TEXT, output.text)
                                    putString(GemmaCallProtocol.KEY_BACKEND, output.report.backend.name)
                                    putInt(GemmaCallProtocol.KEY_ESTIMATED_TOKENS, output.report.estimatedTokens)
                                    putLong(GemmaCallProtocol.KEY_LATENCY_MS, output.report.loadMs + output.report.generationMs)
                                    putString(GemmaCallProtocol.KEY_MODEL_IDENTITY, app.externalModelIdentity())
                                }
                            },
                            onFailure = { e ->
                                Bundle().apply { putString(GemmaCallProtocol.KEY_ERROR, e.message ?: "Could not generate a response.") }
                            },
                        )
                    }
                }
            }
            else -> Bundle().apply { putString(GemmaCallProtocol.KEY_ERROR, "Unknown method: $method") }
        }
    }

    /**
     * [Binder.getCallingUid] resolved back to a package name — the same
     * primitive an AIDL service's `onTransact` would read, just without one.
     * Falls back to "unknown" rather than throwing: a caller Cues cannot
     * identify is still logged, never silently ignored.
     */
    private fun callingPackage(): String {
        val ctx = context ?: return "unknown"
        val uid = Binder.getCallingUid()
        val names = ctx.packageManager.getPackagesForUid(uid)
        return names?.firstOrNull() ?: "unknown"
    }

    private fun signingDigest(packageName: String): String = try {
        val ctx = context ?: return "unavailable"
        val info = ctx.packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
        val bytes = info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray() ?: return "unavailable"
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    } catch (_: Exception) { "unavailable" }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ) = throw UnsupportedOperationException("CuesGemmaProvider only supports call().")

    override fun getType(uri: Uri): String? =
        throw UnsupportedOperationException("CuesGemmaProvider only supports call().")

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("CuesGemmaProvider only supports call().")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("CuesGemmaProvider only supports call().")

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("CuesGemmaProvider only supports call().")
}
