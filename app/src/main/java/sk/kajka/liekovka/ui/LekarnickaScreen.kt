package sk.kajka.liekovka.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import sk.kajka.liekovka.Notif
import sk.kajka.liekovka.data.SucetDoplatkov
import sk.kajka.liekovka.data.casyText
import sk.kajka.liekovka.data.formatujCasy
import sk.kajka.liekovka.data.parsujCasy
import sk.kajka.liekovka.data.sucetDoplatkov
import java.time.LocalTime
import kotlinx.coroutines.launch
import sk.kajka.liekovka.data.MojLiek
import sk.kajka.liekovka.data.Profil
import sk.kajka.liekovka.data.Repository
import sk.kajka.liekovka.data.buducaZmenaText
import sk.kajka.liekovka.data.datum
import sk.kajka.liekovka.data.eur
import sk.kajka.liekovka.data.jeLacnejsia
import sk.kajka.liekovka.data.stiahnutieKratko
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LekarnickaScreen(onOtvor: (String) -> Unit) {
    val ctx = LocalContext.current
    val repo = Repository.get(ctx)
    val scope = rememberCoroutineScope()
    val profily by remember { repo.db.profily().all() }.collectAsState(emptyList())
    val profilId by repo.prefs.profil.collectAsState()
    val profil = profily.firstOrNull { it.id == profilId } ?: profily.firstOrNull()
    val moje by remember(profil?.id) { profil?.let { repo.db.moje().all(it.id) } ?: flowOf(emptyList()) }.collectAsState(null)
    var upravProfil by remember { mutableStateOf<Profil?>(null) }
    var novyProfil by remember { mutableStateOf(false) }
    var zdielat by remember { mutableStateOf(false) }
    val zoznam = moje ?: return
    if (profil == null) return

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Moja lekárnička", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (zoznam.isNotEmpty()) IconButton(onClick = { zdielat = true }) { Icon(Icons.Default.Share, "Zdieľať zoznam liekov") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                profily.forEach { p ->
                    val vybrany = p.id == profil.id
                    FilterChip(
                        selected = vybrany,
                        onClick = { if (vybrany) upravProfil = p else repo.prefs.setProfil(p.id) },
                        label = { Text(p.meno) },
                        trailingIcon = if (vybrany) ({ Icon(Icons.Default.Edit, "Upraviť", Modifier.size(16.dp)) }) else null,
                    )
                }
                AssistChip(onClick = { novyProfil = true }, label = { Text("Pridať osobu") },
                    leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(18.dp)) })
            }
        }
        if (zoznam.isEmpty()) {
            item {
                Text(
                    "Lieky, ktoré ${if (profily.size > 1) "${profil.meno} užíva" else "užívate"} alebo máte doma, si pridáte cez vyhľadávanie " +
                        "(tlačidlo „Pridať do mojej lekárničky“). Liekovka vás potom upozorní, keď:\n\n" +
                        "• ŠÚKL ohlási prerušenie alebo skončenie dodávok\n" +
                        "• sa zmení doplatok, aj vopred od budúceho mesiaca\n" +
                        "• existuje dostupný liek s nižším doplatkom\n" +
                        "• sa blíži dátum exspirácie\n" +
                        "• je čas užiť liek (ak si nastavíte pripomienky)\n\n" +
                        "Zoznam zostáva len vo vašom telefóne. Nemáme účty ani server s vašimi údajmi. " +
                        "Lieky rodičov alebo detí si môžete viesť zvlášť cez „Pridať osobu“.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            item { Suhrny(zoznam, profil.id, repo) }
            items(zoznam, key = { it.id }) { m -> PolozkaLekarnicky(m, repo, onOtvor) }
        }
    }

    if (zdielat) AlertDialog(
        onDismissRequest = { zdielat = false },
        title = { Text("Zdieľať zoznam liekov") },
        text = { Text("Prehľad liekov (${profil.meno}) s účinnou látkou, poznámkou a exspiráciou, napríklad pre lekára, do nemocnice alebo pre rodinu.") },
        confirmButton = {
            TextButton(onClick = { zdielat = false; scope.launch { Zdielanie.pdf(ctx, repo.zoznamNaZdielanie(profil)) } }) { Text("Ako PDF") }
        },
        dismissButton = {
            TextButton(onClick = { zdielat = false; scope.launch { Zdielanie.text(ctx, repo.zoznamNaZdielanie(profil)) } }) { Text("Ako text") }
        },
    )
    if (novyProfil) MenoDialog("Pridať osobu", "", onDismiss = { novyProfil = false }) { meno ->
        novyProfil = false
        scope.launch { repo.prefs.setProfil(repo.db.profily().insert(Profil(meno = meno))) }
    }
    upravProfil?.let { p ->
        MenoDialog(
            "Upraviť osobu", p.meno, onDismiss = { upravProfil = null },
            onOdstranit = if (profily.size > 1) ({
                upravProfil = null
                scope.launch {
                    repo.db.moje().deleteProfil(p.id); repo.db.profily().delete(p.id)
                    repo.prefs.setProfil(profily.first { it.id != p.id }.id)
                }
            }) else null,
        ) { meno -> upravProfil = null; scope.launch { repo.db.profily().update(p.copy(meno = meno)) } }
    }
}

@Composable
private fun MenoDialog(titulok: String, pociatok: String, onDismiss: () -> Unit, onOdstranit: (() -> Unit)? = null, onUloz: (String) -> Unit) {
    var meno by remember { mutableStateOf(pociatok) }
    var potvrdit by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (potvrdit) "Odstrániť ${pociatok}?" else titulok) },
        text = {
            if (potvrdit) Text("Odstráni sa aj celá lekárnička tejto osoby.")
            else OutlinedTextField(meno, { meno = it.take(30) }, label = { Text("Meno, napr. Mama") }, singleLine = true)
        },
        confirmButton = {
            if (potvrdit) TextButton(onClick = { onOdstranit?.invoke() }) { Text("Odstrániť") }
            else TextButton(onClick = { onUloz(meno.trim()) }, enabled = meno.isNotBlank()) { Text("Uložiť") }
        },
        dismissButton = {
            Row {
                if (onOdstranit != null && !potvrdit) TextButton(onClick = { potvrdit = true }) { Text("Odstrániť") }
                TextButton(onClick = onDismiss) { Text("Zrušiť") }
            }
        },
    )
}

@Composable
private fun Suhrny(zoznam: List<MojLiek>, profilId: Long, repo: Repository) {
    val sVypadkom by remember(profilId) { repo.db.moje().sVypadkom(profilId) }.collectAsState(0)
    val dnes = LocalDate.now()
    val coskoro = zoznam.count { m ->
        m.exspiracia?.let { runCatching { ChronoUnit.DAYS.between(dnes, LocalDate.parse(it)) <= 30 }.getOrNull() } == true
    }
    val cs = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
        Suhrn("${zoznam.size}", slovoLiek(zoznam.size), cs.primaryContainer, cs.onPrimaryContainer, Modifier.weight(1f))
        Suhrn("$coskoro", "exspiruje do 30 dní",
            if (coskoro > 0) cs.tertiaryContainer else cs.surfaceContainerHigh,
            if (coskoro > 0) cs.onTertiaryContainer else cs.onSurfaceVariant, Modifier.weight(1f))
        Suhrn("$sVypadkom", "s výpadkom",
            if (sVypadkom > 0) cs.errorContainer else cs.surfaceContainerHigh,
            if (sVypadkom > 0) cs.onErrorContainer else cs.onSurfaceVariant, Modifier.weight(1f))
    }
    val sucet by produceState<SucetDoplatkov?>(null, zoznam) {
        value = withContext(Dispatchers.IO) {
            val d = repo.db.lieky()
            sucetDoplatkov(zoznam.map { m ->
                val l = d.getNow(m.kod)
                l to l?.takeIf { it.skupina >= 0 }?.let { d.najlacnejsiNow(it.skupina, m.kod) }
            })
        }
    }
    sucet?.takeIf { it.spolu > 0 || it.bezUdaja < zoznam.size }?.let { s ->
        Surface(color = cs.surfaceContainerHigh, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text("Koľko doplácam", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                Text(eur(s.spolu), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("Súčet doplatkov za jedno balenie každého lieku" +
                    (if (s.bezUdaja > 0) " (${s.bezUdaja} bez údaja o doplatku, napr. voľnopredajné)" else ""),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                if (s.uspora >= 0.01) Text(
                    "S dostupnými lacnejšími alternatívami by to bolo ${eur(s.sAlternativami)}, teda o ${eur(s.uspora)} menej. " +
                        "Zámenu konzultujte s lekárom alebo lekárnikom.",
                    style = MaterialTheme.typography.bodyMedium, color = cs.primary, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PolozkaLekarnicky(m: MojLiek, repo: Repository, onOtvor: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val liek by remember(m.kod) { repo.db.lieky().observe(m.kod) }.collectAsState(null)
    val l = liek
    val alt by remember(l?.skupina, m.kod) {
        if (l != null && l.skupina >= 0) repo.db.lieky().najlacnejsi(l.skupina, m.kod) else flowOf(null)
    }.collectAsState(null)
    val stiahnutia by remember(m.kod) { repo.db.stiahnutia().preKod(m.kod) }.collectAsState(emptyList())
    val ctx = LocalContext.current
    var zmazat by remember { mutableStateOf(false) }
    var upravit by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme

    Karta(Modifier.clickable { onOtvor(m.kod) }) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(top = 4.dp)) {
                Text(m.nazov, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (m.doplnok.isNotBlank()) Text(m.doplnok, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                if (m.poznamka.isNotBlank()) Text("Poznámka: ${m.poznamka}", style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp))
                val casy = parsujCasy(m.casy)
                if (casy.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(Icons.Default.Notifications, null, Modifier.size(16.dp), tint = cs.primary)
                    Text(" Pripomienky: ${casyText(casy)}", style = MaterialTheme.typography.bodyMedium, color = cs.primary)
                }
            }
            IconButton(onClick = { upravit = true }) { Icon(Icons.Default.Edit, "Upraviť exspiráciu, poznámku a pripomienky") }
            IconButton(onClick = { zmazat = true }) { Icon(Icons.Default.Delete, "Odstrániť") }
        }
        FlowRow(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ExspiraciaStitok(m.exspiracia)
            if (l == null) Stitok("Nie je v registri", cs.errorContainer, cs.onErrorContainer)
            else StavStitok(l.vypadok, l.vypadokOd)
            if (l?.doplatok != null) Text("doplatok ${eur(l.doplatok)}", style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterVertically))
        }
        stiahnutia.forEach { s ->
            Info("${stiahnutieKratko(s)} (${datum(s.datum)}). Skontrolujte číslo šarže na obale.",
                cs.errorContainer, cs.onErrorContainer, onClick = { otvor(ctx, s.url) })
        }
        val od = repo.prefs.buduceOd
        if (l != null && l.buducaZmena && od.isNotBlank()) {
            val drahsie = l.buduciDoplatok == null || (l.doplatok != null && l.buduciDoplatok > l.doplatok)
            Info(buducaZmenaText(l, od), if (drahsie) cs.tertiaryContainer else cs.secondaryContainer,
                if (drahsie) cs.onTertiaryContainer else cs.onSecondaryContainer)
        }
        val a = alt
        if (l != null && a != null && jeLacnejsia(l, a)) {
            Info(
                "Lacnejšia alternatíva: ${a.nazov}, doplatok ${eur(a.doplatok)} (o ${eur(l.doplatok!! - a.doplatok!!)} menej). " +
                    "Zámenu konzultujte s lekárom alebo lekárnikom.",
                cs.primaryContainer, cs.onPrimaryContainer, onClick = { onOtvor(a.kod) },
            )
        }
    }
    if (zmazat) AlertDialog(
        onDismissRequest = { zmazat = false },
        title = { Text("Odstrániť z lekárničky?") },
        text = { Text(m.nazov) },
        confirmButton = { TextButton(onClick = { zmazat = false; scope.launch { repo.db.moje().delete(m.id) } }) { Text("Odstrániť") } },
        dismissButton = { TextButton(onClick = { zmazat = false }) { Text("Zrušiť") } },
    )
    if (upravit) UpravDialog(m, onDismiss = { upravit = false }) { exp, pozn, casy ->
        upravit = false
        scope.launch {
            repo.db.moje().update(m.copy(exspiracia = exp, poznamka = pozn.trim(), casy = casy,
                upozornenieExspiracia = if (exp != m.exspiracia) false else m.upozornenieExspiracia))
        }
    }
}

/** Farebný pás v karte lieku (budúci doplatok, lacnejšia alternatíva). */
@Composable
private fun Info(text: String, bg: Color, fg: Color, onClick: (() -> Unit)? = null) {
    Surface(
        color = bg, contentColor = fg, shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }
}

/** Úprava dátumu exspirácie, poznámky a časov pripomienok. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UpravDialog(m: MojLiek, onDismiss: () -> Unit, onUloz: (String?, String, String) -> Unit) {
    val ctx = LocalContext.current
    var exp by remember { mutableStateOf(m.exspiracia) }
    var pozn by remember { mutableStateOf(m.poznamka) }
    var casy by remember { mutableStateOf(parsujCasy(m.casy)) }
    var datumDialog by remember { mutableStateOf(false) }
    var casDialog by remember { mutableStateOf(false) }
    val povolenie = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(m.nazov) },
        text = {
            Column {
                TextButton(onClick = { datumDialog = true }) {
                    Text(exp?.let { "Exspirácia: ${datum(it)}" } ?: "Zadať dátum exspirácie")
                }
                OutlinedTextField(pozn, { pozn = it.take(120) }, label = { Text("Poznámka, napr. ráno a večer") },
                    modifier = Modifier.fillMaxWidth())
                Text("Pripomienky užívania", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 14.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    casy.forEach { c ->
                        InputChip(selected = false, onClick = { casy = casy - c }, label = { Text(casyText(listOf(c))) },
                            trailingIcon = { Icon(Icons.Default.Close, "Odstrániť čas", Modifier.size(16.dp)) })
                    }
                    if (casy.size < 6) AssistChip(onClick = { casDialog = true }, label = { Text("Pridať čas") },
                        leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(18.dp)) })
                }
                Text("Pripomienky nastavujte podľa pokynov lekára. Aplikácia nekontroluje dávkovanie; v úspornom režime môže pripomienka prísť o pár minút neskôr.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onUloz(exp, pozn, formatujCasy(casy)) }) { Text("Uložiť") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušiť") } },
    )
    if (datumDialog) ExspiraciaDialog(exp, onDismiss = { datumDialog = false }) { exp = it; datumDialog = false }
    if (casDialog) CasDialog(onDismiss = { casDialog = false }) { c ->
        casDialog = false
        casy = (casy + c).distinct().sorted()
        if (Build.VERSION.SDK_INT >= 33 && !Notif.moze(ctx)) povolenie.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CasDialog(onDismiss: () -> Unit, onVyber: (LocalTime) -> Unit) {
    val st = rememberTimePickerState(initialHour = 8, initialMinute = 0, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Čas pripomienky") },
        text = { TimePicker(st) },
        confirmButton = { TextButton(onClick = { onVyber(LocalTime.of(st.hour, st.minute)) }) { Text("Pridať") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušiť") } },
    )
}

@Composable
private fun Suhrn(cislo: String, popis: String, bg: Color, fg: Color, modifier: Modifier) {
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(cislo, style = MaterialTheme.typography.headlineSmall)
            Text(popis, style = MaterialTheme.typography.labelMedium, maxLines = 2)
        }
    }
}

@Composable
private fun ExspiraciaStitok(exp: String?) {
    val cs = MaterialTheme.colorScheme
    if (exp == null) {
        Stitok("Bez dátumu exspirácie", cs.surfaceContainerHigh, cs.onSurfaceVariant); return
    }
    val dni = runCatching { ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(exp)) }.getOrNull() ?: return
    when {
        dni < 0 -> Stitok("Po exspirácii (${datum(exp)})", cs.errorContainer, cs.onErrorContainer)
        dni <= 30 -> Stitok("EXP ${datum(exp)}", cs.tertiaryContainer, cs.onTertiaryContainer)
        else -> Stitok("EXP ${datum(exp)}", cs.surfaceContainerHigh, cs.onSurfaceVariant)
    }
}
