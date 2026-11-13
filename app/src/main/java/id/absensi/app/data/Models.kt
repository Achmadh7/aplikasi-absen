package id.absensi.app.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Jam kerja. Shift 1 08.00-15.00, shift 2 16.00-22.00. */
enum class Shift(val label: String, val masukMenit: Int, val pulangMenit: Int) {
    SHIFT_1("Shift 1", 8 * 60, 15 * 60),
    SHIFT_2("Shift 2", 16 * 60, 22 * 60);

    companion object {
        fun dariNama(nama: String): Shift = entries.firstOrNull { it.name == nama } ?: SHIFT_1
    }
}

enum class StatusHadir { BELUM, HADIR, TERLAMBAT, IZIN, SAKIT, CUTI, ALPA }

fun labelStatus(status: StatusHadir): String = when (status) {
    StatusHadir.BELUM -> "Belum absen"
    StatusHadir.HADIR -> "Hadir"
    StatusHadir.TERLAMBAT -> "Terlambat"
    StatusHadir.IZIN -> "Izin"
    StatusHadir.SAKIT -> "Sakit"
    StatusHadir.CUTI -> "Cuti"
    StatusHadir.ALPA -> "Alpa"
}

/** 485 -> "08.05". */
fun menitKeJam(menit: Int): String = "%02d.%02d".format(menit / 60, menit % 60)

fun menitSekarang(): Int {
    val t = LocalTime.now()
    return t.hour * 60 + t.minute
}

fun tanggalHariIni(): String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

/** "2026-11-12" -> "Kamis, 12 Nov 2026". */
fun formatTanggalIndonesia(iso: String): String {
    val d = LocalDate.parse(iso)
    return DateTimeFormatter.ofPattern("EEEE, d MMM yyyy", Locale("id", "ID")).format(d)
}

/** "2026-11" -> "November 2026". */
fun formatBulanIndonesia(prefix: String): String {
    val ym = YearMonth.parse(prefix)
    return DateTimeFormatter.ofPattern("MMMM yyyy", Locale("id", "ID")).format(ym)
}

/** HADIR kalau masuk tidak lewat standar + toleransi, selebihnya TERLAMBAT. */
fun statusMasuk(standarMenit: Int, masukMenit: Int, toleransiMenit: Int): StatusHadir =
    if (masukMenit <= standarMenit + toleransiMenit) StatusHadir.HADIR else StatusHadir.TERLAMBAT

/** "08.05" atau "08:05" -> 485. Null bila format salah. */
fun parseJamMenit(teks: String): Int? {
    val pisah = teks.trim().split(".", ":")
    if (pisah.size != 2) return null
    val jam = pisah[0].toIntOrNull() ?: return null
    val menitTeks = pisah[1]
    if (menitTeks.length != 2) return null
    val menit = menitTeks.toIntOrNull() ?: return null
    if (jam !in 0..23 || menit !in 0..59) return null
    return jam * 60 + menit
}

/** Semua hari kerja (Senin-Jumat) di bulan `prefix` ("yyyy-MM") sampai tanggal `sampai`. */
fun hariKerjaBulan(prefix: String, sampai: LocalDate = LocalDate.now()): List<LocalDate> {
    val ym = YearMonth.parse(prefix)
    val akhir = minOf(ym.atEndOfMonth(), sampai)
    val hasil = mutableListOf<LocalDate>()
    var d = ym.atDay(1)
    while (!d.isAfter(akhir)) {
        if (d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY) hasil.add(d)
        d = d.plusDays(1)
    }
    return hasil
}

/** Satu baris riwayat harian untuk layar detail per karyawan. */
data class RiwayatHari(
    val tanggal: String,
    val status: StatusHadir,
    val masukMenit: Int?,
    val pulangMenit: Int?,
    /** Menit lewat jam masuk shift, hanya bila TERLAMBAT. */
    val terlambatMenit: Int = 0
)

/** Baris riwayat satu karyawan selama satu bulan — murni (bisa dites).
 * Izin didahulukan; tanpa catatan masuk dan tanpa izin = ALPA. */
fun barisRiwayat(
    karyawan: KaryawanEntity,
    absensi: List<AbsensiEntity>,
    izin: List<IzinEntity>,
    prefix: String,
    sampai: LocalDate,
    toleransiMenit: Int
): List<RiwayatHari> {
    val shift = Shift.dariNama(karyawan.shift)
    val absPerTanggal = absensi.associateBy { it.tanggal }
    val izinPerTanggal = izin.associateBy { it.tanggal }
    return hariKerjaBulan(prefix, sampai).map { d ->
        val tgl = d.toString()
        val iz = izinPerTanggal[tgl]
        if (iz != null) {
            val s = when (iz.jenis) {
                "SAKIT" -> StatusHadir.SAKIT
                "CUTI" -> StatusHadir.CUTI
                else -> StatusHadir.IZIN
            }
            RiwayatHari(tgl, s, null, null)
        } else {
            val a = absPerTanggal[tgl]
            val masuk = a?.masukMenit
            if (masuk == null) {
                RiwayatHari(tgl, StatusHadir.ALPA, null, a?.pulangMenit)
            } else {
                val s = statusMasuk(shift.masukMenit, masuk, toleransiMenit)
                RiwayatHari(
                    tgl, s, masuk, a?.pulangMenit,
                    if (s == StatusHadir.TERLAMBAT) masuk - shift.masukMenit else 0
                )
            }
        }
    }
}

/** Baris karyawan di layar Absen. */
data class KaryawanAbsenUi(
    val id: Long,
    val nama: String,
    val jabatan: String,
    val shift: Shift,
    val status: StatusHadir,
    val masukMenit: Int?,
    val pulangMenit: Int?
)

/** Rekap satu karyawan selama satu bulan. */
data class RekapKaryawan(
    val karyawanId: Long,
    val nama: String,
    val jabatan: String,
    val hadir: Int,
    val terlambat: Int,
    val izin: Int,
    val sakit: Int,
    val cuti: Int,
    val alpa: Int
) {
    val persenHadir: Int
        get() {
            val total = hadir + terlambat + izin + sakit + cuti + alpa
            if (total == 0) return 100
            return ((hadir + terlambat) * 100) / total
        }
}
