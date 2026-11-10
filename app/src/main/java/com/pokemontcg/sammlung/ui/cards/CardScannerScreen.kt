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
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@Composable
fun CardScannerScreen(
    onCardDetected: (detectedCardNumber: String?, rawText: String) -> Unit,
    onDismiss: () -> Unit,
    batchMode: Boolean = false,
    onBatchModeChange: (Boolean) -> Unit = {},
    batchCount: Int = 0,
    lastBatchName: String? = null,
    onFinishBatch: () -> Unit = {}
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
    // Confirms a fingerprint over consecutive frames before it triggers a catalog search.
    val submitGate = remember { ScannerSubmitGate() }
    var torchOn by remember { mutableStateOf(false) }
    var zoomRatio by remember { mutableStateOf(1f) }
    // ROI mode (region of interest): only the area inside the guide frame is OCR-read.
    // Less surrounding card text means fewer false matches on the number line.
    var roiMode by rememberSaveable { mutableStateOf(false) }
    val density = LocalDensity.current
    // The analyzer crop needs the guide frame (300x420dp) in bitmap-view pixels.
    val roiFrameWidthPx = with(density) { 300.dp.toPx() }.roundToInt()
    val roiFrameHeightPx = with(density) { 420.dp.toPx() }.roundToInt()

    Box(modifier = Modifier.fillMaxSize()) {
        key(cameraRetryKey) {
            CameraPreview(
                onTextRecognized = { rawText, cardNumberMatch ->
                    detectedText = rawText
                    // A readable number is a strong signal and is confirmed over two
                    // frames; the fuzzy name fallback is noisier and needs three
                    // identical frames in a row before it triggers a catalog search.
                    if (!cardNumberMatch.isNullOrBlank()) {
                        if (submitGate.submitNumber(cardNumberMatch)) {
                            onCardDetected(cardNumberMatch, rawText)
                        }
                    } else if (submitGate.submitNameTokens(extractOcrSearchTokens(rawText, limit = 4))) {
                        onCardDetected(null, rawText)
                    }
                },
                onCameraError = { cameraError = true },
                zoomRatio = zoomRatio,
                torchOn = torchOn,
                roiEnabled = roiMode,
                roiFrameWidthPx = roiFrameWidthPx,
                roiFrameHeightPx = roiFrameHeightPx
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
                        torchOn = false
                        zoomRatio = 1f
                        cameraRetryKey += 1
                    }) {
                        Text(stringResource(R.string.scanner_camera_retry))
                    }
                }
            }
        } else {
            // Overlay Box: in ROI mode the frame is not just a guide, it IS the scan area.
            Box(
                modifier = Modifier
                    .size(300.dp, 420.dp)
                    .align(Alignment.Center)
                    .border(
                        3.dp,
                        if (roiMode) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(16.dp)
                    )
            )

            // Kamera-Steuerung: Zoom bringt mehr Pixel auf die Karte, Licht hilft in
            // dunklen Raeumen. Digital-Zoom beschneidet den Sensor, bevor die Analyse-
            // Aufloesung skaliert wird - die Kartennummer gewinnt also echte Schärfe.
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                ) {
                    Column(
                        modifier = Modifier.padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // "+" and "−" as text: the icon set shipped with the app has no
                        // dedicated minus glyph (no material-icons-extended dependency).
                        val zoomInDescription = stringResource(R.string.scanner_zoom_in)
                        val zoomOutDescription = stringResource(R.string.scanner_zoom_out)
                        IconButton(
                            onClick = { if (zoomRatio < 4f) zoomRatio += 0.5f },
                            modifier = Modifier.semantics { contentDescription = zoomInDescription }
                        ) {
                            Text("+", style = MaterialTheme.typography.titleLarge)
                        }
                        Text(
                            String.format(Locale.US, "%.1fx", zoomRatio),
                            style = MaterialTheme.typography.labelSmall
                        )
                        IconButton(
                            onClick = { if (zoomRatio > 1f) zoomRatio -= 0.5f },
                            modifier = Modifier.semantics { contentDescription = zoomOutDescription }
                        ) {
                            Text("−", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                ) {
                    TextButton(onClick = {
                        torchOn = !torchOn
                    }) {
                        Text(
                            stringResource(if (torchOn) R.string.scanner_torch_off else R.string.scanner_torch_on),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
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
                Text(stringResource(R.string.scanner_hint_focus), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (roiMode) {
                    Text(stringResource(R.string.scanner_roi_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                }
                Spacer(Modifier.height(8.dp))
                // Both extra modes are combinable: ROI narrows WHAT is read,
                // batch collects multiple cards without leaving the camera.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = roiMode,
                        onClick = { roiMode = !roiMode },
                        label = { Text(stringResource(R.string.scanner_mode_roi)) }
                    )
                    FilterChip(
                        selected = batchMode,
                        onClick = { onBatchModeChange(!batchMode) },
                        label = { Text(stringResource(R.string.scanner_mode_batch)) }
                    )
                }
                if (batchMode) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.scanner_batch_count, batchCount),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    lastBatchName?.let { name ->
                        Text(
                            stringResource(R.string.scanner_batch_last_added, name),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onFinishBatch, enabled = batchCount > 0) {
                        Text(stringResource(R.string.scanner_batch_finish))
                    }
                } else {
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onDismiss) {
                        Text(stringResource(R.string.scanner_btn_stop))
                    }
                }
            }
        }
    }
}

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
    onCameraError: () -> Unit,
    zoomRatio: Float,
    torchOn: Boolean,
    roiEnabled: Boolean = false,
    roiFrameWidthPx: Int = 0,
    roiFrameHeightPx: Int = 0
) {
    val currentOnCameraError by rememberUpdatedState(onCameraError)
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    // Asian prints: the Latin model is blind to Han, Kana and Hangul, so the scanner
    // chains Japanese, Korean and Chinese models whenever the Latin pass alone cannot
    // identify the card (no number, or a number without any readable name text).
    val japaneseRecognizer = remember { TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build()) }
    val koreanRecognizer = remember { TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build()) }
    val chineseRecognizer = remember { TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()) }
    val disposed = remember { AtomicBoolean(false) }
    // ML Kit processing is asynchronous; keep at most one frame in flight.
    val frameGate = remember { ScannerFrameGate() }
    val cameraProviderRef = remember { AtomicReference<ProcessCameraProvider?>(null) }
    val previewViewRef = remember { AtomicReference<PreviewView?>(null) }
    val cameraRef = remember { AtomicReference<Camera?>(null) }
    // Short-lived marker where the user tapped to focus.
    var focusIndicator by remember { mutableStateOf<Offset?>(null) }
    // Rendered size of the preview; needed to map the on-screen ROI frame into the bitmap.
    val previewSizeState = remember { mutableStateOf(IntSize.Zero) }

    // Apply torch and zoom whenever the controls change or a new camera comes up.
    LaunchedEffect(torchOn) {
        cameraRef.get()?.cameraControl?.enableTorch(torchOn)
    }
    LaunchedEffect(zoomRatio) {
        cameraRef.get()?.cameraControl?.setZoomRatio(zoomRatio)
    }

    LaunchedEffect(focusIndicator) {
        if (focusIndicator != null) {
            delay(900)
            focusIndicator = null
        }
    }

    DisposableEffect(executor, recognizer) {
        disposed.set(false)
        onDispose {
            disposed.set(true)
            cameraProviderRef.get()?.unbindAll()
            executor.shutdownNow()
            recognizer.close()
            japaneseRecognizer.close()
            koreanRecognizer.close()
            chineseRecognizer.close()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            previewViewRef.set(previewView)
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

                // Analysefotos duerfen nicht auf ein Querformat festgenagelt werden:
                // Karten werden hochkant vor die Kamera gehalten, wodurch die OCR-Zeile mit der
                // Kartennummer nach einem Querformat-Downscale unlesbar wurde. Der
                // ResolutionSelector haelt die laengere Kante bei ~1920 px und laesst die
                // Ausrichtung offen: ML Kit erkennt die kleine Kartennummer deutlich
                // stabiler als bei den frueheren ~1280 px, und der Frame-Gate haelt die
                // Framerate trotzdem im Rahmen.
                val analysisResolution = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(1920, 1920),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()

                val imageAnalysis = ImageAnalysis.Builder()
                    .setResolutionSelector(analysisResolution)
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
                        // ROI mode: convert the frame to an upright bitmap and crop it to the
                        // on-screen guide frame before OCR, so only that region is searched.
                        val inputImage = if (roiEnabled) {
                            val bitmap = imageProxy.toBitmap()
                            val rotation = imageProxy.imageInfo.rotationDegrees
                            val upright = if (rotation != 0) {
                                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                            } else {
                                bitmap
                            }
                            val previewSize = previewSizeState.value
                            val crop = computeRoiCropRect(
                                frameWidthPx = roiFrameWidthPx,
                                frameHeightPx = roiFrameHeightPx,
                                viewWidthPx = previewSize.width,
                                viewHeightPx = previewSize.height,
                                bitmapWidth = upright.width,
                                bitmapHeight = upright.height
                            )
                            if (crop.width >= upright.width && crop.height >= upright.height) {
                                InputImage.fromBitmap(upright, 0)
                            } else {
                                InputImage.fromBitmap(
                                    Bitmap.createBitmap(upright, crop.left, crop.top, crop.width, crop.height),
                                    0
                                )
                            }
                        } else {
                            InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees
                            )
                        }

                        // Emits exactly once per frame and always releases the gate + frame.
                        val frameDone = AtomicBoolean(false)

                        // Zeilen nach Schriftgroesse sortieren: Der Kartenname
                        // ist das groesste Textelement, Regel-/Angriffstext und
                        // Fussnoten sind klein. Die Namenssuche nimmt die ersten
                        // Tokens - so liefern sie den Kartennamen statt Ballast.
                        fun sizeOrderedLines(text: com.google.mlkit.vision.text.Text): List<String> =
                            text.textBlocks
                                .asSequence()
                                .flatMap { it.lines.asSequence() }
                                .sortedByDescending { it.boundingBox?.height() ?: 0 }
                                .map { it.text }
                                .toList()

                        fun complete(primary: com.google.mlkit.vision.text.Text?, secondary: com.google.mlkit.vision.text.Text?) {
                            if (!frameDone.compareAndSet(false, true)) return
                            if (!disposed.get() && (primary != null || secondary != null)) {
                                val sizeOrderedText = (primary?.let(::sizeOrderedLines).orEmpty() +
                                        secondary?.let(::sizeOrderedLines).orEmpty())
                                    .joinToString("\n")
                                val rawText = listOfNotNull(primary?.text, secondary?.text)
                                    .joinToString("\n")
                                val cardMatch = extractCardNumber(rawText)
                                onTextRecognized(sizeOrderedText, cardMatch)
                            }
                            frameGate.release()
                            imageProxy.close()
                        }

                        // Asian script models run on the same frame when the Latin pass
                        // alone cannot identify the card. The first script model that
                        // reads a card number wins (it usually also reads the name);
                        // otherwise the CJK-richest text is merged with the Latin one so
                        // number and name clues are both preserved.
                        val scriptResults = mutableListOf<com.google.mlkit.vision.text.Text>()
                        fun finishScriptChain(latinText: com.google.mlkit.vision.text.Text?) {
                            val best = scriptResults
                                .filter { containsCjk(it.text) }
                                .maxWithOrNull(compareBy({ it.text.count(::isCjkChar) }, { it.text.length }))
                            complete(best, latinText)
                        }
                        fun runScriptChain(index: Int, latinText: com.google.mlkit.vision.text.Text?) {
                            val recognizers = listOf(japaneseRecognizer, koreanRecognizer, chineseRecognizer)
                            if (index >= recognizers.size) {
                                finishScriptChain(latinText)
                                return
                            }
                            recognizers[index].process(inputImage)
                                .addOnSuccessListener { scriptText ->
                                    if (extractCardNumber(scriptText.text) != null) {
                                        complete(scriptText, latinText)
                                    } else {
                                        scriptResults.add(scriptText)
                                        runScriptChain(index + 1, latinText)
                                    }
                                }
                                .addOnFailureListener { runScriptChain(index + 1, latinText) }
                        }

                        recognizer.process(inputImage)
                            .addOnSuccessListener { latinText ->
                                if (!needsScriptFallback(latinText.text)) {
                                    // Latin pass already identifies the card: use it directly.
                                    complete(latinText, null)
                                    return@addOnSuccessListener
                                }
                                // No usable identification from the Latin model: the frame may
                                // be a Japanese, Korean or Chinese print - or simply unreadable.
                                runScriptChain(0, latinText)
                            }
                            .addOnFailureListener { runScriptChain(0, null) }
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
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                        cameraRef.set(camera)
                        // Den aktuellen Steuerzustand sofort auf die neue Kamera anwenden,
                        // z. B. nach dem Neustart nach einem Kamerafehler.
                        camera.cameraControl.setZoomRatio(zoomRatio)
                        if (torchOn) {
                            camera.cameraControl.enableTorch(true)
                        }
                    }
                } catch (_: Exception) {
                    imageAnalysis.clearAnalyzer()
                    currentOnCameraError()
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates -> previewSizeState.value = coordinates.size }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val previewView = previewViewRef.get() ?: return@detectTapGestures
                    val camera = cameraRef.get() ?: return@detectTapGestures
                    // Die MeteringPointFactory der PreviewView rechnet Ansichts-Koordinaten
                    // selbst in Sensor-Koordinaten um (inkl. FILL_CENTER Skalierung).
                    val meteringPoint = previewView.meteringPointFactory
                        .createPoint(offset.x, offset.y)
                    runCatching {
                        camera.cameraControl.startFocusAndMetering(
                            FocusMeteringAction.Builder(
                                meteringPoint,
                                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                            ).build()
                        )
                    }
                    focusIndicator = offset
                }
            }
    )

        // Kurz sichtbarer Fokus-Ring an der Tipp-Position.
        focusIndicator?.let { offset ->
            Box(
                modifier = Modifier
                    .offset { IntOffset(offset.x.roundToInt() - 28, offset.y.roundToInt() - 28) }
                    .size(56.dp)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
    }
}

