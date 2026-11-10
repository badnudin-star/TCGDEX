# P3-15 – Scanner: mehrsprachige Erkennung (Japanisch, Koreanisch, Chinesisch) und robusteres Matching

## Verhalten

- Die Kamera-Analyse verkettet jetzt **vier ML-Kit-Modelle pro Frame**: Latein (de/en/fr/es/it/pt/nl/pl/id …), Japanisch (Kana + Kanji), Koreanisch (Hangul) und Chinesisch (Han).
- Die Skript-Modelle laufen nur, wenn der Latein-Pass allein keine Identifikation liefert:
  1. gar keine Kartennummer lesbar (bisheriger Fallback-Anlass), oder
  2. eine Nummer wurde gefunden, aber der Text enthält weder durchsuchbare Namenstokens noch CJK — das Muster einer asiatischen Karte, deren Name für das Latein-Modell unsichtbar ist.
- Findet ein Skript-Modell direkt eine Kartennummer, gewinnt es (es liest meist auch den Namen). Sonst wird der CJK-reichste Skripttext mit dem Latein-Text **zusammengeführt**: Die Zeilen des Skript-Textes stehen vorne (Namenssuche), die Nummer aus dem Latein-Teil bleibt erhalten.
- Japanische Promo-Drucke tragen die Set-Kennung **hinter** der Nummer („001/SV-P"). Der Number-Matcher normalisiert beide Druckrichtungen auf die kanonische Form „SV-P001"; die Katalogsuche matcht für präfixierte Nummern zusätzlich auf `setId = Präfix AND number = Nummer`, weil asiatische Promo-Sets im Katalog genau so gespeichert sind (Set „SV-P", Nummer „001").
- Das Kandidaten-Ranking ist **sprachbewusst**: Kana im OCR → japanische Zeile zuerst, Hangul → koreanische, Han ohne Kana → chinesische, Kyrillisch → russische. Latein bleibt mehrdeutig — dann gewinnt die App-Defaultsprache Deutsch vor Englisch. Namenstokens schlagen weiterhin alle Sprach-Defaults.
- Die OCR-Suche ignoriert jetzt Layout-Floskeln weiterer Sprachen (fr/es/it/pt/ja/ko/zh), damit sie das 4-Token-Budget nicht verschwenden.
- OCR ohne Akzente („EVOLI") findet akzentbehaftete Kartennamen („Évoli") über einen Accent-Folded-Retry gegen `name`/`setName` (REPLACE-Kette im SQL; läuft nur, wenn die rohe Suche leer ist).

## Grenzen

- ML Kit On-Device unterstützt **kein Kyrillisch und kein Thai** (ru/th). Für diese Drucke funktioniert weiterhin der Nummern-Pfad (Latein-Modell liest die arabischen Ziffern), nicht aber die Namenssuche.
- zh-cn-Karten sind im Katalog ohnehin lückenhaft (bekannte Datenlage), nicht aber ein Scanner-Problem.
- Die vier Modelle erhöhen die APK-Größe um ca. 8–10 MB (gebundelte On-Device-Modelle, gleiche Technik wie das bestehende chinesische Modell). Für Latein-Karten ändert sich die Latenz nicht (ein Durchlauf); asiatische Karten brauchen im Falle bis zu vier Durchläufe pro Frame — der Frame-Gate begrenzt die Auslastung wie bisher.

## Aufrufkette

`CardScannerScreen` (Latein → `needsScriptFallback`? → ja/ko/zh-Kette → `pickScriptFallbackText`-Logik in `finishScriptChain`) → `extractCardNumber` (auch reversed Promo-Formate) → `resolveScannerMatches` → `CatalogRepository.getCardsByNumber` (inkl. `splitPrefixedNumber` → setId-Match) bzw. `getCardsByOcrText` (inkl. `foldOcrAccents`-Retry) → `rankCardMatches` (inkl. `detectOcrLanguageClue`) → Bestätigungsdialog (unverändert: keine automatische Auswahl).

## Tests

- Unit: `ScannerOcrChainTest` (Kette-Entscheidungen, Skript-Erkennung), `CardNumberMatcherTest` (reversed Formate, Präfix-Split, Akzent-Folding, mehrsprachige Ignorierliste), `CardMatchRankerTest` (Sprach-Ranking, Defaults, Namen vor Sprache).
- Instrumentiert: `CatalogSearchDaoTest` (SetId-Match für „SV-P001"/„SV-P1", Accent-Folded-Suche für „EVOLI"/„EPEE").
