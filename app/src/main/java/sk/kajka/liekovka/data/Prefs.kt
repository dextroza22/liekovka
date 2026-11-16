package sk.kajka.liekovka.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Malé nastavenia a informácie o verzii dát. */
class Prefs(ctx: Context, nazov: String = "liekovka") {
    private val sp = ctx.getSharedPreferences(nazov, Context.MODE_PRIVATE)

    var verzia: String
        get() = sp.getString("verzia", "") ?: ""
        set(v) = sp.edit().putString("verzia", v).apply()
    var kategorizaciaOd: String
        get() = sp.getString("kategorizaciaOd", "") ?: ""
        set(v) = sp.edit().putString("kategorizaciaOd", v).apply()
    var vypadkyK: String
        get() = sp.getString("vypadkyK", "") ?: ""
        set(v) = sp.edit().putString("vypadkyK", v).apply()
    var hladajNaSukl: String
        get() = sp.getString("hladajNaSukl", null) ?: SUKL_SEARCH
        set(v) = sp.edit().putString("hladajNaSukl", v).apply()
    /** Od kedy platí zoznam s budúcimi doplatkami (RRRR-MM-01), prázdne ak ešte nie je zverejnený. */
    var buduceOd: String
        get() = sp.getString("buduceOd", "") ?: ""
        set(v) = sp.edit().putString("buduceOd", v).apply()

    private val _profil = MutableStateFlow(sp.getLong("profil", 1L))
    /** Profil, ktorého lekárnička sa práve zobrazuje. */
    val profil: StateFlow<Long> = _profil
    fun setProfil(v: Long) {
        sp.edit().putLong("profil", v).apply()
        _profil.value = v
    }

    /** Oznamy o stiahnutí, ktoré už boli vyhodnotené (aby sa neupozorňovalo opakovane). */
    var videneStiahnutia: Set<String>
        get() = sp.getStringSet("videneStiahnutia", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("videneStiahnutia", v).apply()
    var stiahnutiaInit: Boolean
        get() = sp.getBoolean("stiahnutiaInit", false)
        set(v) = sp.edit().putBoolean("stiahnutiaInit", v).apply()

    var poslednaKontrola: Long
        get() = sp.getLong("poslednaKontrola", 0)
        set(v) = sp.edit().putLong("poslednaKontrola", v).apply()
    var upozorneniePrijate: Boolean
        get() = sp.getBoolean("upozorneniePrijate", false)
        set(v) = sp.edit().putBoolean("upozorneniePrijate", v).apply()

    private val _lekarnik = MutableStateFlow(sp.getBoolean("lekarnik", false))
    val lekarnik: StateFlow<Boolean> = _lekarnik
    fun setLekarnik(v: Boolean) {
        sp.edit().putBoolean("lekarnik", v).apply()
        _lekarnik.value = v
    }

    /** "system", "svetla" alebo "tmava". */
    private val _tema = MutableStateFlow(sp.getString("tema", "system") ?: "system")
    val tema: StateFlow<String> = _tema
    fun setTema(v: String) {
        sp.edit().putString("tema", v).apply()
        _tema.value = v
    }

    /** Násobok veľkosti písma (k systémovému nastaveniu), jedna z hodnôt [VELKOSTI]. */
    private val _pismo = MutableStateFlow(sp.getFloat("pismo", 1f))
    val pismo: StateFlow<Float> = _pismo
    fun setPismo(v: Float) {
        sp.edit().putFloat("pismo", v).apply()
        _pismo.value = v
    }

    companion object {
        val VELKOSTI = listOf(0.9f to "Malé", 1f to "Normálne", 1.15f to "Veľké", 1.3f to "Najväčšie")
        const val SUKL_SEARCH = "https://www.sukl.sk/rozsirene-vyhladavanie?search_type=drugs"
    }
}
