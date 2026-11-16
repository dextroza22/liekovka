package sk.kajka.liekovka.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZalohaTest {
    private val profily = listOf(Profil(1, "Ja"), Profil(2, "Mama"))
    private val lieky = listOf(
        MojLiek(id = 5, kod = "8723B", nazov = "Helicid 10", doplnok = "cps", profilId = 1, exspiracia = "2027-03-01", poznamka = "ráno", casy = "08:00"),
        MojLiek(id = 6, kod = "07339", nazov = "Tyverb", doplnok = "tbl", profilId = 2),
    )

    @Test fun tamASpat() {
        val z = Zaloha.zJson(Zaloha.naJson(profily, lieky, "2026-11-12"))
        assertEquals(profily, z.profily)
        assertEquals(listOf("8723B", "07339"), z.lieky.map { it.kod })
        assertEquals("ráno", z.lieky[0].poznamka)
        assertEquals("08:00", z.lieky[0].casy)
        assertEquals("2027-03-01", z.lieky[0].exspiracia)
        assertEquals(null, z.lieky[1].exspiracia)
        assertEquals(2L, z.lieky[1].profilId)
    }

    @Test fun naNovyTelefon() {
        // nový telefón má len predvolenú osobu "Ja"
        val z = Zaloha.zJson(Zaloha.naJson(profily, lieky, "2026-11-12"))
        val (nove, l) = Zaloha.spoj(z, listOf(Profil(1, "Ja")), emptyList())
        assertEquals(listOf("Mama"), nove.map { it.meno })
        assertEquals(1L, l[0].profilId)              // Ja → existujúca osoba
        assertEquals(nove[0].id, l[1].profilId)      // Mama → nová osoba (dočasné id)
        assertTrue(l.all { it.id == 0L })
    }

    @Test fun bezDuplicit() {
        val z = Zaloha.zJson(Zaloha.naJson(profily, lieky, "2026-11-12"))
        val (nove, l) = Zaloha.spoj(z, profily, lieky)
        assertTrue(nove.isEmpty())
        assertTrue(l.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class) fun cudziSubor() {
        Zaloha.zJson("""{"ahoj": 1}""")
    }
}
