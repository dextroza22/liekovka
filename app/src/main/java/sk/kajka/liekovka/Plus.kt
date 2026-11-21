package sk.kajka.liekovka

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Liekovka Plus: jednorazový nákup cez Google Play (produkt [PRODUKT] v Play Console, typ „jednorazový produkt“).
 * Stav nákupu sa ukladá aj lokálne, aby Plus fungovalo offline; pri každom spustení sa overí v Google Play.
 * Ak je BuildConfig.PLUS_AKTIVNE = false, všetko je odomknuté (napr. verzia 1.0 úplne zadarmo).
 */
object Plus {
    const val PRODUKT = "liekovka_plus"
    private const val PREFS = "plus"
    /** SHA-256 kódu na odomknutie (pre kontrolu Google Play a darčekové odomknutie); samotný kód v aplikácii nie je. */
    private const val KOD_HASH = "66c2fbae49ca97cf570f28875f7b6b485b05557daff17d3c52a71a02e0525010"

    private val _odomknute = MutableStateFlow(true)
    /** Má používateľ funkcie Plus? */
    val odomknute: StateFlow<Boolean> = _odomknute
    private val _cena = MutableStateFlow<String?>(null)
    /** Cena z Google Play v mene používateľa (napr. „2,99 €“), null ak nie je známa. */
    val cena: StateFlow<String?> = _cena
    private val _sprava = MutableStateFlow<String?>(null)
    /** Krátka správa po nákupe alebo chybe (zobrazí sa raz). */
    val sprava: StateFlow<String?> = _sprava
    fun spravaPrecitana() { _sprava.value = null }

    private var client: BillingClient? = null
    private var detaily: ProductDetails? = null
    private lateinit var app: Context

    fun init(ctx: Context) {
        app = ctx.applicationContext
        _odomknute.value = !BuildConfig.PLUS_AKTIVNE || kupene() || testovacie() || kodom()
        if (!BuildConfig.PLUS_AKTIVNE) return
        val c = BillingClient.newBuilder(app)
            .setListener(PurchasesUpdatedListener { r, nakupy -> poNakupe(r, nakupy) })
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
        client = c
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(r: BillingResult) {
                if (r.responseCode == BillingClient.BillingResponseCode.OK) { nacitajCenu(); overNakupy() }
            }
            override fun onBillingServiceDisconnected() {}
        })
    }

    private fun sp() = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun kupene() = sp().getBoolean("kupene", false)
    private fun kodom() = sp().getBoolean("kod", false)

    /** Odomkne Plus kódom. Vráti true, ak je kód správny. */
    fun skusKod(kod: String): Boolean {
        val h = java.security.MessageDigest.getInstance("SHA-256").digest(kod.trim().uppercase().toByteArray())
            .joinToString("") { "%02x".format(it) }
        if (h != KOD_HASH) return false
        sp().edit().putBoolean("kod", true).apply()
        _odomknute.value = true
        return true
    }
    /** Len v testovacej (debug) verzii: odomknutie bez Google Play na vyskúšanie. */
    fun testovacie() = BuildConfig.DEBUG && sp().getBoolean("test", false)
    fun nastavTestovacie(v: Boolean) {
        if (!BuildConfig.DEBUG) return
        sp().edit().putBoolean("test", v).apply()
        _odomknute.value = !BuildConfig.PLUS_AKTIVNE || kupene() || v || kodom()
    }
    private fun uloz(v: Boolean) {
        sp().edit().putBoolean("kupene", v).apply()
        _odomknute.value = !BuildConfig.PLUS_AKTIVNE || v || testovacie() || kodom()
    }

    private fun nacitajCenu() {
        val p = QueryProductDetailsParams.Product.newBuilder().setProductId(PRODUKT).setProductType(BillingClient.ProductType.INAPP).build()
        client?.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(listOf(p)).build()) { r, vysledok ->
            if (r.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            detaily = vysledok.productDetailsList.firstOrNull()
            _cena.value = detaily?.oneTimePurchaseOfferDetails?.formattedPrice
        }
    }

    /** Overí nákupy v Google Play (aj po preinštalovaní alebo na novom telefóne). */
    fun overNakupy(onHotovo: ((Boolean) -> Unit)? = null) {
        val c = client ?: run { onHotovo?.invoke(false); return }
        c.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { r, nakupy ->
            if (r.responseCode != BillingClient.BillingResponseCode.OK) { onHotovo?.invoke(false); return@queryPurchasesAsync }
            val moj = nakupy.filter { PRODUKT in it.products }
            moj.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach { potvrd(it) }
            val ma = moj.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            uloz(ma)   // aj vrátené peniaze (refund) nákup odoberú
            onHotovo?.invoke(ma)
        }
    }

    /** Otvorí platobné okno Google Play. Vráti false, ak Google Play nie je dostupné. */
    fun kup(activity: Activity): Boolean {
        val c = client ?: return false
        val d = detaily ?: run { nacitajCenu(); return false }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()))
            .build()
        return c.launchBillingFlow(activity, params).responseCode == BillingClient.BillingResponseCode.OK
    }

    private fun poNakupe(r: BillingResult, nakupy: List<Purchase>?) {
        when (r.responseCode) {
            BillingClient.BillingResponseCode.OK -> nakupy.orEmpty().filter { PRODUKT in it.products }.forEach { n ->
                when (n.purchaseState) {
                    Purchase.PurchaseState.PURCHASED -> { potvrd(n); uloz(true); _sprava.value = "Ďakujeme! Liekovka Plus je odomknutá." }
                    Purchase.PurchaseState.PENDING -> _sprava.value = "Platba čaká na dokončenie. Plus sa odomkne, keď ju Google Play potvrdí."
                    else -> {}
                }
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> { uloz(true); _sprava.value = "Liekovku Plus už máte, je odomknutá." }
            BillingClient.BillingResponseCode.USER_CANCELED -> {}
            else -> _sprava.value = "Nákup sa nepodaril. Skúste to znova neskôr."
        }
    }

    /** Google vyžaduje potvrdiť nákup do 3 dní, inak ho automaticky vráti. */
    private fun potvrd(n: Purchase) {
        if (n.isAcknowledged) return
        client?.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(n.purchaseToken).build()) {}
    }
}
