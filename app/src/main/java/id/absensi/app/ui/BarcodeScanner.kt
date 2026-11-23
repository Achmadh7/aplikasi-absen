package id.absensi.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import id.absensi.app.data.buatUrlWa
import java.util.concurrent.Executors

/** Buka WhatsApp orang tua dengan pesan siap kirim. Mengembalikan false jika nomor tidak valid / gagal buka. */
fun bukaWhatsApp(context: Context, nomorWa: String, pesan: String): Boolean {
    val url = buatUrlWa(nomorWa, pesan) ?: return false
    return runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        true
    }.getOrDefault(false)
}

/** Buat bitmap QR code dari kode siswa (NIS) untuk ditampilkan/dicetak. */
fun buatQrBitmap(teks: String, ukuranPx: Int = 512): Bitmap? {
    if (teks.isBlank()) return null
    return runCatching {
        val matrix = QRCodeWriter().encode(teks, BarcodeFormat.QR_CODE, ukuranPx, ukuranPx)
        Bitmap.createBitmap(ukuranPx, ukuranPx, Bitmap.Config.RGB_565).apply {
            for (x in 0 until ukuranPx) {
                for (y in 0 until ukuranPx) {
                    setPixel(x, y, if (matrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE)
                }
            }
        }
    }.getOrNull()
}

/**
 * Pemindai barcode/QR kamera menggunakan CameraX + ML Kit.
 * Memanggil [onTerbaca] sekali saat barcode terdeteksi (dengan jeda anti-duplikat).
 */
@Composable
fun BarcodeScannerView(
    aktif: Boolean,
    onTerbaca: (String) -> Unit,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(260.dp)
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val aktifTerbaru by rememberUpdatedState(aktif)
    val onTerbacaTerbaru by rememberUpdatedState(onTerbaca)
    var punyaIzin by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> punyaIzin = granted }

    LaunchedEffect(Unit) {
        if (!punyaIzin) launcher.launch(Manifest.permission.CAMERA)
    }

    if (!punyaIzin) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Izin kamera diperlukan untuk scan barcode siswa.")
                Button(
                    onClick = { launcher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Izinkan Kamera")
                }
            }
        }
        return
    }

    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient() }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var terakhirKode by remember { mutableStateOf("") }
    var terakhirWaktu by remember { mutableStateOf(0L) }

    DisposableEffect(Unit) {
        onDispose {
            cameraProvider?.unbindAll()
            scanner.close()
            executor.shutdown()
        }
    }

    Box(
        modifier = modifier.clip(RoundedCornerShape(16.dp))
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val future = ProcessCameraProvider.getInstance(ctx)
                future.addListener({
                    val provider = future.get()
                    cameraProvider = provider
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(executor) { imageProxy ->
                        prosesFrame(
                            imageProxy = imageProxy,
                            scanner = scanner,
                            aktif = aktifTerbaru,
                            onKode = { kode ->
                                val now = System.currentTimeMillis()
                                if (kode != terakhirKode || now - terakhirWaktu > 2500L) {
                                    terakhirKode = kode
                                    terakhirWaktu = now
                                    onTerbacaTerbaru(kode)
                                }
                            }
                        )
                    }
                    runCatching {
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis
                        )
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            }
        )
    }
}

@OptIn(ExperimentalGetImage::class)
private fun prosesFrame(
    imageProxy: androidx.camera.core.ImageProxy,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    aktif: Boolean,
    onKode: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (!aktif || mediaImage == null) {
        imageProxy.close()
        return
    }
    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner.process(image)
        .addOnSuccessListener { barcodes ->
            val raw = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue?.trim()
            if (!raw.isNullOrBlank()) onKode(raw)
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}
