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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import id.absensi.app.data.Repository
import id.absensi.app.ui.screens.AbsenScreen
import id.absensi.app.ui.screens.RekapScreen
import id.absensi.app.ui.screens.RiwayatSiswaScreen
import id.absensi.app.ui.screens.SiswaScreen
import id.absensi.app.ui.theme.AbsensiTheme
import id.absensi.app.ui.theme.AuroraBackground
import id.absensi.app.ui.theme.BentoEmerald
import id.absensi.app.ui.theme.BentoIndigo
import id.absensi.app.ui.theme.BentoSlate
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
    val gelap = false
    val toleransi = remember { prefs.getInt("toleransi", 15) }
    var tab by remember { mutableIntStateOf(0) }
    var riwayatTarget by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var repo by remember { mutableStateOf<Repository?>(null) }
    var notifAbsen by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val db = DatabaseProvider.init(context)
        repo = Repository(db)
    }

    LaunchedEffect(notifAbsen) {
        if (notifAbsen != null) {
            delay(2600)
            notifAbsen = null
        }
    }

    AbsensiTheme(darkTheme = gelap) {
        AuroraBackground(isDark = gelap) {
            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    if (riwayatTarget == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(28.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                shadowElevation = 6.dp
                            ) {
                                val itemColors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = BentoIndigo,
                                    selectedTextColor = BentoIndigo,
                                    indicatorColor = BentoIndigo.copy(alpha = 0.12f),
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                NavigationBar(
                                    containerColor = Color.Transparent,
                                    tonalElevation = 0.dp
                                ) {
                                    NavigationBarItem(
                                        selected = tab == 0,
                                        onClick = { tab = 0 },
                                        colors = itemColors,
                                        icon = { Icon(Icons.Filled.CheckCircle, contentDescription = "Absen") },
                                        label = { Text("Absen") }
                                    )
                                    NavigationBarItem(
                                        selected = tab == 1,
                                        onClick = { tab = 1 },
                                        colors = itemColors,
                                        icon = { Icon(Icons.Filled.BarChart, contentDescription = "Rekap") },
                                        label = { Text("Rekap") }
                                    )
                                    NavigationBarItem(
                                        selected = tab == 2,
                                        onClick = { tab = 2 },
                                        colors = itemColors,
                                        icon = { Icon(Icons.Filled.Group, contentDescription = "Siswa") },
                                        label = { Text("Siswa") }
                                    )
                                }
                            }
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
                            target != null -> RiwayatSiswaScreen(
                                repo = r,
                                siswaId = target.first,
                                prefix = target.second,
                                toleransiMenit = toleransi,
                                onBack = { riwayatTarget = null },
                                onPesan = { notifAbsen = it }
                            )
                            else -> when (tab) {
                                0 -> AbsenScreen(r, toleransi) { notifAbsen = it }
                                1 -> RekapScreen(r, toleransi) { sid, pref -> riwayatTarget = sid to pref }
                                else -> SiswaScreen(r)
                            }
                        }
                    }
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
            color = BentoSlate,
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
                    tint = BentoEmerald,
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
