package id.absensi.app

import id.absensi.app.data.AbsensiEntity
import id.absensi.app.data.IzinEntity
import id.absensi.app.data.JamSekolah
import id.absensi.app.data.RekapSiswa
import id.absensi.app.data.SiswaEntity
import id.absensi.app.data.StatusHadir
import id.absensi.app.data.barisRiwayat
import id.absensi.app.data.buatCsvRekap
import id.absensi.app.data.buatPesanWaAbsen
import id.absensi.app.data.buatUrlWa
import id.absensi.app.data.formatBulanIndonesia
import id.absensi.app.data.formatTanggalIndonesia
import id.absensi.app.data.hariKerjaBulan
import id.absensi.app.data.menitKeJam
import id.absensi.app.data.normalisasiNomorWa
import id.absensi.app.data.parseCsvSiswa
import id.absensi.app.data.parseJamMenit
import id.absensi.app.data.statusMasuk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AbsensiLogicTest {

    @Test
    fun menitKeJam_formatBenar() {
        assertEquals("07.00", menitKeJam(420))
        assertEquals("08.05", menitKeJam(485))
        assertEquals("13.00", menitKeJam(780))
        assertEquals("17.00", menitKeJam(1020))
        assertEquals("00.00", menitKeJam(0))
    }

    @Test
    fun jamSekolah_benar() {
        assertEquals(420, JamSekolah.PAGI.masukMenit)
        assertEquals(780, JamSekolah.PAGI.pulangMenit)
        assertEquals(780, JamSekolah.SIANG.masukMenit)
        assertEquals(1020, JamSekolah.SIANG.pulangMenit)
    }

    @Test
    fun statusMasuk_tepatWaktuAdalahHadir() {
        assertEquals(StatusHadir.HADIR, statusMasuk(420, 420, 15))
        assertEquals(StatusHadir.HADIR, statusMasuk(420, 435, 15))
    }

    @Test
    fun statusMasuk_lewatToleransiAdalahTerlambat() {
        assertEquals(StatusHadir.TERLAMBAT, statusMasuk(420, 436, 15))
        assertEquals(StatusHadir.TERLAMBAT, statusMasuk(420, 480, 15))
    }

    @Test
    fun statusMasuk_toleransiNol() {
        assertEquals(StatusHadir.HADIR, statusMasuk(780, 780, 0))
        assertEquals(StatusHadir.TERLAMBAT, statusMasuk(780, 781, 0))
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
        val hari = hariKerjaBulan("2026-11", LocalDate.of(2026, 11, 30))
        assertEquals(21, hari.size)
    }

    @Test
    fun hariKerjaBulan_tidakTermasukAkhirPekan() {
        val hari = hariKerjaBulan("2026-11", LocalDate.of(2026, 11, 8))
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
        assertNull(parseJamMenit("24.00"))
        assertNull(parseJamMenit("08.60"))
        assertNull(parseJamMenit("abc"))
        assertNull(parseJamMenit(""))
        assertNull(parseJamMenit("8.5"))
        assertNull(parseJamMenit("08.05.00"))
    }

    @Test
    fun normalisasiNomorWa_formatIndonesia() {
        assertEquals("6281234567890", normalisasiNomorWa("0812-3456-7890"))
        assertEquals("6281234567890", normalisasiNomorWa("+62 812 3456 7890"))
        assertEquals("6281234567890", normalisasiNomorWa("6281234567890"))
        assertEquals("6281234567890", normalisasiNomorWa("81234567890"))
        assertNull(normalisasiNomorWa("0812"))
        assertNull(normalisasiNomorWa(""))
    }

    @Test
    fun buatUrlWa_validDanTerencode() {
        val pesan = buatPesanWaAbsen(
            namaSiswa = "Budi",
            kelas = "7A",
            status = StatusHadir.HADIR,
            jamMenit = 415,
            pulang = false,
            tanggalIso = "2026-11-12"
        )
        assertTrue(pesan.contains("Budi (7A)"))
        assertTrue(pesan.contains("MASUK"))
        assertTrue(pesan.contains("06.55"))
        val url = buatUrlWa("081234567890", pesan)
        assertTrue(url!!.startsWith("https://wa.me/6281234567890?text="))
        assertNull(buatUrlWa("123", pesan))
    }

    private fun siswaTes() = SiswaEntity(
        id = 1,
        nama = "Budi",
        nis = "2026001",
        kelas = "7A",
        noWaOrtu = "081234567890",
        sesi = "PAGI"
    )

    private fun absensiTes() = listOf(
        AbsensiEntity(id = 1, siswaId = 1, tanggal = "2026-11-02", masukMenit = 420),
        AbsensiEntity(id = 2, siswaId = 1, tanggal = "2026-11-03", masukMenit = 440),
        AbsensiEntity(id = 3, siswaId = 1, tanggal = "2026-11-05", masukMenit = 415, pulangMenit = 780)
    )

    private fun izinTes() = listOf(
        IzinEntity(id = 1, siswaId = 1, tanggal = "2026-11-04", jenis = "SAKIT")
    )

    @Test
    fun barisRiwayat_statusBenar() {
        // 2-6 Nov 2026 = Senin-Jumat; 6 Nov tanpa catatan = ALPA.
        val baris = barisRiwayat(siswaTes(), absensiTes(), izinTes(), "2026-11", LocalDate.of(2026, 11, 6), 15)
        assertEquals(5, baris.size)
        assertEquals(StatusHadir.HADIR, baris[0].status)
        assertEquals(StatusHadir.TERLAMBAT, baris[1].status)
        assertEquals(20, baris[1].terlambatMenit)
        assertEquals(StatusHadir.SAKIT, baris[2].status)
        assertEquals(StatusHadir.HADIR, baris[3].status)
        assertEquals(780, baris[3].pulangMenit)
        assertEquals(StatusHadir.ALPA, baris[4].status)
    }

    @Test
    fun buatCsvRekap_headerDanEscapingBenar() {
        val csv = buatCsvRekap(
            listOf(
                RekapSiswa(1, "Budi, S.", "7A", hadir = 18, terlambat = 2, izin = 0, sakit = 0, alpa = 0)
            )
        )
        assertTrue(csv.startsWith("\uFEFFNama,Kelas,Hadir,Terlambat,Izin,Sakit,Alpa,Persen Hadir"))
        assertTrue(csv.contains("\"Budi, S.\",7A,18,2,0,0,0,100%"))
    }

    @Test
    fun parseCsvSiswa_dukungKomaTitikKomaDanHeader() {
        val csv = "\uFEFFNama;NIS;Kelas;No WA;Sesi\n" +
            "Budi;2026001;7A;08123456789;SIANG\n" +
            "\"Siti, A.\";2026002;7B;;\n" +
            ";2026003;7C;;PAGI\n"
        val hasil = parseCsvSiswa(csv)
        assertEquals(2, hasil.valid.size)
        assertEquals(1, hasil.dilewati)
        assertEquals("Budi", hasil.valid[0].nama)
        assertEquals(JamSekolah.SIANG, hasil.valid[0].sesi)
        assertEquals("Siti, A.", hasil.valid[1].nama)
        assertEquals(JamSekolah.PAGI, hasil.valid[1].sesi)
    }
}
