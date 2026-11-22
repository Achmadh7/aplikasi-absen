package id.absensi.app.data

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** Satu-satunya pintu data untuk UI. Semua akses Room lewat sini. */
class Repository(private val db: AppDatabase) {

    // ---- Siswa ----

    suspend fun siswaAktif(): List<SiswaEntity> = db.siswaDao().aktif()

    suspend fun semuaSiswa(): List<SiswaEntity> = db.siswaDao().semua()

    suspend fun jumlahSiswaAktif(): Int = db.siswaDao().jumlahAktif()

    suspend fun tambahSiswa(
        nama: String,
        nis: String,
        kelas: String,
        noWaOrtu: String,
        sesi: JamSekolah
    ): Long {
        require(nama.isNotBlank()) { "Nama tidak boleh kosong" }
        require(nis.isNotBlank()) { "NIS / kode barcode tidak boleh kosong" }
        return db.siswaDao().insert(
            SiswaEntity(
                nama = nama.trim(),
                nis = nis.trim(),
                kelas = kelas.trim(),
                noWaOrtu = noWaOrtu.trim(),
                sesi = sesi.name
            )
        )
    }

    suspend fun ubahSiswa(e: SiswaEntity) {
        require(e.nama.isNotBlank()) { "Nama tidak boleh kosong" }
        require(e.nis.isNotBlank()) { "NIS / kode barcode tidak boleh kosong" }
        db.siswaDao().update(e)
    }

    suspend fun nonaktifkanSiswa(id: Long) = db.siswaDao().nonaktifkan(id)

    suspend fun aktifkanSiswa(id: Long) = db.siswaDao().aktifkan(id)

    // ---- Absen ----

    /**
     * Tap nama atau scan barcode siswa.
     * Belum ada catatan hari ini -> tercatat MASUK.
     * Sudah masuk, belum pulang -> tercatat PULANG.
     * Sudah pulang -> tidak berubah.
     */
    suspend fun tapAbsen(
        siswa: SiswaEntity,
        sekarangMenit: Int,
        tanggal: String = tanggalHariIni()
    ): AbsensiEntity {
        val dao = db.absensiDao()
        val ada = dao.bySiswaTanggal(siswa.id, tanggal)
        if (ada == null) {
            val id = dao.insert(
                AbsensiEntity(siswaId = siswa.id, tanggal = tanggal, masukMenit = sekarangMenit)
            )
            return AbsensiEntity(id = id, siswaId = siswa.id, tanggal = tanggal, masukMenit = sekarangMenit)
        }
        if (ada.pulangMenit == null) {
            val upd = ada.copy(pulangMenit = sekarangMenit)
            dao.update(upd)
            return upd
        }
        return ada
    }

    suspend fun tapAbsenById(
        siswaId: Long,
        sekarangMenit: Int,
        tanggal: String = tanggalHariIni()
    ): AbsensiEntity? {
        val siswa = db.siswaDao().byId(siswaId) ?: return null
        return tapAbsen(siswa, sekarangMenit, tanggal)
    }

    /**
     * Proses hasil scan barcode/QR (NIS siswa).
     * Null bila kode tidak cocok dengan siswa aktif mana pun.
     */
    suspend fun scanBarcodeAbsen(
        kode: String,
        sekarangMenit: Int,
        toleransiMenit: Int,
        tanggal: String = tanggalHariIni()
    ): HasilScanAbsen? {
        val siswa = db.siswaDao().byNisAktif(kode.trim()) ?: return null
        val sebelum = db.absensiDao().bySiswaTanggal(siswa.id, tanggal)
        val sudahSelesai = sebelum?.masukMenit != null && sebelum.pulangMenit != null
        val abs = tapAbsen(siswa, sekarangMenit, tanggal)
        val sesi = JamSekolah.dariNama(siswa.sesi)
        val status = statusMasuk(sesi.masukMenit, abs.masukMenit ?: sekarangMenit, toleransiMenit)
        val baruPulang = sebelum?.masukMenit != null && sebelum.pulangMenit == null && abs.pulangMenit != null
        val jamMenit = if (baruPulang || sudahSelesai) (abs.pulangMenit ?: sekarangMenit) else (abs.masukMenit ?: sekarangMenit)
        return HasilScanAbsen(
            siswa = siswa,
            status = status,
            jamMenit = jamMenit,
            baruPulang = baruPulang,
            sudahSelesaiHariIni = sudahSelesai
        )
    }

    /** Status + catatan absen satu siswa di satu tanggal (izin didahulukan). */
    suspend fun statusHariIni(
        siswa: SiswaEntity,
        tanggal: String,
        toleransiMenit: Int
    ): Pair<StatusHadir, AbsensiEntity?> {
        val izin = db.izinDao().byTanggal(tanggal).firstOrNull { it.siswaId == siswa.id }
        if (izin != null) {
            val s = if (izin.jenis == "SAKIT") StatusHadir.SAKIT else StatusHadir.IZIN
            return s to null
        }
        val abs = db.absensiDao().bySiswaTanggal(siswa.id, tanggal)
        val masuk = abs?.masukMenit ?: return StatusHadir.BELUM to null
        val sesi = JamSekolah.dariNama(siswa.sesi)
        return statusMasuk(sesi.masukMenit, masuk, toleransiMenit) to abs
    }

    /** Daftar siswa + status hari ini untuk layar Absen. */
    suspend fun daftarAbsenHariIni(
        tanggal: String = tanggalHariIni(),
        toleransiMenit: Int
    ): List<SiswaAbsenUi> {
        return siswaAktif().map { s ->
            val (status, abs) = statusHariIni(s, tanggal, toleransiMenit)
            SiswaAbsenUi(
                id = s.id,
                nama = s.nama,
                nis = s.nis,
                kelas = s.kelas,
                noWaOrtu = s.noWaOrtu,
                sesi = JamSekolah.dariNama(s.sesi),
                status = status,
                masukMenit = abs?.masukMenit,
                pulangMenit = abs?.pulangMenit
            )
        }
    }

    // ---- Izin ----

    suspend fun catatIzin(siswaId: Long, tanggal: String, jenis: String, keterangan: String = "") {
        require(jenis in listOf("IZIN", "SAKIT")) { "Jenis izin tidak dikenal" }
        db.izinDao().insert(
            IzinEntity(siswaId = siswaId, tanggal = tanggal, jenis = jenis, keterangan = keterangan.trim())
        )
    }

    suspend fun hapusIzin(siswaId: Long, tanggal: String) = db.izinDao().hapus(siswaId, tanggal)

    // ---- Koreksi manual ----

    suspend fun koreksiAbsen(siswaId: Long, tanggal: String, masukMenit: Int?, pulangMenit: Int?) {
        require(masukMenit == null || masukMenit in 0..1439) { "Jam masuk tidak valid" }
        require(pulangMenit == null || pulangMenit in 0..1439) { "Jam pulang tidak valid" }
        require(masukMenit == null || pulangMenit == null || pulangMenit >= masukMenit) {
            "Jam pulang tidak boleh sebelum jam masuk"
        }
        val dao = db.absensiDao()
        val ada = dao.bySiswaTanggal(siswaId, tanggal)
        if (masukMenit == null && pulangMenit == null) {
            if (ada != null) dao.hapus(siswaId, tanggal)
            return
        }
        if (ada == null) {
            dao.insert(AbsensiEntity(siswaId = siswaId, tanggal = tanggal, masukMenit = masukMenit, pulangMenit = pulangMenit))
        } else {
            dao.update(ada.copy(masukMenit = masukMenit, pulangMenit = pulangMenit))
        }
    }

    // ---- Riwayat per siswa ----

    /** Siswa + baris harian satu bulan. Null bila siswa tidak ditemukan. */
    suspend fun riwayatSiswa(
        siswaId: Long,
        prefix: String,
        toleransiMenit: Int
    ): Pair<SiswaEntity, List<RiwayatHari>>? {
        val siswa = db.siswaDao().byId(siswaId) ?: return null
        val sampai = if (prefix == LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))) {
            LocalDate.now()
        } else {
            YearMonth.parse(prefix).atEndOfMonth()
        }
        val baris = barisRiwayat(
            siswa = siswa,
            absensi = db.absensiDao().byBulan(prefix).filter { it.siswaId == siswaId },
            izin = db.izinDao().byBulan(prefix).filter { it.siswaId == siswaId },
            prefix = prefix,
            sampai = sampai,
            toleransiMenit = toleransiMenit
        )
        return siswa to baris
    }

    // ---- Rekap ----

    /** Rekap satu bulan ("yyyy-MM"). Alpa = hari sekolah tanpa absen dan tanpa izin. */
    suspend fun rekapBulan(prefix: String, toleransiMenit: Int): List<RekapSiswa> {
        val siswa = db.siswaDao().semua()
        val absensi = db.absensiDao().byBulan(prefix)
        val izin = db.izinDao().byBulan(prefix)
        val sampai = if (prefix == LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))) {
            LocalDate.now()
        } else {
            YearMonth.parse(prefix).atEndOfMonth()
        }
        val hariSekolah = hariKerjaBulan(prefix, sampai).map { it.toString() }.toSet()
        return siswa.map { s ->
            val abs = absensi.filter { it.siswaId == s.id }
            val izn = izin.filter { it.siswaId == s.id }
            val sesi = JamSekolah.dariNama(s.sesi)
            var hadir = 0
            var terlambat = 0
            abs.forEach { a ->
                a.masukMenit?.let { m ->
                    if (statusMasuk(sesi.masukMenit, m, toleransiMenit) == StatusHadir.HADIR) hadir++ else terlambat++
                }
            }
            val tanggalAbsen = abs.filter { it.masukMenit != null }.map { it.tanggal }.toSet()
            val tanggalIzin = izn.map { it.tanggal }.toSet()
            val alpa = hariSekolah.count { it !in tanggalAbsen && it !in tanggalIzin }
            RekapSiswa(
                siswaId = s.id,
                nama = s.nama,
                kelas = s.kelas,
                hadir = hadir,
                terlambat = terlambat,
                izin = izn.count { it.jenis == "IZIN" },
                sakit = izn.count { it.jenis == "SAKIT" },
                alpa = alpa
            )
        }
    }
}
