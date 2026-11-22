package id.absensi.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlin.jvm.Volatile

// shortcut: NIS dipakai langsung sebagai kode barcode; tambah kolom kodeBarcode terpisah jika kartu cetak memakai ID berbeda.
@Entity(
    tableName = "siswa",
    indices = [Index(value = ["nis"], unique = true)]
)
data class SiswaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nama: String,
    val nis: String,
    val kelas: String,
    val noWaOrtu: String,
    /** JamSekolah.name */
    val sesi: String,
    val aktif: Boolean = true,
    val dibuat: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "absensi",
    indices = [Index(value = ["siswaId", "tanggal"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = SiswaEntity::class,
            parentColumns = ["id"],
            childColumns = ["siswaId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class AbsensiEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val siswaId: Long,
    /** yyyy-MM-dd */
    val tanggal: String,
    val masukMenit: Int? = null,
    val pulangMenit: Int? = null,
    val dibuat: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "izin",
    indices = [Index(value = ["siswaId", "tanggal"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = SiswaEntity::class,
            parentColumns = ["id"],
            childColumns = ["siswaId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class IzinEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val siswaId: Long,
    /** yyyy-MM-dd */
    val tanggal: String,
    /** IZIN atau SAKIT */
    val jenis: String,
    val keterangan: String = "",
    val dibuat: Long = System.currentTimeMillis()
)

@Dao
interface SiswaDao {
    @Query("SELECT * FROM siswa WHERE aktif = 1 ORDER BY kelas ASC, nama ASC")
    suspend fun aktif(): List<SiswaEntity>

    @Query("SELECT * FROM siswa ORDER BY aktif DESC, kelas ASC, nama ASC")
    suspend fun semua(): List<SiswaEntity>

    @Query("SELECT * FROM siswa WHERE id = :id")
    suspend fun byId(id: Long): SiswaEntity?

    @Query("SELECT * FROM siswa WHERE nis = :nis AND aktif = 1 LIMIT 1")
    suspend fun byNisAktif(nis: String): SiswaEntity?

    @Insert
    suspend fun insert(e: SiswaEntity): Long

    @Update
    suspend fun update(e: SiswaEntity)

    @Query("UPDATE siswa SET aktif = 0 WHERE id = :id")
    suspend fun nonaktifkan(id: Long)

    @Query("UPDATE siswa SET aktif = 1 WHERE id = :id")
    suspend fun aktifkan(id: Long)

    @Query("SELECT COUNT(*) FROM siswa WHERE aktif = 1")
    suspend fun jumlahAktif(): Int
}

@Dao
interface AbsensiDao {
    @Query("SELECT * FROM absensi WHERE tanggal = :tanggal")
    suspend fun byTanggal(tanggal: String): List<AbsensiEntity>

    @Query("SELECT * FROM absensi WHERE siswaId = :sid AND tanggal = :tanggal")
    suspend fun bySiswaTanggal(sid: Long, tanggal: String): AbsensiEntity?

    @Query("SELECT * FROM absensi WHERE tanggal LIKE :prefix || '%'")
    suspend fun byBulan(prefix: String): List<AbsensiEntity>

    @Insert
    suspend fun insert(e: AbsensiEntity): Long

    @Update
    suspend fun update(e: AbsensiEntity)

    @Query("DELETE FROM absensi WHERE siswaId = :sid AND tanggal = :tanggal")
    suspend fun hapus(sid: Long, tanggal: String)
}

@Dao
interface IzinDao {
    @Query("SELECT * FROM izin WHERE tanggal = :tanggal")
    suspend fun byTanggal(tanggal: String): List<IzinEntity>

    @Query("SELECT * FROM izin WHERE tanggal LIKE :prefix || '%'")
    suspend fun byBulan(prefix: String): List<IzinEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(e: IzinEntity): Long

    @Query("DELETE FROM izin WHERE siswaId = :sid AND tanggal = :tanggal")
    suspend fun hapus(sid: Long, tanggal: String)
}

@Database(
    entities = [SiswaEntity::class, AbsensiEntity::class, IzinEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun siswaDao(): SiswaDao
    abstract fun absensiDao(): AbsensiDao
    abstract fun izinDao(): IzinDao
}

object DatabaseProvider {
    @Volatile
    private var instance: AppDatabase? = null

    fun init(context: Context): AppDatabase {
        return instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "absensi.db"
            ).fallbackToDestructiveMigration().build().also { instance = it }
        }
    }

    fun isInitialized(): Boolean = instance != null
}
