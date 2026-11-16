package sk.kajka.liekovka.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import sk.kajka.liekovka.data.SUKL_NEZIADUCE_UCINKY
import sk.kajka.liekovka.data.parsujPravnyText
import androidx.compose.material3.OutlinedButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import sk.kajka.liekovka.data.Prefs
import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import sk.kajka.liekovka.BuildConfig
import sk.kajka.liekovka.data.Aktualizacia
import sk.kajka.liekovka.data.Repository
import sk.kajka.liekovka.data.datum

@Composable
fun InfoScreen() {
    val ctx = LocalContext.current
    val repo = Repository.get(ctx)
    val scope = rememberCoroutineScope()
    val lekarnik by repo.prefs.lekarnik.collectAsState()
    var stav by remember { mutableStateOf<String?>(null) }
    var bezi by remember { mutableStateOf(false) }
    var obnov by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Nastavenia", style = MaterialTheme.typography.titleLarge)

        Vzhlad(repo)

        Nadpis("Zobrazenie údajov")
        Karta {
            Row(
                Modifier.fillMaxWidth().clickable { repo.prefs.setLekarnik(!lekarnik) }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Režim pre lekárnika", style = MaterialTheme.typography.bodyLarge)
                    Text("ATC a ŠÚKL kódy, konečná cena, registračné číslo, SPC na prvom mieste, hľadanie podľa ATC.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = lekarnik, onCheckedChange = { repo.prefs.setLekarnik(it) })
            }
        }

        ZalohaKarta(repo)

        Nadpis("Dáta")
        Karta {
            val p = repo.prefs
            key(obnov) {
                Udaj("Zoznam kategorizovaných liekov", if (p.kategorizaciaOd.isBlank()) "–" else "platný od ${datum(p.kategorizaciaOd)}")
                Udaj("Výpadky dodávok ŠÚKL", if (p.vypadkyK.isBlank()) "–" else "k ${datum(p.vypadkyK)}")
                Udaj("Verzia dát", p.verzia.ifBlank { "–" })
            }
            FilledTonalButton(
                enabled = !bezi,
                onClick = {
                    bezi = true; stav = "Kontrolujem…"
                    scope.launch {
                        stav = when (val r = repo.aktualizuj()) {
                            Aktualizacia.NieJeNastavena -> "Server s dátami ešte nie je nastavený (DATA_URL v app/build.gradle.kts)."
                            Aktualizacia.BezZmeny -> "Máte najnovšie dáta."
                            is Aktualizacia.Nove -> "Dáta aktualizované" + if (r.zmien > 0) ", ${r.zmien} upozornení." else "."
                            is Aktualizacia.Chyba -> r.sprava
                        }
                        bezi = false; obnov++
                    }
                },
                modifier = Modifier.padding(16.dp),
            ) { Text("Skontrolovať aktualizácie") }
            stav?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) }
        }

        Nadpis("Zdroje")
        Karta {
            Zdroj("Register liekov, SPC a PIL", "Štátny ústav pre kontrolu liečiv (ŠÚKL)", "https://www.sukl.sk/o-nas/databazy-a-servis/datove-archivy-a-zoznamy")
            Oddelovac()
            Zdroj("Ceny, úhrady a doplatky", "Ministerstvo zdravotníctva SR, zoznam kategorizovaných liekov",
                "https://www.health.gov.sk/odbornik/kategorizacia-a-cenotvorba/kategorizacia-liekov/zoznam-kategorizovanych-liekov/")
            Oddelovac()
            Zdroj("Prerušenie a obnovenie dodávok", "ŠÚKL, hlásenia držiteľov registrácie", "https://portal.sukl.sk/PreruseniePublic/?act=PrerusenieOznList")
        }

        OAplikacii(repo)
        Text("Verzia ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 20.dp))
    }
}

/** Nezávislosť, upozornenie, právne texty, kontakt, hlásenie chýb a vymazanie údajov. */
@Composable
private fun OAplikacii(repo: Repository) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf<String?>(null) }      // názov súboru s právnym textom na zobrazenie
    var vymazat by remember { mutableStateOf(false) }
    var sprava by remember { mutableStateOf<String?>(null) }

    Nadpis("O aplikácii")
    Karta {
        Text(
            "Liekovka nie je oficiálnou aplikáciou ŠÚKL ani Ministerstva zdravotníctva SR a nie je s nimi spojená. " +
                "Zobrazuje ich verejne dostupné údaje. Má len informatívny charakter, nenahrádza odbornú radu lekára ani " +
                "lekárnika a neslúži na diagnostiku, liečbu ani rozhodovanie o dávkovaní. Pred zmenou lieku sa poraďte " +
                "s lekárom alebo lekárnikom. Rozhodujúce je vždy aktuálne znenie dokumentov na stránke ŠÚKL.",
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp),
        )
        Oddelovac()
        Text(
            "Bez účtu, bez reklám a bez sledovania. Lekárnička sa ukladá len v tomto telefóne a nikam sa neodosiela. " +
                "Aplikácia nie je financovaná výrobcami liekov ani lekárňami a žiadny liek neodporúča.",
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp),
        )
        Oddelovac()
        Polozka("Zásady ochrany súkromia") { text = "sukromie.txt" }
        Oddelovac()
        Polozka("Podmienky používania") { text = "podmienky.txt" }
    }

    Nadpis("Pomoc a kontakt")
    Karta {
        Udaj("Prevádzkovateľ", BuildConfig.PREVADZKOVATEL)
        Udaj("Kontakt", BuildConfig.KONTAKT_EMAIL)
        Oddelovac()
        Polozka("Nahlásiť chybu v údajoch", "Napíšete nám e-mail. Chybu overíme v zdrojoch ŠÚKL a MZ SR.") { nahlasChybu(ctx, null) }
        Oddelovac()
        Polozka("Nahlásiť nežiaduci účinok lieku", "Otvorí sa formulár na stránke ŠÚKL.") { otvor(ctx, SUKL_NEZIADUCE_UCINKY) }
    }

    Nadpis("Moje údaje")
    Karta {
        Polozka("Vymazať všetky moje údaje", "Lekárničky všetkých osôb, poznámky a upozornenia. Nedá sa to vrátiť späť.") { vymazat = true }
        sprava?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) }
    }

    text?.let { subor ->
        val t = remember(subor) {
            parsujPravnyText(ctx.assets.open(subor).use { it.readBytes().decodeToString() }, BuildConfig.PREVADZKOVATEL, BuildConfig.KONTAKT_EMAIL)
        }
        AlertDialog(
            onDismissRequest = { text = null },
            title = { Text(t.titulok) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(t.ucinnost, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    t.odseky.forEach { o ->
                        if (o.nadpis.isNotBlank()) Text(o.nadpis, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 14.dp))
                        Text(o.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { text = null }) { Text("Zavrieť") } },
        )
    }
    if (vymazat) AlertDialog(
        onDismissRequest = { vymazat = false },
        title = { Text("Vymazať všetky moje údaje?") },
        text = { Text("Vymažú sa lekárničky všetkých osôb, poznámky, dátumy exspirácie a upozornenia. Ak ich chcete zachovať, uložte si najprv zálohu. Nedá sa to vrátiť späť.") },
        confirmButton = {
            TextButton(onClick = {
                vymazat = false
                scope.launch { repo.vymazMojeUdaje(); sprava = "Všetky vaše údaje boli vymazané." }
            }) { Text("Vymazať", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { vymazat = false }) { Text("Zrušiť") } },
    )
}

@Composable
private fun Polozka(nazov: String, popis: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(nazov, style = MaterialTheme.typography.bodyLarge)
            if (popis != null) Text(popis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Uloženie lekárničky do súboru a obnovenie na inom telefóne. */
@Composable
private fun ZalohaKarta(repo: Repository) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var sprava by remember { mutableStateOf<String?>(null) }
    val ulozit = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            sprava = runCatching {
                val json = repo.zalohaJson()
                withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)!!.use { it.write(json.toByteArray()) } }
                "Záloha je uložená. Na novom telefóne ju obnovíte tlačidlom „Obnoviť zo zálohy“."
            }.getOrElse { "Zálohu sa nepodarilo uložiť: ${it.message}" }
        }
    }
    val obnovit = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            sprava = runCatching {
                val text = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }
                val (osoby, lieky) = repo.obnovZalohu(text)
                if (osoby == 0 && lieky == 0) "Všetko zo zálohy už v lekárničke je."
                else "Obnovené: $lieky ${slovoLiek(lieky)}" + if (osoby > 0) " a $osoby ${if (osoby == 1) "osoba" else "osoby"}." else "."
            }.getOrElse { it.message ?: "Zálohu sa nepodarilo obnoviť." }
        }
    }
    Nadpis("Záloha lekárničky")
    Karta {
        Text(
            "Lekárnička sa ukladá len v tomto telefóne. Pred výmenou telefónu si uložte zálohu (napríklad na Google Disk " +
                "alebo si ju pošlite e-mailom) a na novom telefóne ju obnovte. Záloha obsahuje osoby, lieky, exspirácie a poznámky.",
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
        )
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { ulozit.launch("liekovka-zaloha-${LocalDate.now()}.json") }, modifier = Modifier.weight(1f)) {
                Text("Uložiť zálohu")
            }
            OutlinedButton(onClick = { obnovit.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) },
                modifier = Modifier.weight(1f)) { Text("Obnoviť zo zálohy") }
        }
        sprava?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Vzhlad(repo: Repository) {
    val tema by repo.prefs.tema.collectAsState()
    val pismo by repo.prefs.pismo.collectAsState()
    Nadpis("Vzhľad")
    Karta {
        Text("Téma", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp))
        val temy = listOf("system" to "Podľa systému", "svetla" to "Svetlá", "tmava" to "Tmavá")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(16.dp)) {
            temy.forEachIndexed { i, (kod, nazov) ->
                SegmentedButton(
                    selected = tema == kod,
                    onClick = { repo.prefs.setTema(kod) },
                    shape = SegmentedButtonDefaults.itemShape(i, temy.size),
                    icon = {},
                ) { Text(nazov, maxLines = 1, style = MaterialTheme.typography.labelLarge) }
            }
        }
        Oddelovac()
        val velkosti = Prefs.VELKOSTI
        val idx = velkosti.indexOfFirst { kotlin.math.abs(it.first - pismo) < 0.01f }.coerceAtLeast(1)
        Row(Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Veľkosť písma", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(velkosti[idx].second, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("A", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = idx.toFloat(),
                onValueChange = { repo.prefs.setPismo(velkosti[it.roundToInt().coerceIn(0, velkosti.lastIndex)].first) },
                valueRange = 0f..velkosti.lastIndex.toFloat(),
                steps = velkosti.size - 2,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            Text("A", style = MaterialTheme.typography.titleLarge)
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text("Ukážka textu", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Paralen 500 · tbl 24 × 500 mg", style = MaterialTheme.typography.titleMedium)
                Text("Užívajte podľa príbalového letáku.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun Zdroj(co: String, kto: String, url: String) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth().clickable { otvor(ctx, url) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(co, style = MaterialTheme.typography.bodyLarge)
        Text(kto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun key(k: Any, content: @Composable () -> Unit) = androidx.compose.runtime.key(k) { content() }
