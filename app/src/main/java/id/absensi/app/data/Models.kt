package id.absensi.app.data

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Jam sekolah. Pagi 07.00-13.00, Siang 13.00-17.00. */
enum class JamSekolah(val label: String, val masukMenit: Int, val pulangMenit: Int) {
    PAGI("Pagi", 7 * 60, 13 * 60),
    SIANG("Siang", 13 * 60, 17 * 60);

    companion object {
        fun dariNama(nama: String): JamSekolah = entries.firstOrNull { it.name == nama } ?: PAGI
    }
}

enum class StatusHadir { BELUM, HADIR, TERLAMBAT, IZIN, SAKIT, ALPA }

fun labelStatus(status: StatusHadir): String = when (status) {
    StatusHadir.BELUM -> "Belum absen"
    StatusHadir.HADIR -> "Hadir"
    StatusHadir.TERLAMBAT -> "Terlambat"
    StatusHadir.IZIN -> "Izin"
    StatusHadir.SAKIT -> "Sakit"
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

/**
 * Normalisasi nomor WhatsApp Indonesia ke format internasional tanpa '+' (contoh: 08123456789 -> 628123456789).
 * Mengembalikan null bila kosong atau terlalu pendek (< 9 digit).
 */
fun normalisasiNomorWa(nomor: String): String? {
    val digit = nomor.filter { it.isDigit() }
    if (digit.length < 9) return null
    val hasil = when {
        digit.startsWith("62") -> digit
        digit.startsWith("0") -> "62" + digit.drop(1)
        digit.startsWith("8") -> "62$digit"
        else -> digit
    }
    return if (hasil.length in 10..16) hasil else null
}

/** Susun pesan notifikasi absensi untuk dikirim ke WhatsApp orang tua. */
fun buatPesanWaAbsen(
    namaSiswa: String,
    kelas: String,
    status: StatusHadir,
    jamMenit: Int,
    pulang: Boolean,
    tanggalIso: String
): String {
    val infoKelas = if (kelas.isNotBlank()) " ($kelas)" else ""
    val jam = menitKeJam(jamMenit)
    val tgl = formatTanggalIndonesia(tanggalIso)
    return if (pulang) {
        "Halo Bapak/Ibu, menginformasikan ananda $namaSiswa$infoKelas telah absen PULANG pada pukul $jam ($tgl)."
    } else {
        "Halo Bapak/Ibu, menginformasikan ananda $namaSiswa$infoKelas telah absen MASUK pada pukul $jam ($tgl) dengan status ${labelStatus(status)}."
    }
}

/** Buat URL deep-link https://wa.me/<nomor>?text=<pesan>. Null bila nomor WA tidak valid. */
fun buatUrlWa(nomorWa: String, pesan: String): String? {
    val norm = normalisasiNomorWa(nomorWa) ?: return null
    val encoded = URLEncoder.encode(pesan, StandardCharsets.UTF_8.toString()).replace("+", "%20")
    return "https://wa.me/$norm?text=$encoded"
}

/** Semua hari sekolah (Senin-Jumat) di bulan `prefix` ("yyyy-MM") sampai tanggal `sampai`. */
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

/** Satu baris riwayat harian untuk layar detail per siswa. */
data class RiwayatHari(
    val tanggal: String,
    val status: StatusHadir,
    val masukMenit: Int?,
    val pulangMenit: Int?,
    /** Menit lewat jam masuk sekolah, hanya bila TERLAMBAT. */
    val terlambatMenit: Int = 0
)

/** Baris riwayat satu siswa selama satu bulan — murni (bisa dites).
 * Izin didahulukan; tanpa catatan masuk dan tanpa izin = ALPA. */
fun barisRiwayat(
    siswa: SiswaEntity,
    absensi: List<AbsensiEntity>,
    izin: List<IzinEntity>,
    prefix: String,
    sampai: LocalDate,
    toleransiMenit: Int
): List<RiwayatHari> {
    val sesi = JamSekolah.dariNama(siswa.sesi)
    val absPerTanggal = absensi.associateBy { it.tanggal }
    val izinPerTanggal = izin.associateBy { it.tanggal }
    return hariKerjaBulan(prefix, sampai).map { d ->
        val tgl = d.toString()
        val iz = izinPerTanggal[tgl]
        if (iz != null) {
            val s = if (iz.jenis == "SAKIT") StatusHadir.SAKIT else StatusHadir.IZIN
            RiwayatHari(tgl, s, null, null)
        } else {
            val a = absPerTanggal[tgl]
            val masuk = a?.masukMenit
            if (masuk == null) {
                RiwayatHari(tgl, StatusHadir.ALPA, null, a?.pulangMenit)
            } else {
                val s = statusMasuk(sesi.masukMenit, masuk, toleransiMenit)
                RiwayatHari(
                    tgl, s, masuk, a?.pulangMenit,
                    if (s == StatusHadir.TERLAMBAT) masuk - sesi.masukMenit else 0
                )
            }
        }
    }
}

/** Baris siswa di layar Absen. */
data class SiswaAbsenUi(
    val id: Long,
    val nama: String,
    val nis: String,
    val kelas: String,
    val noWaOrtu: String,
    val sesi: JamSekolah,
    val status: StatusHadir,
    val masukMenit: Int?,
    val pulangMenit: Int?
)

/** Hasil pemindaian barcode/QR untuk ditampilkan & dikirim ke WA orang tua. */
data class HasilScanAbsen(
    val siswa: SiswaEntity,
    val status: StatusHadir,
    val jamMenit: Int,
    val baruPulang: Boolean,
    val sudahSelesaiHariIni: Boolean
)

/** Rekap satu siswa selama satu bulan. */
data class RekapSiswa(
    val siswaId: Long,
    val nama: String,
    val kelas: String,
    val hadir: Int,
    val terlambat: Int,
    val izin: Int,
    val sakit: Int,
    val alpa: Int
) {
    val persenHadir: Int
        get() {
            val total = hadir + terlambat + izin + sakit + alpa
            if (total == 0) return 100
            return ((hadir + terlambat) * 100) / total
        }
}
