package id.absensi.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import id.absensi.app.ui.theme.BentoEmerald
import id.absensi.app.ui.theme.BentoIndigo
import id.absensi.app.ui.theme.BentoIndigoLight
import id.absensi.app.ui.theme.BentoSlate
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
    val rasioHadir = if (daftar.isNotEmpty()) sudah.toFloat() / daftar.size else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = "Absensi Siswa",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = BentoSlate
        )
        Text(
            text = formatTanggalIndonesia(tanggal),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))

        // Bento Grid baris 1: Jam live + Ringkasan hadir
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KacaCard(
                modifier = Modifier
                    .weight(1f)
                    .height(100.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(BentoEmerald, CircleShape)
                        )
                        Text(
                            text = "Waktu Aktif",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = menitKeJam(jamMenit),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = BentoSlate
                    )
                }
            }

            KacaCard(
                modifier = Modifier
                    .weight(1f)
                    .height(100.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { rasioHadir },
                            modifier = Modifier.size(52.dp),
                            color = BentoEmerald,
                            trackColor = MaterialTheme.colorScheme.outlineVariant,
                            strokeWidth = 6.dp
                        )
                        Text(
                            text = "${(rasioHadir * 100).toInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoSlate
                        )
                    }
                    Column {
                        Text(
                            text = "$sudah/${daftar.size} Hadir",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = BentoSlate
                        )
                        Text(
                            text = "$telat terlambat",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Bento Grid baris 2: Scan Barcode utama + Toggle Auto WA Ortu
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier
                    .weight(1.45f)
                    .height(92.dp)
                    .clickable { bukaScanner = true },
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(listOf(BentoIndigo, BentoIndigoLight)))
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White.copy(alpha = 0.18f),
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.QrCodeScanner,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Scan Barcode",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Arahkan kartu siswa",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            KacaCard(
                modifier = Modifier
                    .weight(1f)
                    .height(92.dp)
                    .clickable { autoBukaWa = !autoBukaWa }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Auto WA Ortu",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = BentoSlate
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (autoBukaWa) "Aktif" else "Manual",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (autoBukaWa) BentoEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Switch(
                            checked = autoBukaWa,
                            onCheckedChange = { autoBukaWa = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = BentoEmerald
                            )
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "Daftar Siswa",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = BentoSlate
        )
        Spacer(Modifier.height(8.dp))
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
                                color = BentoIndigo.copy(alpha = 0.12f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                val init = s.nama.split(" ")
                                    .filter { it.isNotBlank() }
                                    .take(2)
                                    .joinToString("") { it.first().toString() }
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = init,
                                        color = BentoIndigo,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp)
                            ) {
                                Text(text = s.nama, fontWeight = FontWeight.Bold, color = BentoSlate)
                                Text(
                                    text = "${s.kelas.ifBlank { "-" }} • NIS ${s.nis} • ${s.sesi.label}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                StatusChip(s.status, s.masukMenit)
                                if (s.masukMenit != null && normalisasiNomorWa(s.noWaOrtu) != null) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = BentoEmerald.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, BentoEmerald.copy(alpha = 0.35f)),
                                        modifier = Modifier.clickable {
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
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.Send,
                                                contentDescription = "Kirim WA Orang Tua",
                                                tint = BentoEmerald,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = "WA Ortu",
                                                color = BentoEmerald,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
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
