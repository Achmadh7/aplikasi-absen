package id.absensi.app.ui.screens

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
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.absensi.app.data.RekapKaryawan
import id.absensi.app.data.Repository
import id.absensi.app.data.formatBulanIndonesia
import id.absensi.app.ui.KacaCard
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun RekapScreen(repo: Repository, toleransiMenit: Int) {
    var bulan by remember { mutableStateOf(YearMonth.now()) }
    var rekap by remember { mutableStateOf<List<RekapKaryawan>>(emptyList()) }
    val prefix = bulan.format(DateTimeFormatter.ofPattern("yyyy-MM"))

    LaunchedEffect(bulan) {
        rekap = repo.rekapBulan(prefix, toleransiMenit)
    }

    val totalHadir = rekap.sumOf { it.hadir }
    val totalTelat = rekap.sumOf { it.terlambat }
    val totalIzin = rekap.sumOf { it.izin + it.sakit + it.cuti }
    val totalAlpa = rekap.sumOf { it.alpa }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Rekap",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold
        )
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
            KartuRingkasan("Izin/Sakit/Cuti", totalIzin.toString(), Color(0xFF2E5A88), Color(0xFFD3E3F3), Modifier.weight(1f))
            KartuRingkasan("Alpa", totalAlpa.toString(), Color(0xFFB3261E), Color(0xFFFDECEA), Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Per karyawan",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Spacer(Modifier.height(8.dp))
        if (rekap.isEmpty()) {
            KacaCard(Modifier.fillMaxWidth()) {
                Text(
                    text = "Belum ada karyawan.",
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(rekap, key = { it.karyawanId }) { r ->
                    KacaCard(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = r.nama, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "H ${r.hadir}  •  T ${r.terlambat}  •  I ${r.izin}  •  S ${r.sakit}  •  C ${r.cuti}  •  A ${r.alpa}",
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

@Composable
private fun KartuRingkasan(
    label: String,
    nilai: String,
    fg: Color,
    bg: Color,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.Surface(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        color = bg
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = label, color = Color(0xFF5A6878), fontSize = 13.sp)
            Text(text = nilai, color = fg, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}
