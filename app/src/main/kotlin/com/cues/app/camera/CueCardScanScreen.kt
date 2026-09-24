package com.cues.app.camera

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cues.app.ui.cuesColors
import java.io.File
import kotlinx.coroutines.launch

/**
 * "Scan a Cue Card" — the receiving half of [com.cues.app.ui.CueCardShareScreen].
 *
 * Captures or picks one image, reads a QR code from it offline via
 * [CueCardScanner], and hands the raw payload text to [onScanned]. That text
 * is exactly as untrusted as any shared or typed text until
 * [com.cues.core.share.CueCards.decode] checks its digest — nothing here
 * decodes the card itself or resolves it against this phone's devices,
 * places or contexts.
 *
 * WRITTEN AGAINST REAL CameraX/ML Kit APIs, VERIFIED ON NOTHING — see
 * CLEANUP.md CL-21, the same disclosure `TimetableCaptureScreen` already
 * carries.
 */
@Composable
fun CueCardScanScreen(onScanned: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCameraPermission = granted }

    var isProcessing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    fun handle(uri: Uri) {
        isProcessing = true
        scope.launch {
            runCatching { CueCardScanner.scan(context, uri) }
                .onSuccess { payload ->
                    isProcessing = false
                    if (payload != null) onScanned(payload) else message = "No QR code was found in that image."
                }
                .onFailure {
                    isProcessing = false
                    message = "Could not read that image. ${it.message.orEmpty()}"
                }
        }
    }

    val galleryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? -> uri?.let(::handle) }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (hasCameraPermission) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val providerFuture = ProcessCameraProvider.getInstance(ctx)
                        providerFuture.addListener({
                            val provider = providerFuture.get()
                            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                            val capture = ImageCapture.Builder().build()
                            provider.unbindAll()
                            try {
                                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                                imageCapture = capture
                            } catch (e: Exception) {
                                message = "Could not start the camera: ${e.message}"
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                )
            } else {
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                    Text(
                        "Camera access lets Cues scan a shared Cue Card, on this phone only.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cuesColors.ink200,
                    )
                    Button(
                        onClick = { cameraPermission.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    ) { Text("Allow camera") }
                }
            }
            if (isProcessing) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }

        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp)) }

        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Button(
                onClick = {
                    val capture = imageCapture ?: return@Button
                    val target = File(context.cacheDir, "cuecard-scan-${System.currentTimeMillis()}.jpg")
                    val output = ImageCapture.OutputFileOptions.Builder(target).build()
                    isProcessing = true
                    capture.takePicture(
                        output,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                                handle(Uri.fromFile(target))
                            }

                            override fun onError(exception: ImageCaptureException) {
                                isProcessing = false
                                message = "The photo could not be taken: ${exception.message}"
                            }
                        },
                    )
                },
                enabled = hasCameraPermission && imageCapture != null && !isProcessing,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Capture") }

            OutlinedButton(
                onClick = { galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = !isProcessing,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text("Choose an existing photo") }

            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Cancel") }
        }
    }

    DisposableEffect(Unit) {
        onDispose { runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() } }
    }
}
