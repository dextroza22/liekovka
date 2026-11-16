package sk.kajka.liekovka.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sk.kajka.liekovka.data.Liek
import sk.kajka.liekovka.data.eur
import sk.kajka.liekovka.data.nedostupny
import sk.kajka.liekovka.data.vypadokText
import sk.kajka.liekovka.data.suklHladajKod
import sk.kajka.liekovka.data.suklStranka
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Farebný štítok stavu dodávok. */
@Composable
fun StavStitok(stav: String?, od: String?, modifier: Modifier = Modifier) {
    val text = vypadokText(stav, od) ?: return
    val (bg, fg) = if (nedostupny(stav))
        MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    else MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    Stitok(text, bg, fg, modifier)
}

@Composable
fun Stitok(text: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(6.dp), modifier = modifier) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

/** Riadok lieku vo výsledkoch hľadania a v zoznamoch. */
@Composable
fun LiekRiadok(l: Liek, lekarnik: Boolean, onClick: () -> Unit, zvyrazni: String? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Avatar(l)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(l.nazov, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (l.doplnok.isNotBlank()) Text(l.doplnok, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val podtitul = buildList {
                    if (l.latka.isNotBlank()) add(l.latka)
                    if (lekarnik) { if (l.atc.isNotBlank()) add(l.atc); add("ŠÚKL ${l.kod}") }
                }.joinToString(" · ")
                if (podtitul.isNotBlank()) Text(podtitul, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                when {
                    l.doplatok != null -> {
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            shape = RoundedCornerShape(8.dp)) {
                            Text(eur(l.doplatok), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                        Text("doplatok", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp))
                    }
                    l.vydaj == "F" -> Text("bez predpisu", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> Text("nehradený", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val stitky = vypadokText(l.vypadok, l.vypadokOd) != null || zvyrazni != null
        if (stitky) Row(Modifier.padding(top = 6.dp, start = 52.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (zvyrazni != null) Stitok(zvyrazni, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
            StavStitok(l.vypadok, l.vypadokOd)
        }
    }
}

/** Kruh s prvým písmenom názvu; farba podľa dostupnosti a toho, či je liek bez predpisu. */
@Composable
fun Avatar(l: Liek) {
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when {
        nedostupny(l.vypadok) -> cs.errorContainer to cs.onErrorContainer
        l.vypadok != null -> cs.tertiaryContainer to cs.onTertiaryContainer
        l.vydaj == "F" -> cs.secondaryContainer to cs.onSecondaryContainer
        else -> cs.primaryContainer to cs.onPrimaryContainer
    }
    Box(Modifier.size(40.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Text(l.nazov.take(1).uppercase(), color = fg, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Karta(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(14.dp),
    ) { Column(Modifier.padding(vertical = 4.dp)) { content() } }
}

@Composable
fun Udaj(nazov: String, hodnota: String) {
    if (hodnota.isBlank()) return
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(nazov, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f))
        Text(hodnota, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.58f))
    }
}

@Composable
fun Oddelovac() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))

@Composable
fun Nadpis(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold, modifier = modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp))
}

/** Otvorí dokument ŠÚKL; ak priamy odkaz nie je, otvorí vyhľadávanie ŠÚKL a skopíruje názov lieku. */
fun otvorDokument(ctx: Context, url: String, nazovLieku: String, hladajNaSukl: String) {
    if (url.isNotBlank()) return otvor(ctx, url)
    val cb = ctx.getSystemService(ClipboardManager::class.java)
    cb.setPrimaryClip(ClipData.newPlainText("Názov lieku", nazovLieku))
    Toast.makeText(ctx, "Názov lieku je skopírovaný. Vložte ho do vyhľadávania ŠÚKL.", Toast.LENGTH_LONG).show()
    otvor(ctx, hladajNaSukl)
}

/**
 * Otvorí stránku lieku na sukl.sk. Adresa sa skladá z názvu a kódu; ak sa názov na webe líši od registra
 * a stránka neexistuje, otvorí sa vyhľadávanie podľa kódu ŠÚKL (nájde liek vždy).
 */
suspend fun otvorStrankuLieku(ctx: Context, l: Liek) {
    val stranka = suklStranka(l.nazov, l.kod)
    val existuje = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val c = java.net.URL(stranka).openConnection() as java.net.HttpURLConnection
            c.connectTimeout = 4000; c.readTimeout = 4000; c.instanceFollowRedirects = true
            try { c.responseCode == 200 } finally { c.disconnect() }
        }.getOrDefault(true)   // bez spojenia to skúsi prehliadač sám
    }
    otvor(ctx, if (existuje) stranka else suklHladajKod(l.kod))
}

/** Otvorí e-mail prevádzkovateľovi s predvyplneným liekom; bez e-mailovej aplikácie skopíruje adresu. */
fun nahlasChybu(ctx: Context, l: Liek?) {
    val email = sk.kajka.liekovka.BuildConfig.KONTAKT_EMAIL
    val predmet = "Liekovka: chyba v údajoch" + (l?.let { " (${it.nazov}, kód ${it.kod})" } ?: "")
    val telo = (l?.let { "Liek: ${it.nazov} ${it.doplnok}\nKód ŠÚKL: ${it.kod}\n\n" } ?: "") +
        "Čo je nesprávne:\n\nAko je to správne (ak viete, aj zdroj):\n\n" +
        "Verzia aplikácie: ${sk.kajka.liekovka.BuildConfig.VERSION_NAME}"
    val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        .putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
        .putExtra(Intent.EXTRA_SUBJECT, predmet)
        .putExtra(Intent.EXTRA_TEXT, telo)
    try {
        ctx.startActivity(i)
    } catch (_: Exception) {
        ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("E-mail", email))
        Toast.makeText(ctx, "E-mailová aplikácia sa nenašla. Adresa $email je skopírovaná.", Toast.LENGTH_LONG).show()
    }
}

fun otvor(ctx: Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
        Toast.makeText(ctx, "Odkaz sa nepodarilo otvoriť.", Toast.LENGTH_SHORT).show()
    }
}

/** Dialóg na výber dátumu exspirácie; vracia RRRR-MM-DD. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExspiraciaDialog(pociatocny: String?, onDismiss: () -> Unit, onVyber: (String?) -> Unit) {
    val init = pociatocny?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
    val st = rememberDatePickerState(initialSelectedDateMillis = init)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onVyber(st.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() })
            }) { Text("Uložiť") }
        },
        dismissButton = {
            Row {
                if (pociatocny != null) TextButton(onClick = { onVyber(null) }) { Text("Odstrániť") }
                TextButton(onClick = onDismiss) { Text("Zrušiť") }
            }
        },
    ) {
        DatePicker(st, title = { Text("Dátum exspirácie (EXP na obale)", Modifier.padding(start = 24.dp, top = 16.dp)) })
    }
}
