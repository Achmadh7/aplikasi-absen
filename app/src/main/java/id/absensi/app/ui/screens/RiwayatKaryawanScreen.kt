package id.absensi.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.absensi.app.data.KaryawanEntity
import id.absensi.app.data.Repository
import id.absensi.app.data.RiwayatHari
import id.absensi.app.data.StatusHadir
import id.absensi.app.data.formatBulanIndonesia
import id.absensi.app.data.formatTanggalIndonesia
import id.absensi.app.data.labelStatus
import id.absensi.app.data.menitKeJam
import id.absensi.app.data.parseJamMenit
import id.absensi.app.ui.KacaCard
import id.absensi.app.ui.StatusChip
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

/** S6 — riwayat satu karyawan selama satu bulan. Ketuk baris harian untuk koreksi (S1). */
@Composable
fun RiwayatKaryawanScreen(
    repo: Repository,
    karyawanId: Long,
    prefix: String,
    toleransiMenit: Int,
    onBack: () -> Unit,
    onPesan: (String) -> Unit
) {
    var karyawan by remember { mutableStateOf<KaryawanEntity?>(null) }
    var baris by remember { mutableStateOf<List<RiwayatHari>>(emptyList()) }
    var koreksiHari by remember { mutableStateOf<RiwayatHari?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun muat() {
        val hasil = repo.riwayatKaryawan(karyawanId, prefix, toleransiMenit)
        karyawan = hasil?.first
        baris = hasil?.second ?: emptyList()
    }

    LaunchedEffect(karyawanId, prefix) { muat() }
    BackHandler(onBack = onBack)

    val k = karyawan
    val hadir = baris.count { it.status == StatusHadir.HADIR }
    val telat = baris.count { it.status == StatusHadir.TERLAMBAT }
    val alpa = baris.count { it.status == StatusHadir.ALPA }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali")
            }
            Column {
                Text(
                    text = k?.nama ?: "Riwayat",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (k != null) "${k.jabatan} • ${formatBulanIndonesia(prefix)}" else formatBulanIndonesia(prefix),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Hadir $hadir  •  Terlambat $telat  •  Alpa $alpa",
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Ketuk baris harian untuk mengoreksi jam absen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        if (baris.isEmpty()) {
            KacaCard(Modifier.fillMaxWidth()) {
                Text(
                    text = "Belum ada hari kerja pada bulan ini.",
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(baris, key = { it.tanggal }) { r ->
                    val bisaKoreksi = r.status != StatusHadir.IZIN &&
                        r.status != StatusHadir.SAKIT && r.status != StatusHadir.CUTI
                    KacaCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (bisaKoreksi) Modifier.clickable { koreksiHari = r } else Modifier)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = LocalDate.parse(r.tanggal).format(
                                        DateTimeFormatter.ofPattern("EEEE, d MMM", Locale("id", "ID"))
                                    ),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (r.masukMenit != null || r.pulangMenit != null) {
                                        "${r.masukMenit?.let { menitKeJam(it) } ?: "--.--"} – ${r.pulangMenit?.let { menitKeJam(it) } ?: "--.--"}"
                                    } else {
                                        "Tidak ada catatan"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (r.status == StatusHadir.TERLAMBAT) {
                                    Text(
                                        text = "Telat ${r.terlambatMenit} mnt",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFB26A00),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            StatusChip(r.status, r.masukMenit)
                        }
                    }
                }
            }
        }
    }

    val hari = koreksiHari
    if (hari != null && k != null) {
        DialogKoreksi(
            hari = hari,
            onTutup = { koreksiHari = null },
            onSimpan = { masuk, pulang ->
                scope.launch {
                    runCatching { repo.koreksiAbsen(karyawanId, hari.tanggal, masuk, pulang) }
                        .onSuccess {
                            koreksiHari = null
                            muat()
                            onPesan("Koreksi ${k.nama} ${formatTanggalIndonesia(hari.tanggal)} disimpan.")
                        }
                }
            }
        )
    }
}

/** S1 — dialog koreksi jam masuk/pulang. Kedua jam kosong = hapus catatan hari itu. */
@Composable
private fun DialogKoreksi(
    hari: RiwayatHari,
    onTutup: () -> Unit,
    onSimpan: (Int?, Int?) -> Unit
) {
    var masukTeks by remember { mutableStateOf(hari.masukMenit?.let { menitKeJam(it) } ?: "") }
    var pulangTeks by remember { mutableStateOf(hari.pulangMenit?.let { menitKeJam(it) } ?: "") }
    var galat by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onTutup,
        title = { Text("Koreksi — ${formatTanggalIndonesia(hari.tanggal)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Status hari itu: ${labelStatus(hari.status)}. Kosongkan kedua jam untuk menghapus catatan hari itu.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = masukTeks,
                    onValueChange = { masukTeks = it },
                    label = { Text("Jam masuk (cth: 08.05)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pulangTeks,
                    onValueChange = { pulangTeks = it },
                    label = { Text("Jam pulang (cth: 15.00)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                galat?.let {
                    Text(text = it, color = Color(0xFFB3261E), fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val masuk = if (masukTeks.isBlank()) null else parseJamMenit(masukTeks)
                val pulang = if (pulangTeks.isBlank()) null else parseJamMenit(pulangTeks)
                if (masukTeks.isNotBlank() && masuk == null) {
                    galat = "Jam masuk tidak valid — pakai format JJ.MM, cth: 08.05"
                    return@TextButton
                }
                if (pulangTeks.isNotBlank() && pulang == null) {
                    galat = "Jam pulang tidak valid — pakai format JJ.MM, cth: 15.00"
                    return@TextButton
                }
                if (masuk != null && pulang != null && pulang < masuk) {
                    galat = "Jam pulang tidak boleh sebelum jam masuk"
                    return@TextButton
                }
                onSimpan(masuk, pulang)
            }) { Text("Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onTutup) { Text("Batal") }
        }
    )
}
