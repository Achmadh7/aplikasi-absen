package id.absensi.app.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.absensi.app.data.KaryawanAbsenUi
import id.absensi.app.data.Repository
import id.absensi.app.data.StatusHadir
import id.absensi.app.data.formatTanggalIndonesia
import id.absensi.app.data.labelStatus
import id.absensi.app.data.menitKeJam
import id.absensi.app.data.menitSekarang
import id.absensi.app.data.tanggalHariIni
import id.absensi.app.ui.KacaCard
import id.absensi.app.ui.StatusChip
import id.absensi.app.ui.theme.AuroraCyan
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AbsenScreen(repo: Repository, toleransiMenit: Int) {
    var daftar by remember { mutableStateOf<List<KaryawanAbsenUi>>(emptyList()) }
    var jamMenit by remember { mutableStateOf(menitSekarang()) }
    var pesan by remember { mutableStateOf<String?>(null) }
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

    fun ketuk(k: KaryawanAbsenUi) {
        scope.launch {
            when {
                k.status == StatusHadir.IZIN || k.status == StatusHadir.SAKIT || k.status == StatusHadir.CUTI -> {
                    pesan = "${k.nama} hari ini ${labelStatus(k.status).lowercase()}."
                }
                k.pulangMenit != null -> {
                    pesan = "${k.nama} sudah pulang ${menitKeJam(k.pulangMenit)}."
                }
                else -> {
                    val hasil = repo.tapAbsenById(k.id, menitSekarang(), tanggal)
                    if (hasil != null) {
                        pesan = if (hasil.pulangMenit != null) {
                            "${k.nama} • Pulang ${menitKeJam(hasil.pulangMenit)}"
                        } else {
                            "${k.nama} • Masuk ${menitKeJam(hasil.masukMenit ?: menitSekarang())}"
                        }
                    }
                    muat()
                }
            }
        }
    }

    val sudah = daftar.count { it.status == StatusHadir.HADIR || it.status == StatusHadir.TERLAMBAT }
    val telat = daftar.count { it.status == StatusHadir.TERLAMBAT }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Absensi",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = formatTanggalIndonesia(tanggal),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = menitKeJam(jamMenit),
            fontSize = 52.sp,
            fontWeight = FontWeight.ExtraBold,
            color = AuroraCyan
        )
        Spacer(Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = "$sudah dari ${daftar.size} sudah absen  •  $telat terlambat",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                fontWeight = FontWeight.Bold
            )
        }
        pesan?.let {
            Spacer(Modifier.height(8.dp))
            KacaCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { pesan = null }
            ) {
                Text(
                    text = it,
                    modifier = Modifier.padding(12.dp),
                    fontWeight = FontWeight.Bold,
                    color = AuroraCyan
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (daftar.isEmpty()) {
            KacaCard(Modifier.fillMaxWidth()) {
                Text(
                    text = "Belum ada karyawan. Tambah dulu di menu Karyawan.",
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(daftar, key = { it.id }) { k ->
                    KacaCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { ketuk(k) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            ) {
                                val init = k.nama.split(" ").take(2).joinToString("") { it.first().toString() }
                                Box(
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = init,
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
                                Text(text = k.nama, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${k.jabatan} • ${k.shift.label}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusChip(k.status, k.masukMenit)
                        }
                    }
                }
            }
        }
    }
}
