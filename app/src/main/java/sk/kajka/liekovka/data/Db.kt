package sk.kajka.liekovka.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Liek (balenie) z registra ŠÚKL, doplnený o ceny z kategorizácie MZ SR a stav dodávok. */
@Entity(tableName = "liek", indices = [Index("skupina"), Index("atc")])
data class Liek(
    @PrimaryKey val kod: String,
    val nazov: String,
    val doplnok: String,
    val atc: String,
    val latka: String,
    val vydaj: String,
    val drzitel: String,
    /** Referenčná skupina MZ SR (rovnaká látka, sila, forma), -1 ak liek nie je kategorizovaný. */
    val skupina: Int,
    val cena: Double?,
    val uhrada: Double?,
    val doplatok: Double?,
    val spc: String,
    val pil: String,
    val reg: String,
    /** Text na vyhľadávanie: názov, látka, doplnok bez diakritiky a malými písmenami. */
    val hladaj: String,
    /** R prerušené, Z skončené, PR/PZ ohlásené do budúcna, null = bez hlásenia. */
    val vypadok: String? = null,
    val vypadokOd: String? = null,
    /** Zmena od budúceho mesiaca: nový doplatok; pri [buducaZmena] = true a null je liek vyradený zo zoznamu. */
    val buduciDoplatok: Double? = null,
    val buducaZmena: Boolean = false,
)

@Entity(tableName = "skupina")
data class Skupina(
    @PrimaryKey val id: Int,
    val atc: String,
    val latka: String,
    val forma: String,
)

/** Osoba, ktorej lieky sa evidujú (Ja, Mama, Dcéra…). */
@Entity(tableName = "profil")
data class Profil(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val meno: String,
)

/** Liek v lekárničke používateľa. Ukladá sa len v telefóne. */
@Entity(tableName = "moj_liek", indices = [Index("kod"), Index("profilId")])
data class MojLiek(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kod: String,
    val nazov: String,
    val doplnok: String,
    val profilId: Long = 1,
    val pridane: Long = System.currentTimeMillis(),
    /** Dátum exspirácie RRRR-MM-DD alebo null. */
    val exspiracia: String? = null,
    /** Vlastná poznámka, napr. ako liek užívať podľa lekára. */
    val poznamka: String = "",
    // posledný známy stav, podľa ktorého sa po aktualizácii dát zisťujú zmeny
    val posledDoplatok: Double? = null,
    val posledVypadok: String? = null,
    val posledExistuje: Boolean = true,
    /** Kľúč už oznámenej budúcej zmeny doplatku ("RRRR-MM-DD|doplatok"), aby sa neoznamovala opakovane. */
    val posledBuduci: String? = null,
    val upozornenieExspiracia: Boolean = false,
    /** Časy pripomienok užívania "HH:mm" oddelené čiarkou (napr. "08:00,20:00"), prázdne = bez pripomienok. */
    @androidx.room.ColumnInfo(defaultValue = "") val casy: String = "",
)

@Entity(tableName = "upozornenie")
data class Upozornenie(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kod: String,
    val nazov: String,
    val text: String,
    val cas: Long = System.currentTimeMillis(),
    val precitane: Boolean = false,
)

/** Oznam ŠÚKL o stiahnutí lieku z trhu. */
@Entity(tableName = "stiahnutie")
data class Stiahnutie(
    @PrimaryKey val url: String,
    val nazov: String,
    /** Dátum zverejnenia RRRR-MM-DD. */
    val datum: String,
    val dovod: String,
    /** Trieda urgentnosti (I, II, III) alebo prázdne. */
    val trieda: String,
)

/** Liek (kód ŠÚKL), ktorého sa oznam týka, so šaržami. */
@Entity(tableName = "stiahnutie_liek", primaryKeys = ["url", "kod"], indices = [Index("kod")])
data class StiahnutieLiek(
    val url: String,
    val kod: String,
    /** Šarže ako "číslo|exspirácia" oddelené znakom ";" (napr. "8706L|apríl 2027;8768L|september 2027"). */
    val sarze: String,
    val vsetky: Boolean,
)

/** Oznam spojený s konkrétnym liekom (výsledok dopytu). */
data class StiahnutieInfo(
    val url: String,
    val nazov: String,
    val datum: String,
    val dovod: String,
    val kod: String,
    val sarze: String,
    val vsetky: Boolean,
) {
    /** Zoznam (číslo šarže, exspirácia). */
    val zoznamSarzi: List<Pair<String, String>>
        get() = sarze.split(';').filter { it.isNotBlank() }.map { it.substringBefore('|') to it.substringAfter('|', "") }
}

@Dao
interface StiahnutieDao {
    @Query("DELETE FROM stiahnutie")
    fun deleteAll()

    @Query("DELETE FROM stiahnutie_liek")
    fun deleteLieky()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(s: List<Stiahnutie>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertLieky(s: List<StiahnutieLiek>)

    @Query("SELECT * FROM stiahnutie ORDER BY datum DESC, nazov")
    fun all(): Flow<List<Stiahnutie>>

    @Query("SELECT * FROM stiahnutie_liek")
    fun allLieky(): Flow<List<StiahnutieLiek>>

    @Query(
        """SELECT s.url, s.nazov, s.datum, s.dovod, l.kod, l.sarze, l.vsetky
           FROM stiahnutie_liek l JOIN stiahnutie s ON s.url = l.url WHERE l.kod = :kod ORDER BY s.datum DESC"""
    )
    fun preKod(kod: String): Flow<List<StiahnutieInfo>>

    @Query(
        """SELECT s.url, s.nazov, s.datum, s.dovod, l.kod, l.sarze, l.vsetky
           FROM stiahnutie_liek l JOIN stiahnutie s ON s.url = l.url"""
    )
    fun vsetkyInfoNow(): List<StiahnutieInfo>
}

@Dao
interface LiekDao {
    @Query("SELECT COUNT(*) FROM liek")
    fun count(): Int

    @Query("DELETE FROM liek")
    fun deleteAll()

    @Query("DELETE FROM skupina")
    fun deleteSkupiny()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(lieky: List<Liek>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSkupiny(s: List<Skupina>)

    @Query("UPDATE liek SET vypadok = :stav, vypadokOd = :od WHERE kod = :kod")
    fun setVypadok(kod: String, stav: String, od: String)

    @Query("UPDATE liek SET buduciDoplatok = :doplatok, buducaZmena = 1 WHERE kod = :kod")
    fun setBuduci(kod: String, doplatok: Double?)

    @Query("SELECT * FROM liek WHERE kod = :kod")
    fun getNow(kod: String): Liek?

    @Query("SELECT * FROM liek WHERE kod = :kod")
    fun observe(kod: String): Flow<Liek?>

    @Query("SELECT * FROM skupina WHERE id = :id")
    fun skupina(id: Int): Flow<Skupina?>

    /** Lieky v rovnakej referenčnej skupine, dostupné a najlacnejšie navrchu. */
    @Query(
        """SELECT * FROM liek WHERE skupina = :skupina AND skupina >= 0
           ORDER BY (vypadok IN ('R','Z')), doplatok IS NULL, doplatok, nazov"""
    )
    fun vSkupine(skupina: Int): Flow<List<Liek>>

    /** Najlacnejší dostupný iný liek v rovnakej referenčnej skupine (kandidát na lacnejšiu alternatívu). */
    @Query(
        """SELECT * FROM liek WHERE skupina = :skupina AND skupina >= 0 AND kod != :kod AND doplatok IS NOT NULL
           AND (vypadok IS NULL OR vypadok NOT IN ('R','Z'))
           ORDER BY doplatok, nazov LIMIT 1"""
    )
    fun najlacnejsi(skupina: Int, kod: String): Flow<Liek?>

    @Query(
        """SELECT * FROM liek WHERE skupina = :skupina AND skupina >= 0 AND kod != :kod AND doplatok IS NOT NULL
           AND (vypadok IS NULL OR vypadok NOT IN ('R','Z'))
           ORDER BY doplatok, nazov LIMIT 1"""
    )
    fun najlacnejsiNow(skupina: Int, kod: String): Liek?

    /** Prehľad pre lekárnikov: hlásenia o výpadku s dátumom od [od] (vrátane ohlásených do budúcna). */
    @Query(
        """SELECT * FROM liek WHERE vypadok IS NOT NULL AND vypadokOd >= :od AND atc LIKE :atc || '%'
           ORDER BY vypadokOd DESC, nazov LIMIT 400"""
    )
    fun noveVypadky(od: String, atc: String): Flow<List<Liek>>

    /** Prehľad pre lekárnikov: lieky so zmenou doplatku od budúceho mesiaca. */
    @Query("SELECT * FROM liek WHERE buducaZmena = 1 AND atc LIKE :atc || '%' ORDER BY nazov LIMIT 400")
    fun buduceZmeny(atc: String): Flow<List<Liek>>

    @Query("SELECT * FROM liek WHERE atc = :atc AND atc != '' AND kod != :kod ORDER BY nazov LIMIT 40")
    fun rovnakeAtc(atc: String, kod: String): Flow<List<Liek>>

    /** Hrubé vyhľadanie podľa prvého slova; ďalšie slová sa filtrujú v kóde. */
    @Query(
        """SELECT * FROM liek
           WHERE hladaj LIKE '%' || :slovo || '%' OR kod = :presne OR atc LIKE :presne || '%'
           ORDER BY (hladaj NOT LIKE :slovo || '%'), (skupina < 0), nazov, doplnok
           LIMIT 400"""
    )
    suspend fun hladaj(slovo: String, presne: String): List<Liek>
}

@Dao
interface ProfilDao {
    @Query("SELECT * FROM profil ORDER BY id")
    fun all(): Flow<List<Profil>>

    @Query("SELECT * FROM profil ORDER BY id")
    fun allNow(): List<Profil>

    @Insert
    suspend fun insert(p: Profil): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertNow(p: Profil): Long

    @Update
    suspend fun update(p: Profil)

    @Query("DELETE FROM profil WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM profil")
    fun deleteAllNow()
}

@Dao
interface MojeDao {
    @Query("SELECT * FROM moj_liek WHERE profilId = :profil ORDER BY exspiracia IS NULL, exspiracia, nazov")
    fun all(profil: Long): Flow<List<MojLiek>>

    @Query("SELECT * FROM moj_liek")
    fun allNow(): List<MojLiek>

    @Query("SELECT * FROM moj_liek")
    fun vsetky(): Flow<List<MojLiek>>

    @Query("SELECT * FROM moj_liek WHERE profilId = :profil ORDER BY nazov")
    suspend fun vProfile(profil: Long): List<MojLiek>

    @Query("SELECT COUNT(*) FROM moj_liek WHERE kod = :kod AND profilId = :profil")
    fun countKod(kod: String, profil: Long): Flow<Int>

    /** Počet liekov v lekárničke profilu, pri ktorých ŠÚKL hlási alebo ohlásil výpadok. */
    @Query("SELECT COUNT(*) FROM moj_liek m JOIN liek l ON l.kod = m.kod WHERE m.profilId = :profil AND l.vypadok IS NOT NULL")
    fun sVypadkom(profil: Long): Flow<Int>

    @Insert
    suspend fun insert(m: MojLiek): Long

    @Insert
    fun insertNow(m: MojLiek): Long

    @Update
    suspend fun update(m: MojLiek)

    @Update
    fun updateNow(m: MojLiek)

    @Query("DELETE FROM moj_liek WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM moj_liek WHERE profilId = :profil")
    suspend fun deleteProfil(profil: Long)

    @Query("DELETE FROM moj_liek")
    fun deleteAllNow()
}

@Dao
interface UpozornenieDao {
    @Query("SELECT * FROM upozornenie ORDER BY cas DESC LIMIT 300")
    fun all(): Flow<List<Upozornenie>>

    @Query("SELECT COUNT(*) FROM upozornenie WHERE precitane = 0")
    fun neprecitane(): Flow<Int>

    @Insert
    fun insertNow(u: Upozornenie): Long

    @Query("UPDATE upozornenie SET precitane = 1 WHERE id = :id")
    suspend fun precitaj(id: Long)

    @Query("UPDATE upozornenie SET precitane = 1")
    suspend fun precitajVsetky()

    @Query("DELETE FROM upozornenie")
    suspend fun zmazVsetky()

    @Query("DELETE FROM upozornenie")
    fun zmazVsetkyNow()
}

@Database(
    entities = [Liek::class, Skupina::class, Profil::class, MojLiek::class, Upozornenie::class,
        Stiahnutie::class, StiahnutieLiek::class],
    version = 4,
)
abstract class AppDb : RoomDatabase() {
    abstract fun lieky(): LiekDao
    abstract fun profily(): ProfilDao
    abstract fun moje(): MojeDao
    abstract fun upozornenia(): UpozornenieDao
    abstract fun stiahnutia(): StiahnutieDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "liekovka.db")
                // verzia 1 bola len testovacia (pred zverejnením); od verzie 2 treba písať migrácie
                .fallbackToDestructiveMigrationFrom(1)
                .addMigrations(MIGRACIA_2_3, MIGRACIA_3_4)
                .build().also { inst = it }
        }
    }
}

/** 2 → 3: tabuľky so stiahnutiami z trhu. Lekárnička a upozornenia zostávajú. */
val MIGRACIA_2_3 = object : androidx.room.migration.Migration(2, 3) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `stiahnutie` (`url` TEXT NOT NULL, `nazov` TEXT NOT NULL, `datum` TEXT NOT NULL, `dovod` TEXT NOT NULL, `trieda` TEXT NOT NULL, PRIMARY KEY(`url`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `stiahnutie_liek` (`url` TEXT NOT NULL, `kod` TEXT NOT NULL, `sarze` TEXT NOT NULL, `vsetky` INTEGER NOT NULL, PRIMARY KEY(`url`, `kod`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stiahnutie_liek_kod` ON `stiahnutie_liek` (`kod`)")
    }
}

/** 3 → 4: časy pripomienok užívania pri lieku v lekárničke. */
val MIGRACIA_3_4 = object : androidx.room.migration.Migration(3, 4) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `moj_liek` ADD COLUMN `casy` TEXT NOT NULL DEFAULT ''")
    }
}
