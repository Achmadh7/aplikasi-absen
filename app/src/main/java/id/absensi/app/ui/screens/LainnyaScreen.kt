package id.absensi.app.ui.screens

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import id.absensi.app.data.Repository
import id.absensi.app.ui.KacaCard
import kotlinx.coroutines.launch

@Composable
fun LainnyaScreen(
    repo: Repository,
    toleransiMenit: Int,
    pinSaatIni: String,
    onToleransiSimpan: (Int) -> Unit,
    onPinSimpan: (String) -> Unit
) {
    var toleransiTeks by remember(toleransiMenit) { mutableStateOf(toleransiMenit.toString()) }
    var dialogPin by remember { mutableStateOf(false) }
    var jumlahKaryawan by remember { mutableStateOf(0) }

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
                Text(text = "Keamanan", fontWeight = FontWeight.Bold)
                Text(text = "Menu Rekap, Karyawan, dan Lainnya dikunci PIN admin.")
                Button(onClick = { dialogPin = true }) { Text("Ubah PIN Admin") }
            }
        }
        KacaCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = "Tentang", fontWeight = FontWeight.Bold)
                Text(text = "Absensi — versi 1.0.0")
                Text(text = "Aplikasi absensi karyawan. Satu HP di kantor, karyawan absen dengan tap nama.")
                Text(text = "Karyawan aktif: $jumlahKaryawan")
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (dialogPin) {
        var lama by remember { mutableStateOf("") }
        var baru by remember { mutableStateOf("") }
        var konfirmasi by remember { mutableStateOf("") }
        var salah by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { dialogPin = false },
            title = { Text("Ubah PIN Admin") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = lama,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) lama = it },
                        label = { Text("PIN lama") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = baru,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) baru = it },
                        label = { Text("PIN baru (4 digit)") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = konfirmasi,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) konfirmasi = it },
                        label = { Text("Ulangi PIN baru") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    salah?.let {
                        Text(text = it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when {
                        lama != pinSaatIni -> salah = "PIN lama salah."
                        baru.length != 4 -> salah = "PIN baru harus 4 digit."
                        baru != konfirmasi -> salah = "PIN baru tidak sama dengan konfirmasi."
                        else -> {
                            onPinSimpan(baru)
                            dialogPin = false
                        }
                    }
                }) { Text("Simpan") }
            },
            dismissButton = {
                TextButton(onClick = { dialogPin = false }) { Text("Batal") }
            }
        )
    }
}
