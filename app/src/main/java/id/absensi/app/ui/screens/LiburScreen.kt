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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import id.absensi.app.data.LiburEntity
import id.absensi.app.data.Repository
import id.absensi.app.data.formatTanggalIndonesia
import id.absensi.app.data.tanggalHariIni
import id.absensi.app.ui.KacaCard
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch

/** S4 — kelola hari libur / tanggal merah. Hari libur bukan hari kerja: tidak ada Alpa. */
@Composable
fun LiburScreen(repo: Repository, onBack: () -> Unit) {
    var daftar by remember { mutableStateOf<List<LiburEntity>>(emptyList()) }
    var dialogTambah by remember { mutableStateOf(false) }
    var hapusTarget by remember { mutableStateOf<LiburEntity?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun muat() {
        daftar = repo.daftarLibur()
    }

    LaunchedEffect(Unit) { muat() }
    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali")
            }
            Text(
                text = "Hari Libur",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Hari libur tidak dihitung sebagai hari kerja — karyawan tidak ditandai Alpa, dan hari itu tidak muncul di riwayat.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = { dialogTambah = true }) {
            Text("Tambah Hari Libur")
        }
        Spacer(Modifier.height(12.dp))
        if (daftar.isEmpty()) {
            KacaCard(Modifier.fillMaxWidth()) {
                Text(
                    text = "Belum ada hari libur yang dicatat.",
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(daftar, key = { it.tanggal }) { libur ->
                    KacaCard(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = formatTanggalIndonesia(libur.tanggal),
                                    fontWeight = FontWeight.Bold
                                )
                                if (libur.keterangan.isNotBlank()) {
                                    Text(
                                        text = libur.keterangan,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(onClick = { hapusTarget = libur }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Hapus hari libur")
                            }
                        }
                    }
                }
            }
        }
    }

    if (dialogTambah) {
        DialogTambahLibur(
            onBatal = { dialogTambah = false },
            onSimpan = { tanggal, keterangan ->
                scope.launch {
                    repo.tambahLibur(tanggal, keterangan)
                    dialogTambah = false
                    muat()
                }
            }
        )
    }

    val target = hapusTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { hapusTarget = null },
            title = { Text("Hapus hari libur?") },
            text = { Text("Tanggal ${formatTanggalIndonesia(target.tanggal)} akan dihitung lagi sebagai hari kerja.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repo.hapusLibur(target.tanggal)
                        hapusTarget = null
                        muat()
                    }
                }) { Text("Hapus") }
            },
            dismissButton = {
                TextButton(onClick = { hapusTarget = null }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun DialogTambahLibur(
    onBatal: () -> Unit,
    onSimpan: (String, String) -> Unit
) {
    var tanggal by remember { mutableStateOf(LocalDate.parse(tanggalHariIni())) }
    var keterangan by remember { mutableStateOf("") }
    var pilihTanggal by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onBatal,
        title = { Text("Tambah Hari Libur") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Tanggal: ${formatTanggalIndonesia(tanggal.toString())}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { tanggal = LocalDate.now() }) { Text("Hari ini") }
                    TextButton(onClick = { tanggal = LocalDate.now().plusDays(1) }) { Text("Besok") }
                    TextButton(onClick = { pilihTanggal = true }) { Text("Pilih tanggal") }
                }
                OutlinedTextField(
                    value = keterangan,
                    onValueChange = { keterangan = it },
                    label = { Text("Keterangan (cth: Hari Raya)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSimpan(tanggal.toString(), keterangan) }) { Text("Simpan") }
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
