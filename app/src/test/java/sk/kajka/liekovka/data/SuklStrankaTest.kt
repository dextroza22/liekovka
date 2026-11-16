package sk.kajka.liekovka.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Adresy overené na sukl.sk 12. 11. 2026 (názov z registra → skutočná adresa stránky lieku). */
class SuklStrankaTest {
    private val overene = listOf(
        Triple("Helicid 10", "8723B", "helicid-10-8723b"),
        Triple("Ralbior 2,5 mg/1,25 mg", "2841E", "ralbior-25-mg-125-mg-2841e"),
        Triple("Beklometazón/Formoterol Genetic 200 mikrogramov/6 mikrogramov/dávka inhalačný roztok v tlakovom obale", "0328F",
            "beklometazon-formoterol-genetic-200-mikrogramov-6-mikrogramov-davka-inhalacny-roztok-v-tlakovom-obale-0328f"),
        Triple("PEROXID VODÍKA 3 % Fagron", "36727", "peroxid-vodika-3-fagron-36727"),
        Triple("CLEXANE FORTE 15000 IU (150 mg)/1 ml", "8666C", "clexane-forte-15000-iu-150-mg-1-ml-8666c"),
        Triple("Serynox 50% / 50% stlačený medicinálny plyn", "6593D", "serynox-50-50-stlaceny-medicinalny-plyn-6593d"),
        Triple("EMEND 125 mg tvrdé kapsuly, EMEND 80 mg tvrdé kapsuly", "40852", "emend-125-mg-tvrde-kapsuly-emend-80-mg-tvrde-kapsuly-40852"),
        Triple("Keppra 100 mg/ml perorálny roztok", "40998", "keppra-100-mg-ml-peroralny-roztok-40998"),
        Triple("Hartmann's Solution Fresenius Kabi", "34564", "hartmanns-solution-fresenius-kabi-34564"),
        Triple("MINIRIN Melt 120 µg", "92650", "minirin-melt-120-g-92650"),
        Triple("Plasmalyte + Glucose 5%", "0831F", "plasmalyte-glucose-5-0831f"),
        Triple("TANTUM VERDE Orange & Honey", "2876E", "tantum-verde-orange-honey-2876e"),
        Triple("Holoclar 79 000 – 316 000 buniek/cm2 náhrada živého tkaniva", "3265B", "holoclar-79-000-316-000-buniek-cm2-nahrada-ziveho-tkaniva-3265b"),
        Triple("Co-Amlessa 4 mg/10 mg/1,25 mg tablety", "7930A", "co-amlessa-4-mg-10-mg-125-mg-tablety-7930a"),
        Triple("Levocetirizin Dr.Max 5 mg filmom obalené tablety", "2558F", "levocetirizin-dr.max-5-mg-filmom-obalene-tablety-2558f"),
    )

    @Test fun adresy() {
        overene.forEach { (nazov, kod, slug) -> assertEquals(nazov, "https://www.sukl.sk/$slug", suklStranka(nazov, kod)) }
    }
}
