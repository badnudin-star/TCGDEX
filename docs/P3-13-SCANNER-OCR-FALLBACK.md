# P3-13 – OCR-Fallback ohne lesbare Kartennummer

## Verhalten

- Die Scanner-Suche verwendet weiterhin die erkannte Kartennummer, wenn eine unterstützt wird.
- Ist keine Kartennummer lesbar, wartet der Scanner auf drei aufeinanderfolgende OCR-Ergebnisse mit denselben bereinigten Namens-/Set-Tokens.
- Die Fallback-Suche verwendet höchstens vier eindeutige Tokens und fragt für jedes Token maximal 30 Katalogkarten ab. Die zusammengeführte Kandidatenliste ist auf 60 Einträge begrenzt.
- Allgemeine Kartenbeschriftungen wie `BASIC`, `HP`, `TRADING` und `GAME` werden nicht als Suchbegriffe verwendet.
- Kandidaten werden anhand des gesamten OCR-Texts sortiert. Es erfolgt keine automatische Auswahl oder Speicherung; die bestehende Bestätigung bleibt erforderlich.

## Aufrufkette

`CardScannerScreen` → `extractOcrSearchTokens` → `CatalogRepository.getCardsByOcrText` → `CardDao.getCardsByNameFragment` → `rankCardMatches` → bestehender Auswahl-/Bestätigungsdialog.

## Grenzen

Der Fallback ist eine best-effort Suche im lokalen Katalog. OCR-Fehler, nicht importierte Karten und Karten, deren Name/Set im Text nicht erkannt wird, können weiterhin zu keinen oder unpassenden Kandidaten führen. Android-/Room-Codegenerierung und Laufzeitverhalten müssen durch den Android-Build und Gerätetest bestätigt werden.


## Kamera-Lastbegrenzung

Der Kamera-Analyzer verwendet zusätzlich einen nicht blockierenden Single-Flight-Guard. Da ML Kit asynchron arbeitet, könnte der Analyzer sonst bereits weitere Frames annehmen, während die vorherige OCR-Aufgabe noch läuft. Während eine Erkennung aktiv ist, werden neue Frames sofort geschlossen. Der Guard wird nach Abschluss oder bei einem synchronen Setup-Fehler freigegeben.
