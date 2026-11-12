package id.absensi.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.absensi.app.data.StatusHadir
import id.absensi.app.data.labelStatus
import id.absensi.app.data.menitKeJam

/** Kartu kaca R1: permukaan gelap + garis tepi tipis. */
@Composable
fun KacaCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content
    )
}

private val chipWarna = mapOf(
    StatusHadir.HADIR to (Color(0xFF2E7D32) to Color(0xFFE8F5E9)),
    StatusHadir.TERLAMBAT to (Color(0xFFB26A00) to Color(0xFFFFF3E0)),
    StatusHadir.BELUM to (Color(0xFF5A6878) to Color(0xFFE8ECF1)),
    StatusHadir.IZIN to (Color(0xFF2E5A88) to Color(0xFFD3E3F3)),
    StatusHadir.SAKIT to (Color(0xFF2E5A88) to Color(0xFFD3E3F3)),
    StatusHadir.CUTI to (Color(0xFF2E5A88) to Color(0xFFD3E3F3)),
    StatusHadir.ALPA to (Color(0xFFB3261E) to Color(0xFFFDECEA))
)

/** Pil status kehadiran. Tambahkan jam masuk bila ada, mis. "Hadir 08.02". */
@Composable
fun StatusChip(status: StatusHadir, masukMenit: Int? = null) {
    val (fg, bg) = chipWarna[status] ?: (Color.Gray to Color.LightGray)
    val teks = if (masukMenit != null && (status == StatusHadir.HADIR || status == StatusHadir.TERLAMBAT)) {
        "${labelStatus(status)} ${menitKeJam(masukMenit)}"
    } else {
        labelStatus(status)
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bg
    ) {
        Text(
            text = teks,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
