package sk.kajka.liekovka.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Záloha lekárničky do súboru (osoby a lieky), aby sa dala preniesť na nový telefón.
 * Obsahuje len to, čo zadal používateľ; údaje o liekoch (doplatky, výpadky) sa po obnovení doplnia z aktuálnych dát.
 */
object Zaloha {
    const val FORMAT = 1
    const val APP = "Liekovka"

    data class Obsah(val profily: List<Profil>, val lieky: List<MojLiek>)

    fun naJson(profily: List<Profil>, lieky: List<MojLiek>, vytvorene: String): String = JSONObject()
        .put("app", APP)
        .put("format", FORMAT)
        .put("vytvorene", vytvorene)
        .put("profily", JSONArray(profily.map { JSONObject().put("id", it.id).put("meno", it.meno) }))
        .put("lieky", JSONArray(lieky.map { m ->
            JSONObject()
                .put("profilId", m.profilId)
                .put("kod", m.kod)
                .put("nazov", m.nazov)
                .put("doplnok", m.doplnok)
                .put("exspiracia", m.exspiracia ?: JSONObject.NULL)
                .put("poznamka", m.poznamka)
                .put("pridane", m.pridane)
                .put("casy", m.casy)
        }))
        .toString(1)

    /** Načíta zálohu; pri súbore, ktorý nie je zálohou Liekovky, vyhodí chybu so zrozumiteľným textom. */
    fun zJson(text: String): Obsah {
        val o = runCatching { JSONObject(text) }.getOrElse { throw IllegalArgumentException("Súbor nie je záloha Liekovky.") }
        require(o.optString("app") == APP) { "Súbor nie je záloha Liekovky." }
        require(o.optInt("format", 1) <= FORMAT) { "Zálohu vytvorila novšia verzia aplikácie. Aktualizujte Liekovku." }
        val p = o.optJSONArray("profily") ?: JSONArray()
        val l = o.optJSONArray("lieky") ?: JSONArray()
        val profily = (0 until p.length()).map { i -> p.getJSONObject(i).let { Profil(it.getLong("id"), it.getString("meno")) } }
        val lieky = (0 until l.length()).map { i ->
            l.getJSONObject(i).let {
                MojLiek(
                    kod = it.getString("kod"),
                    nazov = it.optString("nazov"),
                    doplnok = it.optString("doplnok"),
                    profilId = it.optLong("profilId", 1),
                    exspiracia = if (it.isNull("exspiracia")) null else it.optString("exspiracia").ifBlank { null },
                    poznamka = it.optString("poznamka"),
                    pridane = it.optLong("pridane", System.currentTimeMillis()),
                    casy = it.optString("casy"),
                )
            }
        }
        return Obsah(profily, lieky)
    }

    /**
     * Spojí zálohu s tým, čo už v telefóne je: osoby sa párujú podľa mena, rovnaký liek
     * (osoba, kód, exspirácia, poznámka) sa nepridá druhýkrát.
     * Vráti nové osoby (s dočasnými zápornými id) a lieky s priradeným id osoby.
     */
    fun spoj(zaloha: Obsah, existujuce: List<Profil>, existujuceLieky: List<MojLiek>): Pair<List<Profil>, List<MojLiek>> {
        val podlaMena = existujuce.associateBy { it.meno.trim().lowercase() }
        val nove = mutableListOf<Profil>()
        val mapa = mutableMapOf<Long, Long>()
        var docasne = -1L
        for (p in zaloha.profily) {
            val e = podlaMena[p.meno.trim().lowercase()] ?: nove.firstOrNull { it.meno.equals(p.meno.trim(), true) }
            mapa[p.id] = e?.id ?: Profil(docasne--, p.meno.trim()).also { nove += it }.id
        }
        val kluc = { m: MojLiek -> listOf(m.profilId, m.kod, m.exspiracia, m.poznamka.trim()) }
        val uz = existujuceLieky.map(kluc).toMutableSet()
        val lieky = zaloha.lieky.mapNotNull { m ->
            val pid = mapa[m.profilId] ?: existujuce.firstOrNull()?.id ?: 1L
            val n = m.copy(id = 0, profilId = pid)
            if (uz.add(kluc(n))) n else null
        }
        return nove to lieky
    }
}
