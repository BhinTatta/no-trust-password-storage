package com.notrust.vault.android.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.notrust.vault.android.ui.theme.VaultColors
import com.notrust.vault.android.ui.theme.VaultLabelTextStyle
import com.notrust.vault.android.ui.theme.VaultScreenTitleTextStyle

/**
 * Live QR scan for TOTP provisioning, via CameraX + on-device ML Kit
 * barcode decoding — nothing captured here is ever written to disk or
 * sent anywhere; frames are analyzed in memory and immediately discarded.
 * Calls [onScanned] once with the raw decoded text (expected to be an
 * `otpauth://totp/...` URI) and stops — the caller is responsible for
 * validating/parsing it (see TotpSeedParser in :shared).
 */
@Composable
fun QrScannerScreen(onScanned: (String) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Guards against the analyzer handing back more than one frame's
    // result while this screen is on its way out after the first hit —
    // ML Kit's decode is async, so a second in-flight frame can complete
    // a moment after the first already triggered onScanned.
    var hasScanned by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(VaultColors.Void)) {
        if (hasCameraPermission) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val scanner = BarcodeScanning.getClient()
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                            analyzeFrame(scanner, imageProxy) { value ->
                                if (!hasScanned) {
                                    hasScanned = true
                                    onScanned(value)
                                }
                            }
                        }
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysis
                            )
                        } catch (e: Exception) {
                            // No back camera, or binding failed for some
                            // device-specific reason — the cancel button
                            // is still there, and manual paste always works.
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                }
            )

            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Point the camera at a TOTP QR code",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier
                        .background(VaultColors.Void.copy(alpha = 0.7f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Camera permission needed",
                    style = VaultScreenTitleTextStyle.copy(color = VaultColors.TextPrimary)
                )
                Text(
                    "You can still paste the secret or otpauth:// link by hand instead.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VaultColors.TextMuted,
                    modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
                )
                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.buttonColors(containerColor = VaultColors.Signal, contentColor = Color(0xFF00201C))
                ) {
                    Text("BACK", style = VaultLabelTextStyle.copy(color = Color(0xFF00201C)))
                }
            }
        }

        IconButton(
            onClick = onCancel,
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Cancel scan", tint = Color.White)
        }
    }
}

@OptIn(ExperimentalGetImage::class)
private fun analyzeFrame(scanner: com.google.mlkit.vision.barcode.BarcodeScanner, imageProxy: ImageProxy, onValue: (String) -> Unit) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }
    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner.process(image)
        .addOnSuccessListener { barcodes ->
            barcodes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE && !it.rawValue.isNullOrBlank() }
                ?.rawValue?.let(onValue)
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}
