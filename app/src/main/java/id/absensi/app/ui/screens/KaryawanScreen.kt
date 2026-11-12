package id.absensi.app.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import id.absensi.app.data.KaryawanEntity
import id.absensi.app.data.Repository
import id.absensi.app.data.Shift
import id.absensi.app.data.formatTanggalIndonesia
import id.absensi.app.data.tanggalHariIni
import id.absensi.app.ui.KacaCard
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KaryawanScreen(repo: Repository) {
    var daftar by remember { mutableStateOf<List<KaryawanEntity>>(emptyList()) }
    var dialogTambah by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<KaryawanEntity?>(null) }
    var izinTarget by remember { mutableStateOf<KaryawanEntity?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun muat() {
        daftar = repo.semuaKaryawan()
    }

    LaunchedEffect(Unit) { muat() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Karyawan",
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
                        text = "Belum ada karyawan. Ketuk tombol + untuk menambah.",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(daftar, key = { it.id }) { k ->
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
                                            text = k.nama.split(" ").take(2)
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
                                        text = k.nama + if (k.aktif) "" else " (nonaktif)",
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${k.jabatan} • ${Shift.dariNama(k.shift).label}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { izinTarget = k }) {
                                    Icon(Icons.Filled.Event, contentDescription = "Izin")
                                }
                                IconButton(onClick = { editTarget = k }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Ubah")
                                }
                                IconButton(onClick = {
                                    scope.launch {
                                        if (k.aktif) repo.nonaktifkanKaryawan(k.id)
                                        else repo.aktifkanKaryawan(k.id)
                                        muat()
                                    }
                                }) {
                                    Icon(
                                        if (k.aktif) Icons.Filled.PersonOff else Icons.Filled.PersonAdd,
                                        contentDescription = if (k.aktif) "Nonaktifkan" else "Aktifkan"
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
            Icon(Icons.Filled.Add, contentDescription = "Tambah karyawan")
        }
    }

    if (dialogTambah || editTarget != null) {
        DialogKaryawan(
            awal = editTarget,
            onBatal = { dialogTambah = false; editTarget = null },
            onSimpan = { nama, jabatan, shift ->
                scope.launch {
                    if (editTarget != null) {
                        repo.ubahKaryawan(editTarget!!.copy(nama = nama, jabatan = jabatan, shift = shift.name))
                    } else {
                        repo.tambahKaryawan(nama, jabatan, shift)
                    }
                    dialogTambah = false
                    editTarget = null
                    muat()
                }
            }
        )
    }

    izinTarget?.let { k ->
        DialogIzin(
            karyawan = k,
            onBatal = { izinTarget = null },
            onSimpan = { tanggal, jenis, keterangan ->
                scope.launch {
                    repo.catatIzin(k.id, tanggal, jenis, keterangan)
                    izinTarget = null
                }
            }
        )
    }
}

@Composable
private fun DialogKaryawan(
    awal: KaryawanEntity?,
    onBatal: () -> Unit,
    onSimpan: (String, String, Shift) -> Unit
) {
    var nama by remember { mutableStateOf(awal?.nama ?: "") }
    var jabatan by remember { mutableStateOf(awal?.jabatan ?: "") }
    var shift by remember { mutableStateOf(awal?.let { Shift.dariNama(it.shift) } ?: Shift.SHIFT_1) }
    var salah by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onBatal,
        title = { Text(if (awal == null) "Tambah Karyawan" else "Ubah Karyawan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = nama,
                    onValueChange = { nama = it; salah = false },
                    label = { Text("Nama") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = jabatan,
                    onValueChange = { jabatan = it },
                    label = { Text("Jabatan") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Shift.entries.forEach { s ->
                        FilterChip(
                            selected = shift == s,
                            onClick = { shift = s },
                            label = { Text(s.label) }
                        )
                    }
                }
                if (salah) {
                    Text("Nama tidak boleh kosong.", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (nama.isBlank()) {
                    salah = true
                } else {
                    onSimpan(nama, jabatan.ifBlank { "-" }, shift)
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
private fun DialogIzin(
    karyawan: KaryawanEntity,
    onBatal: () -> Unit,
    onSimpan: (String, String, String) -> Unit
) {
    var jenis by remember { mutableStateOf("IZIN") }
    var tanggal by remember { mutableStateOf(LocalDate.parse(tanggalHariIni())) }
    var keterangan by remember { mutableStateOf("") }
    var pilihTanggal by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onBatal,
        title = { Text("Izin — ${karyawan.nama}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("IZIN", "SAKIT", "CUTI").forEach { j ->
                        FilterChip(
                            selected = jenis == j,
                            onClick = { jenis = j },
                            label = { Text(j.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
                Text(text = "Tanggal: ${formatTanggalIndonesia(tanggal.toString())}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { tanggal = LocalDate.now() }) { Text("Hari ini") }
                    TextButton(onClick = { tanggal = LocalDate.now().plusDays(1) }) { Text("Besok") }
                    TextButton(onClick = { pilihTanggal = true }) { Text("Pilih tanggal") }
                }
                OutlinedTextField(
                    value = keterangan,
                    onValueChange = { keterangan = it },
                    label = { Text("Keterangan (boleh kosong)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSimpan(tanggal.toString(), jenis, keterangan) }) { Text("Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onBatal) { Text("Batal") }
        }
    )
    if (pilihTanggal) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = tanggal.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { pilihTanggal = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        tanggal = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
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
