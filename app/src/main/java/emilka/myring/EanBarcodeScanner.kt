package emilka.myring

import android.annotation.SuppressLint
import android.hardware.camera2.CaptureRequest
import android.util.Size
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.ZoomSuggestionOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Skaner EAN — CameraX + ML Kit (tylko EAN-8/13), wg zaleceń Google. */
@OptIn(ExperimentalCamera2Interop::class)
@Composable
fun EanBarcodeScannerBox(
    modifier: Modifier = Modifier,
    previewHeight: androidx.compose.ui.unit.Dp = 220.dp,
    onScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val cameraRef = remember { AtomicReference<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    val zoomState = remember { AtomicReference(1f) }

    val barcodeScanner = remember {
        buildEanScanner { ratio ->
            val cam = cameraRef.get() ?: return@buildEanScanner false
            val state = cam.cameraInfo.zoomState.value
            val clamped = ratio.coerceIn(state?.minZoomRatio ?: 1f, state?.maxZoomRatio ?: 6f)
            cam.cameraControl.setZoomRatio(clamped)
            zoomState.set(clamped)
            true
        }
    }

    DisposableEffect(Unit) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val resolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(1280, 720),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
                .build()

            val preview = Preview.Builder()
                .setResolutionSelector(resolutionSelector)
                .also { configureBarcodeCamera2(it) }
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val imageAnalysis = ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .also { configureBarcodeCamera2(it) }
                .build()

            val analyzer = EanMlKitAnalyzer(barcodeScanner, onScanned)
            imageAnalysis.setAnalyzer(executor, analyzer)

            val pinchZoom = ScaleGestureDetector(
                context,
                object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                        val cam = cameraRef.get() ?: return false
                        val state = cam.cameraInfo.zoomState.value ?: return false
                        val newRatio = (zoomState.get() * detector.scaleFactor)
                            .coerceIn(state.minZoomRatio, state.maxZoomRatio)
                        cam.cameraControl.setZoomRatio(newRatio)
                        zoomState.set(newRatio)
                        return true
                    }
                }
            )

            @SuppressLint("ClickableViewAccessibility")
            previewView.setOnTouchListener { view, event ->
                pinchZoom.onTouchEvent(event)
                val cam = cameraRef.get()
                if (cam != null && event.action == MotionEvent.ACTION_DOWN) {
                    val factory = previewView.meteringPointFactory
                    val point = factory.createPoint(event.x, event.y)
                    val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                        .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    cam.cameraControl.startFocusAndMetering(action)
                }
                view.performClick()
                true
            }

            try {
                provider.unbindAll()
                val bound = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
                cameraRef.set(bound)
                zoomState.set(bound.cameraInfo.zoomState.value?.zoomRatio ?: 1f)
            } catch (_: Exception) { }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            runCatching { barcodeScanner.close() }
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            executor.shutdown()
        }
    }

    LaunchedEffect(torchOn) {
        cameraRef.get()?.cameraControl?.enableTorch(torchOn)
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(previewHeight)
                .clip(RoundedCornerShape(16.dp))
        ) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width * 0.78f
                val h = size.height * 0.36f
                val left = (size.width - w) / 2f
                val top = (size.height - h) / 2f
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.9f),
                    topLeft = androidx.compose.ui.geometry.Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(w, h),
                    cornerRadius = CornerRadius(10f, 10f),
                    style = Stroke(width = 2.5f)
                )
            }
            FilledIconButton(
                onClick = { torchOn = !torchOn },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(40.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.45f),
                    contentColor = Color.White
                )
            ) {
                Icon(
                    if (torchOn) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                    contentDescription = if (torchOn) "Wyłącz lampę" else "Włącz lampę"
                )
            }
        }
        Text(
            "Ustaw kod w ramce · tap = ostrość · szczyp = zoom",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@ExperimentalCamera2Interop
private fun configureBarcodeCamera2(builder: Preview.Builder) {
    Camera2Interop.Extender(builder)
        .setCaptureRequestOption(
            CaptureRequest.CONTROL_AF_MODE,
            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
        )
        .setCaptureRequestOption(
            CaptureRequest.CONTROL_AE_MODE,
            CaptureRequest.CONTROL_AE_MODE_ON
        )
        .setCaptureRequestOption(
            CaptureRequest.CONTROL_AE_ANTIBANDING_MODE,
            CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO
        )
}

@ExperimentalCamera2Interop
private fun configureBarcodeCamera2(builder: ImageAnalysis.Builder) {
    Camera2Interop.Extender(builder)
        .setCaptureRequestOption(
            CaptureRequest.CONTROL_AF_MODE,
            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
        )
        .setCaptureRequestOption(
            CaptureRequest.CONTROL_AE_MODE,
            CaptureRequest.CONTROL_AE_MODE_ON
        )
}

private fun buildEanScanner(onZoomSuggested: (Float) -> Boolean): BarcodeScanner {
    val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8)
        .setZoomSuggestionOptions(
            ZoomSuggestionOptions.Builder(onZoomSuggested)
                .setMaxSupportedZoomRatio(6f)
                .build()
        )
        .build()
    return BarcodeScanning.getClient(options)
}

private class EanMlKitAnalyzer(
    private val scanner: BarcodeScanner,
    private val onScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val busy = AtomicBoolean(false)
    private var lastCandidate: String? = null
    private var stableHits = 0
    private val delivered = AtomicBoolean(false)

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        if (delivered.get() || !busy.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        val media = imageProxy.image
        if (media == null) {
            busy.set(false)
            imageProxy.close()
            return
        }
        val input = InputImage.fromMediaImage(media, imageProxy.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                if (delivered.get()) return@addOnSuccessListener
                val code = barcodes.firstOrNull {
                    val f = it.format
                    (f == Barcode.FORMAT_EAN_13 || f == Barcode.FORMAT_EAN_8) && !it.rawValue.isNullOrBlank()
                }?.rawValue?.trim()
                if (code != null && isValidEan(code)) {
                    if (code == lastCandidate) {
                        stableHits++
                    } else {
                        lastCandidate = code
                        stableHits = 1
                    }
                    if (stableHits >= 2 && delivered.compareAndSet(false, true)) {
                        onScanned(code)
                        lastCandidate = null
                        stableHits = 0
                    }
                } else {
                    lastCandidate = null
                    stableHits = 0
                }
            }
            .addOnCompleteListener {
                busy.set(false)
                imageProxy.close()
            }
    }

    private fun isValidEan(value: String): Boolean = EanValidator.isValid(value)
}
