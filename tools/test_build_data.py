"""Testy dátovej pipeline na umelých súboroch vo formáte oficiálnych zdrojov.

Spustenie:  python3 -m unittest tools/test_build_data.py
"""
import datetime as dt
import gzip
import io
import json
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

import openpyxl

sys.path.insert(0, str(Path(__file__).parent))
import build_data as bd  # noqa: E402


def xlsx(rows, sheet="List1"):
    wb = openpyxl.Workbook()
    ws = wb.active
    ws.title = sheet
    for r in rows:
        ws.append(r)
    b = io.BytesIO()
    wb.save(b)
    return b.getvalue()


def register_zip(n_extra=0):
    head = ["ŠÚKL kód", "Názov", "Doplnok", "ATC kód", "atc_nazov", "Výdaj", "Názov držiteľa",
            "Registračné číslo", "SPC", "PIL"]
    rows = [head,
            ["8723B", "Helicid 10", "cps dur 28x10 mg", "A02BC01", "omeprazol", "R", "Zentiva", "09/0001/96-S",
             "504346", "PIL00504045.docx"],
            ["1111A", "Omeprazol Teva 10", "cps 28x10 mg", "A02BC01", "omeprazol", "R", "Teva", "09/0002/05-S", "", ""],
            ["2222C", "Paralen 500", "tbl 24x500 mg", "N02BE01", "paracetamol", "F", "Zentiva", "07/0001/90-S", "", ""],
            ["9999X", "Rádiofarmakum", "inj", "V09AX", "x", "R", "Y", "", "", ""]]
    rows += [[f"{i:05d}", f"Liek {i}", "tbl", "C09AA01", "kaptopril", "R", "Z", "", "", ""] for i in range(n_extra)]
    z = io.BytesIO()
    with zipfile.ZipFile(z, "w") as zf:
        zf.writestr("zoznam_liekov.xlsx", xlsx(rows, "Export_lieky_R"))
    return z.getvalue()


def kategorizacia_xlsx(n_extra=0):
    h1 = ["Kód", "", "", "", "Typ lieku", "Názov", "Doplnok", "Drž.", "Štát", "SDL", "Konečná c.", "ÚZP", "SÚ", "DOP"]
    h2 = [""] * 14
    rows = [h1, h2,
            ["A02BC01", None, None, None, None, "Omeprazol", "p.o. 10 mg (28 ks)", None, None, None, None, None, None, None],
            ["8723B", "X", "", "", "1G", "Helicid 10", "cps dur 28x10 mg", "ZNT", "SK", 14, 3.77, 0.62, "S", 3.15],
            ["1111A", "X", "", "", "1G", "Omeprazol Teva 10", "cps 28x10 mg", "TEV", "IL", 14, 2.50, 0.62, "S", 1.88],
            ["C09AA01", None, None, None, None, "Kaptopril", "p.o. 25 mg", None, None, None, None, None, None, None]]
    rows += [[f"{i:05d}", "", "", "", "1G", f"Liek {i}", "tbl", "Z", "SK", 1, 5.0, 4.0, "S", 1.0] for i in range(n_extra)]
    return xlsx(rows)


VYPADKY = ("Kód;Názov;Predmet;Účinnosť;Podanie\n"
           "8723B;Helicid 10;R;2026-11-01;2026-10-30\n"
           "1111A;Omeprazol Teva;R;2026-10-01;2026-09-30\n"
           "1111A;Omeprazol Teva;O;2026-11-05;2026-11-04\n"
           "2222C;Paralen;Z;2026-12-31;2026-11-01\n").encode("utf-8-sig")


class PipelineTest(unittest.TestCase):
    def setUp(self):
        self._min = (bd.MIN_REGISTER, bd.MIN_KATEGORIZACIA)
        bd.MIN_REGISTER, bd.MIN_KATEGORIZACIA = 3, 2

    def tearDown(self):
        bd.MIN_REGISTER, bd.MIN_KATEGORIZACIA = self._min

    def test_register(self):
        r = bd.parse_register(register_zip())
        self.assertEqual(r["8723B"]["nazov"], "Helicid 10")
        self.assertEqual(r["8723B"]["pil"], "https://www.sukl.sk/buxus/docs/download/PIL00504045.docx")
        self.assertEqual(r["8723B"]["spc"], "https://www.sukl.sk/buxus/docs/download/SPC00504346")
        self.assertEqual(r["2222C"]["vydaj"], "F")

    def test_register_too_small(self):
        bd.MIN_REGISTER = 100
        with self.assertRaises(SystemExit):
            bd.parse_register(register_zip())

    def test_kategorizacia(self):
        skupiny, lieky = bd.parse_kategorizacia(kategorizacia_xlsx())
        self.assertEqual(skupiny, [["A02BC01", "Omeprazol", "p.o. 10 mg (28 ks)"], ["C09AA01", "Kaptopril", "p.o. 25 mg"]])
        self.assertEqual(lieky["1111A"]["doplatok"], 1.88)
        self.assertEqual(lieky["8723B"]["skupina"], 0)

    def test_kategorizacia_changed_structure(self):
        bad = openpyxl.load_workbook(io.BytesIO(kategorizacia_xlsx()))
        bad.active["K1"] = "Iný stĺpec"
        b = io.BytesIO()
        bad.save(b)
        with self.assertRaises(SystemExit):
            bd.parse_kategorizacia(b.getvalue())

    def test_vypadky(self):
        v = bd.parse_vypadky(VYPADKY, dt.date(2026, 11, 12))
        self.assertEqual(v["8723B"], ["R", "2026-11-01"])      # prerušené
        self.assertNotIn("1111A", v)                           # obnovené
        self.assertEqual(v["2222C"], ["PZ", "2026-12-31"])     # ohlásené skončenie

    def test_zmeny_doplatkov(self):
        _, teraz = bd.parse_kategorizacia(kategorizacia_xlsx())
        buduce = {k: dict(v) for k, v in teraz.items()}
        buduce["8723B"]["doplatok"] = 4.20       # zdražie
        del buduce["1111A"]                      # vyradený zo zoznamu
        z = bd.zmeny_doplatkov(teraz, buduce)
        self.assertEqual(z, {"8723B": 4.20, "1111A": None})
        reg = bd.parse_register(register_zip())
        sk, kat = bd.parse_kategorizacia(kategorizacia_xlsx())
        b = bd.build(reg, sk, kat, {}, "2026-11-01", z, "2026-12-01")
        self.assertEqual(b["buduce"], {"od": "2026-12-01", "doplatky": {"8723B": 4.20, "1111A": None}})
        self.assertNotIn("buduce", bd.build(reg, sk, kat, {}, "2026-11-01"))

    def test_drzitelia(self):
        g = xlsx([["Časť G - Zoznam skratiek výrobcov"], ["Skratka", "Názov"], ["ZNT", "Zentiva, k.s."], ["TEV", "Teva B.V."]])
        d = bd.parse_drzitelia(g)
        self.assertEqual(d, {"ZNT": "Zentiva, k.s.", "TEV": "Teva B.V."})
        reg = bd.parse_register(register_zip())
        sk, kat = bd.parse_kategorizacia(kategorizacia_xlsx())
        b = bd.build(reg, sk, kat, {}, "2026-11-01", drzitelia={"Zentiva": "Zentiva, k.s."})
        i = b["polia"].index("drzitel")
        self.assertEqual(next(x for x in b["lieky"] if x[0] == "8723B")[i], "Zentiva, k.s.")   # kód nahradený menom
        self.assertEqual(next(x for x in b["lieky"] if x[0] == "1111A")[i], "Teva")            # neznámy kód zostane

    def test_web_stranky(self):
        h = bd.pravny_text_html("Zásady\nÚčinné od 1. 1. 2027\n\n## Kto\n{MENO}, kontakt: {EMAIL}.\n\nDruhý <odsek>.", "Ján Novák", "j@x.sk")
        self.assertIn("<h1>Zásady</h1>", h)
        self.assertIn("<h2>Kto</h2><p>Ján Novák, kontakt: j@x.sk.</p><p>Druhý &lt;odsek&gt;.</p>", h)
        self.assertNotIn("{", h.split("</style>")[1])
        with tempfile.TemporaryDirectory() as d:
            bd.write_web(Path(d))
            for n in ("sukromie.html", "podmienky.html", "index.html"):
                self.assertTrue((Path(d) / n).exists(), n)
            self.assertIn(bd.gradle_hodnota("KONTAKT_EMAIL"), (Path(d) / "sukromie.html").read_text(encoding="utf-8"))
            self.assertTrue(bd.gradle_hodnota("KONTAKT_EMAIL"))

    def test_end_to_end(self):
        reg = bd.parse_register(register_zip())
        sk, kat = bd.parse_kategorizacia(kategorizacia_xlsx())
        vyp = bd.parse_vypadky(VYPADKY, dt.date(2026, 11, 12))
        bundle = bd.build(reg, sk, kat, vyp, "2026-11-01")
        kody = [x[0] for x in bundle["lieky"]]
        self.assertNotIn("9999X", kody)                        # rádiofarmaká vynechané
        helicid = next(x for x in bundle["lieky"] if x[0] == "8723B")
        self.assertEqual(helicid[bundle["polia"].index("doplatok")], 3.15)
        paralen = next(x for x in bundle["lieky"] if x[0] == "2222C")
        self.assertEqual(paralen[bundle["polia"].index("skupina")], -1)
        with tempfile.TemporaryDirectory() as d:
            bd.write(bundle, Path(d), assets=False)
            data = json.loads(gzip.decompress((Path(d) / "lieky.json.gz").read_bytes()))
            meta = json.loads((Path(d) / "meta.json").read_text())
            self.assertEqual(meta["pocetLiekov"], len(data["lieky"]))
            self.assertEqual(len(meta["sha256"]), 64)


if __name__ == "__main__":
    unittest.main()


# ---------------------------------------------------------------- stiahnutia z trhu
# Úryvky vychádzajú z doslovného znenia skutočných oznamov ŠÚKL (Rowatinex, Zoely, Tyverb);
# tabuľka je umelá vzorka vo formáte oznamu o viacerých liekoch.

OZNAM_TEXT = """<html><body><h1>Stiahnutie lieku Rowatinex z trhu</h1>
<p>Liek sa sťahuje II. triedou urgentnosti z úrovne distribučných spoločností, lekární a zdravotníckych zariadení</p>
<p>Štátny ústav pre kontrolu liečiv informuje o stiahnutí lieku<strong>Rowatinex, gtt por 1x10 ml</strong>, <strong>ŠÚKL kód: 26808</strong> z trhu.</p>
<p>Stiahnutie z trhu sa vzťahuje na šarže číslo 8706L s dátumom exspirácie apríl 2027 a číslo 8768L s dátumom exspirácie september 2027.</p>
<p>Dôvodom stiahnutia dotknutých šarží lieku Rowatinex, gtt por 1x10 ml (liek. skl. jantár) je zistený nedostatok v kvalite lieku - výsledok mimo limitu špecifikácie lieku v parametri peroxidové číslo.</p>
<p>ŠÚKL (Štátny ústav pre kontrolu liečiv)</p></body></html>"""

OZNAM_TYVERB = """<p>Štátny ústav pre kontrolu liečiv informuje o stiahnutí lieku Tyverb 250 mg filmom obalené tablety, ŠÚKL kód: 07339, z trhu.</p>
<p>Stiahnutie lieku z trhu sa vzťahuje na jednu šaržu s číslom RD6172 s dátumom exspirácie február 2029.</p>"""

OZNAM_TABULKA = """<p>Lieky sa sťahujú II. triedou urgentnosti</p><p>Stiahnutie z trhu sa vzťahuje na nasledujúce šarže:</p>
<table><tr><th>Liek</th><th>Kód ŠÚKL</th><th>Číslo šarže</th><th>Exspirácia</th></tr>
<tr><td>Liek A</td><td>1234A</td><td>X100</td><td>05/2027</td></tr>
<tr><td></td><td></td><td>X101</td><td>06/2027</td></tr>
<tr><td>Liek B</td><td>5678B</td><td>všetky šarže v rámci exspirácie</td><td></td></tr></table>
<p>Dôvodom stiahnutia dotknutých šarží liekov je zistený nedostatok vo výrobe liekov – nesúlad so správnou výrobnou praxou.</p>"""

RSS = b"""<?xml version="1.0" encoding="utf-8"?><rss version="2.0"><channel><title>t</title><description></description>
<item><title>Stiahnutie lieku Rowatinex z trhu</title><link>https://www.sukl.sk/a</link><guid>https://www.sukl.sk/a</guid><pubDate>Tue, 18 Aug 2026 14:42:46 +0200</pubDate></item>
<item><title>Stiahnutie lieku Rowatinex z trhu</title><link>https://www.sukl.sk/a</link><guid>https://www.sukl.sk/a</guid><pubDate>Tue, 18 Aug 2026 14:42:46 +0200</pubDate></item>
<item><title>Stiahnutie lieku Zoely z trhu</title><link>https://www.sukl.sk/b</link><pubDate>Thu, 13 Aug 2026 15:08:12 +0200</pubDate></item>
</channel></rss>"""


class StiahnutiaTest(unittest.TestCase):
    def test_rss_bez_duplicit(self):
        r = bd.parse_rss(RSS)
        self.assertEqual([x["url"] for x in r], ["https://www.sukl.sk/a", "https://www.sukl.sk/b"])
        self.assertEqual(r[0]["datum"], "2026-08-18")

    def test_text(self):
        o = bd.parse_oznam(OZNAM_TEXT)
        self.assertEqual(o["lieky"], [{"kod": "26808", "sarze": [["8706L", "apríl 2027"], ["8768L", "september 2027"]], "vsetky": False}])
        self.assertEqual(o["trieda"], "II")
        self.assertTrue(o["dovod"].startswith("Dôvodom stiahnutia dotknutých šarží lieku Rowatinex"))
        self.assertTrue(o["dovod"].endswith("peroxidové číslo."))

    def test_text_jedna_sarza(self):
        o = bd.parse_oznam(OZNAM_TYVERB)
        self.assertEqual(o["lieky"], [{"kod": "07339", "sarze": [["RD6172", "február 2029"]], "vsetky": False}])

    def test_tabulka(self):
        o = bd.parse_oznam(OZNAM_TABULKA)
        self.assertEqual(o["lieky"], [
            {"kod": "1234A", "sarze": [["X100", "05/2027"], ["X101", "06/2027"]], "vsetky": False},
            {"kod": "5678B", "sarze": [], "vsetky": True},
        ])

    def test_tabulka_len_kody(self):
        # formát oznamu Egoropal: tabuľka Kód ŠÚKL | Názov lieku | Doplnok, šarže v texte
        oznam = """<p>Štátny ústav pre kontrolu liečiv (ŠÚKL) informuje o stiahnutí lieku Egoropal z trhu.</p>
<p>Stiahnutie lieku z trhu sa vzťahuje na všetky šarže lieku.</p>
<table><tr><th>Kód ŠÚKL</th><th>Názov lieku</th><th>Doplnok</th></tr>
<tr><td>0683E</td><td>Egoropal 50 mg</td><td>sus iph1x50 mg</td></tr>
<tr><td>0684E</td><td>Egoropal 75 mg</td><td>sus iph 1x75 mg</td></tr></table>"""
        o = bd.parse_oznam(oznam)
        self.assertEqual(o["lieky"], [{"kod": "0683E", "sarze": [], "vsetky": True}, {"kod": "0684E", "sarze": [], "vsetky": True}])

    def test_dve_sarze_jeden_datum(self):
        # doslovné znenie oznamu Nitrofurantoin Pharmevid
        o = bd.parse_oznam("<p>Štátny ústav pre kontrolu liečiv (ŠÚKL) informuje o stiahnutí lieku Nitrofurantoin Pharmevid 100 mg, "
                           "ŠÚKL kód: 8087E z trhu.</p><p>Stiahnutie lieku z trhu sa vzťahuje na šarže s číslom 8250243 a 8250235 "
                           "s dátumom exspirácie marec 2028.</p>")
        self.assertEqual(o["lieky"], [{"kod": "8087E", "sarze": [["8250243", "marec 2028"], ["8250235", "marec 2028"]], "vsetky": False}])

    def test_datum_cislami(self):
        # doslovné znenie oznamu LIBTAYO (dve medzery za "číslom")
        o = bd.parse_oznam("<p>ŠÚKL kód: 2200D z trhu.</p><p>Stiahnutie lieku sa vzťahuje na šaržu s číslom  8496700006 "
                           "s dátumom exspirácie 31. 7. 2027.</p>")
        self.assertEqual(o["lieky"], [{"kod": "2200D", "sarze": [["8496700006", "31. 7. 2027"]], "vsetky": False}])

    def test_bez_kodu(self):
        self.assertEqual(bd.parse_oznam("<p>Oznam bez údajov.</p>")["lieky"], [])
