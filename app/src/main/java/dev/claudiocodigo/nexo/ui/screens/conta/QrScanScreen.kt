package dev.claudiocodigo.nexo.ui.screens.conta

import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * CameraX-only QR scanner (no Google Play Services). The scan result is the raw
 * QR payload string, passed to [onQrResult]. Permission is requested on entry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScanScreen(
    onQrResult: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val cameraProviderRef = remember { AtomicReference<ProcessCameraProvider?>(null) }
    val screenActive = remember { AtomicBoolean(true) }
    val callback by rememberUpdatedState(onQrResult)
    val delivered = remember { AtomicBoolean(false) }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            screenActive.set(false)
            cameraProviderRef.getAndSet(null)?.unbindAll()
            analyzerExecutor.shutdownNow()
        }
    }

    var permissionGranted by remember { mutableStateOf(false) }
    var cameraStarted by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        if (granted) cameraStarted = true
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            permissionGranted = true
            cameraStarted = true
        } else {
            permissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        modifier = Modifier.testTag("screen_qr"),
        topBar = {
            TopAppBar(
                title = { Text("Escanear QR do Nextcloud") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                permissionGranted && cameraStarted -> {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            PreviewView(ctx).also { previewView ->
                                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                                providerFuture.addListener({
                                    if (screenActive.get()) {
                                        runCatching {
                                            val provider = providerFuture.get()
                                            if (!screenActive.get()) return@runCatching
                                            cameraProviderRef.set(provider)
                                            val preview = Preview.Builder().build().also {
                                                it.setSurfaceProvider(previewView.surfaceProvider)
                                            }
                                            val analysis = ImageAnalysis.Builder()
                                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                                .build()
                                                .also { it.setAnalyzer(analyzerExecutor) { image ->
                                                    try {
                                                        val luma = extractLuma(image)
                                                        val rotated = rotateLuma(luma, image.width, image.height, image.imageInfo.rotationDegrees)
                                                        val result = QrDecoder.decodeLuma(rotated.bytes, rotated.width, rotated.height)
                                                        if (result != null && delivered.compareAndSet(false, true)) {
                                                            Handler(Looper.getMainLooper()).post { callback(result) }
                                                        }
                                                    } finally {
                                                        image.close()
                                                    }
                                                } }
                                            provider.unbindAll()
                                            if (screenActive.get()) {
                                                provider.bindToLifecycle(
                                                    lifecycleOwner,
                                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                                    preview,
                                                    analysis
                                                )
                                            } else {
                                                provider.unbindAll()
                                            }
                                        }
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                            }
                        }
                    )
                }
                !permissionGranted -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.align(Alignment.Center).padding(16.dp)
                    ) {
                        Text(
                            text = "Permissão de câmera necessária. Volte para inserir os dados manualmente ou tente novamente.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { permissionLauncher.launch(android.Manifest.permission.CAMERA) }) {
                            Text("Tentar novamente")
                        }
                    }
                }
                else -> {
                    Icon(
                        Icons.Rounded.QrCodeScanner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

private fun extractLuma(image: androidx.camera.core.ImageProxy): ByteArray {
    val width = image.width
    val height = image.height
    val plane = image.planes[0]
    val buffer = plane.buffer.duplicate()
    return ByteArray(width * height).also { result ->
        for (row in 0 until height) for (col in 0 until width) {
            val index = row * plane.rowStride + col * plane.pixelStride
            result[row * width + col] = if (index < buffer.limit()) buffer.get(index) else 0
        }
    }
}

private data class RotatedLuma(val bytes: ByteArray, val width: Int, val height: Int)

private fun rotateLuma(bytes: ByteArray, width: Int, height: Int, degrees: Int): RotatedLuma {
    val normalized = ((degrees % 360) + 360) % 360
    if (normalized == 0) return RotatedLuma(bytes, width, height)
    val outWidth = if (normalized == 180) width else height
    val outHeight = if (normalized == 180) height else width
    val out = ByteArray(outWidth * outHeight)
    for (y in 0 until height) for (x in 0 until width) {
        val (nx, ny) = when (normalized) {
            90 -> height - 1 - y to x
            180 -> width - 1 - x to height - 1 - y
            else -> y to width - 1 - x
        }
        out[ny * outWidth + nx] = bytes[y * width + x]
    }
    return RotatedLuma(out, outWidth, outHeight)
}
