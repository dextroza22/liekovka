package sk.kajka.liekovka.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Overí, že aktualizácia databázy z verzie 2 na 3 zachová lekárničku používateľa. */
@RunWith(AndroidJUnit4::class)
class MigraciaTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDb::class.java)

    @Test
    fun migracia2na3() {
        helper.createDatabase("migracia-test", 2).apply {
            execSQL("INSERT INTO profil (id, meno) VALUES (1, 'Ja')")
            execSQL(
                "INSERT INTO moj_liek (id, kod, nazov, doplnok, profilId, pridane, exspiracia, poznamka, posledDoplatok, " +
                    "posledVypadok, posledExistuje, posledBuduci, upozornenieExspiracia) " +
                    "VALUES (1, '26808', 'Rowatinex', 'gtt por 1x10 ml', 1, 0, '2027-04-01', 'ráno', NULL, NULL, 1, NULL, 0)"
            )
            close()
        }
        val db = helper.runMigrationsAndValidate("migracia-test", 3, true, MIGRACIA_2_3)
        db.query("SELECT nazov, poznamka FROM moj_liek").use {
            it.moveToFirst()
            assertEquals("Rowatinex", it.getString(0))
            assertEquals("ráno", it.getString(1))
        }
        db.query("SELECT COUNT(*) FROM stiahnutie").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        db.close()
        // 3 → 4: pribudnú časy pripomienok, lekárnička zostane
        val db4 = helper.runMigrationsAndValidate("migracia-test", 4, true, MIGRACIA_3_4)
        db4.query("SELECT nazov, casy FROM moj_liek").use {
            it.moveToFirst()
            assertEquals("Rowatinex", it.getString(0))
            assertEquals("", it.getString(1))
        }
    }
}
