package id.absensi.app.data

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Info rilis terbaru dari GitHub Releases. */
data class InfoPembaruan(
    val versi: String,
    val catatan: String,
    val urlApk: String
)

private const val URL_RILIS_TERBARU =
    "https://api.github.com/repos/Achmadh7/aplikasi-absen/releases/latest"

/** "1.0.0" / "v1.0.0" -> Triple(1, 0, 0); gagal parse -> null. */
fun parseVersi(versi: String): Triple<Int, Int, Int>? {
    val bersih = versi.trim().removePrefix("v").removePrefix("V")
    val bagian = bersih.split(".")
    if (bagian.size != 3) return null
    val angka = bagian.map { it.toIntOrNull() ?: return null }
    return Triple(angka[0], angka[1], angka[2])
}

/** True bila versiTerbaru lebih baru dari versiSaatIni (banding angka, bukan teks). */
fun versiLebihBaru(versiSaatIni: String, versiTerbaru: String): Boolean {
    val lama = parseVersi(versiSaatIni) ?: return false
    val baru = parseVersi(versiTerbaru) ?: return false
    if (baru.first != lama.first) return baru.first > lama.first
    if (baru.second != lama.second) return baru.second > lama.second
    return baru.third > lama.third
}

/** Versi aplikasi terpasang, mis. "1.0.0". */
fun versiAplikasi(context: Context): String {
    return try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
    } catch (e: Exception) {
        "0.0.0"
    }
}

/**
 * Cek rilis terbaru di GitHub. Kembalikan info pembaruan bila ada versi
 * lebih baru DAN ada file APK di rilisnya; null bila sudah terbaru, belum
 * ada rilis, atau jaringan gagal. Semua kegagalan diam (null) — layar
 * Absen tidak boleh terganggu oleh cek ini.
 */
suspend fun cekPembaruan(versiSaatIni: String): InfoPembaruan? = withContext(Dispatchers.IO) {
    var conn: HttpURLConnection? = null
    try {
        conn = (URL(URL_RILIS_TERBARU).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        if (conn.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
        val json = JSONObject(conn.inputStream.bufferedReader().readText())
        val tag = json.optString("tag_name", "")
        if (!versiLebihBaru(versiSaatIni, tag)) return@withContext null
        val assets = json.optJSONArray("assets") ?: return@withContext null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val nama = asset.optString("name", "")
            if (nama.endsWith(".apk", ignoreCase = true)) {
                return@withContext InfoPembaruan(
                    versi = tag.removePrefix("v").removePrefix("V"),
                    catatan = json.optString("body", ""),
                    urlApk = asset.optString("browser_download_url", "")
                )
            }
        }
        null
    } catch (e: Exception) {
        null
    } finally {
        conn?.disconnect()
    }
}

/** Unduh APK lewat DownloadManager; kembalikan id unduhan. */
fun unduhPembaruan(context: Context, info: InfoPembaruan): Long {
    val req = DownloadManager.Request(Uri.parse(info.urlApk)).apply {
        setTitle("Absensi ${info.versi}")
        setDescription("Mengunduh pembaruan aplikasi…")
        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        setDestinationInExternalPublicDir(
            Environment.DIRECTORY_DOWNLOADS,
            "Absensi-${info.versi}.apk"
        )
        setMimeType("application/vnd.android.package-archive")
    }
    return (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
}

/** Buka installer untuk APK hasil unduhan DownloadManager. */
fun pasangApkTerunduh(context: Context, idUnduhan: Long) {
    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val uri = dm.getUriForDownloadedFile(idUnduhan) ?: return
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
