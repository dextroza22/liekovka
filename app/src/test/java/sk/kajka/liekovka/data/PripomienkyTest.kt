package sk.kajka.liekovka.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

class PripomienkyTest {
    @Test fun casy() {
        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)), parsujCasy("20:00, 08:00,xx,08:00"))
        assertEquals("08:00,20:00", formatujCasy(listOf(LocalTime.of(20, 0), LocalTime.of(8, 0))))
        assertEquals("8:00, 20:30", casyText(parsujCasy("08:00,20:30")))
        assertEquals(emptyList<LocalTime>(), parsujCasy(""))
    }

    @Test fun najblizsi() {
        val casy = parsujCasy("08:00,20:00")
        assertEquals(LocalDateTime.of(2026, 11, 15, 20, 0), najblizsiCas(casy, LocalDateTime.of(2026, 11, 15, 9, 30)))
        assertEquals(LocalDateTime.of(2026, 11, 16, 8, 0), najblizsiCas(casy, LocalDateTime.of(2026, 11, 15, 20, 0)))   // presne v čase → ďalší
        assertNull(najblizsiCas(emptyList(), LocalDateTime.now()))
    }

    @Test fun liekyVCase() {
        val a = MojLiek(id = 1, kod = "A", nazov = "A", doplnok = "", casy = "08:00,20:00")
        val b = MojLiek(id = 2, kod = "B", nazov = "B", doplnok = "", casy = "20:00")
        assertEquals(listOf(a, b), liekyNaCas(listOf(a, b), LocalTime.of(20, 0)))
        assertEquals(listOf(a), liekyNaCas(listOf(a, b), LocalTime.of(8, 0)))
    }

    private fun liek(kod: String, doplatok: Double?, vypadok: String? = null) =
        Liek(kod, kod, "", "A02BC01", "omeprazol", "R", "", 0, null, null, doplatok, "", "", "", kod, vypadok)

    @Test fun sucet() {
        val s = sucetDoplatkov(listOf(
            liek("A", 3.15) to liek("X", 1.20),        // lacnejšia alternatíva
            liek("B", 2.00) to liek("Y", 2.50),        // alternatíva drahšia → bez zmeny
            liek("C", null) to null,                   // nehradený
            null to null,                              // liek už nie je v registri
        ))
        assertEquals(5.15, s.spolu, 0.001)
        assertEquals(3.20, s.sAlternativami, 0.001)
        assertEquals(1.95, s.uspora, 0.001)
        assertEquals(2, s.bezUdaja)
    }
}
