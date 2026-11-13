package id.absensi.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import id.absensi.app.data.DatabaseProvider
import id.absensi.app.data.InfoPembaruan
import id.absensi.app.data.Repository
import id.absensi.app.data.cekPembaruan
import id.absensi.app.data.versiAplikasi
import id.absensi.app.ui.screens.AbsenScreen
import id.absensi.app.ui.screens.KaryawanScreen
import id.absensi.app.ui.screens.LainnyaScreen
import id.absensi.app.ui.screens.RekapScreen
import id.absensi.app.ui.theme.AbsensiTheme
import id.absensi.app.ui.theme.AuroraBackground

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("absensi_prefs", Context.MODE_PRIVATE)
        setContent {
            AbsensiApp(prefs)
        }
    }
}

@Composable
private fun AbsensiApp(prefs: SharedPreferences) {
    val context = LocalContext.current
    var gelap by remember { mutableStateOf(prefs.getBoolean("gelap", true)) }
    var toleransi by remember { mutableIntStateOf(prefs.getInt("toleransi", 15)) }
    var pin by remember { mutableStateOf(prefs.getString("pin_admin", "") ?: "") }
    var tab by remember { mutableIntStateOf(0) }
    var adminTerbuka by remember { mutableStateOf(false) }
    var tabTunggu by remember { mutableIntStateOf(-1) }
    var modePin by remember { mutableStateOf<String?>(null) }
    var repo by remember { mutableStateOf<Repository?>(null) }
    var pembaruanOtomatis by remember { mutableStateOf<InfoPembaruan?>(null) }

    LaunchedEffect(Unit) {
        val db = DatabaseProvider.init(context)
        repo = Repository(db)
    }

    // Cek pembaruan diam-diam saat aplikasi dibuka; hasilnya hanya tampil
    // di kartu Pembaruan pada tab Lainnya (layar Absen tidak terganggu).
    LaunchedEffect(Unit) {
        pembaruanOtomatis = cekPembaruan(versiAplikasi(context))
    }

    fun mintaAdmin(keTab: Int) {
        if (adminTerbuka) {
            tab = keTab
            return
        }
        tabTunggu = keTab
        modePin = if (pin.isEmpty()) "buat" else "masuk"
    }

    AbsensiTheme(darkTheme = gelap) {
        AuroraBackground(isDark = gelap) {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            selected = tab == 0,
                            onClick = { tab = 0 },
                            icon = { Icon(Icons.Filled.CheckCircle, contentDescription = "Absen") },
                            label = { Text("Absen") }
                        )
                        NavigationBarItem(
                            selected = tab == 1,
                            onClick = { mintaAdmin(1) },
                            icon = { Icon(Icons.Filled.BarChart, contentDescription = "Rekap") },
                            label = { Text("Rekap") }
                        )
                        NavigationBarItem(
                            selected = tab == 2,
                            onClick = { mintaAdmin(2) },
                            icon = { Icon(Icons.Filled.Group, contentDescription = "Karyawan") },
                            label = { Text("Karyawan") }
                        )
                        NavigationBarItem(
                            selected = tab == 3,
                            onClick = { mintaAdmin(3) },
                            icon = { Icon(Icons.Filled.Settings, contentDescription = "Lainnya") },
                            label = { Text("Lainnya") }
                        )
                    }
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    val r = repo
                    if (r == null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        when (tab) {
                            0 -> AbsenScreen(r, toleransi)
                            1 -> RekapScreen(r, toleransi)
                            2 -> KaryawanScreen(r)
                            3 -> LainnyaScreen(
                                repo = r,
                                toleransiMenit = toleransi,
                                pinSaatIni = pin,
                                onToleransiSimpan = {
                                    toleransi = it
                                    prefs.edit().putInt("toleransi", it).apply()
                                },
                                onPinSimpan = {
                                    pin = it
                                    prefs.edit().putString("pin_admin", it).apply()
                                },
                                pembaruanOtomatis = pembaruanOtomatis
                            )
                        }
                    }
                }
            }
        }
    }

    when (modePin) {
        "buat" -> DialogPinBuat(
            onBatal = { modePin = null; tabTunggu = -1 },
            onOk = { pinBaru ->
                pin = pinBaru
                prefs.edit().putString("pin_admin", pinBaru).apply()
                adminTerbuka = true
                modePin = null
                if (tabTunggu >= 0) tab = tabTunggu
                tabTunggu = -1
            }
        )
        "masuk" -> DialogPinMasuk(
            onBatal = { modePin = null; tabTunggu = -1 },
            onOk = {
                adminTerbuka = true
                modePin = null
                if (tabTunggu >= 0) tab = tabTunggu
                tabTunggu = -1
            },
            cekPin = { it == pin }
        )
    }
}

@Composable
private fun DialogPinMasuk(
    onBatal: () -> Unit,
    onOk: () -> Unit,
    cekPin: (String) -> Boolean
) {
    var pin by remember { mutableStateOf("") }
    var salah by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onBatal,
        title = { Text("PIN Admin") },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                            pin = it
                            salah = false
                        }
                    },
                    label = { Text("PIN 4 digit") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true
                )
                if (salah) {
                    Spacer(Modifier.height(8.dp))
                    Text("PIN salah, coba lagi.", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (cekPin(pin)) onOk() else salah = true },
                enabled = pin.length == 4
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onBatal) { Text("Batal") }
        }
    )
}

@Composable
private fun DialogPinBuat(
    onBatal: () -> Unit,
    onOk: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var ulang by remember { mutableStateOf("") }
    var salah by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onBatal,
        title = { Text("Buat PIN Admin") },
        text = {
            Column {
                Text("Menu Rekap, Karyawan, dan Lainnya dikunci PIN. Buat PIN 4 digit untuk admin.")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        if (it.length <= 4 && it.all { c -> c.isDigit() }) pin = it
                    },
                    label = { Text("PIN 4 digit") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = ulang,
                    onValueChange = {
                        if (it.length <= 4 && it.all { c -> c.isDigit() }) ulang = it
                    },
                    label = { Text("Ulangi PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true
                )
                salah?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    pin.length != 4 -> salah = "PIN harus 4 digit."
                    pin != ulang -> salah = "PIN tidak sama dengan ulangannya."
                    else -> onOk(pin)
                }
            }) { Text("Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onBatal) { Text("Batal") }
        }
    )
}
