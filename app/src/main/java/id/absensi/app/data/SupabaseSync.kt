package id.absensi.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

data class SyncStatusUi(
    val configured: Boolean,
    val email: String,
    val lastSyncAt: Long,
    val pendingCount: Int
)

class SyncPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("absensi_sync_prefs", Context.MODE_PRIVATE)

    var url: String
        get() = prefs.getString("url", "") ?: ""
        set(v) = prefs.edit().putString("url", v.trim().trimEnd('/')).apply()

    var anonKey: String
        get() = prefs.getString("anon_key", "") ?: ""
        set(v) = prefs.edit().putString("anon_key", v.trim()).apply()

    var accessToken: String
        get() = prefs.getString("access_token", "") ?: ""
        set(v) = prefs.edit().putString("access_token", v).apply()

    var refreshToken: String
        get() = prefs.getString("refresh_token", "") ?: ""
        set(v) = prefs.edit().putString("refresh_token", v).apply()

    var email: String
        get() = prefs.getString("email", "") ?: ""
        set(v) = prefs.edit().putString("email", v.trim()).apply()

    var lastSyncAt: Long
        get() = prefs.getLong("last_sync_at", 0L)
        set(v) = prefs.edit().putLong("last_sync_at", v).apply()

    val deviceId: String
        get() {
            val existing = prefs.getString("device_id", null)
            if (!existing.isNullOrBlank()) return existing
            val baru = "hp-" + UUID.randomUUID().toString().take(8)
            prefs.edit().putString("device_id", baru).apply()
            return baru
        }

    val isConfigured: Boolean
        get() = url.isNotBlank() && anonKey.isNotBlank() && accessToken.isNotBlank()

    fun logout() {
        prefs.edit()
            .remove("access_token")
            .remove("refresh_token")
            .remove("email")
            .apply()
    }
}

class SupabaseClient(context: Context) {
    val prefs = SyncPrefs(context)

    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun signIn(
        supabaseUrl: String,
        anonKey: String,
        email: String,
        password: String
    ): kotlin.Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val base = supabaseUrl.trim().trimEnd('/')
            val key = anonKey.trim()
            require(base.startsWith("https://")) { "URL Supabase harus diawali https://" }
            require(key.isNotBlank()) { "Anon key wajib diisi" }
            require(email.isNotBlank() && password.isNotBlank()) { "Email & password wajib diisi" }

            val body = JSONObject()
                .put("email", email.trim())
                .put("password", password)
                .toString()
                .toRequestBody(jsonType)

            val req = Request.Builder()
                .url("$base/auth/v1/token?grant_type=password")
                .addHeader("apikey", key)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            http.newCall(req).execute().use { res ->
                val txt = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    val msg = runCatching { JSONObject(txt).optString("error_description") }
                        .getOrNull().orEmpty().ifBlank { "Login gagal (${res.code})" }
                    error(msg)
                }
                val json = JSONObject(txt)
                prefs.url = base
                prefs.anonKey = key
                prefs.email = email.trim()
                prefs.accessToken = json.getString("access_token")
                prefs.refreshToken = json.optString("refresh_token", "")
            }
        }
    }

    private fun refreshSession(): Boolean {
        val rt = prefs.refreshToken
        if (rt.isBlank() || prefs.url.isBlank() || prefs.anonKey.isBlank()) return false
        val body = JSONObject().put("refresh_token", rt).toString().toRequestBody(jsonType)
        val req = Request.Builder()
            .url("${prefs.url}/auth/v1/token?grant_type=refresh_token")
            .addHeader("apikey", prefs.anonKey)
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        return runCatching {
            http.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return false
                val json = JSONObject(res.body?.string().orEmpty())
                prefs.accessToken = json.getString("access_token")
                prefs.refreshToken = json.optString("refresh_token", rt)
                true
            }
        }.getOrDefault(false)
    }

    private fun executeAuthed(build: (token: String) -> Request): Pair<Int, String> {
        val first = build(prefs.accessToken)
        http.newCall(first).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (res.code != 401) return res.code to body
        }
        if (!refreshSession()) return 401 to "Sesi habis, silakan login ulang"
        val retry = build(prefs.accessToken)
        http.newCall(retry).execute().use { res ->
            return res.code to res.body?.string().orEmpty()
        }
    }

    suspend fun syncAll(db: AppDatabase): kotlin.Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            check(prefs.isConfigured) { "Belum login Supabase" }
            var pushed = 0

            // 1. Push siswa baru/diubah
            val siswaPending = db.siswaDao().belumSync()
            if (siswaPending.isNotEmpty()) {
                val arr = JSONArray()
                siswaPending.forEach { s ->
                    arr.put(
                        JSONObject()
                            .put("nis", s.nis)
                            .put("nama", s.nama)
                            .put("kelas", s.kelas)
                            .put("no_wa_ortu", s.noWaOrtu)
                            .put("sesi", s.sesi)
                            .put("aktif", s.aktif)
                    )
                }
                val (code, err) = executeAuthed { token ->
                    Request.Builder()
                        .url("${prefs.url}/rest/v1/siswa?on_conflict=nis")
                        .addHeader("apikey", prefs.anonKey)
                        .addHeader("Authorization", "Bearer $token")
                        .addHeader("Prefer", "resolution=merge-duplicates")
                        .post(arr.toString().toRequestBody(jsonType))
                        .build()
                }
                if (code !in 200..299) error("Sync siswa gagal ($code): $err")
                siswaPending.forEach { db.siswaDao().tandaiSynced(it.id) }
                pushed += siswaPending.size
            }

            // 2. Pull daftar siswa dari server (agar semua HP punya data siswa terbaru)
            val (sCode, sBody) = executeAuthed { token ->
                Request.Builder()
                    .url("${prefs.url}/rest/v1/siswa?select=nis,nama,kelas,no_wa_ortu,sesi,aktif")
                    .addHeader("apikey", prefs.anonKey)
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()
            }
            if (sCode in 200..299) {
                val arr = JSONArray(sBody)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val nis = o.getString("nis")
                    val lokal = db.siswaDao().byNis(nis)
                    val nama = o.getString("nama")
                    val kelas = o.optString("kelas", "")
                    val wa = o.optString("no_wa_ortu", "")
                    val sesi = o.optString("sesi", JamSekolah.PAGI.name)
                    val aktif = o.optBoolean("aktif", true)
                    if (lokal == null) {
                        db.siswaDao().insert(
                            SiswaEntity(
                                nama = nama,
                                nis = nis,
                                kelas = kelas,
                                noWaOrtu = wa,
                                sesi = sesi,
                                aktif = aktif,
                                synced = true
                            )
                        )
                    } else if (lokal.synced) {
                        db.siswaDao().update(
                            lokal.copy(
                                nama = nama,
                                kelas = kelas,
                                noWaOrtu = wa,
                                sesi = sesi,
                                aktif = aktif,
                                synced = true
                            )
                        )
                    }
                }
            }

            // 3. Push absensi belum sync (memicu Edge Function auto-WA di server)
            val absenPending = db.absensiDao().belumSync()
            for (a in absenPending) {
                val siswa = db.siswaDao().byId(a.siswaId) ?: continue
                val payload = JSONObject()
                    .put("p_nis", siswa.nis)
                    .put("p_tanggal", a.tanggal)
                    .put("p_masuk_menit", a.masukMenit ?: JSONObject.NULL)
                    .put("p_pulang_menit", a.pulangMenit ?: JSONObject.NULL)
                    .put("p_device_id", prefs.deviceId)

                val (code, resp) = executeAuthed { token ->
                    Request.Builder()
                        .url("${prefs.url}/rest/v1/rpc/upsert_absensi")
                        .addHeader("apikey", prefs.anonKey)
                        .addHeader("Authorization", "Bearer $token")
                        .post(payload.toString().toRequestBody(jsonType))
                        .build()
                }
                if (code !in 200..299) error("Sync absensi gagal ($code): $resp")
                val waStatus = runCatching { JSONObject(resp).optString("wa_masuk_status", "sent") }
                    .getOrDefault("sent")
                db.absensiDao().tandaiSynced(a.id, waStatus)
                pushed++
            }

            // 4. Pull absensi hari ini agar semua HP melihat status scan terbaru
            val hariIni = tanggalHariIni()
            val (aCode, aBody) = executeAuthed { token ->
                Request.Builder()
                    .url("${prefs.url}/rest/v1/absensi?tanggal=eq.$hariIni&select=nis,tanggal,masuk_menit,pulang_menit,wa_masuk_status,wa_pulang_status")
                    .addHeader("apikey", prefs.anonKey)
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()
            }
            if (aCode in 200..299) {
                val arr = JSONArray(aBody)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val siswa = db.siswaDao().byNis(o.getString("nis")) ?: continue
                    val masuk = if (o.isNull("masuk_menit")) null else o.getInt("masuk_menit")
                    val pulang = if (o.isNull("pulang_menit")) null else o.getInt("pulang_menit")
                    val waStatus = o.optString("wa_masuk_status", "sent")
                    val lokal = db.absensiDao().bySiswaTanggal(siswa.id, hariIni)
                    if (lokal == null) {
                        db.absensiDao().insert(
                            AbsensiEntity(
                                siswaId = siswa.id,
                                tanggal = hariIni,
                                masukMenit = masuk,
                                pulangMenit = pulang,
                                synced = true,
                                waStatus = waStatus
                            )
                        )
                    } else if (lokal.synced) {
                        db.absensiDao().update(
                            lokal.copy(
                                masukMenit = lokal.masukMenit ?: masuk,
                                pulangMenit = lokal.pulangMenit ?: pulang,
                                waStatus = waStatus
                            )
                        )
                    }
                }
            }

            // 5. Push izin belum sync
            val izinPending = db.izinDao().belumSync()
            for (iz in izinPending) {
                val siswa = db.siswaDao().byId(iz.siswaId) ?: continue
                val payload = JSONObject()
                    .put("nis", siswa.nis)
                    .put("tanggal", iz.tanggal)
                    .put("jenis", iz.jenis)
                    .put("keterangan", iz.keterangan)
                val (code, resp) = executeAuthed { token ->
                    Request.Builder()
                        .url("${prefs.url}/rest/v1/izin?on_conflict=nis,tanggal")
                        .addHeader("apikey", prefs.anonKey)
                        .addHeader("Authorization", "Bearer $token")
                        .addHeader("Prefer", "resolution=merge-duplicates")
                        .post(payload.toString().toRequestBody(jsonType))
                        .build()
                }
                if (code !in 200..299) error("Sync izin gagal ($code): $resp")
                db.izinDao().tandaiSynced(iz.id)
                pushed++
            }

            prefs.lastSyncAt = System.currentTimeMillis()
            pushed
        }
    }
}

class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val client = SupabaseClient(applicationContext)
        if (!client.prefs.isConfigured) return Result.success()
        val db = DatabaseProvider.init(applicationContext)
        val res = client.syncAll(db)
        return if (res.isSuccess) Result.success() else Result.retry()
    }

    companion object {
        private const val WORK_NAME = "absensi_supabase_sync"

        fun enqueue(context: Context) {
            val req = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, req)
        }
    }
}
