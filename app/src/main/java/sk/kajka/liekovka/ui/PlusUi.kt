package sk.kajka.liekovka.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import sk.kajka.liekovka.BuildConfig
import sk.kajka.liekovka.Plus

/** Čo obsahuje Liekovka Plus; to isté je v popise v Google Play. */
val PLUS_FUNKCIE = listOf(
    "Lekárnička pre viac osôb (rodičia, deti)",
    "Pripomienky užívania liekov",
    "Koľko doplácam: súčet doplatkov a možná úspora",
    "Zoznam liekov ako PDF pre lekára",
    "Prehľad pre lekárnikov: výpadky, budúce doplatky, stiahnutia, filter ATC",
)

fun Context.najdiActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

@Composable
fun plusOdomknute(): Boolean = Plus.odomknute.collectAsState().value

/** Zobrazí jednorazovú správu po nákupe. Volá sa raz v App. */
@Composable
fun PlusSpravy() {
    val ctx = LocalContext.current
    val sprava by Plus.sprava.collectAsState()
    LaunchedEffect(sprava) { sprava?.let { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show(); Plus.spravaPrecitana() } }
}

/** Dialóg s ponukou Plus. [funkcia] = čo používateľ práve skúsil otvoriť. */
@Composable
fun PlusDialog(funkcia: String?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val cena by Plus.cena.collectAsState()
    val ma = plusOdomknute()
    LaunchedEffect(ma) { if (ma) onDismiss() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Liekovka Plus") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (funkcia != null) Text("$funkcia je súčasťou Liekovky Plus.", style = MaterialTheme.typography.bodyMedium)
                PLUS_FUNKCIE.forEach { f ->
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Check, null, Modifier.padding(top = 2.dp, end = 8.dp).size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(f, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text("Jednorazová platba, žiadne predplatné. Upozornenia na výpadky, doplatky a stiahnutia z trhu zostávajú zadarmo pre všetkých.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = ctx.najdiActivity()
                if (a == null || !Plus.kup(a))
                    Toast.makeText(ctx, "Google Play nie je dostupné. Skontrolujte pripojenie a skúste to znova.", Toast.LENGTH_LONG).show()
            }) { Text(cena?.let { "Kúpiť za $it" } ?: "Kúpiť") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Teraz nie") } },
    )
}

/** Malý štítok pri zamknutej funkcii. */
@Composable
fun PlusStitok(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.tertiaryContainer, contentColor = cs.onTertiaryContainer, shape = RoundedCornerShape(6.dp), modifier = modifier) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Lock, null, Modifier.size(12.dp))
            Text(" Plus", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Karta v Nastaveniach: stav Plus, kúpa a obnovenie nákupu. */
@Composable
fun PlusKarta() {
    if (!BuildConfig.PLUS_AKTIVNE) return
    val ctx = LocalContext.current
    val ma = plusOdomknute()
    var dialog by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    Nadpis("Liekovka Plus")
    Karta {
        Column(Modifier.padding(16.dp)) {
            Text(if (ma) "Máte Liekovku Plus. Ďakujeme za podporu!" else "Viac osôb, pripomienky, súčet doplatkov, PDF pre lekára a prehľad pre lekárnikov za jednorazovú platbu.",
                style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                if (!ma) TextButton(onClick = { dialog = true }) { Text("Zobraziť Plus") }
                TextButton(onClick = {
                    Plus.overNakupy { m ->
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            Toast.makeText(ctx, if (m) "Nákup obnovený, Plus je odomknuté." else "V Google Play sa nenašiel nákup Liekovky Plus pre tento účet.", Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text("Obnoviť nákup") }
            }
            if (BuildConfig.DEBUG) {
                var test by remember { mutableStateOf(Plus.testovacie()) }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { test = !test; Plus.nastavTestovacie(test) }) {
                    Text("Testovacia verzia: odomknúť Plus bez platby", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Switch(test, { test = it; Plus.nastavTestovacie(it) })
                }
            }
        }
    }
    if (dialog) PlusDialog(null) { dialog = false }
}
