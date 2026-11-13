package id.absensi.app

import id.absensi.app.data.AbsensiEntity
import id.absensi.app.data.IzinEntity
import id.absensi.app.data.KaryawanEntity
import id.absensi.app.data.Shift
import id.absensi.app.data.StatusHadir
import id.absensi.app.data.barisRiwayat
import id.absensi.app.data.formatBulanIndonesia
import id.absensi.app.data.formatTanggalIndonesia
import id.absensi.app.data.hariKerjaBulan
import id.absensi.app.data.menitKeJam
import id.absensi.app.data.parseJamMenit
import id.absensi.app.data.statusMasuk
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class AbsensiLogicTest {

    @Test
    fun menitKeJam_formatBenar() {
        assertEquals("08.00", menitKeJam(480))
        assertEquals("08.05", menitKeJam(485))
        assertEquals("15.00", menitKeJam(900))
        assertEquals("22.00", menitKeJam(1320))
        assertEquals("00.00", menitKeJam(0))
    }

    @Test
    fun shift_jamKerjaBenar() {
        assertEquals(480, Shift.SHIFT_1.masukMenit)
        assertEquals(900, Shift.SHIFT_1.pulangMenit)
        assertEquals(960, Shift.SHIFT_2.masukMenit)
        assertEquals(1320, Shift.SHIFT_2.pulangMenit)
    }

    @Test
    fun statusMasuk_tepatWaktuAdalahHadir() {
        assertEquals(StatusHadir.HADIR, statusMasuk(480, 480, 15))
        assertEquals(StatusHadir.HADIR, statusMasuk(480, 495, 15))
    }

    @Test
    fun statusMasuk_lewatToleransiAdalahTerlambat() {
        assertEquals(StatusHadir.TERLAMBAT, statusMasuk(480, 496, 15))
        assertEquals(StatusHadir.TERLAMBAT, statusMasuk(480, 540, 15))
    }

    @Test
    fun statusMasuk_toleransiNol() {
        assertEquals(StatusHadir.HADIR, statusMasuk(960, 960, 0))
        assertEquals(StatusHadir.TERLAMBAT, statusMasuk(960, 961, 0))
    }

    @Test
    fun formatTanggalIndonesia_benar() {
        assertEquals("Kamis, 12 Nov 2026", formatTanggalIndonesia("2026-11-12"))
    }

    @Test
    fun formatBulanIndonesia_benar() {
        assertEquals("November 2026", formatBulanIndonesia("2026-11"))
    }

    @Test
    fun hariKerjaBulan_november2026_21Hari() {
        // November 2026: 1 Nov = Minggu; hari kerja sampai 30 Nov = 21 hari.
        val hari = hariKerjaBulan("2026-11", LocalDate.of(2026, 11, 30))
        assertEquals(21, hari.size)
    }

    @Test
    fun hariKerjaBulan_tidakTermasukAkhirPekan() {
        val hari = hariKerjaBulan("2026-11", LocalDate.of(2026, 11, 8))
        // 2-6 Nov (Senin-Jumat) = 5 hari; 7-8 Nov akhir pekan.
        assertEquals(5, hari.size)
    }

    @Test
    fun parseJamMenit_formatBenar() {
        assertEquals(485, parseJamMenit("08.05"))
        assertEquals(485, parseJamMenit("8:05"))
        assertEquals(1320, parseJamMenit("22.00"))
        assertEquals(0, parseJamMenit("00.00"))
    }

    @Test
    fun parseJamMenit_formatSalahNull() {
        assertEquals(null, parseJamMenit("24.00"))
        assertEquals(null, parseJamMenit("08.60"))
        assertEquals(null, parseJamMenit("abc"))
        assertEquals(null, parseJamMenit(""))
        assertEquals(null, parseJamMenit("8.5"))
        assertEquals(null, parseJamMenit("08.05.00"))
    }

    private fun karyawanTes() = KaryawanEntity(id = 1, nama = "Budi", jabatan = "Staff", shift = "SHIFT_1")

    private fun absensiTes() = listOf(
        AbsensiEntity(id = 1, karyawanId = 1, tanggal = "2026-11-02", masukMenit = 480),
        AbsensiEntity(id = 2, karyawanId = 1, tanggal = "2026-11-03", masukMenit = 500),
        AbsensiEntity(id = 3, karyawanId = 1, tanggal = "2026-11-05", masukMenit = 470, pulangMenit = 900)
    )

    private fun izinTes() = listOf(
        IzinEntity(id = 1, karyawanId = 1, tanggal = "2026-11-04", jenis = "SAKIT")
    )

    @Test
    fun barisRiwayat_statusBenar() {
        // 2-6 Nov 2026 = Senin-Jumat; 6 Nov tanpa catatan = ALPA.
        val baris = barisRiwayat(karyawanTes(), absensiTes(), izinTes(), "2026-11", LocalDate.of(2026, 11, 6), 15)
        assertEquals(5, baris.size)
        assertEquals(StatusHadir.HADIR, baris[0].status)
        assertEquals(StatusHadir.TERLAMBAT, baris[1].status)
        assertEquals(20, baris[1].terlambatMenit)
        assertEquals(StatusHadir.SAKIT, baris[2].status)
        assertEquals(StatusHadir.HADIR, baris[3].status)
        assertEquals(900, baris[3].pulangMenit)
        assertEquals(StatusHadir.ALPA, baris[4].status)
    }
}
