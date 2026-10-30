package emilka.myring

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

/** Panel "zapas krążków" — wysuwany z lewej po przeciągnięciu palcem w prawo. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun StockSheet(context: Context, onBack: () -> Unit = {}, onStockChanged: () -> Unit = {}) {
    var packages by remember { mutableStateOf(StockStore.load(context)) }
    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<RingPackage?>(null) }
    val total = packages.sumOf { it.remaining }

    // Odśwież listę po każdej zmianie zapasów (dodawanie / zużycie krążka).
    fun refresh() {
        packages = StockStore.load(context)
        onStockChanged()
    }

    Surface(
        Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (kotlin.math.abs(dragAmount) < 50f) return@detectHorizontalDragGestures
                    if (dragAmount < 0) onBack()   // poziome gest w lewo → wróć do okręgu
                }
            },
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Zapas krążków",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
            }
            Text("Pozostało łącznie: $total",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary)
            // Do kiedy starczą krążki (zakładając stały cykl: weeks*7 dni + 7 dni przerwy).
            if (total > 0) {
                val weeks = SettingsStore.loadWeeks(context) ?: 3
                val daysPerCycle = weeks * 7L + 7L
                val endDate = LocalDate.now().plusDays(total * daysPerCycle)
                Text("Krążki starczą do: ${endDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))

            if (packages.isEmpty()) {
                Text("Brak opakowań. Dodaj pierwsze — zeskanuj kod EAN z opakowania.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 260.dp)) {
                    items(packages) { p ->
                        Surface(
                            Modifier
                                .fillMaxWidth()
                                .combinedClickable(onClick = {}, onLongClick = { pendingDelete = p }),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        ) {
                            Row(Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text(p.label, fontWeight = FontWeight.SemiBold)
                                    Text("EAN: ${StockStore.baseEan(p.ean)}", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("zost. ${p.remaining}/${p.total}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { showAdd = true },
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Text("＋ Dodaj nowe opakowanie", fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showAdd) {
        AddPackageDialog(
            context = context,
            isFirst = packages.isEmpty(),
            onAdded = { pkg, useForCurrent ->
                if (useForCurrent && pkg.remaining > 0) StockStore.useRingByBase(context, pkg.ean)
                refresh()
                showAdd = false
            },
            onDismiss = { showAdd = false }
        )
    }

    // Długie przytrzymanie na opakowaniu → potwierdzenie usunięcia.
    pendingDelete?.let { pkg ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Usunąć opakowanie?") },
            text = { Text("Czy usunąć \u0022${pkg.label}\u0022 (${StockStore.baseEan(pkg.ean)}) z zapasów?") },
            confirmButton = {
                TextButton(onClick = {
                    StockStore.removePackage(context, pkg.ean)
                    pendingDelete = null
                    refresh()
                }) { Text("Usuń", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Anuluj") }
            }
        )
    }
}

/** Dialog dodawania opakowania: skan EAN (lub wpis ręczny) + liczba krążków (+ "użyj 1 na obecny cykl"). */
@Composable
fun AddPackageDialog(
    context: Context,
    isFirst: Boolean,
    onAdded: (RingPackage, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var scanMode by remember { mutableStateOf(true) }
    var manualEan by remember { mutableStateOf("") }
    var manualCount by remember { mutableStateOf("") }
    var scannedEan by remember { mutableStateOf<String?>(null) }
    var useForCurrent by remember { mutableStateOf(isFirst) }
    var error by remember { mutableStateOf<String?>(null) }

    // Kamera wymaga uprawnienia.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) error = "Brak dostępu do kamery"
    }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun finishAdd() {
        val ean = scannedEan ?: manualEan.trim()
        val count = manualCount.toIntOrNull()
        if (ean.isEmpty()) { error = "Wpisz lub zeskanuj EAN"; return }
        if (count == null || count <= 0) { error = "Podaj liczbę krążków w opakowaniu"; return }
        val pkg = RingPackage(ean, "Opakowanie (${ean.takeLast(3)})", count, 0)
        StockStore.addPackage(context, ean, pkg.label, count)
        onAdded(pkg, useForCurrent)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dodaj opakowanie") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Przełącznik: skan / ręcznie
                Row {
                    FilterChip(selected = scanMode, onClick = { scanMode = true }, label = { Text("Skanuj") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = !scanMode, onClick = { scanMode = false }, label = { Text("Wpisz ręcznie") })
                }

                if (scanMode) {
                    BarcodeScannerBox(onScanned = { ean ->
                        scannedEan = ean
                        error = null
                        // Znany EAN (nawet ze zużytego opakowania) → podpowiedz liczbę krążków.
                        val known = StockStore.knownTotal(context, ean)
                        if (known > 0) {
                            manualCount = known.toString()
                            useForCurrent = false
                        }
                    })
                    if (scannedEan != null) {
                        Surface(
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text("✓ Zeskanowano EAN: ${scannedEan}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(12.dp))
                        }
                    }
                } else {
                    OutlinedTextField(value = manualEan, onValueChange = { manualEan = it },
                        label = { Text("Kod EAN") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }

                OutlinedTextField(value = manualCount, onValueChange = { manualCount = it },
                    label = { Text("Liczba krążków w opakowaniu") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth())

                // Pytanie o wykorzystanie jednego krążka na obecny cykl (zawsze widoczne).
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = useForCurrent, onCheckedChange = { useForCurrent = it })
                    Text("Użyj 1 krążek na obecny cykl", style = MaterialTheme.typography.bodyMedium)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = ::finishAdd) { Text("Dodaj") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
    )
}

/** Podgląd kamery + rozpoznawanie EAN przez ML Kit. */
@Composable
fun BarcodeScannerBox(onScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    var lastRead by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val cameraProvider = providerFuture.get()
            val previewBuilder = Preview.Builder()
            // Ciągły autofokus — lepsze i szybsze odczyty kodów EAN.
            try {
                androidx.camera.camera2.interop.Camera2Interop.Extender(previewBuilder)
                    .setCaptureRequestOption(
                        android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE,
                        android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                    )
            } catch (_: Exception) {}
            val preview = previewBuilder.build()
            preview.setSurfaceProvider(previewView.surfaceProvider)
            val analyzer = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analyzer.setAnalyzer(executor) { imageProxy ->
                val media = imageProxy.image
                if (media != null) {
                    val input = InputImage.fromMediaImage(media, imageProxy.imageInfo.rotationDegrees)
                    BarcodeScanning.getClient().process(input)
                        .addOnSuccessListener { barcodes ->
                            val code = barcodes.firstOrNull { it.format == Barcode.FORMAT_EAN_13 || it.format == Barcode.FORMAT_EAN_8 }
                                ?.rawValue
                            if (code != null && code != lastRead) {
                                lastRead = code
                                onScanned(code)
                            }
                        }
                        .addOnCompleteListener { imageProxy.close() }
                } else {
                    imageProxy.close()
                }
            }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview, analyzer
                )
            } catch (_: Exception) {}
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            executor.shutdown()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxWidth().height(220.dp)
    )
}