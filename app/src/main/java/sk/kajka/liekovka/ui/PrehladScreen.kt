package sk.kajka.liekovka.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sk.kajka.liekovka.data.Liek
import sk.kajka.liekovka.data.Repository
import sk.kajka.liekovka.data.buducaZmenaText
import sk.kajka.liekovka.data.datum
import sk.kajka.liekovka.data.odNovychVypadkov
import java.time.LocalDate

/** Prehľad pre lekárnikov: nové výpadky, zmeny doplatkov od budúceho mesiaca a stiahnutia z trhu, s filtrom podľa ATC. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PrehladScreen(onSpat: () -> Unit, onOtvor: (String) -> Unit) {
    val repo = Repository.get(LocalContext.current)
    var karta by rememberSaveable { mutableIntStateOf(0) }
    var atc by rememberSaveable { mutableStateOf("") }
    val filter = atc.trim().uppercase()
    val cs = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, end = 16.dp)) {
            IconButton(onClick = onSpat) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Späť") }
            Text("Prehľad pre lekárnikov", style = MaterialTheme.typography.titleLarge)
        }
        OutlinedTextField(
            atc, { atc = it.take(7) },
            label = { Text("Filter ATC, napr. C09 alebo N02BE") },
            singleLine = true,
            trailingIcon = { if (atc.isNotEmpty()) IconButton(onClick = { atc = "" }) { Icon(Icons.Default.Clear, "Zrušiť filter") } },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        val volby = listOf("Výpadky", "Doplatky", "Stiahnutia")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(16.dp)) {
            volby.forEachIndexed { i, n ->
                SegmentedButton(selected = karta == i, onClick = { karta = i }, shape = SegmentedButtonDefaults.itemShape(i, volby.size)) {
                    Text(n, maxLines = 1)
                }
            }
        }
        when (karta) {
            0 -> Vypadky(repo, filter, onOtvor)
            1 -> Doplatky(repo, filter, onOtvor)
            else -> Stiahnutia(repo, filter, onOtvor)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Vypadky(repo: Repository, atc: String, onOtvor: (String) -> Unit) {
    var dni by rememberSaveable { mutableLongStateOf(14L) }
    val od = remember(dni) { odNovychVypadkov(LocalDate.now(), dni) }
    val zoznam by remember(od, atc) { repo.db.lieky().noveVypadky(od, atc) }.collectAsState(null)
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                listOf(7L, 14L, 30L).forEach { d -> FilterChip(selected = dni == d, onClick = { dni = d }, label = { Text("$d dní") }) }
            }
            Popis(zoznam?.let { "${it.size}${if (it.size == 400) "+" else ""} liekov s hlásením ŠÚKL od ${datum(od)} (aj ohlásené do budúcna)" })
        }
        items(zoznam.orEmpty(), key = { it.kod }) { l -> Riadok(l, onOtvor) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Doplatky(repo: Repository, atc: String, onOtvor: (String) -> Unit) {
    val od = repo.prefs.buduceOd
    var typ by rememberSaveable { mutableIntStateOf(0) }
    val vsetky by remember(atc) { repo.db.lieky().buduceZmeny(atc) }.collectAsState(null)
    if (od.isBlank()) {
        Popis("MZ SR zatiaľ nezverejnilo zoznam kategorizovaných liekov na budúci mesiac. Zvyčajne sa objaví koncom mesiaca.")
        return
    }
    val zoznam = vsetky.orEmpty().filter { l ->
        val n = l.buduciDoplatok; val d = l.doplatok
        when (typ) {
            1 -> n != null && (d == null || n > d)
            2 -> n != null && d != null && n < d
            3 -> n == null
            else -> true
        }
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                listOf("Všetky", "Zvýšenie", "Zníženie", "Vyradené").forEachIndexed { i, n ->
                    FilterChip(selected = typ == i, onClick = { typ = i }, label = { Text(n) })
                }
            }
            Popis("${zoznam.size} zmien od ${datum(od)} podľa zoznamu MZ SR na budúci mesiac")
        }
        items(zoznam, key = { it.kod }) { l -> Riadok(l, onOtvor, buducaZmenaText(l, od)) }
    }
}

@Composable
private fun Stiahnutia(repo: Repository, atc: String, onOtvor: (String) -> Unit) {
    val ctx = LocalContext.current
    val oznamy by remember { repo.db.stiahnutia().all() }.collectAsState(emptyList())
    val lieky by remember { repo.db.stiahnutia().allLieky() }.collectAsState(emptyList())
    val atcKodu by produceState(emptyMap<String, Liek>(), lieky) {
        value = withContext(Dispatchers.IO) { lieky.map { it.kod }.distinct().mapNotNull { repo.db.lieky().getNow(it) }.associateBy { it.kod } }
    }
    val podlaUrl = lieky.groupBy { it.url }
    val cs = MaterialTheme.colorScheme
    val zoznam = oznamy.filter { s -> atc.isEmpty() || podlaUrl[s.url].orEmpty().any { atcKodu[it.kod]?.atc?.startsWith(atc) == true } }
    LazyColumn(Modifier.fillMaxSize()) {
        item { Popis("${zoznam.size} oznamov ŠÚKL o stiahnutí za posledný rok" + if (atc.isNotEmpty()) " (filter podľa ATC lieku v ozname)" else "") }
        items(zoznam, key = { it.url }) { s ->
            Column(Modifier.fillMaxWidth().clickable { otvor(ctx, s.url) }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(datum(s.datum) + if (s.trieda.isNotBlank()) " · trieda ${s.trieda}" else "",
                    style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                Text(s.nazov, style = MaterialTheme.typography.titleSmall)
                podlaUrl[s.url].orEmpty().forEach { l ->
                    val liek = atcKodu[l.kod]
                    Text("${l.kod}${liek?.let { " · ${it.nazov} · ${it.atc}" } ?: ""}: " +
                        if (l.vsetky) "všetky šarže" else l.sarze.split(';').filter { it.isNotBlank() }.joinToString(", ") { it.substringBefore('|') }
                            .ifBlank { "šarže v ozname" },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 2.dp).then(if (liek != null) Modifier.clickable { onOtvor(l.kod) } else Modifier))
                }
            }
            HorizontalDivider(color = cs.outlineVariant)
        }
    }
}

@Composable
private fun Riadok(l: Liek, onOtvor: (String) -> Unit, extra: String? = null) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clickable { onOtvor(l.kod) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(l.nazov, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(l.kod, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        }
        Text(listOf(l.doplnok, l.atc).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        if (extra != null) Text(extra, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
        else StavStitok(l.vypadok, l.vypadokOd, Modifier.padding(top = 4.dp))
    }
    HorizontalDivider(color = cs.outlineVariant)
}

@Composable
private fun Popis(text: String?) {
    if (text == null) return
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}
