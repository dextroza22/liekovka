package sk.kajka.liekovka

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import sk.kajka.liekovka.data.AppDb
import sk.kajka.liekovka.data.liekyNaCas
import sk.kajka.liekovka.data.najblizsiCas
import sk.kajka.liekovka.data.parsujCasy
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Pripomienky užívania liekov. Naplánuje sa vždy len najbližší čas (jeden budík); po jeho spustení sa naplánuje ďalší.
 * Používa nepresný budík s oknom 5 minút, ktorý nevyžaduje špeciálne povolenie; pri úspornom režime môže prísť o pár minút neskôr.
 */
object Pripominac {
    const val KANAL = "pripomienky"
    private const val AKCIA_PRIPOMIENKA = "sk.kajka.liekovka.PRIPOMIENKA"
    private const val AKCIA_UZITE = "sk.kajka.liekovka.UZITE"
    private const val AKCIA_ODLOZIT = "sk.kajka.liekovka.ODLOZIT"
    private const val EXTRA_CAS = "cas"
    private const val EXTRA_NOTIF = "notif"
    private const val ODLOZENIE_MIN = 15L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun vytvorKanal(ctx: Context) {
        val ch = NotificationChannel(KANAL, "Pripomienky užívania", NotificationManager.IMPORTANCE_HIGH)
        ch.description = "Pripomienky v časoch, ktoré ste si nastavili pri liekoch v lekárničke"
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    /** Naplánuje najbližšiu pripomienku podľa časov pri všetkých liekoch (alebo zruší, ak žiadne nie sú). */
    fun naplanuj(ctx: Context) {
        val app = ctx.applicationContext
        scope.launch {
            val casy = AppDb.get(app).moje().allNow().flatMap { parsujCasy(it.casy) }.toSet()
            val am = app.getSystemService(AlarmManager::class.java)
            val pi = budik(app, null)
            if (!Plus.odomknute.value) { am.cancel(pi); return@launch }   // pripomienky sú súčasťou Plus
            val dalsi = najblizsiCas(casy, LocalDateTime.now())
            if (dalsi == null) { am.cancel(pi); return@launch }
            am.setWindow(AlarmManager.RTC_WAKEUP, dalsi.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), 5 * 60_000L,
                budik(app, dalsi.toLocalTime()))
        }
    }

    private fun budik(ctx: Context, cas: LocalTime?, odlozenie: Boolean = false): PendingIntent {
        val i = Intent(ctx, PripomienkaReceiver::class.java).setAction(AKCIA_PRIPOMIENKA)
        if (cas != null) i.putExtra(EXTRA_CAS, "%02d:%02d".format(cas.hour, cas.minute))
        return PendingIntent.getBroadcast(ctx, if (odlozenie) 2 else 1, i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** Zobrazí pripomienku pre lieky s daným časom, zoskupenú podľa osoby. */
    fun ukaz(ctx: Context, casText: String) {
        val cas = runCatching { LocalTime.parse(casText) }.getOrNull() ?: return
        val db = AppDb.get(ctx)
        val lieky = liekyNaCas(db.moje().allNow(), cas)
        if (lieky.isEmpty() || !Notif.moze(ctx)) return
        val profily = db.profily().allNow()
        lieky.groupBy { it.profilId }.forEach { (pid, zoznam) ->
            val meno = profily.takeIf { it.size > 1 }?.firstOrNull { it.id == pid }?.meno
            val id = (1000 + pid * 100 + cas.hour * 60 + cas.minute).toInt()
            val text = zoznam.joinToString("\n") { it.nazov + if (it.poznamka.isNotBlank()) " – ${it.poznamka}" else "" }
            val otvor = PendingIntent.getActivity(ctx, id,
                Intent(ctx, MainActivity::class.java).putExtra(Notif.EXTRA_TAB, "lekarnicka").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val akcia = { a: String, req: Int -> PendingIntent.getBroadcast(ctx, req,
                Intent(ctx, PripomienkaReceiver::class.java).setAction(a).putExtra(EXTRA_NOTIF, id).putExtra(EXTRA_CAS, casText),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) }
            val n = NotificationCompat.Builder(ctx, KANAL)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle("Čas na lieky" + (meno?.let { " · $it" } ?: "") + " (${cas.hour}:%02d)".format(cas.minute))
                .setContentText(text.lineSequence().first() + if (zoznam.size > 1) " a ďalšie (${zoznam.size})" else "")
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(otvor)
                .setAutoCancel(true)
                .addAction(0, "Užité", akcia(AKCIA_UZITE, id * 10 + 1))
                .addAction(0, "Odložiť o $ODLOZENIE_MIN min", akcia(AKCIA_ODLOZIT, id * 10 + 2))
                .build()
            try { NotificationManagerCompat.from(ctx).notify(id, n) } catch (_: SecurityException) {}
        }
    }

    /** Spracovanie budíka a tlačidiel v notifikácii. */
    fun spracuj(ctx: Context, intent: Intent) {
        val cas = intent.getStringExtra(EXTRA_CAS)
        when (intent.action) {
            AKCIA_PRIPOMIENKA -> { if (cas != null) ukaz(ctx, cas); naplanuj(ctx) }
            AKCIA_UZITE -> NotificationManagerCompat.from(ctx).cancel(intent.getIntExtra(EXTRA_NOTIF, 0))
            AKCIA_ODLOZIT -> {
                NotificationManagerCompat.from(ctx).cancel(intent.getIntExtra(EXTRA_NOTIF, 0))
                if (cas != null) {
                    val kedy = System.currentTimeMillis() + ODLOZENIE_MIN * 60_000L
                    val i = Intent(ctx, PripomienkaReceiver::class.java).setAction(AKCIA_PRIPOMIENKA).putExtra(EXTRA_CAS, cas)
                    val pi = PendingIntent.getBroadcast(ctx, 3 + (intent.getIntExtra(EXTRA_NOTIF, 0) % 10000), i,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    ctx.getSystemService(AlarmManager::class.java).setWindow(AlarmManager.RTC_WAKEUP, kedy, 2 * 60_000L, pi)
                }
            }
            else -> naplanuj(ctx)   // reštart telefónu, zmena času alebo časového pásma
        }
    }
}

class PripomienkaReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val wait = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { Pripominac.spracuj(ctx.applicationContext, intent) } finally { wait.finish() }
        }
    }
}
