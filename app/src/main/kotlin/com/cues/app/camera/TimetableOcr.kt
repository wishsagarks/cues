package com.cues.app.camera

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Offline text recognition over a captured or picked image.
 *
 * `com.google.mlkit:text-recognition` — the bundled variant, whose model
 * ships inside the APK. This call makes no network request; the
 * play-services-hosted variant this project deliberately does not depend on
 * is the one that would. See docs/API_VERIFICATION.md.
 *
 * The recovered text is exactly what came off the image, handed back as a
 * plain string — the caller treats it as untrusted shared/scanned data, the
 * same category [com.cues.core.imports.TimetableExtractor]'s own doc comment
 * describes; nothing here interprets it as an instruction.
 */
object TimetableOcr {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun recognize(context: Context, imageUri: Uri): String =
        suspendCancellableCoroutine { continuation ->
            val image = try {
                InputImage.fromFilePath(context, imageUri)
            } catch (e: Exception) {
                continuation.resumeWithException(e)
                return@suspendCancellableCoroutine
            }
            recognizer.process(image)
                .addOnSuccessListener { visionText -> continuation.resume(visionText.text) }
                .addOnFailureListener { e -> continuation.resumeWithException(e) }
        }
}
