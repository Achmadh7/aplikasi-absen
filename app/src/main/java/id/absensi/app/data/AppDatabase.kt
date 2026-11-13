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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlin.jvm.Volatile

@Entity(tableName = "karyawan")
data class KaryawanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nama: String,
    val jabatan: String,
    /** Shift.name */
    val shift: String,
    val aktif: Boolean = true,
    val dibuat: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "absensi",
    indices = [Index(value = ["karyawanId", "tanggal"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = KaryawanEntity::class,
            parentColumns = ["id"],
            childColumns = ["karyawanId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class AbsensiEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val karyawanId: Long,
    /** yyyy-MM-dd */
    val tanggal: String,
    val masukMenit: Int? = null,
    val pulangMenit: Int? = null,
    val dibuat: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "izin",
    indices = [Index(value = ["karyawanId", "tanggal"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = KaryawanEntity::class,
            parentColumns = ["id"],
            childColumns = ["karyawanId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class IzinEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val karyawanId: Long,
    /** yyyy-MM-dd */
    val tanggal: String,
    /** IZIN, SAKIT, atau CUTI */
    val jenis: String,
    val keterangan: String = "",
    val dibuat: Long = System.currentTimeMillis()
)

@Entity(tableName = "libur")
data class LiburEntity(
    /** yyyy-MM-dd */
    @PrimaryKey val tanggal: String,
    val keterangan: String = "",
    val dibuat: Long = System.currentTimeMillis()
)

@Dao
interface KaryawanDao {
    @Query("SELECT * FROM karyawan WHERE aktif = 1 ORDER BY nama ASC")
    suspend fun aktif(): List<KaryawanEntity>

    @Query("SELECT * FROM karyawan ORDER BY aktif DESC, nama ASC")
    suspend fun semua(): List<KaryawanEntity>

    @Query("SELECT * FROM karyawan WHERE id = :id")
    suspend fun byId(id: Long): KaryawanEntity?

    @Insert
    suspend fun insert(e: KaryawanEntity): Long

    @Update
    suspend fun update(e: KaryawanEntity)

    @Query("UPDATE karyawan SET aktif = 0 WHERE id = :id")
    suspend fun nonaktifkan(id: Long)

    @Query("UPDATE karyawan SET aktif = 1 WHERE id = :id")
    suspend fun aktifkan(id: Long)

    @Query("SELECT COUNT(*) FROM karyawan WHERE aktif = 1")
    suspend fun jumlahAktif(): Int
}

@Dao
interface AbsensiDao {
    @Query("SELECT * FROM absensi WHERE tanggal = :tanggal")
    suspend fun byTanggal(tanggal: String): List<AbsensiEntity>

    @Query("SELECT * FROM absensi WHERE karyawanId = :kid AND tanggal = :tanggal")
    suspend fun byKaryawanTanggal(kid: Long, tanggal: String): AbsensiEntity?

    @Query("SELECT * FROM absensi WHERE tanggal LIKE :prefix || '%'")
    suspend fun byBulan(prefix: String): List<AbsensiEntity>

    @Insert
    suspend fun insert(e: AbsensiEntity): Long

    @Update
    suspend fun update(e: AbsensiEntity)

    @Query("DELETE FROM absensi WHERE karyawanId = :kid AND tanggal = :tanggal")
    suspend fun hapus(kid: Long, tanggal: String)
}

@Dao
interface IzinDao {
    @Query("SELECT * FROM izin WHERE tanggal = :tanggal")
    suspend fun byTanggal(tanggal: String): List<IzinEntity>

    @Query("SELECT * FROM izin WHERE tanggal LIKE :prefix || '%'")
    suspend fun byBulan(prefix: String): List<IzinEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(e: IzinEntity): Long

    @Query("DELETE FROM izin WHERE karyawanId = :kid AND tanggal = :tanggal")
    suspend fun hapus(kid: Long, tanggal: String)
}

@Dao
interface LiburDao {
    @Query("SELECT * FROM libur ORDER BY tanggal ASC")
    suspend fun semua(): List<LiburEntity>

    @Query("SELECT * FROM libur WHERE tanggal LIKE :prefix || '%' ORDER BY tanggal ASC")
    suspend fun byBulan(prefix: String): List<LiburEntity>

    @Query("SELECT * FROM libur WHERE tanggal = :tanggal")
    suspend fun byTanggal(tanggal: String): LiburEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(e: LiburEntity)

    @Query("DELETE FROM libur WHERE tanggal = :tanggal")
    suspend fun hapus(tanggal: String)
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `libur` " +
                "(`tanggal` TEXT NOT NULL, `keterangan` TEXT NOT NULL, PRIMARY KEY(`tanggal`))"
        )
    }
}

@Database(
    entities = [KaryawanEntity::class, AbsensiEntity::class, IzinEntity::class, LiburEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun karyawanDao(): KaryawanDao
    abstract fun absensiDao(): AbsensiDao
    abstract fun izinDao(): IzinDao
    abstract fun liburDao(): LiburDao
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
            ).addMigrations(MIGRATION_1_2).fallbackToDestructiveMigration().build().also { instance = it }
        }
    }

    fun isInitialized(): Boolean = instance != null
}
