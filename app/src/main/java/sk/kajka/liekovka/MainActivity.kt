package sk.kajka.liekovka

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import sk.kajka.liekovka.data.Repository
import sk.kajka.liekovka.ui.App
import sk.kajka.liekovka.ui.LiekovkaTheme
import sk.kajka.liekovka.ui.jeTmava

class MainActivity : ComponentActivity() {
    private val karta = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        karta.value = intent?.getStringExtra(Notif.EXTRA_TAB)
        val prefs = Repository.get(this).prefs
        setContent {
            val tema by prefs.tema.collectAsState()
            val pismo by prefs.pismo.collectAsState()
            val tmava = jeTmava(tema)
            // ikony stavového riadku podľa zvolenej témy, nie len podľa systému
            LaunchedEffect(tmava) {
                val styl = if (tmava) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = styl, navigationBarStyle = styl)
            }
            LiekovkaTheme(tema, pismo) {
                Surface(Modifier.fillMaxSize()) {
                    App(karta.value) { karta.value = null }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        karta.value = intent.getStringExtra(Notif.EXTRA_TAB)
    }
}
