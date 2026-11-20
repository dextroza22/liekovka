# Liekovka v Google Play: texty a postup

Všetko, čo treba vyplniť v Play Console. Obrázky sú v priečinku `grafika/`.

## 1. Podpísaný balík (.aab)
Android Studio → **Build → Generate Signed App Bundle or APK** → **Android App Bundle** → Next.
- Prvýkrát: **Create new…** a vytvor kľúč, napr. `C:\Users\babin\Documents\kluce\liekovka-upload.jks`
  (alias `liekovka`, heslo si zapíš, platnosť 25+ rokov, meno Lukáš Babinec, krajina SK).
- **Kľúč ani heslo nikdy nedávaj do priečinka projektu ani na GitHub.** Ulož si ich zálohu (USB, správca hesiel).
  S „Play App Signing“ (zapnuté automaticky) sa stratený kľúč na nahrávanie dá u Googlu vymeniť, ale trvá to pár dní.
- Build variant **release** → Create. Výsledok: `app/release/app-release.aab`.

Voliteľne z príkazového riadka: súbor `keystore.properties` v koreni projektu (je v .gitignore):
```
storeFile=C:/Users/babin/Documents/kluce/liekovka-upload.jks
storePassword=...
keyAlias=liekovka
keyPassword=...
```
a potom `gradlew bundleRelease`.

## 2. Play Console: nová aplikácia
- Názov: **Liekovka – doplatky a lieky**
- Predvolený jazyk: slovenčina (sk-SK) · Aplikácia · Bezplatná
- Kategória: **Medicína** · E-mail: kajkaforkids@gmail.com
- Zásady ochrany súkromia: `https://dextroza22.github.io/liekovka/sukromie.html`

## 3. Texty v obchode

**Krátky popis** (max. 80 znakov):
> Doplatky, lacnejšie lieky, výpadky a stiahnutia liekov. Dáta ŠÚKL a MZ SR.

**Úplný popis:**
> Liekovka prehľadne ukáže verejné údaje o liekoch na Slovensku: koľko za liek doplatíte, či existuje lacnejší liek s rovnakou účinnou látkou, či je liek dostupný a či ho ŠÚKL nesťahuje z trhu.
>
> HĽADANIE A DETAIL LIEKU
> • vyhľadávanie podľa názvu alebo účinnej látky
> • doplatok pacienta a úhrada poisťovne podľa zoznamu MZ SR
> • lieky s rovnakou látkou, silou a formou zoradené podľa doplatku
> • stav dodávok podľa hlásení ŠÚKL (prerušenie, skončenie, obnovenie)
> • odkaz na príbalový leták a SPC na stránke ŠÚKL
>
> MOJA LEKÁRNIČKA
> • uložte si lieky, ktoré užívate, s dátumom exspirácie a poznámkou
> • upozornenie na výpadok dodávok, zmenu doplatku (aj vopred od budúceho mesiaca), blížiacu sa exspiráciu a stiahnutie šarže z trhu
> • záloha a prenos na nový telefón
>
> LIEKOVKA PLUS (jednorazová platba, žiadne predplatné)
> • lekárnička pre viac osôb – rodičia, deti
> • pripomienky užívania v časoch, ktoré si nastavíte
> • Koľko doplácam: súčet doplatkov a možná úspora
> • zoznam liekov ako PDF pre lekára alebo do nemocnice
> • prehľad pre lekárnikov: nové výpadky, zmeny doplatkov od budúceho mesiaca, stiahnutia, filter ATC
>
> PRE LEKÁRNIKOV
> Režim pre lekárnika zobrazí ATC kód, kód ŠÚKL, konečnú cenu a registračné číslo a umožní hľadať podľa nich.
>
> SÚKROMIE
> Bez účtu, bez reklám, bez sledovania. Vaša lekárnička zostáva len vo vašom telefóne. Funguje aj offline, údaje sa aktualizujú každý deň.
>
> DÔLEŽITÉ
> Liekovka nie je oficiálnou aplikáciou ŠÚKL ani Ministerstva zdravotníctva SR. Údaje majú informatívny charakter a nenahrádzajú radu lekára ani lekárnika. Aplikácia nie je zdravotníckou pomôckou, nediagnostikuje a neurčuje dávkovanie. Zdroje: Štátny ústav pre kontrolu liečiv (register liekov, výpadky, stiahnutia z trhu) a Ministerstvo zdravotníctva SR (zoznam kategorizovaných liekov).

**Grafika:** ikona `grafika/ikona-512.png`, titulná grafika `grafika/titulna-grafika-1024x500.png`,
snímky telefónu `grafika/1-…png` až `6-…png` (1080 × 1920).

## 4. Obsah aplikácie (App content)
- **Prístup k aplikácii:** všetky funkcie sú dostupné bez prihlásenia.
- **Reklamy:** Nie, aplikácia neobsahuje reklamy.
- **Hodnotenie obsahu:** dotazník IARC, kategória „Referenčné, novinky alebo vzdelávacie“; na otázky o násilí, sexe, hazarde atď. odpoveď Nie. Na otázku o kontrolovaných látkach: aplikácia len zobrazuje informácie o registrovaných liekoch, nepredáva ich.
- **Cieľová skupina:** 18+ (nie pre deti).
- **Zdravotnícke aplikácie (Health apps):** vyber „Medicínske referenčné a vzdelávacie“ a „Správa liekov a liečby“ (lekárnička, pripomienky). Aplikácia nie je zdravotníckou pomôckou.
- **Bezpečnosť údajov (Data safety):** Zbiera alebo zdieľa aplikácia údaje? **Nie.** Údaje sa šifrujú pri prenose: Áno (HTTPS). Vymazanie: v aplikácii. Platby spracúva Google Play.
- **Vládna aplikácia:** Nie. **Finančné funkcie:** žiadne. **Novinky:** Nie.

## 5. Produkt Liekovka Plus
Monetizácia → Produkty → **Jednorazové produkty** → Vytvoriť (dá sa až po nahratí prvého .aab):
- ID produktu: **`liekovka_plus`** (presne takto, aplikácia ho hľadá podľa ID)
- Názov: **Liekovka Plus**
- Popis: *Viac osôb, pripomienky užívania, súčet doplatkov, PDF pre lekára a prehľad pre lekárnikov. Jednorazová platba.*
- Cena: **2,99 €** (Google prepočíta pre ostatné krajiny) → Aktivovať

Predtým treba v Play Console nastaviť **platobný profil (merchant)** a vyhlásiť **status obchodníka (trader)**.

## 6. Testovanie a vydanie
1. Testovanie → **Interné testovanie** → nahraj `.aab`, pridaj seba ako testera, nainštaluj cez odkaz.
2. Nastavenia → **Testovanie licencií** → pridaj svoj Gmail: nákup Plus bude testovací, bez skutočnej platby.
3. Ak je vývojársky účet osobný a založený po 13. 11. 2023: **uzavretý test s 12 testermi počas 14 dní**, až potom žiadosť o produkciu.
4. Produkcia → krajiny: Slovensko (prípadne aj Česko) → Odoslať na kontrolu. Prvá kontrola trvá zvyčajne pár dní.

## Poznámky k verziám
**1.0.0:** Prvé vydanie. Vyhľadávanie liekov, doplatky a lacnejšie alternatívy, výpadky dodávok, stiahnutia z trhu, Moja lekárnička s upozorneniami a Liekovka Plus.
