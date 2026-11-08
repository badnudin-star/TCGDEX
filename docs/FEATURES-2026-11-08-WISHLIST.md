# Wunschlisten-Upgrade 2026-11-08

Sieben Nutzerwünsche aus einer Runde, alle app-seitig umgesetzt — ohne Katalog- oder
Room-Schemaänderung (Schema bleibt Version 18, der veröffentlichte Katalog bleibt kompatibel).

## 1. Sprachvarianten in der Wunschlisten-Suche zusammenfassen

Karten mit identischem Bild aus demselben Set erschienen bisher als getrennte Einträge
(deutsche Drucke ohne, fremdsprachige mit `lang:`-Präfix in der ID). Sie werden jetzt zu
einem Eintrag gemergt:

- `CatalogSearchGrouping.kt`: neues `MergedCatalogCard(representative, variants)` mit
  `languages` (groß geschriebene Sprachcodes, sortiert). Merge-Schlüssel ist
  `rawCardId(cardId)` — die ID ohne Sprachpräfix, die bereits Set und lokale Nummer enthält —
  **pro Pokémon**, damit Karten unter mehreren Pokémon nicht versehentlich zusammenfallen.
  Bilder werden bewusst *nicht* per URL verglichen: Die Asset-URL enthält das Sprachsegment,
  Gleichheit würde identische Fallback-Bilder falsch zusammenlegen.
- Repräsentant (angezeigter Druck) ist die bevorzugte App-Sprache, dann Deutsch, dann
  Englisch, dann der erste Treffer (`representativeRank`).
- `PokemonCardGroup.cards` ist jetzt `List<MergedCatalogCard>`; Herz-Status prüft
  `variants.any { it.id in wishlistCardIds }`, damit auch ein fremdsprachiger Druck als
  „auf der Wunschliste“ erkannt wird.
- `WishlistViewModel` kombiniert zusätzlich die bevorzugte Sprache in die Suche
  (`SearchInputs`); `searchResultCount` zählt gemergte Einträge.

## 2. Verfügbare Sprachen anzeigen

- Suchtreffer: Zeile „Sprachen: DE · EN · FR“ pro gemergter Karte.
- Wunschlisten-Einträge (Tab „Meine Liste“): Sprachcode des eigenen Drucks hinter Set/Nummer.

## 3. Kartenbilder Vollbild

Neues `ui/components/CardImageDialog.kt`: Tippen auf ein Kartenbild (beide Tabs) öffnet es
bildschirmfüllend (`Dialog` mit `usePlatformDefaultWidth = false`, dunkler Hintergrund,
Antippen schließt). Angezeigt wird `largeImageUrl` mit `imageUrl`-Fallback; ohne Bild
erscheint ein Hinweistext. Die Klick-Fläche trägt `onClickLabel` für Screenreader.

## 4. Seltenheitsfilter unter englischen Sammelbegriffen

TCGdex speichert Raritäten als englische Rohwerte mit Schreibdrift (47 Werte, z. B.
„Double rare“ neben „Holo Rare V“) plus japanische Symbol-Raritäten („One Diamond“ …).
Neues `data/model/RarityGrouper.kt`:

- `canonical(raw)` bildet jeden Rohwert auf acht kanonische Gruppen ab:
  None, Common, Uncommon, Rare, Holo Rare, Ultra Rare, Illustration Rare, Promo.
  Unbekannte zukünftige Werte fallen auf Substring-Regeln zurück (Reihenfolge wichtig:
  „uncommon“ vor „common“, „promo“ vor „uncommon“); völlig unbekannte Begriffe werden
  selbst zur Gruppe.
- Die Filter-Chips zeigen nur noch die kanonischen Gruppen (bekannte zuerst, unbekannte
  alphabetisch danach). Gewählt wird `WishlistSearchParams.rarities` = Menge kanonischer
  Begriffe; `rawValuesFor()` expandiert sie vor der DAO-Abfrage zurück zu den Rohwerten —
  die SQL-Suche (`searchCatalogCardIds`) bleibt unverändert.
- Auch die Seltenheitsanzeige der Suchtreffer zeigt den kanonischen Begriff.

## 5. Cardmarket-Direktlink

Pro Wunschlisten-Eintrag ein „Cardmarket“-Chip (Warenkorb-Icon, öffnet den Browser):
`util/CardmarketLinks.forCard(name, setName)` baut
`https://www.cardmarket.com/de/Pokemon/Products/Search?searchString=Name+Set`
(URL-encoded via `URLEncoder`). Bewusst ein Such-Link statt Produkt-ID-Link: TCGdex
produkt-IDs existieren nur pro Variante und wären eine Katalog-/Schemamigration wert —
siehe Abschnitt „Bewusst nicht gemacht“.

## 6. Wunschliste als TXT speichern

Teilen-Icon in der Listen-Tab-Leiste mit zwei Einträgen:

- **Als Textdatei speichern** — `CreateDocument("text/plain")`, Dateiname
  `wunschliste-YYYYMMDD.txt`, UTF-8. Snackbar bestätigt Erfolg/Misserfolg.
- **Teilen (z. B. WhatsApp)** — Exportdatei im `cacheDir`, Freigabe über
  `ACTION_SEND` (text/plain, `EXTRA_STREAM` als FileProvider-URI mit
  `FLAG_GRANT_READ_URI_PERMISSION` + `ClipData`, kurzer Begleittext in `EXTRA_TEXT`)
  über das System-Share-Sheet; WhatsApp erscheint dort zusammen mit allen anderen Apps.

Format (`data/export/WishlistTextExporter.kt`, reine Funktion, unit-getestet): Kopf mit
Stand-Datum und Kartenzahl, dann je Karte Name, Set + Nummer, Sprache, Marktpreis,
optional Zielpreis/Notiz und der Cardmarket-Link. Der Export folgt der aktuell gewählten
Sortierung. Fehlt eine Karte im Katalog, steht die ID mit Hinweis statt Absturz.

## Bewusst nicht gemacht

- **Cardmarket-Produkt-IDs im Katalog:** bräuchte eine neue `cards`-Spalte (Schema 19),
  Katalog-Rebuild und wäre ein Update-Zwang — Alt-App + Neukatalog bzw. umgekehrt inkompatibel.
  Der Such-Link löst das ohne Migration.
- **Keine DB-/DAO-Änderungen:** `searchCatalogCardIds` erhält weiterhin Rohwerte; das
  Instrumented-Test-Setup (Fixture-DB, `CatalogSearchDaoTest`) bleibt unberührt.
- **Merge per Bild-URL:** URLs enthalten das Sprachsegment und können pro Sprache fehlen bzw.
  auf EN-Fallbacks zeigen — `(dexId, rawCardId)` ist stabiler (die Raw-ID enthält bereits
  Set und lokale Nummer) und deckt auch Karten ohne Bild ab.

## Tests

- `CatalogSearchGroupingTest`: Merge über Sprachen, Repräsentantenwahl, Nicht-Merge
  verschiedener Sets/Nummern, `rawCardId`-Präfix-Strip (inkl. `es-mx:`), plus angepasste
  Bestandstests auf `representative.id`.
- Neu `RarityGrouperTest`: alle 47 Rohwerte landen in kanonischen Gruppen, Schreibdrift
  kollabiert, japanische Symbole, None/Blank, Fallback-Regeln, Expansion, Chip-Reihenfolge.
- Neu `CardmarketLinksTest`: URL-Encoding (Leerzeichen, `&`, Umlaut-Symbole), deutsche
  Such-URL, leere Namen.
- Neu `WishlistTextExporterTest`: Vollständigkeit aller Zeilen, fehlende Katalogeinträge,
  Leerliste.
- Accessibility-Guard: dekoratives Warenkorb-Icon (Textlabel daneben) in die Allowlist
  aufgenommen — Zähler exakt, keine Aufweichung der Regel.
