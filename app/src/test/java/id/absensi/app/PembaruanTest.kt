package id.absensi.app

import id.absensi.app.data.parseVersi
import id.absensi.app.data.versiLebihBaru
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PembaruanTest {

    @Test
    fun parseVersi_bersih() {
        assertEquals(Triple(1, 0, 0), parseVersi("1.0.0"))
        assertEquals(Triple(2, 3, 4), parseVersi("v2.3.4"))
        assertEquals(Triple(10, 20, 30), parseVersi("10.20.30"))
    }

    @Test
    fun parseVersi_gagal() {
        assertNull(parseVersi("1.0"))
        assertNull(parseVersi("abc"))
        assertNull(parseVersi(""))
        assertNull(parseVersi("1.0.x"))
    }

    @Test
    fun lebihBaru_patchMinorMayor() {
        assertTrue(versiLebihBaru("1.0.0", "v1.0.1"))
        assertTrue(versiLebihBaru("1.0.9", "1.1.0"))
        assertTrue(versiLebihBaru("1.9.9", "2.0.0"))
    }

    @Test
    fun lebihBaru_angkaBukanTeks() {
        // 10 > 9 secara angka; banding teks akan salah ("1.10.0" < "1.9.0").
        assertTrue(versiLebihBaru("1.9.0", "1.10.0"))
        assertFalse(versiLebihBaru("1.10.0", "1.9.0"))
    }

    @Test
    fun lebihBaru_samaDanLebihLama() {
        assertFalse(versiLebihBaru("1.0.0", "1.0.0"))
        assertFalse(versiLebihBaru("1.0.1", "1.0.0"))
        assertFalse(versiLebihBaru("2.0.0", "1.9.9"))
    }

    @Test
    fun lebihBaru_malformed() {
        assertFalse(versiLebihBaru("1.0.0", "abc"))
        assertFalse(versiLebihBaru("abc", "1.0.1"))
    }
}
