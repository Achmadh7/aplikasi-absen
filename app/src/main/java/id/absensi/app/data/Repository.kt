package id.absensi.app.data

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** Satu-satunya pintu data untuk UI. Semua akses Room lewat sini. */
class Repository(private val db: AppDatabase) {

    // ---- Karyawan ----

    suspend fun karyawanAktif(): List<KaryawanEntity> = db.karyawanDao().aktif()

    suspend fun semuaKaryawan(): List<KaryawanEntity> = db.karyawanDao().semua()

    suspend fun jumlahKaryawanAktif(): Int = db.karyawanDao().jumlahAktif()

    suspend fun tambahKaryawan(nama: String, jabatan: String, shift: Shift): Long {
        require(nama.isNotBlank()) { "Nama tidak boleh kosong" }
        return db.karyawanDao().insert(
            KaryawanEntity(nama = nama.trim(), jabatan = jabatan.trim(), shift = shift.name)
        )
    }

    suspend fun ubahKaryawan(e: KaryawanEntity) {
        require(e.nama.isNotBlank()) { "Nama tidak boleh kosong" }
        db.karyawanDao().update(e)
    }

    suspend fun nonaktifkanKaryawan(id: Long) = db.karyawanDao().nonaktifkan(id)

    suspend fun aktifkanKaryawan(id: Long) = db.karyawanDao().aktifkan(id)

    // ---- Absen ----

    /**
     * Tap nama di layar Absen.
     * Belum ada catatan hari ini -> tercatat MASUK.
     * Sudah masuk, belum pulang -> tercatat PULANG.
     * Sudah pulang -> tidak berubah.
     */
    suspend fun tapAbsen(
        karyawan: KaryawanEntity,
        sekarangMenit: Int,
        tanggal: String = tanggalHariIni()
    ): AbsensiEntity {
        val dao = db.absensiDao()
        val ada = dao.byKaryawanTanggal(karyawan.id, tanggal)
        if (ada == null) {
            val id = dao.insert(
                AbsensiEntity(karyawanId = karyawan.id, tanggal = tanggal, masukMenit = sekarangMenit)
            )
            return AbsensiEntity(id = id, karyawanId = karyawan.id, tanggal = tanggal, masukMenit = sekarangMenit)
        }
        if (ada.pulangMenit == null) {
            val upd = ada.copy(pulangMenit = sekarangMenit)
            dao.update(upd)
            return upd
        }
        return ada
    }

    suspend fun tapAbsenById(
        karyawanId: Long,
        sekarangMenit: Int,
        tanggal: String = tanggalHariIni()
    ): AbsensiEntity? {
        val karyawan = db.karyawanDao().byId(karyawanId) ?: return null
        return tapAbsen(karyawan, sekarangMenit, tanggal)
    }

    /** Status + catatan absen satu karyawan di satu tanggal (izin didahulukan). */
    suspend fun statusHariIni(
        karyawan: KaryawanEntity,
        tanggal: String,
        toleransiMenit: Int
    ): Pair<StatusHadir, AbsensiEntity?> {
        val izin = db.izinDao().byTanggal(tanggal).firstOrNull { it.karyawanId == karyawan.id }
        if (izin != null) {
            val s = when (izin.jenis) {
                "SAKIT" -> StatusHadir.SAKIT
                "CUTI" -> StatusHadir.CUTI
                else -> StatusHadir.IZIN
            }
            return s to null
        }
        val abs = db.absensiDao().byKaryawanTanggal(karyawan.id, tanggal)
        val masuk = abs?.masukMenit ?: return StatusHadir.BELUM to null
        val shift = Shift.dariNama(karyawan.shift)
        return statusMasuk(shift.masukMenit, masuk, toleransiMenit) to abs
    }

    /** Daftar karyawan + status hari ini untuk layar Absen. */
    suspend fun daftarAbsenHariIni(
        tanggal: String = tanggalHariIni(),
        toleransiMenit: Int
    ): List<KaryawanAbsenUi> {
        return karyawanAktif().map { k ->
            val (status, abs) = statusHariIni(k, tanggal, toleransiMenit)
            KaryawanAbsenUi(
                id = k.id,
                nama = k.nama,
                jabatan = k.jabatan,
                shift = Shift.dariNama(k.shift),
                status = status,
                masukMenit = abs?.masukMenit,
                pulangMenit = abs?.pulangMenit
            )
        }
    }

    // ---- Izin ----

    suspend fun catatIzin(karyawanId: Long, tanggal: String, jenis: String, keterangan: String = "") {
        require(jenis in listOf("IZIN", "SAKIT", "CUTI")) { "Jenis izin tidak dikenal" }
        db.izinDao().insert(
            IzinEntity(karyawanId = karyawanId, tanggal = tanggal, jenis = jenis, keterangan = keterangan.trim())
        )
    }

    suspend fun hapusIzin(karyawanId: Long, tanggal: String) = db.izinDao().hapus(karyawanId, tanggal)

    // ---- Rekap ----

    /** Rekap satu bulan ("yyyy-MM"). Alpa = hari kerja tanpa absen dan tanpa izin. */
    suspend fun rekapBulan(prefix: String, toleransiMenit: Int): List<RekapKaryawan> {
        val karyawan = db.karyawanDao().semua()
        val absensi = db.absensiDao().byBulan(prefix)
        val izin = db.izinDao().byBulan(prefix)
        val sampai = if (prefix == LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))) {
            LocalDate.now()
        } else {
            YearMonth.parse(prefix).atEndOfMonth()
        }
        val hariKerja = hariKerjaBulan(prefix, sampai).map { it.toString() }.toSet()
        return karyawan.map { k ->
            val abs = absensi.filter { it.karyawanId == k.id }
            val izn = izin.filter { it.karyawanId == k.id }
            val shift = Shift.dariNama(k.shift)
            var hadir = 0
            var terlambat = 0
            abs.forEach { a ->
                a.masukMenit?.let { m ->
                    if (statusMasuk(shift.masukMenit, m, toleransiMenit) == StatusHadir.HADIR) hadir++ else terlambat++
                }
            }
            val tanggalAbsen = abs.filter { it.masukMenit != null }.map { it.tanggal }.toSet()
            val tanggalIzin = izn.map { it.tanggal }.toSet()
            val alpa = hariKerja.count { it !in tanggalAbsen && it !in tanggalIzin }
            RekapKaryawan(
                karyawanId = k.id,
                nama = k.nama,
                jabatan = k.jabatan,
                hadir = hadir,
                terlambat = terlambat,
                izin = izn.count { it.jenis == "IZIN" },
                sakit = izn.count { it.jenis == "SAKIT" },
                cuti = izn.count { it.jenis == "CUTI" },
                alpa = alpa
            )
        }
    }
}
