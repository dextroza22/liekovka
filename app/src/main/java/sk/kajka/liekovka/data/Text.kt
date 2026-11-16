package sk.kajka.liekovka.data

import java.text.Normalizer
import java.util.Locale

private val MARKS = Regex("\\p{Mn}+")
private val SK = Locale("sk", "SK")

/** Malé písmená bez diakritiky: "Paralén" → "paralen". */
fun norm(s: String): String =
    MARKS.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "").lowercase(Locale.ROOT).trim()

fun eur(v: Double?): String = if (v == null) "–" else String.format(SK, "%.2f €", v)

/** Spôsob výdaja z registra ŠÚKL v ľudskej reči. */
fun vydajText(v: String): String = when (v) {
    "F" -> "bez lekárskeho predpisu"
    "R" -> "na lekársky predpis"
    "Rx" -> "na lekársky predpis s obmedzením"
    "Rb" -> "na lekársky predpis (osobitné tlačivo)"
    "" -> "neuvedené"
    else -> v
}

/** Text stavu dodávok podľa hlásení ŠÚKL; null = bez hlásenia o výpadku. */
fun vypadokText(stav: String?, od: String?): String? {
    val d = od?.let { datum(it) } ?: ""
    return when (stav) {
        "R" -> "Dodávky prerušené od $d"
        "Z" -> "Dodávky skončené od $d"
        "PR" -> "Ohlásené prerušenie dodávok od $d"
        "PZ" -> "Ohlásené skončenie dodávok od $d"
        else -> null
    }
}

fun nedostupny(stav: String?) = stav == "R" || stav == "Z"

/** "2026-11-01" → "1. 11. 2026" */
fun datum(iso: String): String {
    val p = iso.take(10).split("-")
    return if (p.size == 3) "${p[2].trimStart('0')}. ${p[1].trimStart('0')}. ${p[0]}" else iso
}

/** Z výsledkov hrubého hľadania ponechá len tie, ktoré obsahujú všetky slová dopytu. */
fun filtrujSlova(lieky: List<Liek>, slova: List<String>): List<Liek> =
    lieky.filter { l -> slova.all { s -> l.hladaj.contains(s) || l.kod.equals(s, true) || norm(l.atc).startsWith(s) } }

fun slovaDopytu(q: String): List<String> = norm(q).split(Regex("\\s+")).filter { it.isNotBlank() }

/**
 * Adresa stránky lieku na sukl.sk (s odkazmi na aktuálny PIL a SPC), napr. "Helicid 10" + 8723B → sukl.sk/helicid-10-8723b.
 * Pravidlo zistené zo stránok ŠÚKL: malé písmená bez diakritiky, čiarky, percentá, zátvorky, úvodzovky a značky
 * sa vynechajú, bodky zostanú, ostatné znaky sa nahradia pomlčkou.
 */
fun suklStranka(nazov: String, kod: String): String {
    val s = norm(nazov)
        .replace(Regex("[,%()'\"®™µ]"), "")
        .replace(Regex("[^a-z0-9.]+"), "-")
        .trim('-', '.')
    return "https://www.sukl.sk/$s-${kod.lowercase()}"
}

/** Vyhľadávanie podľa kódu ŠÚKL; nájde liek vždy, aj keď sa jeho názov na webe líši od registra. */
fun suklHladajKod(kod: String) =
    "https://www.sukl.sk/rozsirene-vyhladavanie?search_type=drugs&medicament_code_fulltext=$kod"
