package id.absensi.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.absensi.app.data.RekapSiswa
import id.absensi.app.data.Repository
import id.absensi.app.data.buatCsvRekap
import id.absensi.app.data.formatBulanIndonesia
import id.absensi.app.ui.FilterCariKelas
import id.absensi.app.ui.KacaCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun RekapScreen(
    repo: Repository,
    toleransiMenit: Int,
    onSiswa: (Long, String) -> Unit,
    onPesan: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bulan by remember { mutableStateOf(YearMonth.now()) }
    var rekap by remember { mutableStateOf<List<RekapSiswa>>(emptyList()) }
    var cari by remember { mutableStateOf("") }
    var kelasDipilih by remember { mutableStateOf<String?>(null) }
    val prefix = bulan.format(DateTimeFormatter.ofPattern("yyyy-MM"))

    LaunchedEffect(bulan) {
        rekap = repo.rekapBulan(prefix, toleransiMenit)
    }

    val daftarKelas = remember(rekap) {
        rekap.map { it.kelas.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val rekapTampil = remember(rekap, cari, kelasDipilih) {
        val q = cari.trim()
        rekap.filter { r ->
            val cocokKelas = kelasDipilih == null || r.kelas.trim() == kelasDipilih
            val cocokCari = q.isEmpty() ||
                r.nama.contains(q, ignoreCase = true) ||
                r.kelas.contains(q, ignoreCase = true)
            cocokKelas && cocokCari
        }
    }

    val launcherExport = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val csv = buatCsvRekap(rekapTampil)
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(csv.toByteArray(Charsets.UTF_8))
                    } ?: error("Gagal membuka file tujuan")
                }.isSuccess
            }
            onPesan(if (ok) "Rekap CSV berhasil disimpan." else "Gagal menyimpan file CSV.")
        }
    }

    val totalHadir = rekapTampil.sumOf { it.hadir }
    val totalTelat = rekapTampil.sumOf { it.terlambat }
    val totalIzin = rekapTampil.sumOf { it.izin + it.sakit }
    val totalAlpa = rekapTampil.sumOf { it.alpa }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Rekap",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold
            )
            FilledTonalButton(
                onClick = {
                    val suffixKelas = kelasDipilih?.replace(Regex("[^A-Za-z0-9_-]"), "_")
                    val namaFile = if (suffixKelas.isNullOrBlank()) {
                        "rekap_absensi_$prefix.csv"
                    } else {
                        "rekap_absensi_${prefix}_$suffixKelas.csv"
                    }
                    launcherExport.launch(namaFile)
                },
                enabled = rekapTampil.isNotEmpty()
            ) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Export CSV")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { bulan = bulan.minusMonths(1) }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Bulan lalu")
            }
            Text(
                text = formatBulanIndonesia(prefix),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            IconButton(
                onClick = { bulan = bulan.plusMonths(1) },
                enabled = bulan < YearMonth.now()
            ) {
                Icon(Icons.Filled.ArrowForward, contentDescription = "Bulan depan")
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KartuRingkasan("Hadir", totalHadir.toString(), Color(0xFF2E7D32), Color(0xFFE8F5E9), Modifier.weight(1f))
            KartuRingkasan("Terlambat", totalTelat.toString(), Color(0xFFB26A00), Color(0xFFFFF3E0), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KartuRingkasan("Izin/Sakit", totalIzin.toString(), Color(0xFF2E5A88), Color(0xFFD3E3F3), Modifier.weight(1f))
            KartuRingkasan("Alpa", totalAlpa.toString(), Color(0xFFB3261E), Color(0xFFFDECEA), Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Per siswa",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Spacer(Modifier.height(8.dp))
        if (rekap.isEmpty()) {
            KacaCard(Modifier.fillMaxWidth()) {
                Text(
                    text = "Belum ada siswa.",
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            FilterCariKelas(
                cari = cari,
                onCariChange = { cari = it },
                daftarKelas = daftarKelas,
                kelasDipilih = kelasDipilih,
                onKelasChange = { kelasDipilih = it },
                placeholder = "Cari nama atau kelas..."
            )
            Spacer(Modifier.height(8.dp))
            if (rekapTampil.isEmpty()) {
                KacaCard(Modifier.fillMaxWidth()) {
                    Text(
                        text = "Tidak ada siswa yang cocok.",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(rekapTampil, key = { it.siswaId }) { r ->
                        KacaCard(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSiswa(r.siswaId, prefix) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (r.kelas.isNotBlank()) "${r.nama} (${r.kelas})" else r.nama,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "H ${r.hadir}  •  T ${r.terlambat}  •  I ${r.izin}  •  S ${r.sakit}  •  A ${r.alpa}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "${r.persenHadir}%",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KartuRingkasan(
    label: String,
    nilai: String,
    fg: Color,
    bg: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = bg
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = label, color = Color(0xFF5A6878), fontSize = 13.sp)
            Text(text = nilai, color = fg, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}
