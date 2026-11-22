package id.absensi.app.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import id.absensi.app.data.JamSekolah
import id.absensi.app.data.Repository
import id.absensi.app.data.SiswaEntity
import id.absensi.app.data.formatTanggalIndonesia
import id.absensi.app.data.tanggalHariIni
import id.absensi.app.ui.KacaCard
import id.absensi.app.ui.buatQrBitmap
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiswaScreen(repo: Repository) {
    var daftar by remember { mutableStateOf<List<SiswaEntity>>(emptyList()) }
    var dialogTambah by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<SiswaEntity?>(null) }
    var izinTarget by remember { mutableStateOf<SiswaEntity?>(null) }
    var qrTarget by remember { mutableStateOf<SiswaEntity?>(null) }
    var pesanError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun muat() {
        daftar = repo.semuaSiswa()
    }

    LaunchedEffect(Unit) { muat() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Siswa",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "${daftar.count { it.aktif }} aktif • ${daftar.size} total",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            if (daftar.isEmpty()) {
                KacaCard(Modifier.fillMaxWidth()) {
                    Text(
                        text = "Belum ada siswa. Ketuk tombol + untuk menambah.",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(daftar, key = { it.id }) { s ->
                        KacaCard(Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = s.nama.split(" ")
                                                .filter { it.isNotBlank() }
                                                .take(2)
                                                .joinToString("") { it.first().toString() },
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
                                    Text(
                                        text = s.nama + if (s.aktif) "" else " (nonaktif)",
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${s.kelas.ifBlank { "-" }} • NIS ${s.nis} • ${JamSekolah.dariNama(s.sesi).label}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (s.noWaOrtu.isNotBlank()) {
                                        Text(
                                            text = "WA Ortu: ${s.noWaOrtu}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(onClick = { qrTarget = s }) {
                                    Icon(Icons.Filled.QrCode2, contentDescription = "QR Siswa")
                                }
                                IconButton(onClick = { izinTarget = s }) {
                                    Icon(Icons.Filled.Event, contentDescription = "Izin")
                                }
                                IconButton(onClick = { editTarget = s }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Ubah")
                                }
                                IconButton(onClick = {
                                    scope.launch {
                                        if (s.aktif) repo.nonaktifkanSiswa(s.id)
                                        else repo.aktifkanSiswa(s.id)
                                        muat()
                                    }
                                }) {
                                    Icon(
                                        if (s.aktif) Icons.Filled.PersonOff else Icons.Filled.PersonAdd,
                                        contentDescription = if (s.aktif) "Nonaktifkan" else "Aktifkan"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { dialogTambah = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Tambah siswa")
        }
    }

    if (dialogTambah || editTarget != null) {
        DialogSiswa(
            awal = editTarget,
            error = pesanError,
            onBatal = { dialogTambah = false; editTarget = null; pesanError = null },
            onSimpan = { nama, nis, kelas, noWa, sesi ->
                scope.launch {
                    val res = runCatching {
                        if (editTarget != null) {
                            repo.ubahSiswa(
                                editTarget!!.copy(
                                    nama = nama,
                                    nis = nis,
                                    kelas = kelas,
                                    noWaOrtu = noWa,
                                    sesi = sesi.name
                                )
                            )
                        } else {
                            repo.tambahSiswa(nama, nis, kelas, noWa, sesi)
                        }
                    }
                    if (res.isFailure) {
                        pesanError = "NIS / kode barcode sudah dipakai siswa lain."
                    } else {
                        dialogTambah = false
                        editTarget = null
                        pesanError = null
                        muat()
                    }
                }
            }
        )
    }

    qrTarget?.let { s ->
        val bmp = remember(s.nis) { buatQrBitmap(s.nis, 480) }
        AlertDialog(
            onDismissRequest = { qrTarget = null },
            title = { Text("QR Code — ${s.nama}") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "QR ${s.nis}",
                            modifier = Modifier.size(220.dp)
                        )
                    }
                    Text("NIS / Kode: ${s.nis}")
                    Text("Kelas: ${s.kelas.ifBlank { "-" }}")
                }
            },
            confirmButton = {
                TextButton(onClick = { qrTarget = null }) { Text("Tutup") }
            }
        )
    }

    izinTarget?.let { s ->
        DialogIzinSiswa(
            siswa = s,
            onBatal = { izinTarget = null },
            onSimpan = { tanggal, jenis, keterangan ->
                scope.launch {
                    repo.catatIzin(s.id, tanggal, jenis, keterangan)
                    izinTarget = null
                }
            }
        )
    }
}

@Composable
private fun DialogSiswa(
    awal: SiswaEntity?,
    error: String?,
    onBatal: () -> Unit,
    onSimpan: (String, String, String, String, JamSekolah) -> Unit
) {
    var nama by remember { mutableStateOf(awal?.nama ?: "") }
    var nis by remember { mutableStateOf(awal?.nis ?: "") }
    var kelas by remember { mutableStateOf(awal?.kelas ?: "") }
    var noWa by remember { mutableStateOf(awal?.noWaOrtu ?: "") }
    var sesi by remember { mutableStateOf(awal?.let { JamSekolah.dariNama(it.sesi) } ?: JamSekolah.PAGI) }
    var salah by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onBatal,
        title = { Text(if (awal == null) "Tambah Siswa" else "Ubah Siswa") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = nama,
                    onValueChange = { nama = it; salah = false },
                    label = { Text("Nama siswa") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = nis,
                    onValueChange = { nis = it; salah = false },
                    label = { Text("NIS / Kode Barcode") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = kelas,
                    onValueChange = { kelas = it },
                    label = { Text("Kelas (mis. 7A)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = noWa,
                    onValueChange = { noWa = it },
                    label = { Text("No. WA Orang Tua (08...)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    JamSekolah.entries.forEach { j ->
                        FilterChip(
                            selected = sesi == j,
                            onClick = { sesi = j },
                            label = { Text(j.label) }
                        )
                    }
                }
                if (salah) {
                    Text("Nama dan NIS tidak boleh kosong.", color = MaterialTheme.colorScheme.error)
                }
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (nama.isBlank() || nis.isBlank()) {
                    salah = true
                } else {
                    onSimpan(nama.trim(), nis.trim(), kelas.trim(), noWa.trim(), sesi)
                }
            }) { Text("Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onBatal) { Text("Batal") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogIzinSiswa(
    siswa: SiswaEntity,
    onBatal: () -> Unit,
    onSimpan: (String, String, String) -> Unit
) {
    var tanggal by remember { mutableStateOf(tanggalHariIni()) }
    var pilihTanggal by remember { mutableStateOf(false) }
    var jenis by remember { mutableStateOf("IZIN") }
    var keterangan by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onBatal,
        title = { Text("Izin — ${siswa.nama}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { pilihTanggal = true }) {
                    Text("Tanggal: ${formatTanggalIndonesia(tanggal)}")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("IZIN", "SAKIT").forEach { j ->
                        FilterChip(
                            selected = jenis == j,
                            onClick = { jenis = j },
                            label = { Text(j) }
                        )
                    }
                }
                OutlinedTextField(
                    value = keterangan,
                    onValueChange = { keterangan = it },
                    label = { Text("Keterangan (opsional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSimpan(tanggal, jenis, keterangan) }) { Text("Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onBatal) { Text("Batal") }
        }
    )

    if (pilihTanggal) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.parse(tanggal)
                .atStartOfDay(ZoneId.of("UTC"))
                .toInstant()
                .toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { pilihTanggal = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        tanggal = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.of("UTC"))
                            .toLocalDate()
                            .toString()
                    }
                    pilihTanggal = false
                }) { Text("Pilih") }
            },
            dismissButton = {
                TextButton(onClick = { pilihTanggal = false }) { Text("Batal") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}
