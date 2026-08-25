package com.tiendatech.mobile.feature.scanner.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.tiendatech.mobile.feature.scanner.camera.BarcodeAnalyzer
import java.util.concurrent.Executors

@Composable
fun ScannerScreen(
    onBack: () -> Unit,
    onProduct: (Long) -> Unit,
    viewModel: ScannerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> permissionGranted = granted }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("← Volver", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp))
        Text("Escanear producto", style = MaterialTheme.typography.headlineMedium)
        Text("La cámara se usa únicamente mientras esta pantalla está abierta para leer EAN, UPC, Code 128 o QR.")
        if (!permissionGranted) {
            Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Se necesita permiso de cámara para escanear.")
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.padding(top = 12.dp)) { Text("Permitir cámara") }
                }
            }
        } else if (!state.analysisPaused) {
            CameraPreview(onCode = viewModel::detected, modifier = Modifier.fillMaxWidth().height(300.dp))
        } else {
            Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { Text("Lectura pausada para evitar códigos duplicados") }
        }
        OutlinedTextField(
            value = state.code,
            onValueChange = viewModel::codeChanged,
            label = { Text("Código manual") },
            supportingText = state.validationError?.let { error -> { Text(error, color = MaterialTheme.colorScheme.error) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = viewModel::search, enabled = !state.lookingUp, modifier = Modifier.fillMaxWidth()) { Text(if (state.lookingUp) "Buscando…" else "Buscar código") }
        state.message?.let {
            Text(
                it,
                color = if (state.productId != null || state.categoryId != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        state.productId?.let { id -> Button(onClick = { onProduct(id) }, modifier = Modifier.fillMaxWidth()) { Text("Abrir producto") } }
        if (state.analysisPaused) OutlinedButton(onClick = viewModel::retry, modifier = Modifier.fillMaxWidth()) { Text("Escanear otro código") }
    }
}

@Composable
private fun CameraPreview(onCode: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val options = remember {
        BarcodeScannerOptions.Builder().setBarcodeFormats(
            Barcode.FORMAT_EAN_8, Barcode.FORMAT_EAN_13, Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_UPC_E, Barcode.FORMAT_CODE_128, Barcode.FORMAT_QR_CODE
        ).build()
    }
    val scanner = remember { BarcodeScanning.getClient(options) }
    DisposableEffect(Unit) { onDispose { scanner.close(); executor.shutdown() } }
    AndroidView(
        factory = { previewContext ->
            PreviewView(previewContext).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                val providerFuture = ProcessCameraProvider.getInstance(previewContext)
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also { it.surfaceProvider = surfaceProvider }
                    val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().also {
                        it.setAnalyzer(executor, BarcodeAnalyzer(scanner, onCode))
                    }
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }, ContextCompat.getMainExecutor(previewContext))
            }
        },
        modifier = modifier
    )
}
