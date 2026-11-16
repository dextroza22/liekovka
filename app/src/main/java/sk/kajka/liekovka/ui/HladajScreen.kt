package sk.kajka.liekovka.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import sk.kajka.liekovka.R
import sk.kajka.liekovka.data.Liek
import sk.kajka.liekovka.data.Repository
import sk.kajka.liekovka.data.datum
import sk.kajka.liekovka.data.filtrujSlova
import sk.kajka.liekovka.data.slovaDopytu
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val NAVRHY = listOf("paracetamol", "ibuprofen", "omeprazol", "atorvastatín", "metformín")

@Composable
fun HladajScreen(onOtvor: (String) -> Unit, onKarta: (String) -> Unit) {
    val repo = Repository.get(LocalContext.current)
    val lekarnik by repo.prefs.lekarnik.collectAsState()
    var q by rememberSaveable { mutableStateOf("") }
    var vysledky by remember { mutableStateOf<List<Liek>?>(null) }

    LaunchedEffect(q) {
        val slova = slovaDopytu(q)
        if (slova.isEmpty()) { vysledky = null; return@LaunchedEffect }
        delay(180)
        val hrube = repo.db.lieky().hladaj(slova[0], q.trim().uppercase())
        vysledky = filtrujSlova(hrube, slova.drop(1)).take(120)
    }

    Column(Modifier.fillMaxSize()) {
        Hlavicka(q, lekarnik, onZmena = { q = it })
        val v = vysledky
        when {
            v == null -> Uvod(repo, lekarnik, onNavrh = { q = it }, onKarta = onKarta)
            v.isEmpty() -> Text("Nenašiel sa žiadny liek. Skúste iný názov alebo účinnú látku.",
                Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Text("${v.size}${if (v.size == 120) "+" else ""} výsledkov", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
                }
                items(v, key = { it.kod }) { l ->
                    LiekRiadok(l, lekarnik, onClick = { onOtvor(l.kod) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 68.dp))
                }
            }
        }
    }
}

/** Farebný pás s názvom aplikácie a vyhľadávacím poľom. */
@Composable
private fun Hlavicka(q: String, lekarnik: Boolean, onZmena: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.primary, contentColor = cs.onPrimary, shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = cs.onPrimary.copy(alpha = 0.16f), shape = RoundedCornerShape(10.dp)) {
                    Icon(painterResource(R.drawable.ic_notify), null, Modifier.padding(6.dp).size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text("Liekovka", style = MaterialTheme.typography.titleLarge)
            }
            Text("Lieky, doplatky a dostupnosť na jednom mieste", style = MaterialTheme.typography.bodyMedium,
                color = cs.onPrimary.copy(alpha = 0.85f), modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
            TextField(
                value = q,
                onValueChange = onZmena,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(if (lekarnik) "Názov, látka, ATC alebo ŠÚKL kód" else "Názov lieku alebo účinná látka") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { onZmena("") }) { Icon(Icons.Default.Clear, "Vymazať") } },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = cs.surfaceContainerLow, unfocusedContainerColor = cs.surfaceContainerLow,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = cs.onSurface, unfocusedTextColor = cs.onSurface,
                    focusedLeadingIconColor = cs.primary, unfocusedLeadingIconColor = cs.onSurfaceVariant,
                    focusedPlaceholderColor = cs.onSurfaceVariant, unfocusedPlaceholderColor = cs.onSurfaceVariant,
                    cursorColor = cs.primary,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Uvod(repo: Repository, lekarnik: Boolean, onNavrh: (String) -> Unit, onKarta: (String) -> Unit) {
    val profilId by repo.prefs.profil.collectAsState()
    val profily by remember { repo.db.profily().all() }.collectAsState(emptyList())
    val moje by remember(profilId) { repo.db.moje().all(profilId) }.collectAsState(emptyList())
    val sVypadkom by remember(profilId) { repo.db.moje().sVypadkom(profilId) }.collectAsState(0)
    val meno = profily.takeIf { it.size > 1 }?.firstOrNull { it.id == profilId }?.meno
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Skúste vyhľadať", style = MaterialTheme.typography.titleSmall, color = cs.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            (if (lekarnik) NAVRHY.take(3) + listOf("C09AA", "N02BE01") else NAVRHY).forEach { n ->
                SuggestionChip(onClick = { onNavrh(n) }, label = { Text(n) })
            }
        }

        // súhrn lekárničky
        val dnes = LocalDate.now()
        val coskoro = moje.count { m ->
            m.exspiracia?.let { runCatching { ChronoUnit.DAYS.between(dnes, LocalDate.parse(it)) <= 30 }.getOrNull() } == true
        }
        Surface(
            color = cs.surfaceContainerLow, shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp).clickable { onKarta("lekarnicka") },
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = cs.primaryContainer, contentColor = cs.onPrimaryContainer, shape = RoundedCornerShape(14.dp)) {
                    Icon(painterResource(R.drawable.ic_lekarnicka), null, Modifier.padding(10.dp).size(24.dp))
                }
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(if (meno != null) "Lekárnička · $meno" else "Moja lekárnička", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            moje.isEmpty() -> "Pridajte si lieky a upozorníme vás na výpadky, zmeny doplatku a exspiráciu."
                            else -> listOfNotNull(
                                "${moje.size} ${slovoLiek(moje.size)}",
                                if (sVypadkom > 0) "$sVypadkom s výpadkom" else null,
                                if (coskoro > 0) "$coskoro exspiruje do 30 dní" else null,
                            ).joinToString(" · ")
                        },
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = cs.onSurfaceVariant)
            }
        }

        if (lekarnik) {
            Surface(
                color = cs.surfaceContainerLow, shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).clickable { onKarta("prehlad") },
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = cs.secondaryContainer, contentColor = cs.onSecondaryContainer, shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.AutoMirrored.Filled.List, null, Modifier.padding(10.dp).size(24.dp))
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text("Prehľad pre lekárnikov", style = MaterialTheme.typography.titleMedium)
                        Text("Nové výpadky, zmeny doplatkov od budúceho mesiaca a stiahnutia, s filtrom ATC",
                            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = cs.onSurfaceVariant)
                }
            }
        }

        Text(
            "Doplatky, generiká a výpadky dodávok podľa oficiálnych údajov MZ SR a ŠÚKL. Bez účtu, bez reklám, funguje aj offline.",
            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 20.dp),
        )
        val p = repo.prefs
        if (p.kategorizaciaOd.isNotBlank()) Text(
            "Doplatky podľa zoznamu MZ SR platného od ${datum(p.kategorizaciaOd)}" +
                (if (p.vypadkyK.isNotBlank()) " · výpadky k ${datum(p.vypadkyK)}" else ""),
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

fun slovoLiek(n: Int) = when {
    n == 1 -> "liek"
    n in 2..4 -> "lieky"
    else -> "liekov"
}
