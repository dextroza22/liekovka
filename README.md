# Liekovka

Android aplikácia s verejnými informáciami o liekoch na Slovensku pre bežných ľudí aj lekárnikov.

**Čo vie (verzia 1.0)**

Zadarmo je všetko okrem funkcií označených **(Plus)**. Liekovka Plus je jednorazový nákup cez Google Play
(produkt `liekovka_plus`); prepínač `PLUS_AKTIVNE` v `app/build.gradle.kts` (false = všetko zadarmo).
Testovacia (debug) verzia má v Nastaveniach prepínač na odomknutie Plus bez platby.

- vyhľadávanie podľa názvu a účinnej látky, v režime pre lekárnika aj podľa ATC kódu a kódu ŠÚKL
- detail lieku: doplatok, úhrada poisťovne, výdaj, držiteľ registrácie, stav dodávok podľa ŠÚKL
- lieky s rovnakou účinnou látkou, silou a formou (referenčná skupina MZ SR), zoradené podľa doplatku, s označením najlacnejšieho dostupného
- odkaz na stránku lieku na sukl.sk s aktuálnym príbalovým letákom (PIL) a SPC
  (adresa sa skladá z názvu a kódu; ak na webe neexistuje, otvorí sa vyhľadávanie podľa kódu ŠÚKL)
- mená držiteľov registrácie (časť G zoznamu MZ SR)
- **pripomienky užívania** (Plus): pri lieku v lekárničke si používateľ nastaví časy (napr. 8:00 a 20:00); notifikácia s tlačidlami
  „Užité“ a „Odložiť o 15 min“, po reštarte telefónu sa pripomienky obnovia. Aplikácia dávkovanie nekontroluje ani neodporúča.
- **koľko doplácam** (Plus): súčet doplatkov za jedno balenie každého lieku v lekárničke a koľko by to bolo s dostupnými lacnejšími alternatívami
- **prehľad pre lekárnikov** (Plus) (v režime lekárnika na úvodnej obrazovke): nové výpadky za 7/14/30 dní, zmeny doplatkov od budúceho
  mesiaca (zvýšenie, zníženie, vyradené) a stiahnutia z trhu, všetko s filtrom podľa ATC
- **záloha lekárničky** do súboru a obnovenie na novom telefóne (Nastavenia → Záloha lekárničky)
- **právne náležitosti** v Nastaveniach: vyhlásenie, že nejde o oficiálnu aplikáciu ŠÚKL ani MZ SR, zásady ochrany súkromia,
  podmienky používania, prevádzkovateľ a kontakt, nahlásenie chyby v údajoch, odkaz na hlásenie nežiaducich účinkov ŠÚKL
  a vymazanie všetkých údajov používateľa
- **Moja lekárnička**: uložené lieky s dátumom exspirácie; upozornenia na výpadok, obnovenie dodávok, zmenu doplatku, zrušenie registrácie a blížiacu sa exspiráciu
- **zmeny doplatkov vopred**: keď MZ SR zverejní zoznam na budúci mesiac, aplikácia ukáže nový doplatok a upozorní pri liekoch v lekárničke
- **lacnejšia alternatíva**: pri lieku aj v lekárničke ukáže dostupný liek s rovnakou látkou, silou a formou a nižším doplatkom
- **osoby (profily)** (Plus pre viac ako jednu osobu): samostatná lekárnička pre seba, rodičov či deti; poznámka k lieku (napr. ráno a večer)
- **zdieľanie zoznamu liekov** ako text, alebo PDF (Plus) (pre lekára, do nemocnice, rodine)
- **stiahnutie z trhu**: oznamy ŠÚKL (RSS + stránka oznamu) s kódom lieku a číslami šarží; upozornenie, ak je liek v lekárničke,
  červený pás v karte lieku a zoznam všetkých stiahnutí za posledný rok
- nastavenia vzhľadu: svetlá/tmavá téma, štyri veľkosti písma
- funguje offline, bez účtu, bez reklám; zoznam liekov používateľa zostáva len v telefóne
- režim pre lekárnika (ATC, ŠÚKL kód, konečná cena, reg. číslo, SPC na prvom mieste)

## Štruktúra

```
app/                       Android aplikácia (Kotlin, Jetpack Compose, Room, WorkManager)
tools/build_data.py        dátová pipeline: ŠÚKL + MZ SR → lieky.json.gz
tools/test_build_data.py   testy pipeline
.github/workflows/data.yml denná aktualizácia dát cez GitHub Actions + GitHub Pages
```

## Ako to funguje

1. `tools/build_data.py` stiahne tri oficiálne zdroje:
   - register liekov ŠÚKL (`sukl.sk/verejne/Zoznam_liekov/zoznam_liekov_aktualny.zip`)
   - zoznam kategorizovaných liekov MZ SR (ceny, úhrady, doplatky, referenčné skupiny)
   - CSV hlásení o prerušení dodávok ŠÚKL
2. Spojí ich do jedného súboru `lieky.json.gz` (cca 1 MB) a `meta.json` s verziou.
3. GitHub Actions to robí každý deň a zverejňuje výsledok na GitHub Pages.
4. Aplikácia raz denne skontroluje `meta.json`. Ak sú nové dáta, stiahne ich, porovná s liekmi v lekárničke
   a pri zmene pošle notifikáciu.

## Krok za krokom

### 1. Prvé dáta (na tvojom počítači)
```
pip install openpyxl
python tools/build_data.py --assets
```
Skript vypíše stĺpce registra ŠÚKL a počty liekov a skopíruje dáta do `app/src/main/assets/lieky.dat`
(pribalia sa do aplikácie, aby fungovala hneď po inštalácii aj bez internetu).

Hláška `stĺpce SPC/PIL nenájdené` je v poriadku: register čísla dokumentov neobsahuje, aplikácia preto otvára
stránku lieku na sukl.sk, kde sú aktuálne PIL aj SPC.

Testy pipeline: `python -m unittest tools/test_build_data.py`

### 2. Android Studio
Otvor priečinok `Liekovka` v Android Studiu a spusti aplikáciu.
- Unit testy (logika upozornení a hľadania): `./gradlew testDebugUnitTest`
- Test importu dát na telefóne/emulátore: `app/src/androidTest/.../ImportTest.kt` → pravý klik → Run.
  Používa vlastnú databázu v pamäti, dáta aplikácie v telefóne nezmení.

### 3. Automatické aktualizácie (GitHub)
1. Účet na github.com (ak ešte nemáš) a program **GitHub Desktop** (desktop.github.com), prihlás sa v ňom.
2. GitHub Desktop → File → Add local repository → priečinok `Liekovka` → „create a repository“ → Create.
3. **Publish repository**, odškrtni „Keep this code private“ (GitHub Pages je v bezplatnom účte len pre verejné repozitáre).
4. Na github.com v repozitári: Settings → Pages → Source: **GitHub Actions**.
5. Actions → „Aktualizácia dát“ → **Run workflow**. Po pár minútach má byť zelená fajka a na adrese
   `https://<používateľ>.github.io/liekovka/meta.json` sa zobrazí verzia dát.
6. V `app/build.gradle.kts` nastav `DATA_URL` na `https://<používateľ>.github.io/liekovka/` a zostav aplikáciu.

Úloha potom beží každý deň sama. Raz mesačne zapíše do repozitára dátum (`.github/posledna-aktivita.txt`),
lebo GitHub vo verejných repozitároch po 60 dňoch bez zmeny plánované úlohy vypína.
Ak úloha zlyhá (napr. ŠÚKL alebo MZ SR zmení formát súboru), GitHub pošle e-mail a aplikácie si ponechajú posledné dáta.

### Pred zverejnením: prevádzkovateľ a právne texty
- V `app/build.gradle.kts` vyplň `PREVADZKOVATEL` (meno a priezvisko) a skontroluj `KONTAKT_EMAIL`.
  Zobrazujú sa v aplikácii aj v právnych textoch.
- Texty sú v `app/src/main/assets/sukromie.txt` a `podmienky.txt`. Číta ich aplikácia a `build_data.py` z nich
  robí webové stránky `sukromie.html` a `podmienky.html` vedľa dát. Adresu
  `https://<používateľ>.github.io/liekovka/sukromie.html` zadaj v Google Play ako zásady ochrany súkromia.
- Pri zmene textu uprav aj dátum účinnosti na druhom riadku súboru.

### 4. Google Play
Postup, texty do obchodu, ikona, titulná grafika a snímky obrazovky sú v priečinku `google-play/` (`NAVOD.md`).

- **Health apps declaration**: aplikácia zobrazuje informácie o liekoch; nie je zdravotnícka pomôcka, nediagnostikuje ani neurčuje dávkovanie.
- **Zásady ochrany súkromia** (povinné). Návrh textu je nižšie.
- **Data safety**: žiadne údaje sa nezbierajú ani nezdieľajú (zoznam liekov je len v zariadení, cloudová záloha je vypnutá).
- Názov aplikácie a `applicationId` (`sk.kajka.liekovka`) si pred prvým nahratím over. `applicationId` sa neskôr nedá zmeniť.

**Návrh zásad ochrany súkromia**
> Liekovka nezbiera, neukladá na serveri ani nezdieľa žiadne osobné údaje. Zoznam liekov a dátumy exspirácie, ktoré si
> používateľ uloží, sa ukladajú iba v jeho zariadení a nezálohujú sa do cloudu. Aplikácia sa pripája na internet len kvôli
> stiahnutiu verejných údajov o liekoch (zdroje: ŠÚKL, MZ SR). Aplikácia neobsahuje reklamy ani analytické nástroje.
> Kontakt: <e-mail>

## Zdroje a podmienky
- Register liekov ŠÚKL je v Národnom katalógu otvorených dát pod licenciou CC0.
- SPC a PIL aplikácia nekopíruje, len odkazuje na oficiálne dokumenty na stránke ŠÚKL.
- Odporúčanie: pred zverejnením si písomne overiť u ŠÚKL podmienky použitia údajov v aplikácii.

## Obmedzenia verzie 1.0
- Databáza má verziu 2. Lekárnička z testovacej verzie 0.1 sa pri aktualizácii vymaže (pred zverejnením to nevadí).
  Od verzie 2 treba pri zmene tabuliek písať migrácie, aby používatelia o dáta neprišli.
  Migrácie 2 → 3 (stiahnutia) a 3 → 4 (časy pripomienok) sú hotové a overuje ich `MigraciaTest` (spustenie na telefóne/emulátore).
- Pripomienky používajú nepresný budík (bez špeciálneho povolenia); v úspornom režime môžu prísť o pár minút neskôr.
- Kód lieku a šarže sa z oznamov ŠÚKL čítajú z textu stránky. Ak ŠÚKL zmení formát, oznam sa zobrazí v zozname
  stiahnutí, ale bez prepojenia na liek; v logu GitHub Actions sa objaví riadok `POZOR: v ozname … sa nenašiel kód ŠÚKL`.
- Formát súboru registra ŠÚKL bol overený podľa dokumentácie a verejného projektu, nie na reálnom súbore; pipeline
  pri zmene štruktúry radšej skončí chybou, než aby vydala zlé dáta.
- Lieky mimo zoznamu kategorizovaných liekov (napr. voľnopredajné) nemajú cenu, lebo ju určuje lekáreň.
- Aplikácia je len v slovenčine.
