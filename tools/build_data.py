#!/usr/bin/env python3
"""Liekovka: zostaví dátový balík pre aplikáciu z oficiálnych verejných zdrojov.

Zdroje:
  1. ŠÚKL, register liekov:  https://www.sukl.sk/verejne/Zoznam_liekov/zoznam_liekov_aktualny.zip
     (zoznam_liekov.xlsx, list Export_lieky_R)
  2. MZ SR, zoznam kategorizovaných liekov (ceny, úhrady, doplatky, referenčné skupiny):
     https://www.health.gov.sk/odbornik/kategorizacia-a-cenotvorba/kategorizacia-liekov/zoznam-kategorizovanych-liekov/liekyRRRRMM/
  3. ŠÚKL, hlásenia o prerušení/obnovení dodávok (CSV):
     https://portal.sukl.sk/PreruseniePublic/?act=PrerusenieOznList&export=csv
  4. ŠÚKL, oznamy o stiahnutí liekov z trhu (RSS + stránka každého oznamu):
     https://www.sukl.sk/sk/rss?page_id=1355&pid=208&days=365

Výstup (adresár --out, predvolene dist/):
  lieky.json.gz  dátový balík pre aplikáciu
  meta.json      verzia a kontrolný súčet (aplikácia podľa neho zistí, či sú nové dáta)
Voliteľne --assets skopíruje dáta aj do app/src/main/assets/lieky.dat (počiatočné dáta v APK;
názov .dat preto, lebo súbor .gz by nástroje Androidu pri zostavení rozbalili).

Použitie:
  pip install openpyxl
  python3 tools/build_data.py                          # stiahne všetko
  python3 tools/build_data.py --register-zip Z.zip --kategorizacia-xlsx K.xlsx --vypadky-csv V.csv
"""
from __future__ import annotations

import argparse
import csv
import datetime as dt
import gzip
import hashlib
import html
import io
import json
import re
import shutil
import sys
import time
import unicodedata
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
import zipfile
from email.utils import parsedate_to_datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
UA = {"User-Agent": "Liekovka data builder (+https://github.com/)"}

REGISTER_URLS = [
    "https://www.sukl.sk/verejne/Zoznam_liekov/zoznam_liekov_aktualny.zip",
    "https://sukl.sk/verejne/Zoznam_liekov/zoznam_liekov_aktualny.zip",
]
MZ_LIST = ("https://www.health.gov.sk/odbornik/kategorizacia-a-cenotvorba/kategorizacia-liekov/"
           "zoznam-kategorizovanych-liekov/lieky{ym}/")
VYPADKY_URL = "https://portal.sukl.sk/PreruseniePublic/?act=PrerusenieOznList&export=csv"
STIAHNUTIA_RSS = "https://www.sukl.sk/sk/rss?page_id=1355&pid=208&days=365"
SUKL_DOC = "https://www.sukl.sk/buxus/docs/download/{prefix}{id}"
SUKL_SEARCH = "https://www.sukl.sk/rozsirene-vyhladavanie?search_type=drugs"

MIN_REGISTER = 20000      # ochrana pred poškodeným/čiastočným súborom
MIN_KATEGORIZACIA = 1000
FORMAT_VERSION = 1


# ---------------------------------------------------------------- pomocné

def log(*a):
    print(*a, file=sys.stderr, flush=True)


def txt(v) -> str:
    return re.sub(r"\s+", " ", str(v if v is not None else "")).strip()


def num(v):
    if v is None or v == "":
        return None
    try:
        return round(float(str(v).replace(",", ".").replace(" ", "")), 2)
    except ValueError:
        return None


def norm(s: str) -> str:
    """Malé písmená bez diakritiky, na hľadanie a porovnávanie hlavičiek."""
    s = unicodedata.normalize("NFKD", s or "")
    return "".join(c for c in s if not unicodedata.combining(c)).lower().strip()


def download(url: str, attempts: int = 8, timeout: int = 120) -> bytes:
    """Stiahne súbor; pri prerušení pokračuje cez HTTP Range."""
    data, total = b"", None
    for k in range(1, attempts + 1):
        headers = dict(UA)
        if data:
            headers["Range"] = f"bytes={len(data)}-"
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=timeout) as r:
                if data and r.status != 206:
                    data = b""
                cl = r.headers.get("Content-Length")
                if total is None or r.status == 200:
                    total = len(data) + int(cl) if cl else None
                data += r.read()
            if total is None or len(data) >= total:
                return data
        except urllib.error.HTTPError as e:
            if e.code == 404:
                raise
            log(f"  {url}: pokus {k}: HTTP {e.code}")
        except Exception as e:  # noqa: BLE001  (sieť je nespoľahlivá, skúšame znova)
            log(f"  {url}: pokus {k}: {e}")
        time.sleep(min(30, 2 * k))
    raise RuntimeError(f"Nepodarilo sa stiahnuť {url}")


def xlsx_rows(blob: bytes, sheet=None):
    import openpyxl  # noqa: PLC0415
    wb = openpyxl.load_workbook(io.BytesIO(blob), read_only=True, data_only=True)
    if sheet and sheet in wb.sheetnames:
        ws = wb[sheet]
    else:
        ws = wb.worksheets[0]
    return ws.iter_rows(values_only=True)


def find_col(head: list[str], *candidates: str, required=True):
    """Index stĺpca podľa začiatku názvu hlavičky (bez diakritiky)."""
    nh = [norm(h) for h in head]
    for cand in candidates:
        c = norm(cand)
        for i, h in enumerate(nh):
            if h == c:
                return i
        for i, h in enumerate(nh):
            if h.startswith(c):
                return i
    if required:
        raise SystemExit(f"Chýba stĺpec {candidates[0]!r}. Hlavička súboru: {head}")
    return None


# ---------------------------------------------------------------- 1. register ŠÚKL

def doc_url(value, prefix: str):
    """Z hodnoty v registri (číslo dokumentu alebo celá adresa) spraví odkaz na dokument ŠÚKL."""
    v = txt(value)
    if not v:
        return ""
    if v.lower().startswith("http"):
        return v
    m = re.search(r"(\d{4,})(\.(pdf|docx?))?", v, re.I)
    if not m:
        return ""
    ext = m.group(2) or ""
    return SUKL_DOC.format(prefix=prefix, id=m.group(1).zfill(8) + ext)


def parse_register(blob: bytes) -> dict:
    z = zipfile.ZipFile(io.BytesIO(blob))
    xname = next((n for n in z.namelist() if n.lower().endswith(".xlsx")), None)
    if not xname:
        raise SystemExit(f"V archíve registra nie je .xlsx: {z.namelist()}")
    rows = xlsx_rows(z.read(xname), "Export_lieky_R")
    head = [txt(h) for h in next(rows)]
    log(f"  register: {xname}, stĺpce: {head}")
    c_kod = find_col(head, "ŠÚKL kód", "Kód ŠÚKL", "Kod")
    c_nazov = find_col(head, "Názov")
    c_dopl = find_col(head, "Doplnok", required=False)
    c_atc = find_col(head, "ATC kód", "ATC")
    c_atcn = find_col(head, "atc_nazov", "Názov ATC", "ATC názov", required=False)
    c_vyd = find_col(head, "Výdaj", required=False)
    c_drz = find_col(head, "Názov držiteľa", "Držiteľ", "Kód držiteľa", required=False)
    c_reg = find_col(head, "Registračné číslo", "Reg. číslo", "Reg.číslo", required=False)
    c_spc = find_col(head, "SPC", required=False)
    c_pil = find_col(head, "PIL", "Písomná informácia", required=False)
    if c_spc is None or c_pil is None:
        log("  register: stĺpce SPC/PIL nenájdené, aplikácia použije vyhľadávanie na sukl.sk")

    def g(r, i):
        return txt(r[i]) if i is not None and i < len(r) else ""

    out = {}
    for r in rows:
        if not r:
            continue
        kod, nazov = g(r, c_kod), g(r, c_nazov)
        if not kod or not nazov:
            continue
        out[kod] = {
            "nazov": nazov,
            "doplnok": g(r, c_dopl),
            "atc": g(r, c_atc),
            "latka": g(r, c_atcn),
            "vydaj": g(r, c_vyd),
            "drzitel": g(r, c_drz),
            "reg": g(r, c_reg),
            "spc": doc_url(r[c_spc], "SPC") if c_spc is not None and c_spc < len(r) else "",
            "pil": doc_url(r[c_pil], "PIL") if c_pil is not None and c_pil < len(r) else "",
        }
    if len(out) < MIN_REGISTER:
        raise SystemExit(f"Register má podozrivo málo liekov ({len(out)}), končím bez zmeny dát.")
    return out


# ---------------------------------------------------------------- 2. kategorizácia MZ SR

_mz_stranky: dict[str, str] = {}


def mz_month_url(ym: str, vzor: str = r"^Zoznam liekov\b") -> str | None:
    """Nájde na podstránke mesiaca odkaz na súbor podľa textu odkazu (predvolene 'Zoznam liekov', časť A)."""
    if ym not in _mz_stranky:
        _mz_stranky[ym] = download(MZ_LIST.format(ym=ym), attempts=4, timeout=90).decode("utf-8", "replace")
    for href, label in re.findall(r'<a\s[^>]*href="([^"]+)"[^>]*>(.*?)</a>', _mz_stranky[ym], re.S):
        label = txt(html.unescape(re.sub(r"<[^>]+>", "", label)))
        if re.match(vzor, label, re.I) and "cloud.health.gov.sk" in href:
            return html.unescape(href)
    return None


def parse_drzitelia(blob: bytes) -> dict:
    """Časť G zoznamu MZ SR: skratka držiteľa registrácie → názov (napr. KRK-2 → KRKA, d.d., Novo mesto)."""
    out = {}
    for r in xlsx_rows(blob):
        if not r or len(r) < 2:
            continue
        k, v = txt(r[0]), txt(r[1])
        if k and v and norm(k) not in ("skratka", "kod") and not k.lower().startswith("časť") and len(k) <= 20:
            out[k] = v
    return out


def fetch_drzitelia(kat_od: str) -> dict:
    """Mená držiteľov z časti G zoznamu platného v danom mesiaci; pri chybe prázdny slovník (zostanú kódy)."""
    try:
        url = mz_month_url(kat_od[:7].replace("-", ""), r"^Časť G\b")
        if not url:
            log("  časť G (držitelia) sa nenašla, zostanú kódy")
            return {}
        d = parse_drzitelia(download(url, attempts=4))
        log(f"  časť G: {len(d)} držiteľov")
        return d
    except Exception as e:  # noqa: BLE001
        log(f"  časť G preskočená: {e}")
        return {}


def fetch_buduca(today: dt.date) -> tuple[bytes, str] | None:
    """Zoznam na budúci mesiac, ak ho MZ SR už zverejnilo (zvyčajne v druhej polovici mesiaca)."""
    nxt = (today.replace(day=1) + dt.timedelta(days=32)).replace(day=1)
    try:
        url = mz_month_url(f"{nxt.year}{nxt.month:02d}")
    except Exception as e:  # noqa: BLE001  (podstránka ešte nemusí existovať)
        log(f"  budúci mesiac {nxt:%Y%m}: {e}")
        return None
    if not url:
        log(f"  budúci mesiac {nxt:%Y%m}: zoznam ešte nie je zverejnený")
        return None
    log(f"  budúci mesiac {nxt:%Y%m}: {url}")
    return download(url), nxt.isoformat()


def zmeny_doplatkov(teraz: dict, buduce: dict) -> dict:
    """Lieky, ktorým sa od budúceho mesiaca zmení doplatok: kód → nový doplatok (None = vyradený zo zoznamu)."""
    out = {}
    for kod, k in teraz.items():
        b = buduce.get(kod)
        if b is None:
            out[kod] = None
        elif k["doplatok"] is not None and b["doplatok"] is not None and abs(k["doplatok"] - b["doplatok"]) >= 0.005:
            out[kod] = b["doplatok"]
    return out


def fetch_kategorizacia(today: dt.date) -> tuple[bytes, str]:
    first = today.replace(day=1)
    nxt = (first + dt.timedelta(days=32)).replace(day=1)
    # platný je zoznam aktuálneho mesiaca; ak ešte nie je, skúsime predchádzajúci
    prev = (first - dt.timedelta(days=1)).replace(day=1)
    for d in (first, prev):
        ym = f"{d.year}{d.month:02d}"
        try:
            url = mz_month_url(ym)
        except urllib.error.HTTPError:
            url = None
        if url:
            log(f"  kategorizácia {ym}: {url}")
            return download(url), d.isoformat()
    raise SystemExit(f"Zoznam kategorizovaných liekov sa nenašiel (skúšané {first:%Y%m}, {prev:%Y%m}; ďalší {nxt:%Y%m}).")


def parse_kategorizacia(blob: bytes) -> tuple[list, dict]:
    """Vráti (skupiny, lieky). Skupina = referenčná skupina MZ SR: rovnaká látka, cesta podania, sila, balenie.

    Formát časti A: dva riadky hlavičky, potom striedavo riadok skupiny (ATC, liečivo, forma)
    a riadky liekov. Pozície stĺpcov sa overujú podľa názvov hlavičky.
    """
    rows = xlsx_rows(blob)
    h1, h2 = next(rows), next(rows)
    head = [txt(a or b) for a, b in zip(h1, h2)]
    expect = {4: "Typ lieku", 5: "Názov", 6: "Doplnok", 10: "Konečná c.", 11: "ÚZP", 13: "DOP"}
    for i, name in expect.items():
        if i >= len(head) or not norm(head[i]).startswith(norm(name)[:4]):
            raise SystemExit(f"Zmenila sa štruktúra súboru MZ SR: na pozícii {i} čakám {name!r}, hlavička: {head}")
    skupiny, lieky, cur = [], {}, None
    for r in rows:
        if not r or len(r) < 14:
            continue
        a0 = txt(r[0])
        if a0 and r[5] and r[10] is None and r[4] is None and re.fullmatch(r"[A-Z]\d\d[A-Z0-9]*", a0):
            cur = len(skupiny)
            skupiny.append([a0, txt(r[5]), txt(r[6])])
        elif cur is not None and r[5] and a0:
            lieky[a0] = {
                "skupina": cur,
                "nazov": txt(r[5]),
                "doplnok": txt(r[6]),
                "drz": txt(r[7]),
                "cena": num(r[10]),
                "uhrada": num(r[11]),
                "doplatok": num(r[13]),
            }
    if len(lieky) < MIN_KATEGORIZACIA:
        raise SystemExit(f"Kategorizácia má podozrivo málo liekov ({len(lieky)}), končím bez zmeny dát.")
    return skupiny, lieky


# ---------------------------------------------------------------- 3. výpadky ŠÚKL

def parse_vypadky(blob: bytes, today: dt.date) -> dict:
    """Aktuálny stav dodávok pre každý kód ŠÚKL.

    R = dodávky prerušené, Z = dodávky skončené, PR/PZ = prerušenie/skončenie ohlásené do budúcna.
    Obnovenie dodávok (O) stav ruší.
    """
    text = blob.decode("utf-8-sig", errors="replace")
    reader = csv.DictReader(io.StringIO(text), delimiter=";")
    fields = {norm(f): f for f in (reader.fieldnames or [])}

    def col(*names):
        for n in names:
            if norm(n) in fields:
                return fields[norm(n)]
        raise SystemExit(f"CSV výpadkov: chýba stĺpec {names[0]!r}, stĺpce: {reader.fieldnames}")
    f_kod, f_ucin, f_pred = col("Kód"), col("Účinnosť"), col("Predmet")
    by_kod: dict[str, list] = {}
    for row in reader:
        kod, ucin, pred = txt(row.get(f_kod)), txt(row.get(f_ucin))[:10], txt(row.get(f_pred)).upper()
        if kod and re.fullmatch(r"\d{4}-\d\d-\d\d", ucin) and pred:
            by_kod.setdefault(kod, []).append((ucin, pred))
    iso, out = today.isoformat(), {}
    for kod, events in by_kod.items():
        events.sort()
        past = [e for e in events if e[0] <= iso]
        future = [e for e in events if e[0] > iso]
        if past and past[-1][1] in ("R", "Z"):
            out[kod] = [past[-1][1], past[-1][0]]
        else:
            nxt = next((e for e in future if e[1] in ("R", "Z")), None)
            if nxt:
                out[kod] = ["P" + nxt[1], nxt[0]]
    return out


# ---------------------------------------------------------------- 4. stiahnutia z trhu (ŠÚKL)

MESIAC = r"(?:január|február|marec|apríl|máj|jún|júl|august|september|október|november|december)"
KOD_RE = re.compile(r"ŠÚKL\s*kód\s*:?\s*([0-9A-Z]{5})\b")
# "šaržu s číslom 50921D s dátumom exspirácie september 2028", "... a číslo 8768L s dátumom exspirácie apríl 2027",
# "šarže s číslom 8250243 a 8250235 s dátumom exspirácie marec 2028", "šaržu s číslom  8496700006 s dátumom exspirácie 31. 7. 2027"
CISLO = r"[A-Za-z0-9][A-Za-z0-9\-/.]*?"
SARZA_RE = re.compile(
    r"čísl(?:o|om)\s+(" + CISLO + r"(?:\s*(?:,|\ba\b)\s*" + CISLO + r")*)\s*,?\s+s\s+dátumom\s+exspirácie\s+"
    r"(" + MESIAC + r"\s+\d{4}|\d{1,2}\.\s*\d{1,2}\.\s*\d{4}|\d{1,2}\s*[./]\s*\d{2,4})", re.I)


def sarze_z_textu(text: str) -> list[list[str]]:
    """Všetky šarže s exspiráciou z textu oznamu; viac čísel pred jedným dátumom dostane ten istý dátum."""
    out = []
    for cisla, exp in SARZA_RE.findall(text):
        for c in re.split(r"\s*(?:,|\ba\b)\s*", cisla):
            c = c.strip().rstrip(".")
            if c:
                out.append([c, re.sub(r"\s+", " ", exp).strip()])
    return out
VSETKY_RE = re.compile(r"všetk\w*\s+šarž", re.I)
DOVOD_RE = re.compile(r"(Dôvodom stiahnutia.*?\.)(?=\s+[A-ZÁČĎÉÍĽĹŇÓÔŔŠŤÚÝŽ]|\s*$)", re.S)
TRIEDA_RE = re.compile(r"\b(I{1,3})\.\s*tried", re.I)


def html_text(fragment: str) -> str:
    t = re.sub(r"(?is)<(script|style)\b.*?</\1>", " ", fragment)
    t = re.sub(r"(?i)<br\s*/?>|</(p|div|li|tr|h\d)>", "\n", t)
    t = re.sub(r"<[^>]+>", " ", t)
    return re.sub(r"[ \t\xa0]+", " ", html.unescape(t)).strip()


def parse_rss(blob: bytes) -> list[dict]:
    """Položky RSS (bez duplicít): názov, dátum, odkaz."""
    root = ET.fromstring(blob)
    out, seen = [], set()
    for it in root.iter("item"):
        link = (it.findtext("link") or "").strip()
        if not link or link in seen:
            continue
        seen.add(link)
        try:
            datum = parsedate_to_datetime(it.findtext("pubDate") or "").date().isoformat()
        except (TypeError, ValueError):
            datum = ""
        out.append({"nazov": txt(it.findtext("title")), "url": link, "datum": datum})
    return out


def parse_oznam(page: str) -> dict:
    """Z HTML oznamu vyberie kódy ŠÚKL, šarže s exspiráciou, príznak 'všetky šarže', dôvod a triedu urgentnosti.

    Dva formáty: tabuľka (Kód ŠÚKL | Číslo šarže | Exspirácia) pri viacerých liekoch,
    inak text ("ŠÚKL kód: 26808" ... "šarže číslo 8706L s dátumom exspirácie apríl 2027").
    """
    lieky: dict[str, dict] = {}
    # tabuľka
    for table in re.findall(r"(?is)<table\b.*?</table>", page):
        rows = [[html_text(c) for c in re.findall(r"(?is)<t[dh]\b[^>]*>(.*?)</t[dh]>", tr)]
                for tr in re.findall(r"(?is)<tr\b.*?</tr>", table)]
        rows = [r for r in rows if any(r)]
        if not rows:
            continue
        head = [norm(c) for c in rows[0]]
        ik = next((i for i, h in enumerate(head) if "kod" in h), None)
        isz = next((i for i, h in enumerate(head) if "sarz" in h), None)
        ie = next((i for i, h in enumerate(head) if "exspir" in h), None)
        if ik is None:
            continue
        kod = None
        for r in rows[1:]:
            k = r[ik] if ik < len(r) else ""
            m = re.search(r"\b([0-9A-Z]{5})\b", k)
            if m:
                kod = m.group(1)
            if not kod:
                continue
            z = lieky.setdefault(kod, {"kod": kod, "sarze": [], "vsetky": False})
            sz = r[isz] if isz is not None and isz < len(r) else ""
            ex = r[ie] if ie is not None and ie < len(r) else ""
            if VSETKY_RE.search(sz):
                z["vsetky"] = True
            elif sz:
                for cislo in re.split(r"[,;\s]+", sz):
                    if cislo:
                        z["sarze"].append([cislo, ex])
    text = html_text(page)
    if lieky and not any(z["sarze"] or z["vsetky"] for z in lieky.values()):
        # tabuľka len s kódmi (napr. Egoropal): šarže alebo "všetky šarže" sú v texte
        sarze = sarze_z_textu(text)
        for z in lieky.values():
            z["sarze"] = [list(x) for x in sarze]
            z["vsetky"] = not sarze and bool(VSETKY_RE.search(text))
    if not lieky:
        kody = list(dict.fromkeys(KOD_RE.findall(text)))
        sarze = sarze_z_textu(text)
        vsetky = not sarze and bool(VSETKY_RE.search(text))
        for k in kody:  # pri viacerých kódoch v texte sa šarže priradia všetkým (radšej upozorniť viac)
            lieky[k] = {"kod": k, "sarze": [list(x) for x in sarze], "vsetky": vsetky}
    m = DOVOD_RE.search(text)
    t = TRIEDA_RE.search(text)
    return {
        "lieky": list(lieky.values()),
        "dovod": re.sub(r"\s+", " ", m.group(1)).strip()[:400] if m else "",
        "trieda": t.group(1).upper() if t else "",
    }


def fetch_stiahnutia() -> list[dict]:
    """Stiahnutia za posledný rok. Chyba pri jednom ozname nezastaví ostatné."""
    polozky = parse_rss(download(STIAHNUTIA_RSS, attempts=4, timeout=60))
    out = []
    for p in polozky:
        try:
            info = parse_oznam(download(p["url"], attempts=3, timeout=60).decode("utf-8", "replace"))
        except Exception as e:  # noqa: BLE001
            log(f"  oznam {p['url']}: {e}")
            info = {"lieky": [], "dovod": "", "trieda": ""}
        if not info["lieky"]:
            log(f"  POZOR: v ozname '{p['nazov']}' sa nenašiel kód ŠÚKL (zobrazí sa len v zozname stiahnutí)")
        out.append({**p, **info})
    return out


# ---------------------------------------------------------------- spojenie

def build(register: dict, skupiny: list, kat: dict, vypadky: dict, kat_od: str,
          buduce: dict | None = None, buduce_od: str = "", stiahnutia: list | None = None,
          drzitelia: dict | None = None) -> dict:
    drzitelia = drzitelia or {}
    lieky = []
    for kod, r in register.items():
        k = kat.get(kod)
        atc = r["atc"]
        if atc.startswith(("V09", "V10")):     # rádiofarmaká (pacient ich v lekárni nekupuje)
            continue
        lieky.append([
            kod,
            r["nazov"],
            r["doplnok"] or (k["doplnok"] if k else ""),
            atc,
            (r["latka"] if len(atc) == 7 else "") or (skupiny[k["skupina"]][1] if k else ""),
            r["vydaj"],
            drzitelia.get(r["drzitel"] or (k["drz"] if k else ""), r["drzitel"] or (k["drz"] if k else "")),
            k["skupina"] if k else -1,
            k["cena"] if k else None,
            k["uhrada"] if k else None,
            k["doplatok"] if k else None,
            r["spc"],
            r["pil"],
            r["reg"],
        ])
    # kategorizované lieky, ktoré v registri chýbajú (zriedkavé), doplníme z MZ SR
    known = set(register)
    for kod, k in kat.items():
        if kod in known:
            continue
        s = skupiny[k["skupina"]]
        lieky.append([kod, k["nazov"], k["doplnok"], s[0], s[1], "", drzitelia.get(k["drz"], k["drz"]), k["skupina"],
                      k["cena"], k["uhrada"], k["doplatok"], "", "", ""])
    lieky.sort(key=lambda x: (norm(x[1]), x[0]))
    out_buduce = {"od": buduce_od, "doplatky": buduce} if buduce is not None and buduce_od else None
    return {
        "format": FORMAT_VERSION,
        "verzia": dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%MZ"),
        "kategorizaciaOd": kat_od,
        "vypadkyK": dt.date.today().isoformat(),
        "hladajNaSukl": SUKL_SEARCH,
        "polia": ["kod", "nazov", "doplnok", "atc", "latka", "vydaj", "drzitel", "skupina",
                  "cena", "uhrada", "doplatok", "spc", "pil", "reg"],
        "skupiny": skupiny,
        "lieky": lieky,
        "vypadky": vypadky,
        # zmeny doplatkov od budúceho mesiaca: {"od": "RRRR-MM-01", "doplatky": {kód: nový doplatok alebo null}}
        **({"buduce": out_buduce} if out_buduce else {}),
        # stiahnutia z trhu za posledný rok: [{nazov, url, datum, lieky: [{kod, sarze: [[číslo, exspirácia]], vsetky}], dovod, trieda}]
        **({"stiahnutia": stiahnutia} if stiahnutia is not None else {}),
    }


# ---------------------------------------------------------------- webové stránky (zásady, podmienky)

ASSETS = ROOT / "app" / "src" / "main" / "assets"
STRANKY = {"sukromie.txt": "sukromie.html", "podmienky.txt": "podmienky.html"}
CSS = ("body{font:16px/1.6 system-ui,sans-serif;max-width:44rem;margin:0 auto;padding:1.5rem 1rem 3rem;color:#181d1b;background:#f6f8f7}"
       "h1{color:#1f6f5c;font-size:1.6rem;margin-bottom:0}h2{font-size:1.1rem;margin-top:1.6rem}.u{color:#55605d;margin-top:.2rem}"
       "a{color:#1f6f5c}@media(prefers-color-scheme:dark){body{background:#111614;color:#e0e4e2}h1,a{color:#8fd3bd}.u{color:#b9c3c0}}")


def gradle_hodnota(nazov: str) -> str:
    """Hodnota buildConfigField z app/build.gradle.kts (meno prevádzkovateľa, kontaktný e-mail)."""
    f = ROOT / "app" / "build.gradle.kts"
    m = re.search(r'buildConfigField\("String",\s*"' + nazov + r'",\s*"\\"(.*?)\\""\)', f.read_text(encoding="utf-8")) if f.exists() else None
    return m.group(1) if m else ""


def pravny_text_html(surovy: str, meno: str, email: str) -> str:
    """Text zásad/podmienok (rovnaký formát ako číta aplikácia) → jednoduchá HTML stránka."""
    riadky = surovy.replace("{MENO}", meno).replace("{EMAIL}", email).splitlines()
    titulok = html.escape(riadky[0].strip())
    telo, odsek = [f"<h1>{titulok}</h1>", f'<p class="u">{html.escape(riadky[1].strip())}</p>'], []

    def uloz():
        if odsek:
            telo.append("<p>" + html.escape(" ".join(odsek)) + "</p>")
            odsek.clear()
    for r in riadky[2:]:
        if r.startswith("## "):
            uloz(); telo.append(f"<h2>{html.escape(r[3:].strip())}</h2>")
        elif not r.strip():
            uloz()
        else:
            odsek.append(r.strip())
    uloz()
    return (f'<!doctype html><html lang="sk"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">'
            f"<title>{titulok} | Liekovka</title><style>{CSS}</style></head><body>{''.join(telo)}"
            f'<p class="u"><a href="./">Liekovka</a></p></body></html>')


def write_web(out: Path):
    """Zásady ochrany súkromia a podmienky používania ako webové stránky vedľa dát (adresa pre Google Play)."""
    meno, email = gradle_hodnota("PREVADZKOVATEL"), gradle_hodnota("KONTAKT_EMAIL")
    hotove = []
    for txt_name, html_name in STRANKY.items():
        src = ASSETS / txt_name
        if src.exists():
            (out / html_name).write_text(pravny_text_html(src.read_text(encoding="utf-8"), meno, email), encoding="utf-8")
            hotove.append(html_name)
    if hotove:
        (out / "index.html").write_text(
            f'<!doctype html><html lang="sk"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">'
            f"<title>Liekovka</title><style>{CSS}</style></head><body><h1>Liekovka</h1>"
            "<p>Informačná aplikácia o liekoch na Slovensku. Nie je oficiálnou aplikáciou ŠÚKL ani Ministerstva zdravotníctva SR.</p>"
            '<p><a href="sukromie.html">Zásady ochrany súkromia</a><br><a href="podmienky.html">Podmienky používania</a></p>'
            f'<p class="u">Kontakt: {html.escape(email)}</p></body></html>', encoding="utf-8")
        log(f"Webové stránky: {', '.join(hotove)}")


def write(bundle: dict, out: Path, assets: bool):
    out.mkdir(parents=True, exist_ok=True)
    write_web(out)
    raw = json.dumps(bundle, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    gz = gzip.compress(raw, compresslevel=9, mtime=0)
    (out / "lieky.json.gz").write_bytes(gz)
    meta = {"format": bundle["format"], "verzia": bundle["verzia"], "sha256": hashlib.sha256(gz).hexdigest(),
            "velkost": len(gz), "pocetLiekov": len(bundle["lieky"]),
            "kategorizaciaOd": bundle["kategorizaciaOd"], "vypadkyK": bundle["vypadkyK"]}
    (out / "meta.json").write_text(json.dumps(meta, ensure_ascii=False, indent=1), encoding="utf-8")
    log(f"Hotovo: {len(bundle['lieky'])} liekov, {len(bundle['skupiny'])} skupín, "
        f"{len(bundle['vypadky'])} hlásení o výpadku, {len(gz) / 1e6:.1f} MB → {out}")
    if assets:
        dst = ROOT / "app" / "src" / "main" / "assets"
        dst.mkdir(parents=True, exist_ok=True)
        shutil.copy(out / "lieky.json.gz", dst / "lieky.dat")
        for stary in ("lieky.json.gz", "lieky.json"):   # staršie názvy by sa do APK pribalili zbytočne
            (dst / stary).unlink(missing_ok=True)
        log(f"Skopírované do {dst}")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--register-zip", type=Path)
    ap.add_argument("--kategorizacia-xlsx", type=Path)
    ap.add_argument("--kategorizacia-od", help="dátum platnosti zoznamu, ak sa zadá lokálny súbor (RRRR-MM-DD)")
    ap.add_argument("--buduca-xlsx", type=Path, help="zoznam MZ SR na budúci mesiac (lokálny súbor)")
    ap.add_argument("--buduca-od", help="dátum platnosti budúceho zoznamu (RRRR-MM-DD)")
    ap.add_argument("--vypadky-csv", type=Path)
    ap.add_argument("--out", type=Path, default=ROOT / "dist")
    ap.add_argument("--assets", action="store_true", help="skopírovať výsledok aj do assets aplikácie")
    a = ap.parse_args()
    today = dt.date.today()

    log("1/3 Register liekov ŠÚKL")
    reg_blob = a.register_zip.read_bytes() if a.register_zip else next(
        (b for b in (try_download(u) for u in REGISTER_URLS) if b), None)
    if not reg_blob:
        raise SystemExit("Register ŠÚKL sa nepodarilo stiahnuť.")
    register = parse_register(reg_blob)

    log("2/3 Zoznam kategorizovaných liekov MZ SR")
    if a.kategorizacia_xlsx:
        kat_blob, kat_od = a.kategorizacia_xlsx.read_bytes(), a.kategorizacia_od or today.replace(day=1).isoformat()
    else:
        kat_blob, kat_od = fetch_kategorizacia(today)
    skupiny, kat = parse_kategorizacia(kat_blob)
    drzitelia = {} if a.kategorizacia_xlsx else fetch_drzitelia(kat_od)

    # zoznam na budúci mesiac je nepovinný: ak nie je alebo je poškodený, dáta sa zverejnia bez neho
    buduce, buduce_od = None, ""
    try:
        if a.buduca_xlsx:
            nb, buduce_od = a.buduca_xlsx.read_bytes(), a.buduca_od or ""
        else:
            res = fetch_buduca(today)
            nb, buduce_od = res if res else (None, "")
        if nb:
            buduce = zmeny_doplatkov(kat, parse_kategorizacia(nb)[1])
            log(f"  od {buduce_od}: {len(buduce)} liekov so zmenou doplatku alebo vyradených")
    except SystemExit as e:
        log(f"  zoznam na budúci mesiac preskočený: {e}")
        buduce, buduce_od = None, ""

    log("3/3 Výpadky ŠÚKL")
    vyp_blob = a.vypadky_csv.read_bytes() if a.vypadky_csv else download(VYPADKY_URL, attempts=5)
    vypadky = parse_vypadky(vyp_blob, today)

    log("Stiahnutia z trhu ŠÚKL")
    stiahnutia = None
    try:
        stiahnutia = fetch_stiahnutia()
        log(f"  {len(stiahnutia)} oznamov, {sum(len(x['lieky']) for x in stiahnutia)} liekov")
    except Exception as e:  # noqa: BLE001  (bez stiahnutí sa dáta zverejnia aj tak)
        log(f"  stiahnutia preskočené: {e}")

    write(build(register, skupiny, kat, vypadky, kat_od, buduce, buduce_od, stiahnutia, drzitelia), a.out, a.assets)


def try_download(url):
    try:
        b = download(url, attempts=3, timeout=300)
        return b if b[:2] == b"PK" else None
    except Exception as e:  # noqa: BLE001
        log(f"  {url}: {e}")
        return None


if __name__ == "__main__":
    main()
