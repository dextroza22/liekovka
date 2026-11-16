package sk.kajka.liekovka.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import sk.kajka.liekovka.Notif
import sk.kajka.liekovka.data.Liek
import sk.kajka.liekovka.data.Repository
import sk.kajka.liekovka.data.datum
import sk.kajka.liekovka.data.eur
import sk.kajka.liekovka.data.jeLacnejsia
import sk.kajka.liekovka.data.stiahnutieText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.Alignment
import sk.kajka.liekovka.data.nedostupny
import sk.kajka.liekovka.data.vydajText
import sk.kajka.liekovka.data.vypadokText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(kod: String, onSpat: () -> Unit, onOtvor: (String) -> Unit) {
    val ctx = LocalContext.current
    val repo = Repository.get(ctx)
    val lekarnik by repo.prefs.lekarnik.collectAsState()
    val liek by remember(kod) { repo.db.lieky().observe(kod) }.collectAsState(null)
    val profilId by repo.prefs.profil.collectAsState()
    val vLekarnicke by remember(kod, profilId) { repo.db.moje().countKod(kod, profilId) }.collectAsState(0)

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(liek?.nazov ?: "", maxLines = 1) },
            navigationIcon = { IconButton(onClick = onSpat) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Späť") } },
        )
        val l = liek ?: return@Column
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Hlavicka(l, repo.prefs.buduceOd)
            Stiahnutia(l, repo)
            LacnejsiaAlternativa(l, repo, onOtvor)
            Dokumenty(l, lekarnik, repo.prefs.hladajNaSukl)
            PridatDoLekarnicky(l, vLekarnicke, repo)
            CenaADostupnost(l, lekarnik, repo)
            Udaje(l, lekarnik)
            Alternativy(l, lekarnik, repo, onOtvor)
            Text(
                "Informácie pochádzajú z verejných zdrojov ŠÚKL a MZ SR a majú informatívny charakter. " +
                    "Nenahrádzajú odbornú radu lekára ani lekárnika.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp),
            )
            TextButton(onClick = { nahlasChybu(ctx, l) }) { Text("Nahlásiť chybu v údajoch") }
        }
    }
}

/** Farebná karta s názvom lieku a dvoma dlaždicami: doplatok a dostupnosť. */
@Composable
private fun Hlavicka(l: Liek, buduceOd: String) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.primaryContainer, contentColor = cs.onPrimaryContainer, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(l.nazov, style = MaterialTheme.typography.headlineSmall)
            if (l.doplnok.isNotBlank()) Text(l.doplnok, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 2.dp))
            if (l.latka.isNotBlank()) Text("Účinná látka: ${l.latka}", style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Dlazdica(
                    nadpis = if (l.doplatok != null) "Doplatok" else "Úhrada",
                    hodnota = if (l.doplatok != null) eur(l.doplatok) else "nehradený",
                    popis = when {
                        l.buducaZmena && buduceOd.isNotBlank() ->
                            "od ${datum(buduceOd).substringBeforeLast(" ")}: ${l.buduciDoplatok?.let { eur(it) } ?: "nehradený"}"
                        l.vydaj == "F" -> "bez predpisu"
                        else -> "na predpis"
                    },
                    modifier = Modifier.weight(1f),
                )
                val nedost = nedostupny(l.vypadok)
                Dlazdica(
                    nadpis = "Dostupnosť",
                    hodnota = when { nedost -> "výpadok"; l.vypadok != null -> "ohlásený výpadok"; else -> "bez hlásení" },
                    popis = l.vypadokOd?.let { "od ${datum(it)}" } ?: "podľa ŠÚKL",
                    farba = when { nedost -> cs.errorContainer to cs.onErrorContainer; l.vypadok != null -> cs.tertiaryContainer to cs.onTertiaryContainer; else -> null },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Oznamy ŠÚKL o stiahnutí tohto lieku (balenia) z trhu. */
@Composable
private fun Stiahnutia(l: Liek, repo: Repository) {
    val ctx = LocalContext.current
    val zoznam by remember(l.kod) { repo.db.stiahnutia().preKod(l.kod) }.collectAsState(emptyList())
    val cs = MaterialTheme.colorScheme
    zoznam.forEach { s ->
        Surface(
            color = cs.errorContainer, contentColor = cs.onErrorContainer, shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        ) {
            Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 4.dp)) {
                Text("Stiahnutie z trhu · ${datum(s.datum)}", style = MaterialTheme.typography.titleSmall)
                Text(stiahnutieText(s), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp, end = 8.dp))
                TextButton(onClick = { otvor(ctx, s.url) }) { Text("Otvoriť oznam ŠÚKL", color = cs.onErrorContainer) }
            }
        }
    }
}

/** Upozornenie na dostupný liek s rovnakou látkou, silou a formou a nižším doplatkom. */
@Composable
private fun LacnejsiaAlternativa(l: Liek, repo: Repository, onOtvor: (String) -> Unit) {
    if (l.skupina < 0 || l.doplatok == null) return
    val alt by remember(l.kod, l.skupina) { repo.db.lieky().najlacnejsi(l.skupina, l.kod) }.collectAsState(null)
    val a = alt ?: return
    if (!jeLacnejsia(l, a)) return
    val cs = MaterialTheme.colorScheme
    Surface(
        color = cs.secondaryContainer, contentColor = cs.onSecondaryContainer, shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).clickable { onOtvor(a.kod) },
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Dostupný liek s nižším doplatkom", style = MaterialTheme.typography.titleSmall)
                Text("${a.nazov} · ${eur(a.doplatok)} (o ${eur(l.doplatok - a.doplatok!!)} menej). Zámenu konzultujte s lekárom alebo lekárnikom.",
                    style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }
}

@Composable
private fun Dlazdica(
    nadpis: String, hodnota: String, popis: String, modifier: Modifier = Modifier,
    farba: Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color>? = null,
) {
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = farba ?: (cs.surfaceContainerLow to cs.onSurface)
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(nadpis, style = MaterialTheme.typography.labelMedium)
            Text(hodnota, style = MaterialTheme.typography.titleLarge, maxLines = 2)
            Text(popis, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun Dokumenty(l: Liek, lekarnik: Boolean, hladajNaSukl: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (l.pil.isNotBlank() || l.spc.isNotBlank()) {
            // priame odkazy na dokumenty (ak by ich dáta niekedy obsahovali)
            val pil = @Composable { m: Modifier ->
                val akcia = { otvorDokument(ctx, l.pil, l.nazov, hladajNaSukl) }
                if (lekarnik) OutlinedButton(onClick = akcia, modifier = m) { Text("Príbalový leták (PIL)") }
                else Button(onClick = akcia, modifier = m) { Text("Príbalový leták (PIL)") }
            }
            val spc = @Composable { m: Modifier ->
                val akcia = { otvorDokument(ctx, l.spc, l.nazov, hladajNaSukl) }
                if (lekarnik) Button(onClick = akcia, modifier = m) { Text("Súhrn charakteristických vlastností (SPC)") }
                else OutlinedButton(onClick = akcia, modifier = m) { Text("Súhrn charakteristických vlastností (SPC)") }
            }
            if (lekarnik) { spc(Modifier.fillMaxWidth()); pil(Modifier.fillMaxWidth()) }
            else { pil(Modifier.fillMaxWidth()); spc(Modifier.fillMaxWidth()) }
        } else {
            Button(onClick = { scope.launch { otvorStrankuLieku(ctx, l) } }, modifier = Modifier.fillMaxWidth()) {
                Text("Príbalový leták a SPC")
            }
        }
        Text(
            "Otvorí sa stránka lieku na webe ŠÚKL s aktuálne schválenými dokumentmi.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PridatDoLekarnicky(l: Liek, vLekarnicke: Int, repo: Repository) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val profily by remember { repo.db.profily().all() }.collectAsState(emptyList())
    val aktivny by repo.prefs.profil.collectAsState()
    var dialog by remember { mutableStateOf(false) }
    var datumDialog by remember { mutableStateOf(false) }
    var exp by remember { mutableStateOf<String?>(null) }
    var pozn by remember { mutableStateOf("") }
    var profil by remember { mutableStateOf(aktivny) }
    val povolenie = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val meno = profily.firstOrNull { it.id == aktivny }?.meno

    FilledTonalButton(onClick = { exp = null; pozn = ""; profil = aktivny; dialog = true }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Icon(if (vLekarnicke > 0) Icons.Default.Check else Icons.Default.Add, null)
        Text(
            when {
                vLekarnicke > 0 -> "  V lekárničke ($vLekarnicke) · pridať ďalšie balenie"
                profily.size > 1 && meno != null -> "  Pridať do lekárničky ($meno)"
                else -> "  Pridať do mojej lekárničky"
            }
        )
    }
    if (dialog) AlertDialog(
        onDismissRequest = { dialog = false },
        title = { Text("Pridať do lekárničky") },
        text = {
            Column {
                Text("Upozorníme vás na výpadok dodávok, zmenu doplatku a blížiacu sa exspiráciu. Údaje zostávajú len vo vašom telefóne.")
                if (profily.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                    profily.forEach { p -> FilterChip(selected = p.id == profil, onClick = { profil = p.id }, label = { Text(p.meno) }) }
                }
                TextButton(onClick = { datumDialog = true }, modifier = Modifier.padding(top = 4.dp)) {
                    Text(exp?.let { "Exspirácia: ${datum(it)}" } ?: "Zadať dátum exspirácie (nepovinné)")
                }
                OutlinedTextField(pozn, { pozn = it.take(120) }, label = { Text("Poznámka, napr. ráno a večer") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                dialog = false
                scope.launch { repo.pridaj(l, exp, profil, pozn) }
                if (Build.VERSION.SDK_INT >= 33 && !Notif.moze(ctx)) povolenie.launch(Manifest.permission.POST_NOTIFICATIONS)
            }) { Text("Pridať") }
        },
        dismissButton = { TextButton(onClick = { dialog = false }) { Text("Zrušiť") } },
    )
    if (datumDialog) ExspiraciaDialog(exp, onDismiss = { datumDialog = false }) { exp = it; datumDialog = false }
}

@Composable
private fun CenaADostupnost(l: Liek, lekarnik: Boolean, repo: Repository) {
    Nadpis("Cena a dostupnosť")
    Karta {
        val stav = vypadokText(l.vypadok, l.vypadokOd)
        Udaj("Dostupnosť", stav ?: "Bez hlásenia o výpadku dodávok")
        Oddelovac()
        if (l.doplatok != null) {
            Udaj("Doplatok pacienta", eur(l.doplatok))
            val bOd = repo.prefs.buduceOd
            if (l.buducaZmena && bOd.isNotBlank()) Udaj("Od ${datum(bOd)}",
                l.buduciDoplatok?.let { "doplatok ${eur(it)}" } ?: "nebude v zozname kategorizovaných liekov")
            Udaj("Úhrada poisťovne", eur(l.uhrada))
            if (lekarnik) Udaj("Konečná cena", eur(l.cena))
            val od = repo.prefs.kategorizaciaOd
            if (od.isNotBlank()) Text(
                "Podľa zoznamu kategorizovaných liekov MZ SR platného od ${datum(od)}. Doplatok v lekárni sa môže líšiť.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        } else {
            Udaj("Úhrada", "Liek nie je v zozname kategorizovaných liekov, poisťovňa ho spravidla nehradí. Cenu určuje lekáreň.")
        }
    }
}

@Composable
private fun Udaje(l: Liek, lekarnik: Boolean) {
    Nadpis("Údaje o lieku")
    Karta {
        Udaj("Výdaj", vydajText(l.vydaj))
        Udaj("Držiteľ registrácie", l.drzitel)
        if (lekarnik) {
            Udaj("ATC kód", l.atc)
            Udaj("Kód ŠÚKL", l.kod)
            Udaj("Registračné číslo", l.reg)
        }
    }
}

@Composable
private fun Alternativy(l: Liek, lekarnik: Boolean, repo: Repository, onOtvor: (String) -> Unit) {
    val dao = repo.db.lieky()
    if (l.skupina >= 0) {
        val skupina by remember(l.skupina) { dao.skupina(l.skupina) }.collectAsState(null)
        val ine by remember(l.skupina) { dao.vSkupine(l.skupina) }.collectAsState(emptyList())
        val ostatne = ine.filter { it.kod != l.kod }
        if (ostatne.isEmpty()) return
        val najlacnejsi = ine.filter { !nedostupny(it.vypadok) && it.doplatok != null }.minByOrNull { it.doplatok!! }
        Nadpis("Lieky s rovnakou účinnou látkou, silou a formou")
        Text(
            listOfNotNull(skupina?.latka, skupina?.forma).joinToString(", ") +
                ". Zámenu lieku vždy konzultujte s lekárom alebo lekárnikom.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Karta {
            ostatne.forEachIndexed { i, x ->
                if (i > 0) Oddelovac()
                LiekRiadok(x, lekarnik, onClick = { onOtvor(x.kod) },
                    zvyrazni = if (x.kod == najlacnejsi?.kod) "Najnižší doplatok z dostupných" else null)
            }
        }
    } else if (l.atc.length >= 5) {
        val ine by remember(l.kod) { dao.rovnakeAtc(l.atc, l.kod) }.collectAsState(emptyList())
        val bezDuplicit = ine.distinctBy { it.nazov + it.doplnok }
        if (bezDuplicit.isEmpty()) return
        Nadpis("Iné lieky s rovnakým ATC kódom")
        Text(
            "Môžu sa líšiť silou, liekovou formou aj veľkosťou balenia. Zámenu konzultujte s lekárom alebo lekárnikom.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Karta {
            bezDuplicit.forEachIndexed { i, x ->
                if (i > 0) Oddelovac()
                LiekRiadok(x, lekarnik, onClick = { onOtvor(x.kod) })
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}
