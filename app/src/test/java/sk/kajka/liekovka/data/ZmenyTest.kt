package sk.kajka.liekovka.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ZmenyTest {
    private fun liek(doplatok: Double? = 3.15, vypadok: String? = null, od: String? = null) = Liek(
        "8723B", "Helicid 10", "cps 28x10 mg", "A02BC01", "omeprazol", "R", "Zentiva", 0,
        3.77, 0.62, doplatok, "", "", "", norm("Helicid 10 omeprazol cps 28x10 mg"), vypadok, od,
    )

    private val moj = MojLiek(kod = "8723B", nazov = "Helicid 10", doplnok = "", posledDoplatok = 3.15)

    @Test fun bezZmeny() {
        val r = porovnaj(moj, liek())
        assertTrue(r.spravy.isEmpty())
    }

    @Test fun vypadok() {
        val r = porovnaj(moj, liek(vypadok = "R", od = "2026-11-01"))
        assertEquals(listOf("Dodávky prerušené od 1. 11. 2026."), r.spravy)
        assertEquals("R", r.novy.posledVypadok)
        // druhé porovnanie s rovnakým stavom už neupozorňuje
        assertTrue(porovnaj(r.novy, liek(vypadok = "R", od = "2026-11-01")).spravy.isEmpty())
    }

    @Test fun obnovenie() {
        val r = porovnaj(moj.copy(posledVypadok = "R"), liek())
        assertEquals(listOf("Dodávky lieku boli obnovené."), r.spravy)
    }

    @Test fun zmenaDoplatku() {
        val r = porovnaj(moj, liek(doplatok = 1.20))
        assertEquals(1, r.spravy.size)
        assertTrue(r.spravy[0].startsWith("Doplatok sa zmenil z 3,15 € na 1,20 €"))
    }

    @Test fun zrusenyLiek() {
        val r = porovnaj(moj, null)
        assertEquals(1, r.spravy.size)
        assertTrue(porovnaj(r.novy, null).spravy.isEmpty())
    }

    @Test fun buducaZmena() {
        val l = liek().copy(buduciDoplatok = 4.20, buducaZmena = true)
        val r = porovnaj(moj, l, "2026-12-01")
        assertEquals(listOf("Od 1. 12. 2026 sa doplatok zvýši z 3,15 € na 4,20 €."), r.spravy)
        assertTrue(porovnaj(r.novy, l, "2026-12-01").spravy.isEmpty())   // oznámi sa len raz
        val vyradeny = liek().copy(buduciDoplatok = null, buducaZmena = true)
        assertTrue(porovnaj(moj, vyradeny, "2026-12-01").spravy[0].contains("nebude v zozname"))
        assertTrue(porovnaj(moj, l, "").spravy.isEmpty())                // bez dátumu nič
    }

    @Test fun lacnejsia() {
        val l = liek()
        assertTrue(jeLacnejsia(l, liek(doplatok = 1.20)))
        assertTrue(!jeLacnejsia(l, liek(doplatok = 3.15)))
        assertTrue(!jeLacnejsia(l, null))
    }

    @Test fun exspiracia() {
        val dnes = LocalDate.of(2026, 11, 12)
        assertNull(exspiraciaSprava(moj.copy(exspiracia = "2027-06-01"), dnes))
        assertEquals("Liek exspiruje o 3 dni (15. 11. 2026).", exspiraciaSprava(moj.copy(exspiracia = "2026-11-15"), dnes))
        assertTrue(exspiraciaSprava(moj.copy(exspiracia = "2026-10-01"), dnes)!!.startsWith("Lieku uplynula"))
        assertNull(exspiraciaSprava(moj.copy(exspiracia = "2026-11-15", upozornenieExspiracia = true), dnes))
    }

    @Test fun hladanie() {
        assertEquals("paralen", norm("Paralén"))
        assertEquals(listOf("omeprazol", "10"), slovaDopytu("  Omeprazol 10 "))
        val l = liek()
        assertEquals(1, filtrujSlova(listOf(l), listOf("28x10")).size)
        assertEquals(0, filtrujSlova(listOf(l), listOf("20mg")).size)
    }
}

class ZoznamTest {
    @Test fun text() {
        val z = ZoznamLiekov(
            "Mama", java.time.LocalDate.of(2026, 11, 12),
            listOf(
                PolozkaZoznamu("Helicid 10", "cps 28x10 mg", "omeprazol", "ráno nalačno", "2027-03-01"),
                PolozkaZoznamu("Paralen 500", "", "", "", null),
            ),
        )
        val t = z.akoText()
        assertTrue(t.startsWith("Zoznam liekov – Mama\nStav k 12. 11. 2026 · 2 lieky\n"))
        assertTrue(t.contains("1. Helicid 10 (cps 28x10 mg)\n   Účinná látka: omeprazol\n   Poznámka: ráno nalačno\n   Exspirácia: 1. 3. 2027\n"))
        assertTrue(t.contains("2. Paralen 500\n"))
    }
}

class StiahnutieTest {
    private fun info(sarze: String, vsetky: Boolean = false) =
        StiahnutieInfo("https://www.sukl.sk/a", "Stiahnutie lieku Rowatinex z trhu", "2026-08-18", "", "26808", sarze, vsetky)

    @Test fun dveSarze() {
        val t = stiahnutieText(info("8706L|apríl 2027;8768L|september 2027"))
        assertEquals(
            "ŠÚKL sťahuje z trhu šarže 8706L (exspirácia apríl 2027), 8768L (exspirácia september 2027). " +
                "Skontrolujte číslo šarže na obale (Lot, č. š.). Ak sa zhoduje, obráťte sa na lekáreň alebo lekára; " +
                "liečbu neprerušujte bez porady s odborníkom.", t,
        )
        assertEquals("Stiahnutie z trhu: šarže 8706L, 8768L", stiahnutieKratko(info("8706L|apríl 2027;8768L|september 2027")))
    }

    @Test fun vsetkySarze() {
        assertTrue(stiahnutieText(info("", vsetky = true)).startsWith("ŠÚKL sťahuje z trhu všetky šarže tohto lieku (oznam z 18. 8. 2026)."))
        assertTrue(stiahnutieText(info("")).contains("Číslo šarže nájdete v ozname ŠÚKL."))
    }
}
