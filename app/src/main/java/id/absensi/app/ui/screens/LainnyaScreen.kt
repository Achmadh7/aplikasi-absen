package id.absensi.app.ui.screens

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import id.absensi.app.data.InfoPembaruan
import id.absensi.app.data.Repository
import id.absensi.app.data.cekPembaruan
import id.absensi.app.data.pasangApkTerunduh
import id.absensi.app.data.unduhPembaruan
import id.absensi.app.data.versiAplikasi
import id.absensi.app.ui.KacaCard
import kotlinx.coroutines.launch

@Composable
fun LainnyaScreen(
    repo: Repository,
    toleransiMenit: Int,
    onToleransiSimpan: (Int) -> Unit,
    pembaruanOtomatis: InfoPembaruan? = null,
    onKelolaLibur: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var toleransiTeks by remember(toleransiMenit) { mutableStateOf(toleransiMenit.toString()) }
    var jumlahKaryawan by remember { mutableStateOf(0) }

    // Pembaruan aplikasi (GitHub Releases).
    val versiSaatIni = remember { versiAplikasi(context) }
    var statusPembaruan by remember { mutableStateOf<String?>(null) }
    var infoManual by remember { mutableStateOf<InfoPembaruan?>(null) }
    var dialogPembaruan by remember { mutableStateOf(false) }
    var mengunduh by remember { mutableStateOf(false) }
    var idUnduhan by remember { mutableStateOf<Long?>(null) }
    val infoPembaruan = infoManual ?: pembaruanOtomatis

    // Begitu unduhan selesai, buka installer otomatis.
    DisposableEffect(mengunduh) {
        if (!mengunduh) return@DisposableEffect onDispose {}
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: -1
                if (id == idUnduhan) {
                    mengunduh = false
                    statusPembaruan = "Unduhan selesai — membuka installer…"
                    pasangApkTerunduh(context, id)
                }
            }
        }
        ContextCompat.registerReceiver(
            context, receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    LaunchedEffect(Unit) {
        jumlahKaryawan = repo.jumlahKaryawanAktif()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Lainnya",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold
        )
        KacaCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Pengaturan", fontWeight = FontWeight.Bold)
                Text(text = "Shift 1 — 08.00–15.00")
                Text(text = "Shift 2 — 16.00–22.00")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = toleransiTeks,
                        onValueChange = { if (it.length <= 3 && it.all { c -> c.isDigit() }) toleransiTeks = it },
                        label = { Text("Toleransi terlambat (menit)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = {
                        onToleransiSimpan(toleransiTeks.toIntOrNull()?.coerceIn(0, 120) ?: 15)
                    }) { Text("Simpan") }
                }
            }
        }
        KacaCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Hari Libur", fontWeight = FontWeight.Bold)
                Text(text = "Tanggal merah tidak dihitung sebagai hari kerja — karyawan tidak ditandai Alpa.")
                Button(onClick = onKelolaLibur) { Text("Kelola Hari Libur") }
            }
        }
        KacaCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Pembaruan Aplikasi", fontWeight = FontWeight.Bold)
                Text(text = "Versi terpasang: $versiSaatIni")
                if (infoPembaruan != null) {
                    Text(text = "Versi baru tersedia: ${infoPembaruan.versi}")
                }
                statusPembaruan?.let { Text(text = it) }
                Button(onClick = {
                    if (infoPembaruan != null) {
                        dialogPembaruan = true
                    } else {
                        scope.launch {
                            statusPembaruan = "Memeriksa pembaruan…"
                            val hasil = cekPembaruan(versiSaatIni)
                            if (hasil != null) {
                                infoManual = hasil
                                statusPembaruan = null
                                dialogPembaruan = true
                            } else {
                                statusPembaruan = "Sudah versi terbaru."
                            }
                        }
                    }
                }) {
                    Text(if (infoPembaruan != null) "Lihat Pembaruan" else "Periksa Pembaruan")
                }
            }
        }
        KacaCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = "Tentang", fontWeight = FontWeight.Bold)
                Text(text = "Absensi — versi $versiSaatIni")
                Text(text = "Aplikasi absensi karyawan. Satu HP di kantor, karyawan absen dengan tap nama.")
                Text(text = "Karyawan aktif: $jumlahKaryawan")
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (dialogPembaruan && infoPembaruan != null) {
        val info = infoPembaruan
        AlertDialog(
            onDismissRequest = { dialogPembaruan = false },
            title = { Text("Pembaruan tersedia") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Versi ${info.versi} sudah tersedia (terpasang: $versiSaatIni).")
                    if (info.catatan.isNotBlank()) Text(text = info.catatan)
                    if (mengunduh) Text(text = "Mengunduh…")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (!context.packageManager.canRequestPackageInstalls()) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                        statusPembaruan = "Izinkan \"Pasang aplikasi tak dikenal\" untuk Absensi, lalu tekan Unduh & Pasang lagi."
                        return@TextButton
                    }
                    idUnduhan = unduhPembaruan(context, info)
                    mengunduh = true
                    dialogPembaruan = false
                    statusPembaruan = "Mengunduh Absensi ${info.versi}…"
                }) { Text(if (mengunduh) "Mengunduh…" else "Unduh & Pasang") }
            },
            dismissButton = {
                TextButton(onClick = { dialogPembaruan = false }) { Text("Nanti") }
            }
        )
    }
}
