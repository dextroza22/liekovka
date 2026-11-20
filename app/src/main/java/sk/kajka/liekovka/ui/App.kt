package sk.kajka.liekovka.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import sk.kajka.liekovka.R
import sk.kajka.liekovka.data.Repository

private data class Karta(val route: String, val nazov: String)

private val KARTY = listOf(
    Karta("hladaj", "Hľadať"),
    Karta("lekarnicka", "Lekárnička"),
    Karta("upozornenia", "Upozornenia"),
    Karta("info", "Nastavenia"),
)

@Composable
fun App(pociatocnaKarta: String?, onKartaSpracovana: () -> Unit) {
    val repo = Repository.get(LocalContext.current)
    var pripravene by remember { mutableStateOf<Boolean?>(null) }
    var prijate by remember { mutableStateOf(repo.prefs.upozorneniePrijate) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { pripravene = repo.pripravData() }

    when (pripravene) {
        null -> { Nacitavanie("Pripravujem databázu liekov…"); return }
        false -> {
            var bezi by remember { mutableStateOf(false) }
            Nacitavanie(
                if (bezi) "Sťahujem dáta o liekoch…"
                else "Dáta o liekoch zatiaľ nie sú k dispozícii. Pripojte sa na internet a skúste to znova.",
                progres = bezi,
            ) {
                repo.poslednaChyba?.let {
                    Text("Technický detail: $it", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp))
                }
                if (!bezi) TextButton(onClick = {
                    bezi = true
                    scope.launch { repo.aktualizuj(); pripravene = repo.pripravData(); bezi = false }
                }) { Text("Skúsiť znova") }
            }
            return
        }
        else -> {}
    }

    PlusSpravy()
    val nav = rememberNavController()
    val zaznam by nav.currentBackStackEntryAsState()
    val aktualna = zaznam?.destination?.route
    val neprecitane by remember { repo.db.upozornenia().neprecitane() }.collectAsState(0)

    LaunchedEffect(pociatocnaKarta) {
        if (pociatocnaKarta != null && KARTY.any { it.route == pociatocnaKarta }) {
            nav.navigate(pociatocnaKarta) { popUpTo(nav.graph.findStartDestination().id); launchSingleTop = true }
            onKartaSpracovana()
        }
    }

    Scaffold(
        bottomBar = {
            if (aktualna == null || KARTY.any { it.route == aktualna }) NavigationBar {
                KARTY.forEach { k ->
                    NavigationBarItem(
                        selected = aktualna == k.route,
                        onClick = {
                            nav.navigate(k.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            when (k.route) {
                                "hladaj" -> Icon(Icons.Default.Search, null)
                                "lekarnicka" -> Icon(painterResource(R.drawable.ic_lekarnicka), null)
                                "upozornenia" -> BadgedBox(badge = { if (neprecitane > 0) Badge { Text("$neprecitane") } }) {
                                    Icon(Icons.Default.Notifications, null)
                                }
                                else -> Icon(Icons.Default.Settings, null)
                            }
                        },
                        label = { Text(k.nazov, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = "hladaj", modifier = Modifier.padding(pad)) {
            val otvor: (String) -> Unit = { kod -> nav.navigate("liek/$kod") }
            val naKartu: (String) -> Unit = { route ->
                nav.navigate(route) {
                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            composable("hladaj") { HladajScreen(otvor, onKarta = { r -> if (r == "prehlad") nav.navigate(r) else naKartu(r) }) }
            composable("lekarnicka") { LekarnickaScreen(otvor) }
            composable("upozornenia") { UpozorneniaScreen(otvor) }
            composable("info") { InfoScreen() }
            composable("prehlad") { PrehladScreen(onSpat = { nav.popBackStack() }, onOtvor = otvor) }
            composable("liek/{kod}") { e ->
                DetailScreen(e.arguments?.getString("kod").orEmpty(), onSpat = { nav.popBackStack() }, onOtvor = otvor)
            }
        }
    }

    if (!prijate) AlertDialog(
        onDismissRequest = {},
        title = { Text("Vitajte v Liekovke") },
        text = {
            Text(
                "Liekovka zobrazuje verejné informácie o liekoch zo ŠÚKL a MZ SR: príbalové letáky, doplatky, dostupnosť " +
                    "a lieky s rovnakou účinnou látkou. Nie je oficiálnou aplikáciou ŠÚKL ani MZ SR.\n\n" +
                    "Aplikácia nenahrádza lekára ani lekárnika. Pred zmenou lieku alebo pri otázkach o užívaní sa vždy " +
                    "poraďte s odborníkom.\n\nPodmienky používania a zásady ochrany súkromia nájdete v Nastaveniach."
            )
        },
        confirmButton = { TextButton(onClick = { repo.prefs.upozorneniePrijate = true; prijate = true }) { Text("Rozumiem") } },
    )
}

@Composable
private fun Nacitavanie(text: String, progres: Boolean = true, akcia: @Composable () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (progres) CircularProgressIndicator()
        Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp))
        akcia()
    }
}
