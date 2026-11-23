package id.absensi.app.data

/** Satu baris hasil baca CSV untuk import siswa. */
data class BarisCsvSiswa(
    val nama: String,
    val nis: String,
    val kelas: String,
    val noWaOrtu: String,
    val sesi: JamSekolah
)

/** Hasil parsing teks CSV siswa. */
data class HasilParseCsvSiswa(
    val valid: List<BarisCsvSiswa>,
    val dilewati: Int
)

/** Susun konten CSV rekap bulanan (UTF-8 dengan BOM agar rapi saat dibuka di Excel). */
fun buatCsvRekap(daftar: List<RekapSiswa>): String {
    val sb = StringBuilder()
    sb.append('\uFEFF')
    sb.appendLine("Nama,Kelas,Hadir,Terlambat,Izin,Sakit,Alpa,Persen Hadir")
    for (r in daftar) {
        sb.append(escapeCsv(r.nama)).append(',')
        sb.append(escapeCsv(r.kelas)).append(',')
        sb.append(r.hadir).append(',')
        sb.append(r.terlambat).append(',')
        sb.append(r.izin).append(',')
        sb.append(r.sakit).append(',')
        sb.append(r.alpa).append(',')
        sb.append(r.persenHadir).append('%')
        sb.appendLine()
    }
    return sb.toString()
}

private fun escapeCsv(nilai: String): String {
    val perluKutipan = nilai.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
    if (!perluKutipan) return nilai
    return "\"${nilai.replace("\"", "\"\"")}\""
}

/**
 * Parse teks CSV daftar siswa.
 * Urutan kolom: nama, nis, kelas (opsional), no_wa_ortu (opsional), sesi (opsional: PAGI/SIANG).
 * Mendukung pemisah koma (,) atau titik koma (;), BOM UTF-8, dan baris header opsional.
 */
fun parseCsvSiswa(teks: String): HasilParseCsvSiswa {
    val bersih = teks.removePrefix("\uFEFF")
    val baris = bersih.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toList()
    if (baris.isEmpty()) return HasilParseCsvSiswa(emptyList(), 0)

    val pemisah = deteksiPemisahCsv(baris.first())
    val valid = mutableListOf<BarisCsvSiswa>()
    var dilewati = 0

    baris.forEachIndexed { idx, line ->
        val kolom = pecahBarisCsv(line, pemisah)
        if (idx == 0 && apakahHeaderSiswa(kolom)) return@forEachIndexed

        val nama = kolom.getOrNull(0)?.trim().orEmpty()
        val nis = kolom.getOrNull(1)?.trim().orEmpty()
        if (nama.isBlank() || nis.isBlank()) {
            dilewati++
            return@forEachIndexed
        }
        val kelas = kolom.getOrNull(2)?.trim().orEmpty()
        val noWa = kolom.getOrNull(3)?.trim().orEmpty()
        val sesiTeks = kolom.getOrNull(4)?.trim().orEmpty().uppercase()
        val sesi = if (sesiTeks == "SIANG") JamSekolah.SIANG else JamSekolah.PAGI
        valid.add(BarisCsvSiswa(nama = nama, nis = nis, kelas = kelas, noWaOrtu = noWa, sesi = sesi))
    }

    return HasilParseCsvSiswa(valid = valid, dilewati = dilewati)
}

private fun deteksiPemisahCsv(barisPertama: String): Char {
    var dalamKutipan = false
    var koma = 0
    var titikKoma = 0
    for (c in barisPertama) {
        when (c) {
            '"' -> dalamKutipan = !dalamKutipan
            ',' -> if (!dalamKutipan) koma++
            ';' -> if (!dalamKutipan) titikKoma++
        }
    }
    return if (titikKoma > koma) ';' else ','
}

private fun apakahHeaderSiswa(kolom: List<String>): Boolean {
    val k0 = kolom.getOrNull(0)?.trim()?.lowercase().orEmpty()
    val k1 = kolom.getOrNull(1)?.trim()?.lowercase().orEmpty()
    return k0.contains("nama") && (k1.contains("nis") || k1.contains("kode") || k1.contains("barcode"))
}

private fun pecahBarisCsv(baris: String, pemisah: Char): List<String> {
    val hasil = mutableListOf<String>()
    val sb = StringBuilder()
    var dalamKutipan = false
    var i = 0
    while (i < baris.length) {
        val c = baris[i]
        when {
            c == '"' -> {
                if (dalamKutipan && i + 1 < baris.length && baris[i + 1] == '"') {
                    sb.append('"')
                    i++
                } else {
                    dalamKutipan = !dalamKutipan
                }
            }
            c == pemisah && !dalamKutipan -> {
                hasil.add(sb.toString())
                sb.clear()
            }
            else -> sb.append(c)
        }
        i++
    }
    hasil.add(sb.toString())
    return hasil
}
