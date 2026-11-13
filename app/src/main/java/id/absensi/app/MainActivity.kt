package id.absensi.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import id.absensi.app.ui.screens.RiwayatKaryawanScreen
import id.absensi.app.ui.theme.AbsensiTheme
import id.absensi.app.ui.theme.AuroraBackground
import kotlinx.coroutines.delay

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
    var tab by remember { mutableIntStateOf(0) }
    var riwayatTarget by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var repo by remember { mutableStateOf<Repository?>(null) }
    var pembaruanOtomatis by remember { mutableStateOf<InfoPembaruan?>(null) }
    var notifAbsen by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val db = DatabaseProvider.init(context)
        repo = Repository(db)
    }

    // Pop-up absen hilang sendiri setelah 2,6 detik (gaya badge "Tersinkron" Kasir).
    LaunchedEffect(notifAbsen) {
        if (notifAbsen != null) {
            delay(2600)
            notifAbsen = null
        }
    }

    // Cek pembaruan diam-diam saat aplikasi dibuka; hasilnya hanya tampil
    // di kartu Pembaruan pada tab Lainnya (layar Absen tidak terganggu).
    LaunchedEffect(Unit) {
        pembaruanOtomatis = cekPembaruan(versiAplikasi(context))
    }

    AbsensiTheme(darkTheme = gelap) {
        AuroraBackground(isDark = gelap) {
            Scaffold(
                bottomBar = {
                    // Layar detail riwayat menutupi tab — nav bawah disembunyikan.
                    if (riwayatTarget == null) {
                        NavigationBar {
                        NavigationBarItem(
                            selected = tab == 0,
                            onClick = { tab = 0 },
                            icon = { Icon(Icons.Filled.CheckCircle, contentDescription = "Absen") },
                            label = { Text("Absen") }
                        )
                        NavigationBarItem(
                            selected = tab == 1,
                            onClick = { tab = 1 },
                            icon = { Icon(Icons.Filled.BarChart, contentDescription = "Rekap") },
                            label = { Text("Rekap") }
                        )
                        NavigationBarItem(
                            selected = tab == 2,
                            onClick = { tab = 2 },
                            icon = { Icon(Icons.Filled.Group, contentDescription = "Karyawan") },
                            label = { Text("Karyawan") }
                        )
                        NavigationBarItem(
                            selected = tab == 3,
                            onClick = { tab = 3 },
                            icon = { Icon(Icons.Filled.Settings, contentDescription = "Lainnya") },
                            label = { Text("Lainnya") }
                        )
                        }
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
                        val target = riwayatTarget
                        when {
                            target != null -> RiwayatKaryawanScreen(
                                repo = r,
                                karyawanId = target.first,
                                prefix = target.second,
                                toleransiMenit = toleransi,
                                onBack = { riwayatTarget = null },
                                onPesan = { notifAbsen = it }
                            )
                            else -> when (tab) {
                                0 -> AbsenScreen(r, toleransi) { notifAbsen = it }
                                1 -> RekapScreen(r, toleransi) { kid, pref -> riwayatTarget = kid to pref }
                                2 -> KaryawanScreen(r)
                                3 -> LainnyaScreen(
                                    repo = r,
                                    toleransiMenit = toleransi,
                                    onToleransiSimpan = {
                                        toleransi = it
                                        prefs.edit().putInt("toleransi", it).apply()
                                    },
                                    pembaruanOtomatis = pembaruanOtomatis
                                )
                            }
                        }
                    }
                    // Pop-up absen: anak langsung Box layar penuh supaya
                    // benar-benar di tengah atas (align di dalam Scaffold
                    // tidak berpengaruh — pelajaran dari badge Apotek).
                    NotifAbsenPopUp(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 12.dp),
                        pesan = notifAbsen,
                        onTutup = { notifAbsen = null }
                    )
                }
            }
        }
    }
}

/** Pil mengambang gaya badge "Tersinkron" Kasir: navy + ikon centang cyan. */
@Composable
private fun NotifAbsenPopUp(
    modifier: Modifier = Modifier,
    pesan: String?,
    onTutup: () -> Unit
) {
    AnimatedVisibility(
        visible = pesan != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1B3A5C),
            shadowElevation = 8.dp,
            modifier = Modifier.clickable { onTutup() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF40C4FF),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = pesan ?: "",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
