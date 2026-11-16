package sk.kajka.liekovka.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import sk.kajka.liekovka.data.Repository
import sk.kajka.liekovka.data.Stiahnutie
import sk.kajka.liekovka.data.StiahnutieLiek
import sk.kajka.liekovka.data.datum
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpozorneniaScreen(onOtvor: (String) -> Unit) {
    val repo = Repository.get(LocalContext.current)
    var karta by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Text("Upozornenia", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 16.dp, top = 16.dp))
        val volby = listOf("Moje lieky", "Stiahnuté z trhu")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(16.dp)) {
            volby.forEachIndexed { i, n ->
                SegmentedButton(selected = karta == i, onClick = { karta = i }, shape = SegmentedButtonDefaults.itemShape(i, volby.size)) {
                    Text(n, maxLines = 1)
                }
            }
        }
        if (karta == 0) MojeUpozornenia(repo, onOtvor) else StiahnuteZTrhu(repo, onOtvor)
    }
}

@Composable
private fun MojeUpozornenia(repo: Repository, onOtvor: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val zoznam by remember { repo.db.upozornenia().all() }.collectAsState(emptyList())
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    if (zoznam.isEmpty()) {
        Text(
            "Zatiaľ žiadne upozornenia. Objavia sa tu zmeny pri liekoch z vašej lekárničky: výpadky dodávok, " +
                "zmeny doplatku, stiahnutie šarže z trhu a blížiaca sa exspirácia.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        return
    }
    Row(Modifier.fillMaxWidth().padding(end = 8.dp)) {
        Row(Modifier.weight(1f)) {}
        if (zoznam.any { !it.precitane }) TextButton(onClick = { scope.launch { repo.db.upozornenia().precitajVsetky() } }) {
            Text("Všetko prečítané")
        } else TextButton(onClick = { scope.launch { repo.db.upozornenia().zmazVsetky() } }) { Text("Vymazať") }
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(zoznam, key = { it.id }) { u ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        scope.launch { repo.db.upozornenia().precitaj(u.id) }
                        onOtvor(u.kod)
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (u.precitane) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 7.dp, end = 12.dp).size(8.dp),
                ) {}
                Column {
                    Text(u.nazov, style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (u.precitane) FontWeight.Normal else FontWeight.SemiBold)
                    Text(u.text, style = MaterialTheme.typography.bodyMedium)
                    Text(fmt.format(Date(u.cas)), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/** Všetky oznamy ŠÚKL o stiahnutí liekov za posledný rok; lieky z lekárničky sú zvýraznené. */
@Composable
private fun StiahnuteZTrhu(repo: Repository, onOtvor: (String) -> Unit) {
    val ctx = LocalContext.current
    val oznamy by remember { repo.db.stiahnutia().all() }.collectAsState(emptyList())
    val lieky by remember { repo.db.stiahnutia().allLieky() }.collectAsState(emptyList())
    val moje by remember { repo.db.moje().vsetky() }.collectAsState(emptyList())
    val lekarnik by repo.prefs.lekarnik.collectAsState()
    val mojeKody = moje.map { it.kod }.toSet()
    val podlaUrl = lieky.groupBy { it.url }
    val cs = MaterialTheme.colorScheme
    if (oznamy.isEmpty()) {
        Text("Za posledný rok nie sú k dispozícii žiadne oznamy o stiahnutí liekov z trhu.",
            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text("Oznamy ŠÚKL za posledný rok. Lieky z vašej lekárničky sú označené.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
        }
        items(oznamy, key = { it.url }) { s: Stiahnutie ->
            val ls: List<StiahnutieLiek> = podlaUrl[s.url].orEmpty()
            val mam = ls.any { it.kod in mojeKody }
            Column(Modifier.fillMaxWidth().clickable { otvor(ctx, s.url) }.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(datum(s.datum), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                    if (mam) Stitok("V lekárničke", cs.errorContainer, cs.onErrorContainer)
                }
                Text(s.nazov, style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 2.dp))
                ls.forEach { l ->
                    val sarze = if (l.vsetky) "všetky šarže"
                    else l.sarze.split(';').filter { it.isNotBlank() }.joinToString(", ") {
                        val (c, e) = it.substringBefore('|') to it.substringAfter('|', "")
                        if (e.isBlank()) c else "$c ($e)"
                    }.ifBlank { "šarže uvedené v ozname" }
                    Text((if (lekarnik) "Kód ${l.kod}: " else "Šarža: ") + sarze, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 2.dp).then(if (l.kod in mojeKody) Modifier.clickable { onOtvor(l.kod) } else Modifier))
                }
                if (s.dovod.isNotBlank()) Text(s.dovod, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp))
            }
            HorizontalDivider(color = cs.outlineVariant)
        }
    }
}
