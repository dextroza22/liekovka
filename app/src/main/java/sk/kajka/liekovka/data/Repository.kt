package sk.kajka.liekovka.data

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import sk.kajka.liekovka.BuildConfig
import sk.kajka.liekovka.Notif
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.LocalDate
import java.util.zip.GZIPInputStream

/** Výsledok kontroly aktualizácií pre obrazovku Info. */
sealed interface Aktualizacia {
    data object NieJeNastavena : Aktualizacia
    data object BezZmeny : Aktualizacia
    data class Nove(val verzia: String, val zmien: Int) : Aktualizacia
    data class Chyba(val sprava: String) : Aktualizacia
}

class Repository(
    private val ctx: Context,
    val db: AppDb = AppDb.get(ctx),
    val prefs: Prefs = Prefs(ctx),
) {

    /**
     * Naplní databázu z dát pribalených v aplikácii: pri prvom spustení, a po aktualizácii aplikácie aj vtedy,
     * keď sú pribalené dáta novšie ako tie v telefóne (napr. testovacie dáta → skutočné).
     */
    suspend fun pripravData(): Boolean = withContext(Dispatchers.IO) {
        db.profily().insertNow(Profil(id = 1, meno = "Ja"))   // predvolený profil (ak už existuje, nič sa nestane)
        val prazdna = db.lieky().count() == 0
        val pribalena = runCatching { otvorPribalene().use { verziaBalika(it) } }.getOrDefault("")
        val terajsia = prefs.verzia
        val novsia = pribalena.isNotBlank() && pribalena != terajsia &&
            (terajsia.isBlank() || terajsia.startsWith("TEST") || pribalena > terajsia)
        if (!prazdna && !novsia) return@withContext true
        val ok = runCatching {
            otvorPribalene().use { importuj(it) }
        }.onFailure { Log.w(TAG, "Pribalené dáta sa nepodarilo načítať", it); poslednaChyba = "${it.javaClass.simpleName}: ${it.message}" }
            .onSuccess { poslednaChyba = null }.isSuccess
        if (ok && !prazdna) vyhodnotZmeny()   // aktualizuje uložený stav liekov v lekárničke
        ok || !prazdna
    }

    /** Verzia dátového balíka zo začiatku súboru (bez načítania celých dát). */
    /**
     * Pribalené dáta. Nástroje na zostavenie APK niekedy súbor .gz samy rozbalia (lieky.json.gz → lieky.json),
     * preto sa skúšajú všetky názvy a formát (gzip alebo čistý JSON) sa rozpozná podľa obsahu.
     */
    private fun otvorPribalene(): InputStream {
        for (n in ASSETY) runCatching { return ctx.assets.open(n) }
        throw java.io.FileNotFoundException("V aplikácii chýbajú pribalené dáta (${ASSETY.joinToString()})")
    }

    private fun verziaBalika(input: InputStream): String {
        val r = JsonReader(InputStreamReader(rozbal(input), Charsets.UTF_8))
        r.beginObject()
        while (r.hasNext()) {
            if (r.nextName() == "verzia") return r.nextString()
            r.skipValue()
        }
        return ""
    }

    /** Stiahne nové dáta, ak sú k dispozícii, a vyhodnotí zmeny pri liekoch v lekárničke. */
    suspend fun aktualizuj(): Aktualizacia = withContext(Dispatchers.IO) {
        val base = BuildConfig.DATA_URL.trimEnd('/') + "/"
        if ("YOUR_" in base) return@withContext Aktualizacia.NieJeNastavena
        try {
            val meta = JSONObject(String(stiahni(base + "meta.json"), Charsets.UTF_8))
            prefs.poslednaKontrola = System.currentTimeMillis()
            if (meta.optInt("format", 1) > FORMAT) {
                return@withContext Aktualizacia.Chyba("Nové dáta vyžadujú novšiu verziu aplikácie.")
            }
            val verzia = meta.getString("verzia")
            if (verzia == prefs.verzia && db.lieky().count() > 0) {
                val zmien = skontrolujExspiracie()
                return@withContext if (zmien > 0) Aktualizacia.Nove(verzia, zmien) else Aktualizacia.BezZmeny
            }
            val gz = stiahni(base + "lieky.json.gz")
            val sha = MessageDigest.getInstance("SHA-256").digest(gz).joinToString("") { "%02x".format(it) }
            if (sha != meta.getString("sha256")) return@withContext Aktualizacia.Chyba("Stiahnuté dáta sú poškodené, skúste neskôr.")
            val tmp = File(ctx.cacheDir, "lieky.json.gz").apply { writeBytes(gz) }
            tmp.inputStream().use { importuj(it) }
            tmp.delete()
            val zmien = vyhodnotZmeny() + vyhodnotStiahnutia() + skontrolujExspiracie()
            Aktualizacia.Nove(verzia, zmien)
        } catch (e: Exception) {
            Log.w(TAG, "Aktualizácia zlyhala", e)
            Aktualizacia.Chyba("Nepodarilo sa spojiť so serverom s dátami.")
        }
    }

    // ------------------------------------------------------------ import

    /** Načíta dátový balík (lieky.json.gz) do databázy. Celé v jednej transakcii. */
    fun importuj(input: InputStream) {
        val r = JsonReader(InputStreamReader(rozbal(input), Charsets.UTF_8))
        val dao = db.lieky()
        var verzia = ""
        var katOd = ""
        var vypK = ""
        var hladaj = Prefs.SUKL_SEARCH
        val vypadky = mutableMapOf<String, Pair<String, String>>()
        var buduceOd = ""
        val buduce = mutableMapOf<String, Double?>()
        var stiahnutia: MutableList<Stiahnutie>? = null
        val stiahnutiaLieky = mutableListOf<StiahnutieLiek>()
        db.runInTransaction {
            dao.deleteAll()
            dao.deleteSkupiny()
            r.beginObject()
            while (r.hasNext()) {
                when (r.nextName()) {
                    "format" -> if (r.nextInt() > FORMAT) error("Nepodporovaný formát dát")
                    "verzia" -> verzia = r.nextString()
                    "kategorizaciaOd" -> katOd = r.nextString()
                    "vypadkyK" -> vypK = r.nextString()
                    "hladajNaSukl" -> hladaj = r.nextString()
                    "polia" -> {
                        val polia = mutableListOf<String>()
                        r.beginArray(); while (r.hasNext()) polia += r.nextString(); r.endArray()
                        check(polia.take(POLIA.size) == POLIA) { "Neznáme poradie polí: $polia" }
                    }
                    "skupiny" -> {
                        val s = mutableListOf<Skupina>()
                        r.beginArray()
                        while (r.hasNext()) {
                            r.beginArray()
                            s += Skupina(s.size, r.nextString(), r.nextString(), r.nextString())
                            while (r.hasNext()) r.skipValue()
                            r.endArray()
                        }
                        r.endArray()
                        dao.insertSkupiny(s)
                    }
                    "lieky" -> {
                        val batch = ArrayList<Liek>(2000)
                        r.beginArray()
                        while (r.hasNext()) {
                            batch += citajLiek(r)
                            if (batch.size == 2000) { dao.insertAll(batch); batch.clear() }
                        }
                        r.endArray()
                        dao.insertAll(batch)
                    }
                    "vypadky" -> {
                        r.beginObject()
                        while (r.hasNext()) {
                            val kod = r.nextName()
                            r.beginArray()
                            val stav = r.nextString(); val od = r.nextString()
                            while (r.hasNext()) r.skipValue()
                            r.endArray()
                            vypadky[kod] = stav to od
                        }
                        r.endObject()
                    }
                    "stiahnutia" -> {
                        stiahnutia = mutableListOf()
                        r.beginArray()
                        while (r.hasNext()) {
                            var url = ""; var nazov = ""; var datum = ""; var dovod = ""; var trieda = ""
                            val lieky = mutableListOf<StiahnutieLiek>()
                            r.beginObject()
                            while (r.hasNext()) when (r.nextName()) {
                                "url" -> url = str(r)
                                "nazov" -> nazov = str(r)
                                "datum" -> datum = str(r)
                                "dovod" -> dovod = str(r)
                                "trieda" -> trieda = str(r)
                                "lieky" -> {
                                    r.beginArray()
                                    while (r.hasNext()) {
                                        var kod = ""; var vsetky = false; val sarze = mutableListOf<String>()
                                        r.beginObject()
                                        while (r.hasNext()) when (r.nextName()) {
                                            "kod" -> kod = str(r)
                                            "vsetky" -> vsetky = r.nextBoolean()
                                            "sarze" -> {
                                                r.beginArray()
                                                while (r.hasNext()) {
                                                    r.beginArray()
                                                    val c = str(r); val e = if (r.hasNext()) str(r) else ""
                                                    while (r.hasNext()) r.skipValue()
                                                    r.endArray()
                                                    sarze += "${c.replace(';', ',').replace('|', '/')}|${e.replace(';', ',')}"
                                                }
                                                r.endArray()
                                            }
                                            else -> r.skipValue()
                                        }
                                        r.endObject()
                                        if (kod.isNotBlank()) lieky += StiahnutieLiek("", kod, sarze.joinToString(";"), vsetky)
                                    }
                                    r.endArray()
                                }
                                else -> r.skipValue()
                            }
                            r.endObject()
                            if (url.isNotBlank()) {
                                stiahnutia!! += Stiahnutie(url, nazov, datum, dovod, trieda)
                                stiahnutiaLieky += lieky.map { it.copy(url = url) }
                            }
                        }
                        r.endArray()
                    }
                    "buduce" -> {
                        r.beginObject()
                        while (r.hasNext()) when (r.nextName()) {
                            "od" -> buduceOd = r.nextString()
                            "doplatky" -> {
                                r.beginObject()
                                while (r.hasNext()) { val kod = r.nextName(); buduce[kod] = dbl(r) }
                                r.endObject()
                            }
                            else -> r.skipValue()
                        }
                        r.endObject()
                    }
                    else -> r.skipValue()
                }
            }
            r.endObject()
            vypadky.forEach { (kod, v) -> dao.setVypadok(kod, v.first, v.second) }
            if (buduceOd.isNotBlank()) buduce.forEach { (kod, d) -> dao.setBuduci(kod, d) }
            stiahnutia?.let { st ->   // ak dáta stiahnutia neobsahujú (chyba pri ich načítaní), ponechajú sa predchádzajúce
                val sd = db.stiahnutia()
                sd.deleteLieky(); sd.deleteAll()
                sd.insert(st); sd.insertLieky(stiahnutiaLieky.distinctBy { it.url to it.kod })
            }
            check(dao.count() > 0) { "Prázdne dáta" }
        }
        r.close()
        prefs.verzia = verzia
        prefs.kategorizaciaOd = katOd
        prefs.vypadkyK = vypK
        prefs.hladajNaSukl = hladaj
        prefs.buduceOd = if (buduce.isEmpty()) "" else buduceOd
    }

    private fun citajLiek(r: JsonReader): Liek {
        r.beginArray()
        val kod = str(r); val nazov = str(r); val doplnok = str(r); val atc = str(r); val latka = str(r)
        val vydaj = str(r); val drzitel = str(r); val skupina = r.nextInt()
        val cena = dbl(r); val uhrada = dbl(r); val doplatok = dbl(r)
        val spc = str(r); val pil = str(r); val reg = str(r)
        while (r.hasNext()) r.skipValue()
        r.endArray()
        return Liek(
            kod, nazov, doplnok, atc, latka, vydaj, drzitel, skupina, cena, uhrada, doplatok, spc, pil, reg,
            hladaj = norm("$nazov $latka $doplnok"),
        )
    }

    private fun str(r: JsonReader): String =
        if (r.peek() == JsonToken.NULL) { r.nextNull(); "" } else r.nextString()

    private fun dbl(r: JsonReader): Double? =
        if (r.peek() == JsonToken.NULL) { r.nextNull(); null } else r.nextDouble()

    // ------------------------------------------------------------ zmeny v lekárničke

    /** Porovná lieky v lekárničke s novými dátami; vytvorí upozornenia a notifikáciu. Vráti počet zmien. */
    fun vyhodnotZmeny(): Int {
        val novych = mutableListOf<Upozornenie>()
        val mena = menaProfilov()
        for (m in db.moje().allNow()) {
            val res = porovnaj(m, db.lieky().getNow(m.kod), prefs.buduceOd)
            res.spravy.forEach { novych += Upozornenie(kod = m.kod, nazov = nazovSProfilom(m, mena), text = it) }
            if (res.novy != m) db.moje().updateNow(res.novy)
        }
        return uloz(novych)
    }

    /**
     * Nové oznamy o stiahnutí z trhu porovná s liekmi v lekárničke (všetky osoby).
     * Pri prvom vyhodnotení sa upozorní len na oznamy z posledných 30 dní, staršie sa označia ako videné
     * (v karte lieku sa však zobrazujú stále).
     */
    fun vyhodnotStiahnutia(): Int {
        val vsetky = db.stiahnutia().vsetkyInfoNow()
        val videne = prefs.videneStiahnutia
        val hranica = LocalDate.now().minusDays(30).toString()
        val nove = vsetky.filter { it.url !in videne && (prefs.stiahnutiaInit || it.datum >= hranica) }
        val mena = menaProfilov()
        val novych = mutableListOf<Upozornenie>()
        val moje = db.moje().allNow()
        for (s in nove) for (m in moje.filter { it.kod == s.kod }) {
            novych += Upozornenie(kod = m.kod, nazov = nazovSProfilom(m, mena), text = stiahnutieText(s))
        }
        prefs.videneStiahnutia = videne + vsetky.map { it.url }
        prefs.stiahnutiaInit = true
        return uloz(novych)
    }

    fun skontrolujExspiracie(): Int {
        val novych = mutableListOf<Upozornenie>()
        val dnes = LocalDate.now()
        val mena = menaProfilov()
        for (m in db.moje().allNow()) {
            val s = exspiraciaSprava(m, dnes) ?: continue
            novych += Upozornenie(kod = m.kod, nazov = nazovSProfilom(m, mena), text = s)
            db.moje().updateNow(m.copy(upozornenieExspiracia = true))
        }
        return uloz(novych)
    }

    private fun uloz(u: List<Upozornenie>): Int {
        u.forEach { db.upozornenia().insertNow(it) }
        if (u.size == 1) Notif.ukaz(ctx, u[0].nazov, u[0].text)
        else if (u.size > 1) Notif.ukaz(ctx, "Zmeny pri vašich liekoch (${u.size})", u.joinToString("\n") { "${it.nazov}: ${it.text}" })
        return u.size
    }

    /** Text poslednej chyby pri načítaní dát (zobrazí sa na obrazovke, aby sa dala nahlásiť). */
    @Volatile var poslednaChyba: String? = null

    /** Mená profilov, len ak ich je viac (pri jednom profile sa meno v upozorneniach neuvádza). */
    private fun menaProfilov(): Map<Long, String> =
        db.profily().allNow().takeIf { it.size > 1 }?.associate { it.id to it.meno } ?: emptyMap()

    private fun nazovSProfilom(m: MojLiek, mena: Map<Long, String>) =
        mena[m.profilId]?.let { "$it · ${m.nazov}" } ?: m.nazov

    /** Pridá liek do lekárničky so zapamätaným aktuálnym stavom (vrátane už ohlásenej budúcej zmeny). */
    suspend fun pridaj(l: Liek, exspiracia: String?, profilId: Long, poznamka: String = "") {
        db.moje().insert(
            MojLiek(
                kod = l.kod, nazov = l.nazov, doplnok = l.doplnok, profilId = profilId, exspiracia = exspiracia,
                poznamka = poznamka.trim(), posledDoplatok = l.doplatok, posledVypadok = l.vypadok,
                posledBuduci = if (l.buducaZmena) "${prefs.buduceOd}|${l.buduciDoplatok}" else null,
            )
        )
    }

    /**
     * Vymaže všetko, čo do aplikácie zadal používateľ: lekárničky všetkých osôb, osoby, poznámky, upozornenia
     * a dočasné súbory zo zdieľania. Verejné údaje o liekoch a nastavenia vzhľadu zostávajú.
     */
    suspend fun vymazMojeUdaje() = withContext(Dispatchers.IO) {
        db.runInTransaction {
            db.moje().deleteAllNow()
            db.upozornenia().zmazVsetkyNow()
            db.profily().deleteAllNow()
            db.profily().insertNow(Profil(id = 1, meno = "Ja"))
        }
        prefs.setProfil(1)
        File(ctx.cacheDir, "zdielanie").deleteRecursively()
    }

    /** Záloha osôb a liekov z lekárničky (JSON) na uloženie do súboru. */
    suspend fun zalohaJson(): String = withContext(Dispatchers.IO) {
        Zaloha.naJson(db.profily().allNow(), db.moje().allNow(), LocalDate.now().toString())
    }

    /** Obnoví lekárničku zo zálohy (spojí s existujúcou). Vráti počet nových osôb a liekov. */
    suspend fun obnovZalohu(text: String): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val z = Zaloha.zJson(text)
        var vysledok = 0 to 0
        db.runInTransaction {
            val (nove, lieky) = Zaloha.spoj(z, db.profily().allNow(), db.moje().allNow())
            val ids = nove.associate { it.id to db.profily().insertNow(Profil(meno = it.meno)) }
            lieky.forEach { m ->
                val l = db.lieky().getNow(m.kod)
                db.moje().insertNow(
                    m.copy(
                        profilId = ids[m.profilId] ?: m.profilId,
                        nazov = m.nazov.ifBlank { l?.nazov.orEmpty() },
                        // aktuálny stav ako východisko, aby obnovenie nevyvolalo falošné upozornenia
                        posledDoplatok = l?.doplatok, posledVypadok = l?.vypadok, posledExistuje = l != null,
                        posledBuduci = if (l != null && l.buducaZmena) "${prefs.buduceOd}|${l.buduciDoplatok}" else null,
                        upozornenieExspiracia = false,
                    )
                )
            }
            vysledok = nove.size to lieky.size
        }
        skontrolujExspiracie()
        vysledok
    }

    /** Zoznam liekov profilu ako text na zdieľanie (lekárovi, do nemocnice, rodine). */
    suspend fun zoznamNaZdielanie(profil: Profil): ZoznamLiekov = withContext(Dispatchers.IO) {
        val polozky = db.moje().vProfile(profil.id).map { m ->
            val l = db.lieky().getNow(m.kod)
            PolozkaZoznamu(m.nazov, m.doplnok, l?.latka.orEmpty(), m.poznamka, m.exspiracia)
        }
        ZoznamLiekov(profil.meno, LocalDate.now(), polozky)
    }

    private fun stiahni(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 60_000
        c.setRequestProperty("User-Agent", "Liekovka/${BuildConfig.VERSION_NAME} Android")
        try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode} pre $url")
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }

    companion object {
        private const val TAG = "Liekovka"
        /** Pribalené dáta: lieky.dat (gzip, nástroje ho nemenia); ostatné názvy pre staršie projekty. */
        const val ASSET = "lieky.dat"
        val ASSETY = listOf("lieky.dat", "lieky.json.gz", "lieky.json")

        /** Gzip sa rozpozná podľa prvých dvoch bajtov (1f 8b); inak sa číta ako čistý JSON. */
        fun rozbal(input: InputStream): InputStream {
            val b = java.io.BufferedInputStream(input, 64 * 1024)
            b.mark(2)
            val gzip = b.read() == 0x1f && b.read() == 0x8b
            b.reset()
            return if (gzip) GZIPInputStream(b, 64 * 1024) else b
        }
        const val FORMAT = 1
        val POLIA = listOf("kod", "nazov", "doplnok", "atc", "latka", "vydaj", "drzitel", "skupina",
            "cena", "uhrada", "doplatok", "spc", "pil", "reg")

        @Volatile private var inst: Repository? = null
        fun get(ctx: Context): Repository = inst ?: synchronized(this) {
            inst ?: Repository(ctx.applicationContext).also { inst = it }
        }
    }
}
