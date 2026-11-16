package sk.kajka.liekovka.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TextyTest {
    private fun asset(n: String) = listOf("src/main/assets/$n", "app/src/main/assets/$n").map(::File).first { it.exists() }.readText()

    @Test fun parsovanie() {
        val t = parsujPravnyText("Titulok\nÚčinné od 1. 1. 2027\n\n## Prvý\nA {MENO}\nb.\n\nC {EMAIL}\n\n## Druhý\nD", "Ján", "j@x.sk")
        assertEquals("Titulok", t.titulok)
        assertEquals(listOf(Odsek("Prvý", "A Ján b.\n\nC j@x.sk"), Odsek("Druhý", "D")), t.odseky)
    }

    @Test fun textyVAplikacii() {
        for (n in listOf("sukromie.txt", "podmienky.txt")) {
            val t = parsujPravnyText(asset(n), "Meno", "e@x.sk")
            assertTrue(n, t.odseky.size >= 6)
            assertTrue(n, t.odseky.none { "{" in it.text })       // žiadna nevyplnená značka
        }
        assertTrue(parsujPravnyText(asset("podmienky.txt"), "M", "e").odseky.any { "Nie je oficiálnou aplikáciou" in it.text })
    }
}
