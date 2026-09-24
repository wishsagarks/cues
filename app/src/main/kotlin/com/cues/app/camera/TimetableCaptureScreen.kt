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
 * "Point at your timetable" — the FDD's Shared timetable roadmap item.
 *
 * Captures one photo (or lets the user pick an existing one — the same
 * offline path a Snap Note or Gallery share would take), runs it through the
 * bundled, offline [TimetableOcr], and hands the raw recognized text to
 * [onRecognized]. Nothing here extracts a cue, drafts one, or arms one — the
 * text that comes back is exactly as untrusted as any other shared or typed
 * text, and [com.cues.core.imports.TimetableExtractor] is what turns it into
 * proposals, one screen further on.
 *
 * WRITTEN AGAINST REAL CameraX/ML Kit APIs, VERIFIED ON NOTHING — the same
 * discipline `AndroidActionExecutor` already carries; this cannot be
 * exercised against a live camera in the environment that wrote it. See
 * CLEANUP.md CL-20.
 */
@Composable
fun TimetableCaptureScreen(
    onRecognized: (String) -> Unit,
    onBack: () -> Unit,
) {
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

    val galleryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        isProcessing = true
        scope.launch {
            runCatching { TimetableOcr.recognize(context, uri) }
                .onSuccess { text ->
                    isProcessing = false
                    onRecognized(text)
                }
                .onFailure {
                    isProcessing = false
                    message = "Could not read text from that image. ${it.message.orEmpty()}"
                }
        }
    }

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
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }
                            val capture = ImageCapture.Builder().build()
                            provider.unbindAll()
                            try {
                                provider.bindToLifecycle(
                                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture,
                                )
                                imageCapture = capture
                            } catch (e: Exception) {
                                message = "Could not start the camera: ${e.message}"
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                )
            } else {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Text(
                        "Camera access lets Cues read a photographed timetable, on this phone only.",
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
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
        }

        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Button(
                onClick = {
                    val capture = imageCapture ?: return@Button
                    val target = File(context.cacheDir, "timetable-${System.currentTimeMillis()}.jpg")
                    val output = ImageCapture.OutputFileOptions.Builder(target).build()
                    isProcessing = true
                    capture.takePicture(
                        output,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                                scope.launch {
                                    runCatching { TimetableOcr.recognize(context, Uri.fromFile(target)) }
                                        .onSuccess { text ->
                                            isProcessing = false
                                            onRecognized(text)
                                        }
                                        .onFailure {
                                            isProcessing = false
                                            message = "Could not read text from that photo. ${it.message.orEmpty()}"
                                        }
                                }
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
                onClick = {
                    galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                enabled = !isProcessing,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text("Choose an existing photo") }

            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Cancel")
            }
        }
    }

    // Releases the camera the moment this screen leaves composition — a
    // "point your camera" feature that kept the sensor bound after the user
    // navigated away would be exactly the always-on surveillance surface the
    // FDD rules out elsewhere.
    DisposableEffect(Unit) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        }
    }
}
