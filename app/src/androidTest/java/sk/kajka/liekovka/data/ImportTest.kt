package sk.kajka.liekovka.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/**
 * Import dátového balíka do databázy a dopyty nad ním. Spustenie: Android Studio → pravý klik → Run 'ImportTest'.
 * Ak sú v assets pribalené dáta, test ich naimportuje a overí vyhľadávanie na skutočnom objeme.
 */
@RunWith(AndroidJUnit4::class)
class ImportTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    /** Oddelená databáza v pamäti a vlastné nastavenia, aby test nezmenil dáta aplikácie v telefóne. */
    private fun testRepo() = Repository(
        ctx,
        Room.inMemoryDatabaseBuilder(ctx, AppDb::class.java).build(),
        Prefs(ctx, "liekovka-test"),
    )

    private fun gz(json: String): ByteArray {
        val b = ByteArrayOutputStream()
        GZIPOutputStream(b).use { it.write(json.toByteArray()) }
        return b.toByteArray()
    }

    private val mini = """
        {"format":1,"verzia":"V1","kategorizaciaOd":"2026-11-01","vypadkyK":"2026-11-12",
         "polia":["kod","nazov","doplnok","atc","latka","vydaj","drzitel","skupina","cena","uhrada","doplatok","spc","pil","reg"],
         "skupiny":[["A02BC01","Omeprazol","p.o. 10 mg (28 ks)"]],
         "lieky":[
          ["8723B","Helicid 10","cps 28x10 mg","A02BC01","Omeprazol","R","ZNT",0,3.77,0.62,3.15,"","https://x/PIL1.pdf","09/0001"],
          ["1111A","Omeprazol Teva 10","cps 28x10 mg","A02BC01","Omeprazol","R","TEV",0,2.5,0.62,1.88,"","",""],
          ["2222C","Paralén 500","tbl 24x500 mg","N02BE01","Paracetamol","F","ZNT",-1,null,null,null,"","",""]],
         "vypadky":{"1111A":["R","2026-11-01"]},
         "buduce":{"od":"2026-12-01","doplatky":{"1111A":null}}}
    """.trimIndent()

    @Test
    fun importMalehoBalika() = runBlocking {
        val repo = testRepo()
        repo.importuj(gz(mini).inputStream())
        val dao = repo.db.lieky()
        assertEquals(3, dao.count())
        assertEquals("V1", repo.prefs.verzia)
        assertEquals("R", dao.getNow("1111A")!!.vypadok)
        // budúci mesiac: 1111A vyradený zo zoznamu, 8723B bez zmeny
        assertTrue(dao.getNow("1111A")!!.buducaZmena)
        assertEquals(false, dao.getNow("8723B")!!.buducaZmena)
        assertEquals("2026-12-01", repo.prefs.buduceOd)
        // diakritika: "paralen" nájde "Paralén"
        assertEquals("2222C", dao.hladaj("paralen", "PARALEN").first().kod)
        // v skupine: dostupný Helicid pred nedostupným Omeprazolom Teva
        assertEquals(listOf("8723B", "1111A"), dao.vSkupine(0).first().map { it.kod })

        // lekárnička: Helicid pridaný, potom nové dáta s výpadkom a zmenou doplatku
        repo.pridaj(dao.getNow("8723B")!!, "2026-11-20", 1)
        val nove = mini.replace("\"V1\"", "\"V2\"").replace("3.15,\"\"", "2.90,\"\"")
            .replace("{\"1111A\":[\"R\",\"2026-11-01\"]}", "{\"8723B\":[\"R\",\"2026-11-10\"]}")
        repo.importuj(gz(nove).inputStream())
        assertEquals(2, repo.vyhodnotZmeny())
        val texty = repo.db.upozornenia().all().first().map { it.text }
        assertTrue(texty.any { it.startsWith("Dodávky prerušené od 10. 11. 2026") })
        assertTrue(texty.any { it.startsWith("Doplatok sa zmenil z 3,15 € na 2,90 €") })
        assertEquals(0, repo.vyhodnotZmeny()) // rovnaký stav znova neupozorňuje
        assertEquals(1, repo.skontrolujExspiracie())   // exspirácia 20. 11. 2026 je do 30 dní (alebo už uplynula)
        assertEquals(0, repo.skontrolujExspiracie())   // upozorní len raz
    }

    @Test
    fun importPribalenychDat() = runBlocking {
        val asset = Repository.ASSETY.firstNotNullOfOrNull { runCatching { ctx.assets.open(it) }.getOrNull() }
        assumeTrue("V assets nie sú pribalené dáta", asset != null)
        val repo = testRepo()
        val t0 = System.currentTimeMillis()
        asset!!.use { repo.importuj(it) }
        val ms = System.currentTimeMillis() - t0
        val dao = repo.db.lieky()
        val n = dao.count()
        println("Import: $n liekov za $ms ms")
        assertTrue(n > 1000)
        val slova = slovaDopytu("omeprazol 20")
        val res = filtrujSlova(dao.hladaj(slova[0], "OMEPRAZOL 20"), slova.drop(1))
        println("omeprazol 20 → ${res.size} výsledkov, napr. ${res.take(3).map { it.nazov + " " + it.doplnok }}")
        assertTrue(res.isNotEmpty())
        val sk = res.first { it.skupina >= 0 }.skupina
        println("skupina → ${dao.vSkupine(sk).first().map { "${it.nazov} ${eur(it.doplatok)} ${it.vypadok ?: ""}" }.take(5)}")
        val atc = dao.hladaj("c09aa", "C09AA")
        println("ATC C09AA → ${atc.size}")
        assertTrue(atc.isNotEmpty())
    }
}
