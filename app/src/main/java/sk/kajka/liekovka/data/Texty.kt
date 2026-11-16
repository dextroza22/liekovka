package sk.kajka.liekovka.data

/** Odsek právneho textu: nadpis (môže byť prázdny) a text. */
data class Odsek(val nadpis: String, val text: String)

data class PravnyText(val titulok: String, val ucinnost: String, val odseky: List<Odsek>)

/**
 * Zásady ochrany súkromia a podmienky používania sú v assets (sukromie.txt, podmienky.txt).
 * Z tých istých súborov vzniká aj webová stránka (tools/build_data.py), takže sa texty nemôžu rozísť.
 * Formát: 1. riadok titulok, 2. riadok účinnosť, "## " nadpis, odseky oddelené prázdnym riadkom.
 */
fun parsujPravnyText(surovy: String, meno: String, email: String): PravnyText {
    val riadky = surovy.replace("{MENO}", meno).replace("{EMAIL}", email).lines()
    val odseky = mutableListOf<Odsek>()
    var nadpis = ""
    val casti = mutableListOf<String>()      // odseky pod aktuálnym nadpisom
    val riadok = StringBuilder()
    fun koniecOdseku() { if (riadok.isNotBlank()) casti += riadok.toString().trim(); riadok.clear() }
    fun uloz() { koniecOdseku(); if (casti.isNotEmpty()) odseky += Odsek(nadpis, casti.joinToString("\n\n")); casti.clear() }
    for (r in riadky.drop(2)) {
        when {
            r.startsWith("## ") -> { uloz(); nadpis = r.removePrefix("## ").trim() }
            r.isBlank() -> koniecOdseku()
            else -> riadok.append(r.trim()).append(' ')
        }
    }
    uloz()
    return PravnyText(riadky.getOrElse(0) { "" }.trim(), riadky.getOrElse(1) { "" }.trim(), odseky)
}

const val SUKL_NEZIADUCE_UCINKY =
    "https://www.sukl.sk/pre-verejnost-pacientov-a-media/sluzby-pre-verejnost/hlasenie-podozreni-na-neziaduce-ucinky-liekov"
