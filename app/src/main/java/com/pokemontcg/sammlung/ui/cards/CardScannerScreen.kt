package com.pokemontcg.sammlung.ui.cards

import com.pokemontcg.sammlung.R
import androidx.compose.ui.res.stringResource

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@Composable
fun CardScannerScreen(
    onCardDetected: (detectedCardNumber: String?, rawText: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = remember(context) { context.findActivity() }
    var hasCameraPermission by remember { mutableStateOf(context.hasCameraPermission()) }
    var permissionDenied by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf(false) }
    var cameraRetryKey by remember { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        permissionDenied = !isGranted
    }

    val requestPermission = {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            requestPermission()
        }
    }

    // The user may grant or revoke the permission in the system settings while this screen
    // is paused. Re-check on every resume so the dialog and camera preview stay in sync.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = context.hasCameraPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!hasCameraPermission) {
        val permanentlyDenied = isScannerPermissionPermanentlyDenied(
            hasPermission = hasCameraPermission,
            permissionDenied = permissionDenied,
            hasActivity = activity != null,
            shouldShowRationale = activity != null &&
                ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
        )
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    stringResource(
                        if (permanentlyDenied) R.string.scanner_permission_denied_title
                        else R.string.scanner_permission_title
                    ),
                    modifier = Modifier.semantics { heading() }
                )
            },
            text = {
                Text(
                    stringResource(
                        if (permanentlyDenied) R.string.scanner_permission_denied_text
                        else R.string.scanner_permission_text
                    )
                )
            },
            confirmButton = {
                if (permanentlyDenied) {
                    TextButton(onClick = { context.openAppSettings() }) {
                        Text(stringResource(R.string.scanner_permission_settings))
                    }
                } else {
                    TextButton(onClick = requestPermission) {
                        Text(stringResource(R.string.scanner_permission_grant))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_abbrechen_07af7c)) }
            }
        )
        return
    }

    var detectedText by remember { mutableStateOf("") }
    val lastSubmittedFingerprint = remember { AtomicReference<String?>(null) }
    val stableNameFingerprint = remember { AtomicReference<String?>(null) }
    val stableNameCount = remember { AtomicInteger(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        key(cameraRetryKey) {
            CameraPreview(
                onTextRecognized = { rawText, cardNumberMatch ->
                    detectedText = rawText
                    if (!cardNumberMatch.isNullOrBlank()) {
                        val fingerprint = "number:${cardNumberMatch.uppercase()}"
                        if (lastSubmittedFingerprint.getAndSet(fingerprint) != fingerprint) {
                            stableNameFingerprint.set(null)
                            stableNameCount.set(0)
                            onCardDetected(cardNumberMatch, rawText)
                        }
                    } else {
                        val tokens = extractOcrSearchTokens(rawText, limit = 4)
                        val fingerprint = tokens.joinToString(" ")
                        if (tokens.isNotEmpty() && fingerprint.length >= 8) {
                            if (stableNameFingerprint.get() == fingerprint) {
                                stableNameCount.incrementAndGet()
                            } else {
                                stableNameFingerprint.set(fingerprint)
                                stableNameCount.set(1)
                            }
                            // Require repeated identical OCR text to avoid searching on every camera frame.
                            if (stableNameCount.get() >= 3) {
                                val submitted = "name:$fingerprint"
                                if (lastSubmittedFingerprint.getAndSet(submitted) != submitted) {
                                    onCardDetected(null, rawText)
                                }
                            }
                        } else {
                            stableNameFingerprint.set(null)
                            stableNameCount.set(0)
                        }
                    }
                },
                onCameraError = { cameraError = true }
            )
        }

        if (cameraError) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.scanner_camera_error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Button(onClick = {
                        cameraError = false
                        cameraRetryKey += 1
                    }) {
                        Text(stringResource(R.string.scanner_camera_retry))
                    }
                }
            }
        } else {
            // Overlay Box
            Box(
                modifier = Modifier
                    .size(300.dp, 420.dp)
                    .align(Alignment.Center)
                    .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.scanner_active),
                    modifier = Modifier.semantics { heading() },
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(detectedText.ifBlank { stringResource(R.string.scanner_placeholder) }, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onDismiss) {
                    Text(stringResource(R.string.scanner_btn_stop))
                }
            }
        }
    }
}

internal fun isScannerPermissionPermanentlyDenied(
    hasPermission: Boolean,
    permissionDenied: Boolean,
    hasActivity: Boolean,
    shouldShowRationale: Boolean
): Boolean =
    !hasPermission && permissionDenied && hasActivity && !shouldShowRationale

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null)
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@SuppressLint("UnsafeOptInUsageError")
@Composable
private fun CameraPreview(
    onTextRecognized: (rawText: String, cardNumberMatch: String?) -> Unit,
    onCameraError: () -> Unit
) {
    val currentOnCameraError by rememberUpdatedState(onCameraError)
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val disposed = remember { AtomicBoolean(false) }
    // ML Kit processing is asynchronous; keep at most one frame in flight.
    val frameGate = remember { ScannerFrameGate() }
    val cameraProviderRef = remember { AtomicReference<ProcessCameraProvider?>(null) }

    DisposableEffect(executor, recognizer) {
        disposed.set(false)
        onDispose {
            disposed.set(true)
            cameraProviderRef.get()?.unbindAll()
            executor.shutdownNow()
            recognizer.close()
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = try {
                ProcessCameraProvider.getInstance(ctx)
            } catch (_: Exception) {
                currentOnCameraError()
                return@AndroidView previewView
            }

            cameraProviderFuture.addListener({
                val cameraProvider = try {
                    cameraProviderFuture.get()
                } catch (_: Exception) {
                    currentOnCameraError()
                    return@addListener
                }
                cameraProviderRef.set(cameraProvider)
                if (disposed.get()) return@addListener

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(executor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage == null || disposed.get()) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    // CameraX's KEEP_ONLY_LATEST only limits queued frames while the analyzer
                    // is running. Since ML Kit completes asynchronously, explicitly allow one
                    // OCR task at a time and immediately release frames we cannot process.
                    if (!frameGate.tryAcquire()) {
                        imageProxy.close()
                        return@setAnalyzer
                    }

                    try {
                        val inputImage = InputImage.fromMediaImage(
                            mediaImage,
                            imageProxy.imageInfo.rotationDegrees
                        )

                        recognizer.process(inputImage)
                            .addOnSuccessListener { visionText ->
                                if (!disposed.get()) {
                                    val fullText = visionText.text
                                    val cardMatch = extractCardNumber(fullText)
                                    onTextRecognized(fullText, cardMatch)
                                }
                            }
                            .addOnCompleteListener {
                                frameGate.release()
                                imageProxy.close()
                            }
                    } catch (_: Exception) {
                        // Always release both the gate and camera frame on setup failures.
                        frameGate.release()
                        imageProxy.close()
                    }
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                try {
                    if (!disposed.get()) {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                    }
                } catch (_: Exception) {
                    imageAnalysis.clearAnalyzer()
                    currentOnCameraError()
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

