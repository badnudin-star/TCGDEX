# R19/G – Sammlungs-Upgrade: Graded Slabs, Fotoimport, EN-Namen, robuste Set-Logos

Vier unabhängige Erweiterungen aus dem G-Bundle, gemeinsam in einem PR weil sie sich nicht überschneiden und zusammen getestet wurden.

## 1. Graded Slabs (PSA/CGC/BGS)

Pro Kopie (Primärkarte wie Sekundärkarten) lassen sich jetzt Grading-Firma, Note und Zertifikatsnummer hinterlegen.

- **UserDB v2 → v3**: `MIGRATION_2_3` ergänzt drei nullable TEXT-Spalten `gradingCompany`, `grade`, `certNumber` an `user_cards`. Rohkarten bleiben überall NULL. Schema-JSON `3.json` mit Room-2.6.1-Identity-Hash (md5 über sortierte Entity-Keys) offline erzeugt; `tools/room_identity_hash.py` bewacht jetzt **beide** Datenbanken (AppDB v19, UserDB v3).
- **UI**: `CardSelectionDialog` hat eine neue Sektion „Grading“: Firmen-Dropdown („Nicht gradiert“/PSA/CGC/BGS), Note (max. 6 Zeichen) und Zertifikatsnr. (max. 24) — die letzten beiden Felder erscheinen nur, wenn eine Firma gewählt ist. „Nicht gradiert“ leert alle drei Felder. Die Primärkarten-Zeile im Pokémon-Detail zeigt `Grading: PSA 10` (Tertiärfarbe), wenn gradingCompany gesetzt ist.
- **Sanitizer** (`sanitizeGrading`): Firma wird getrimmt und muss exakt einem `GradingCompany`-Namen entsprechen, sonst fällt das komplette Grading weg (eine Note ohne Firma ist bedeutungslos). Note/Cert werden getrimmt, leere→NULL, Länge gekappt (6/24). `sanitizeUserCardDetails` hat den neuen Parameter `grading` mit `CardGrading()`-Default.
- **Kategorie-Wechsel löscht kein Grading mehr**: `updatePrimaryCategory` reicht das gespeicherte Grading des Primär-Eintrags durch.
- **Export/Import**: JSON-Items bekommen optionale Felder `gradingCompany`/`grade`/`certNumber` (Gson lässt NULL-Felder weg — alte Dateien bleiben schemaVersion 2, kein Breaking Change). CSV-Export schreibt drei neue Spalten; der CSV-Import liest sie **optional** (Legacy-CSV ohne diese Spalten importiert weiter). Merge-Strategien (OVERWRITE/MERGE_ADD) übernehmen Grading-Werte wie `notes`/`condition`: Import-Wert gewinnt, sonst bleibt der Bestand.

## 2. Scanner: Fotoimport aus der Galerie

- TextButton „Aus Galerie“ im Scanner-Bottom-Panel (neben ROI-/Batch-Chips) öffnet `ActivityResultContracts.GetContent()` mit `image/*`.
- Das gewählte Foto läuft durch **exakt dieselbe OCR-Kette** wie die Live-Kamera (`analyzeStillImage`): Latein-Pass; bei `needsScriptFallback` die ja/ko/zh-Kette; erster Skript-Treffer mit Nummer gewinnt, sonst CJK-reichster Text mit Latein-Text gemergt. Namenssuche bekommt — wie bei der Kamera — größen-sortierte Zeilen, die Nummernmatch den Rohtext. Kein Multi-Frame-Submit-Gate (ein Standbild ist ein einzelnes, meist scharfes Sample).
- Die vier ML-Kit-Clients werden jetzt **auf Screen-Ebene** erzeugt und an `CameraPreview` durchgereicht — Kamera und Galerie teilen sich die Modelle (einmal laden, einmal freigeben; `DisposableEffect` am Screen schließt sie).
- Fehler: nicht lesbares Bild bzw. kein Text erkannt → Meldung im Erkennungs-Panel (`detectedText`), kein Absturz.

## 3. Englische Kartennamen als Zusatzzeile

- Einstellungen → Anzeige: neuer Switch „Englische Kartennamen anzeigen“ (`KEY_SHOW_EN_NAMES`, DataStore, Default aus).
- Anzeige als Sekundärzeile `EN: <Name>` unter dem Kartennamen — in der Primärkarten-Zeile des Pokémon-Details (Lookup über gleiche Raw-ID im `cards`-Strom) und in den Kartenzeilen des Set-Detail-Screens (`MergedCatalogCard.englishName` = erste `en`-Variante; Asian-only-Prints ohne EN-Variante zeigen nichts).
- Rein lesender Zusatz: kein DAO-/Schema-Change, folgt der bevorzugten Sprache bei der Repräsentanten-Wahl nicht hinein.

## 4. Logo-Link-Wächter

- Neue `TcgLogoImage`-Komponente (`ui/components/TcgImage.kt`): bei Coil-Ladefehler (`AsyncImagePainter.State.Error`) verschwindet das Bild einfach — statt des Karten-Placeholders, der im Logo-Spot wie ein kaputtes Link-Symbol wirkte (mcd-Varianten-Bilder existieren teils nicht auf dem CDN).
- Eingebaut an allen Set-Logo-/Symbol-Stellen: `SetDetailScreen`, `SetProgressScreen`, `ReleaseCalendarSection`. Kartenbilder behalten bewusst den Placeholder-Fallback (dort ist ein Ersatzsymbol hilfreich, bei Logos irreführend).

## Aufrufkette Grading

`CardSelectionDialog.onDetailsChange(+grading)` → `PokemonDetailScreen.onUpdateCardDetails(+grading)` → `MainActivity` → `PokemonDetailViewModel.updateCardDetails(+grading)` → `sanitizeUserCardDetails(+grading)` → `CollectionRepository.updateUserCard` (DAO-`@Update` unverändert).

## Tests

- Unit: `UserCardDetailsSanitizerTest` (+7: Grading ohne/unbekannte Firma wird verworfen, Trim + NULL bei leer, Längen-Kappung, gültiges Grading bleibt), `CollectionExporterTest` (+5: JSON/CSV-Roundtrip mit Grading, ungradierte Karten ohne Grading-Felder im JSON, Legacy-CSV ohne Grading-Spalten, Merge-Strategien behalten Bestands-Grading ohne Import-Wert).
- Instrumentiert: `DatabaseMigrationTest.testUserDatabaseMigration2To3` (v2-Fixture → Spalten NULL, Werte round-trip-fähig; `runMigrationsAndValidate(…, 3, …, ALL_MIGRATIONS)`).
- Python: `tools/test_room_identity_hash.py` 6/6; `room_identity_hash.py` prüft beide DBs (PASS); Root-Guards (`check_migration_source`, `check_catalog_schema_constants`, `accessibility-check`) grün.
