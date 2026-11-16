package sk.kajka.liekovka.data

import java.time.LocalDate

data class PolozkaZoznamu(
    val nazov: String,
    val doplnok: String,
    val latka: String,
    val poznamka: String,
    val exspiracia: String?,
)

/** Zoznam liekov jedného profilu na zdieľanie s lekárom, v nemocnici alebo s rodinou. */
data class ZoznamLiekov(val meno: String, val stavK: LocalDate, val polozky: List<PolozkaZoznamu>) {
    val nadpis get() = "Zoznam liekov – $meno"
    val podnadpis get() = "Stav k ${datum(stavK.toString())} · ${polozky.size} ${if (polozky.size == 1) "liek" else if (polozky.size in 2..4) "lieky" else "liekov"}"

    /** Riadky jednej položky: názov a doplnok, potom detaily. */
    fun riadky(p: PolozkaZoznamu): List<String> = buildList {
        if (p.latka.isNotBlank()) add("Účinná látka: ${p.latka}")
        if (p.poznamka.isNotBlank()) add("Poznámka: ${p.poznamka}")
        if (p.exspiracia != null) add("Exspirácia: ${datum(p.exspiracia)}")
    }

    fun akoText(): String = buildString {
        appendLine(nadpis)
        appendLine(podnadpis)
        appendLine()
        polozky.forEachIndexed { i, p ->
            append("${i + 1}. ${p.nazov}")
            if (p.doplnok.isNotBlank()) append(" (${p.doplnok})")
            appendLine()
            riadky(p).forEach { appendLine("   $it") }
        }
        appendLine()
        append(PATA)
    }

    companion object {
        const val PATA = "Vytvorené v aplikácii Liekovka. Zoznam má informatívny charakter; aktuálnu liečbu vždy overte u lekára."
    }
}
