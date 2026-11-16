package sk.kajka.liekovka.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Čisté funkcie pre pripomienky užívania a súčet doplatkov (bez Androidu, testovateľné). */

/** "08:00,20:00" → [08:00, 20:00]; neplatné časy sa vynechajú, poradie podľa času, bez duplicít. */
fun parsujCasy(s: String): List<LocalTime> =
    s.split(',').mapNotNull { runCatching { LocalTime.parse(it.trim()) }.getOrNull() }.distinct().sorted()

fun formatujCasy(casy: List<LocalTime>): String =
    casy.distinct().sorted().joinToString(",") { "%02d:%02d".format(it.hour, it.minute) }

/** "8:00, 20:00" na zobrazenie. */
fun casyText(casy: List<LocalTime>): String = casy.joinToString(", ") { "${it.hour}:%02d".format(it.minute) }

/** Najbližší čas pripomienky po [teraz] zo všetkých časov; null ak žiadny liek nemá pripomienky. */
fun najblizsiCas(casy: Collection<LocalTime>, teraz: LocalDateTime): LocalDateTime? {
    if (casy.isEmpty()) return null
    val dnes = teraz.toLocalDate()
    return casy.map { c ->
        val d = LocalDateTime.of(dnes, c)
        if (d.isAfter(teraz)) d else LocalDateTime.of(dnes.plusDays(1), c)
    }.min()
}

/** Lieky z lekárničky, ktorých pripomienka pripadá na daný čas (s presnosťou na minútu). */
fun liekyNaCas(moje: List<MojLiek>, cas: LocalTime): List<MojLiek> =
    moje.filter { m -> parsujCasy(m.casy).any { it.hour == cas.hour && it.minute == cas.minute } }

/** Súčet doplatkov za jedno balenie každého lieku v lekárničke a súčet pri prechode na lacnejšie dostupné alternatívy. */
data class SucetDoplatkov(val spolu: Double, val sAlternativami: Double, val bezUdaja: Int) {
    val uspora get() = spolu - sAlternativami
}

/** [polozky]: pre každý liek v lekárničke dvojica (aktuálne údaje lieku, najlacnejšia dostupná alternatíva). */
fun sucetDoplatkov(polozky: List<Pair<Liek?, Liek?>>): SucetDoplatkov {
    var spolu = 0.0; var alt = 0.0; var bez = 0
    for ((l, a) in polozky) {
        val d = l?.doplatok
        if (d == null) { bez++; continue }
        spolu += d
        alt += if (l != null && jeLacnejsia(l, a)) a!!.doplatok!! else d
    }
    return SucetDoplatkov(spolu, alt, bez)
}

/** Začiatok obdobia "nových" výpadkov v prehľade pre lekárnikov. */
fun odNovychVypadkov(dnes: LocalDate, dni: Long = 14): String = dnes.minusDays(dni).toString()
