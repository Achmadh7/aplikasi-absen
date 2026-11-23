package id.absensi.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.absensi.app.data.HasilScanAbsen
import id.absensi.app.data.Repository
import id.absensi.app.data.buatPesanWaAbsen
import id.absensi.app.data.labelStatus
import id.absensi.app.data.menitKeJam
import id.absensi.app.data.menitSekarang
import id.absensi.app.data.normalisasiNomorWa
import id.absensi.app.data.tanggalHariIni
import id.absensi.app.ui.BarcodeScannerView
import id.absensi.app.ui.KacaCard
import id.absensi.app.ui.bukaWhatsApp
import id.absensi.app.ui.theme.BentoSlate
import kotlinx.coroutines.launch

@Composable
fun ScanBarcodeScreen(
    repo: Repository,
    toleransiMenit: Int,
    autoBukaWa: Boolean,
    onAutoBukaWaChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onPesan: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tanggal = tanggalHariIni()
    var hasilScan by remember { mutableStateOf<HasilScanAbsen?>(null) }
    var kodeManual by remember { mutableStateOf("") }

    BackHandler(onBack = onBack)

    fun prosesKodeBarcode(kode: String) {
        scope.launch {
            val hasil = repo.scanBarcodeAbsen(kode, menitSekarang(), toleransiMenit, tanggal)
            if (hasil == null) {
                onPesan("Barcode \"$kode\" tidak terdaftar.")
                return@launch
            }
            val aksi = if (hasil.baruPulang || hasil.sudahSelesaiHariIni) "Pulang" else "Masuk"
            onPesan("${hasil.siswa.nama} • $aksi ${menitKeJam(hasil.jamMenit)}")
            if (autoBukaWa) {
                val pesan = buatPesanWaAbsen(
                    namaSiswa = hasil.siswa.nama,
                    kelas = hasil.siswa.kelas,
                    status = hasil.status,
                    jamMenit = hasil.jamMenit,
                    pulang = hasil.baruPulang || hasil.sudahSelesaiHariIni,
                    tanggalIso = tanggal
                )
                if (!bukaWhatsApp(context, hasil.siswa.noWaOrtu, pesan)) {
                    onPesan("Nomor WA orang tua ${hasil.siswa.nama} tidak valid.")
                }
            } else {
                hasilScan = hasil
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali")
            }
            Column {
                Text(
                    text = "Scan Barcode",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = BentoSlate
                )
                Text(
                    text = "Arahkan kamera ke kartu siswa",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        BarcodeScannerView(
            aktif = hasilScan == null,
            onTerbaca = { kode -> prosesKodeBarcode(kode) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        KacaCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterChip(
                    selected = autoBukaWa,
                    onClick = { onAutoBukaWaChange(!autoBukaWa) },
                    label = { Text("Otomatis buka WA orang tua") }
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = kodeManual,
                        onValueChange = { kodeManual = it },
                        label = { Text("Atau ketik NIS") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            if (kodeManual.isNotBlank()) {
                                prosesKodeBarcode(kodeManual)
                                kodeManual = ""
                            }
                        }
                    ) { Text("Cek") }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }

    hasilScan?.let { h ->
        val pulang = h.baruPulang || h.sudahSelesaiHariIni
        val waValid = normalisasiNomorWa(h.siswa.noWaOrtu) != null
        AlertDialog(
            onDismissRequest = { hasilScan = null },
            title = { Text(h.siswa.nama) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Kelas: ${h.siswa.kelas.ifBlank { "-" }} • NIS: ${h.siswa.nis}")
                    Text(
                        if (pulang) "Tercatat PULANG pukul ${menitKeJam(h.jamMenit)}"
                        else "Tercatat MASUK pukul ${menitKeJam(h.jamMenit)} (${labelStatus(h.status)})"
                    )
                    if (!waValid) {
                        Text(
                            "Nomor WA orang tua belum diisi atau tidak valid.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                if (waValid) {
                    TextButton(onClick = {
                        val pesan = buatPesanWaAbsen(
                            namaSiswa = h.siswa.nama,
                            kelas = h.siswa.kelas,
                            status = h.status,
                            jamMenit = h.jamMenit,
                            pulang = pulang,
                            tanggalIso = tanggal
                        )
                        bukaWhatsApp(context, h.siswa.noWaOrtu, pesan)
                        hasilScan = null
                    }) {
                        Text("Kirim WA Orang Tua")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { hasilScan = null }) { Text("Selesai") }
            }
        )
    }
}
