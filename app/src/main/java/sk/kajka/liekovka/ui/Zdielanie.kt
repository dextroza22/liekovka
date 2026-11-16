package sk.kajka.liekovka.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import sk.kajka.liekovka.data.ZoznamLiekov
import java.io.File

/** Zdieľanie zoznamu liekov cez systémovú ponuku (e-mail, správy, tlač…). */
object Zdielanie {
    fun text(ctx: Context, z: ZoznamLiekov) {
        val i = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, z.nadpis)
            .putExtra(Intent.EXTRA_TEXT, z.akoText())
        ctx.startActivity(Intent.createChooser(i, "Zdieľať zoznam liekov"))
    }

    fun pdf(ctx: Context, z: ZoznamLiekov) {
        val dir = File(ctx.cacheDir, "zdielanie").apply { mkdirs() }
        val subor = File(dir, "zoznam-liekov-${bezDiakritiky(z.meno)}.pdf")
        vytvorPdf(z, subor)
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.subory", subor)
        val i = Intent(Intent.ACTION_SEND)
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_SUBJECT, z.nadpis)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, "Zdieľať zoznam liekov (PDF)"))
    }

    private fun bezDiakritiky(s: String) =
        sk.kajka.liekovka.data.norm(s).replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "profil" }

    /** A4 (595 × 842 bodov), okraj 48 bodov; dlhý zoznam pokračuje na ďalšej strane. */
    fun vytvorPdf(z: ZoznamLiekov, subor: File) {
        val sirka = 595; val vyska = 842; val okraj = 48f
        val obsah = sirka - 2 * okraj
        val zelena = Color.rgb(0x1F, 0x6F, 0x5C)
        val nadpis = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 20f; typeface = Typeface.DEFAULT_BOLD; color = zelena }
        val sivy = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.5f; color = Color.rgb(0x55, 0x60, 0x5D) }
        val tucny = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12.5f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK }
        val bezny = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = Color.rgb(0x18, 0x1D, 0x1B) }
        val ciara = Paint().apply { color = Color.rgb(0xD3, 0xDB, 0xD8); strokeWidth = 0.8f }

        val doc = PdfDocument()
        var cislo = 1
        var strana = doc.startPage(PdfDocument.PageInfo.Builder(sirka, vyska, cislo).create())
        var y = okraj

        fun layout(t: String, p: TextPaint) =
            StaticLayout.Builder.obtain(t, 0, t.length, p, obsah.toInt()).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()

        fun kresli(l: StaticLayout) {
            strana.canvas.save(); strana.canvas.translate(okraj, y); l.draw(strana.canvas); strana.canvas.restore()
            y += l.height
        }

        fun novaStrana() {
            doc.finishPage(strana)
            cislo++
            strana = doc.startPage(PdfDocument.PageInfo.Builder(sirka, vyska, cislo).create())
            y = okraj
        }

        kresli(layout(z.nadpis, nadpis)); y += 4
        kresli(layout(z.podnadpis, sivy)); y += 14
        z.polozky.forEachIndexed { i, p ->
            val hlavny = layout("${i + 1}. ${p.nazov}" + if (p.doplnok.isNotBlank()) "  ·  ${p.doplnok}" else "", tucny)
            val detaily = z.riadky(p).map { layout(it, bezny) }
            val spolu = hlavny.height + detaily.sumOf { it.height } + 20
            if (y + spolu > vyska - okraj - 30) novaStrana()
            strana.canvas.drawLine(okraj, y, sirka - okraj, y, ciara); y += 8
            kresli(hlavny)
            detaily.forEach { y += 2; kresli(it) }
            y += 10
        }
        y += 8
        if (y > vyska - okraj - 30) novaStrana()
        kresli(layout(ZoznamLiekov.PATA, sivy))
        doc.finishPage(strana)
        subor.outputStream().use { doc.writeTo(it) }
        doc.close()
    }
}
