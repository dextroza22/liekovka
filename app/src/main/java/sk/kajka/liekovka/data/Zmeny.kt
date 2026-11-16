package sk.kajka.liekovka.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Výsledok porovnania lieku v lekárničke s novými dátami. */
data class ZmenaVysledok(val spravy: List<String>, val novy: MojLiek)

/**
 * Porovná posledný známy stav lieku v lekárničke s aktuálnymi dátami
 * a vráti správy o zmenách (výpadok, obnovenie, doplatok, zrušenie) a aktualizovaný záznam.
 */
fun porovnaj(moj: MojLiek, liek: Liek?, buduceOd: String = ""): ZmenaVysledok {
    val spravy = mutableListOf<String>()
    if (liek == null) {
        if (moj.posledExistuje) spravy += "Liek sa už nenachádza v registri ŠÚKL (registrácia mohla byť zrušená)."
        return ZmenaVysledok(spravy, moj.copy(posledExistuje = false))
    }
    if (!moj.posledExistuje) spravy += "Liek je opäť v registri ŠÚKL."

    if (liek.vypadok != moj.posledVypadok) {
        val t = vypadokText(liek.vypadok, liek.vypadokOd)
        when {
            t != null -> spravy += "$t."
            nedostupny(moj.posledVypadok) -> spravy += "Dodávky lieku boli obnovené."
            moj.posledVypadok != null -> spravy += "Ohlásený výpadok bol zrušený."
        }
    }
    val a = moj.posledDoplatok
    val b = liek.doplatok
    if (a != null && b != null && kotlin.math.abs(a - b) >= 0.005) {
        spravy += "Doplatok sa zmenil z ${eur(a)} na ${eur(b)}."
    } else if (a != null && b == null) {
        spravy += "Liek už nie je v zozname kategorizovaných liekov (poisťovňa ho nemusí hradiť)."
    }
    // ohlásená zmena od budúceho mesiaca: oznámi sa raz pre každú novú hodnotu
    var posledBuduci = moj.posledBuduci
    if (liek.buducaZmena && buduceOd.isNotBlank()) {
        val kluc = "$buduceOd|${liek.buduciDoplatok}"
        if (kluc != moj.posledBuduci) {
            spravy += buducaZmenaText(liek, buduceOd) + "."
            posledBuduci = kluc
        }
    }
    return ZmenaVysledok(
        spravy,
        moj.copy(posledDoplatok = b, posledVypadok = liek.vypadok, posledExistuje = true, posledBuduci = posledBuduci),
    )
}

/** "Od 1. 12. 2026 sa doplatok zvýši z 3,15 € na 4,20 €" alebo vyradenie zo zoznamu. */
fun buducaZmenaText(liek: Liek, buduceOd: String): String {
    val od = datum(buduceOd)
    val n = liek.buduciDoplatok
    return when {
        n == null -> "Od $od liek nebude v zozname kategorizovaných liekov (poisťovňa ho nemusí hradiť)"
        liek.doplatok == null -> "Od $od bude doplatok ${eur(n)}"
        n > liek.doplatok -> "Od $od sa doplatok zvýši z ${eur(liek.doplatok)} na ${eur(n)}"
        else -> "Od $od sa doplatok zníži z ${eur(liek.doplatok)} na ${eur(n)}"
    }
}

/** Lacnejšia dostupná alternatíva má zmysel, ak je doplatok nižší aspoň o 1 cent. */
fun jeLacnejsia(liek: Liek, alt: Liek?): Boolean =
    alt != null && liek.doplatok != null && alt.doplatok != null && alt.doplatok <= liek.doplatok - 0.01

/** Správa o blížiacej sa alebo uplynutej exspirácii, ak treba upozorniť (raz pre každý liek). */
fun exspiraciaSprava(moj: MojLiek, dnes: LocalDate, dniVopred: Long = 30): String? {
    if (moj.upozornenieExspiracia) return null
    val d = moj.exspiracia?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    val dni = ChronoUnit.DAYS.between(dnes, d)
    return when {
        dni < 0 -> "Lieku uplynula doba použiteľnosti (${datum(moj.exspiracia)})."
        dni <= dniVopred -> "Liek exspiruje o $dni ${dniSlovo(dni)} (${datum(moj.exspiracia)})."
        else -> null
    }
}

fun dniSlovo(n: Long) = when {
    n == 1L -> "deň"
    n in 2..4 -> "dni"
    else -> "dní"
}

/** Spoločná veta: na čo sa obrátiť; liečbu neprerušovať bez porady (náhle vysadenie môže škodiť). */
const val STIAHNUTIE_RADA = "Obráťte sa na lekáreň alebo lekára; liečbu neprerušujte bez porady s odborníkom."

/** Text upozornenia o stiahnutí lieku z trhu. */
fun stiahnutieText(s: StiahnutieInfo): String {
    val sarze = s.zoznamSarzi
    return when {
        s.vsetky -> "ŠÚKL sťahuje z trhu všetky šarže tohto lieku (oznam z ${datum(s.datum)}). $STIAHNUTIE_RADA"
        sarze.isEmpty() -> "ŠÚKL sťahuje z trhu niektoré šarže tohto lieku (oznam z ${datum(s.datum)}). " +
            "Číslo šarže nájdete v ozname ŠÚKL. $STIAHNUTIE_RADA"
        else -> {
            val zoznam = sarze.joinToString(", ") { (c, e) -> if (e.isBlank()) c else "$c (exspirácia $e)" }
            (if (sarze.size == 1) "ŠÚKL sťahuje z trhu šaržu $zoznam." else "ŠÚKL sťahuje z trhu šarže $zoznam.") +
                " Skontrolujte číslo šarže na obale (Lot, č. š.). Ak sa zhoduje, obráťte sa na lekáreň alebo lekára; " +
                "liečbu neprerušujte bez porady s odborníkom."
        }
    }
}

/** Krátky text do karty lieku, napr. "Stiahnutie z trhu: šarža 8706L, 8768L". */
fun stiahnutieKratko(s: StiahnutieInfo): String = when {
    s.vsetky -> "Stiahnutie z trhu: všetky šarže"
    s.zoznamSarzi.isEmpty() -> "Stiahnutie z trhu: niektoré šarže"
    s.zoznamSarzi.size == 1 -> "Stiahnutie z trhu: šarža ${s.zoznamSarzi[0].first}"
    else -> "Stiahnutie z trhu: šarže ${s.zoznamSarzi.joinToString(", ") { it.first }}"
}
