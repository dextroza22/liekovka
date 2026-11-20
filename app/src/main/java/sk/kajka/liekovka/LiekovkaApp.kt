package sk.kajka.liekovka

import android.app.Application
import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import sk.kajka.liekovka.data.Aktualizacia
import sk.kajka.liekovka.data.Repository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

class LiekovkaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notif.vytvorKanal(this)
        Pripominac.vytvorKanal(this)
        Plus.init(this)
        // pri každej zmene lekárničky (časy, pridanie, odstránenie, obnova zo zálohy) naplánovať najbližšiu pripomienku
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
            kotlinx.coroutines.flow.combine(sk.kajka.liekovka.data.AppDb.get(this@LiekovkaApp).moje().vsetky(), Plus.odomknute) { _, _ -> }
                .collect { Pripominac.naplanuj(this@LiekovkaApp) }
        }
        val req = PeriodicWorkRequestBuilder<AktualizaciaWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork("aktualizacia", ExistingPeriodicWorkPolicy.KEEP, req)
    }
}

/** Raz denne: nové dáta (výpadky, doplatky), zmeny pri liekoch v lekárničke, exspirácie. */
class AktualizaciaWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val repo = Repository.get(applicationContext)
        repo.pripravData()
        return when (repo.aktualizuj()) {
            is Aktualizacia.Chyba -> Result.retry()
            Aktualizacia.NieJeNastavena -> { repo.vyhodnotStiahnutia(); repo.skontrolujExspiracie(); Result.success() }
            else -> Result.success()
        }
    }
}
