package id.absensi.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.sp
import id.absensi.app.data.HasilScanAbsen
import id.absensi.app.data.Repository
import id.absensi.app.data.SiswaAbsenUi
import id.absensi.app.data.StatusHadir
import id.absensi.app.data.buatPesanWaAbsen
import id.absensi.app.data.formatTanggalIndonesia
import id.absensi.app.data.labelStatus
import id.absensi.app.data.menitKeJam
import id.absensi.app.data.menitSekarang
import id.absensi.app.data.normalisasiNomorWa
import id.absensi.app.data.tanggalHariIni
import id.absensi.app.ui.BarcodeScannerView
import id.absensi.app.ui.KacaCard
import id.absensi.app.ui.StatusChip
import id.absensi.app.ui.bukaWhatsApp
import id.absensi.app.ui.theme.AuroraCyan
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AbsenScreen(repo: Repository, toleransiMenit: Int, onPesan: (String) -> Unit) {
    val context = LocalContext.current
    var daftar by remember { mutableStateOf<List<SiswaAbsenUi>>(emptyList()) }
    var jamMenit by remember { mutableStateOf(menitSekarang()) }
    var bukaScanner by remember { mutableStateOf(false) }
    var autoBukaWa by remember { mutableStateOf(false) }
    var hasilScan by remember { mutableStateOf<HasilScanAbsen?>(null) }
    var kodeManual by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val tanggal = tanggalHariIni()

    suspend fun muat() {
        daftar = repo.daftarAbsenHariIni(tanggal, toleransiMenit)
    }

    LaunchedEffect(Unit) {
        muat()
        while (true) {
            delay(20_000)
            jamMenit = menitSekarang()
            muat()
        }
    }

    fun prosesKodeBarcode(kode: String) {
        scope.launch {
            val hasil = repo.scanBarcodeAbsen(kode, menitSekarang(), toleransiMenit, tanggal)
            if (hasil == null) {
                onPesan("Barcode \"$kode\" tidak terdaftar.")
                return@launch
            }
            muat()
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

    fun ketuk(s: SiswaAbsenUi) {
        scope.launch {
            when {
                s.status == StatusHadir.IZIN || s.status == StatusHadir.SAKIT -> {
                    onPesan("${s.nama} hari ini ${labelStatus(s.status).lowercase()}.")
                }
                s.pulangMenit != null -> {
                    onPesan("${s.nama} sudah pulang ${menitKeJam(s.pulangMenit)}.")
                }
                else -> {
                    val hasil = repo.tapAbsenById(s.id, menitSekarang(), tanggal)
                    if (hasil != null) {
                        onPesan(
                            if (hasil.pulangMenit != null) {
                                "${s.nama} • Pulang ${menitKeJam(hasil.pulangMenit)}"
                            } else {
                                "${s.nama} • Masuk ${menitKeJam(hasil.masukMenit ?: menitSekarang())}"
                            }
                        )
                    }
                    muat()
                }
            }
        }
    }

    val sudah = daftar.count { it.status == StatusHadir.HADIR || it.status == StatusHadir.TERLAMBAT }
    val telat = daftar.count { it.status == StatusHadir.TERLAMBAT }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Absensi Siswa",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = formatTanggalIndonesia(tanggal),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = menitKeJam(jamMenit),
                fontSize = 48.sp,
                fontWeight = FontWeight.ExtraBold,
                color = AuroraCyan
            )
            Button(onClick = { bukaScanner = true }) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Scan Barcode")
            }
        }
        Spacer(Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = "$sudah dari ${daftar.size} sudah absen  •  $telat terlambat",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(12.dp))
        if (daftar.isEmpty()) {
            KacaCard(Modifier.fillMaxWidth()) {
                Text(
                    text = "Belum ada siswa. Tambah dulu di menu Siswa.",
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(daftar, key = { it.id }) { s ->
                    KacaCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { ketuk(s) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            ) {
                                val init = s.nama.split(" ")
                                    .filter { it.isNotBlank() }
                                    .take(2)
                                    .joinToString("") { it.first().toString() }
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = init,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp)
                            ) {
                                Text(text = s.nama, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${s.kelas.ifBlank { "-" }} • NIS ${s.nis} • ${s.sesi.label}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusChip(s.status, s.masukMenit)
                            if (s.masukMenit != null && normalisasiNomorWa(s.noWaOrtu) != null) {
                                IconButton(onClick = {
                                    val pulang = s.pulangMenit != null
                                    val jam = s.pulangMenit ?: s.masukMenit
                                    val pesan = buatPesanWaAbsen(
                                        namaSiswa = s.nama,
                                        kelas = s.kelas,
                                        status = s.status,
                                        jamMenit = jam,
                                        pulang = pulang,
                                        tanggalIso = tanggal
                                    )
                                    if (!bukaWhatsApp(context, s.noWaOrtu, pesan)) {
                                        onPesan("Gagal membuka WhatsApp.")
                                    }
                                }) {
                                    Icon(
                                        Icons.Filled.Send,
                                        contentDescription = "Kirim WA Orang Tua"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (bukaScanner) {
        AlertDialog(
            onDismissRequest = { bukaScanner = false },
            title = { Text("Scan Barcode Siswa") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BarcodeScannerView(
                        aktif = hasilScan == null,
                        onTerbaca = { kode -> prosesKodeBarcode(kode) }
                    )
                    FilterChip(
                        selected = autoBukaWa,
                        onClick = { autoBukaWa = !autoBukaWa },
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
            },
            confirmButton = {
                TextButton(onClick = { bukaScanner = false }) { Text("Tutup") }
            }
        )
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
