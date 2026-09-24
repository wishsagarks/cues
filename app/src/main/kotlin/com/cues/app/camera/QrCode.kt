package com.cues.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Renders a Cue Card's encoded text as a QR bitmap.
 *
 * `com.google.zxing:core` is pure Java with no Android or network dependency
 * at all — this never leaves the device. What goes into the code is exactly
 * [com.cues.core.share.CueCards.encode]'s output; nothing here re-encodes or
 * re-interprets the card.
 */
object QrCode {
    fun encode(text: String, sizePx: Int = 900): Bitmap {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }
}

/**
 * Reads a QR code back from a captured or picked image, offline, via the
 * bundled ML Kit barcode scanner. The returned text is exactly the QR
 * payload — untrusted until [com.cues.core.share.CueCards.decode] checks its
 * digest; nothing here interprets it.
 */
object CueCardScanner {
    private val scanner by lazy { BarcodeScanning.getClient() }

    suspend fun scan(context: Context, imageUri: Uri): String? =
        suspendCancellableCoroutine { continuation ->
            val image = try {
                InputImage.fromFilePath(context, imageUri)
            } catch (e: Exception) {
                continuation.resumeWithException(e)
                return@suspendCancellableCoroutine
            }
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    val qr = barcodes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }
                    continuation.resume(qr?.rawValue)
                }
                .addOnFailureListener { e -> continuation.resumeWithException(e) }
        }
}
