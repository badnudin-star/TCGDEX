# Bug-Review R19/G-Bundle – 2026-11-11

Nachreview des gemergten G-Bundles (PR #20). Vier echte Probleme gefunden und behoben, dazu die Grading-Validierung auf Spezifikationsniveau gebracht.

## Gefundene Bugs

### 1. Fotoimport decodierte das Bild auf dem Main-Thread (echter Bug)
`InputImage.fromFilePath(context, uri)` macht Datei-I/O (lesen + dekodieren) und wurde direkt im Picker-Callback auf dem Main-Thread aufgerufen — bei großen Galerie-Fotos drohen Ruckler/ANR und eine StrictMode-Verletzung.
**Fix:** Dekodieren in `withContext(Dispatchers.IO)` innerhalb `rememberCoroutineScope().launch`; `analyzeStillImage` nimmt jetzt das fertige `InputImage` statt `(context, uri)`.

### 2. „Aus Galerie“ war während der OCR erneut klickbar (Race)
`galleryBusy` wurde schon im Picker-Return zurückgesetzt — die ML-Kit-Analyse lief aber noch. Ein zweiter Tap konnte eine zweite OCR über die laufende stülpen; das letzte Ergebnis gewinnt unkontrolliert.
**Fix:** `galleryBusy` bleibt gesetzt, bis `onResult`/`onUnreadable` feuert (oder der Picker abgebrochen wird).

### 3. `galleryBusy` überlebte den Prozess-Tod (Deadlock-Kandidat)
Der Flag stand in `rememberSaveable`. Stirbt der Prozess, während die System-Galerie offen ist, wird der Zustand als `true` wiederhergestellt — ohne laufende Analyse, die ihn zurücksetzen könnte: Button dauerhaft deaktiviert.
**Fix:** Plain `remember` (transient). Konfigurationswechsel verlieren den Flag bewusst in Kauf nehmend — die Kamera verhält sich nach Rotation genauso (Ergebnis verfällt, Nutzer scannt erneut).

### 4. Galerie-OCR-Ergebnis nach dem Verlassen des Scanners (Geister-Dialog)
Verlässt der Nutzer den Scanner, während die OCR läuft, feuerte `onCardDetected` trotzdem — der Treffer-Dialog erschien über einem fremden Screen. Die Kamera-Analyse verwirft nach Disposal über ihr `disposed`-Flag; die Galerie hatte nichts Vergleichbares.
**Fix:** Eigenes `AtomicBoolean` + `DisposableEffect`, `onResult`/`onUnreadable` prüfen es (gleiche Semantik wie `CameraPreview`).

### 5. EN-Zeile duplizierte den Namen bei englischem Eigenbestand (kosmetischer Bug)
`PokemonDetailScreen` zeigte `EN: <Name>` auch dann, wenn das eigene Exemplar bereits der englische Druck ist — die Zeile wiederholte den Namen direkt darunter. `SetDetailScreen` hatte den Guard (`englishName != representative.name`), das Detail nicht.
**Fix:** Derselbe `takeIf { it != primaryCard.name }`-Guard.

## Verschärfte Validierung (Spezifikationslücke)

Die Konsultation zu G sah eine Note-Validierung `^((10)|([1-9](\.[05])?))$` vor; implementiert war nur Trim + Länge. Jetzt:

- **`normalizeGrading` in der Datenschicht** (`UserCardEntityV2.kt`) als einzige Quelle: Firma trimmen + Whitelist (`GradingCompany`), sonst komplettes Grading verwerfen; Note trimmen, deutsches Komma → Punkt, dann Slab-Notation-Regex, sonst `null` (Firma + Cert bleiben); Cert trimmen, ≤ 24 Zeichen. `MAX_GRADE_LENGTH`/`MAX_CERT_NUMBER_LENGTH` wohnen jetzt dort.
- **Sanitizer delegiert** (`sanitizeGrading = normalizeGrading`) — UI-Tests unverändert gültig, Verhalten verschärft.
- **Import normalisiert mit** (`CollectionExporter`): hand-editierte JSON-/CSV-Dateien mit unbekannter Firma oder Murks-Note kommen nicht mehr in die DB — vorher zeigte der Dialog „Nicht gradiert“ (Dropdown-Wert unbekannt), während die Detail-Zeile „Grading: XYZ“ anzeigte und erst beim Speichern stumm wischte. Neue Entitäten und beide Merge-Strategien laufen durch `normalizeGrading`.
- **Dialog-Eingabe** filtert jetzt auf Ziffern + `.`/`,` (Dezimal-Tastatur); überlange Eingaben am Datenlayer-Const gekappt.

## Prüfte und für intakt befundene Bereiche

- **Migration 2→3** + `3.json`-Hash: CI-Emulator-Test grün, Schema-Guard bestätigt Hash-Übereinstimmung mit Rooms Export.
- **Recognizer-Hoisting**: Kamera-Kette (Latein `recognizers.first()`, Skripte `drop(1)`) und gemeinsames `DisposableEffect`-Close — nur in dieser Datei genutzt, keine externen Aufrufer.
- **Export/CSV**: Quoting von `;`-haltigen Werten, Legacy-CSV ohne Grading-Spalten, `startsWith`-Header-Präfix im `ExportImportTest` (weiterhin erfüllt).
- **TcgLogoImage**: `remember(model)`-Reset bei URL-Wechsel, Early-Return vor Composable-Aufrufen, `contentDescription` überall gesetzt (A11y-Check grün).
- **EN-Toggle**: 5-Flow-`combine` im SetDetailViewModel, DataStore-Default `false`, keine DAO-/Schema-Auswirkungen.
- Python-Suiten (16/16), Schema-Guards beider DBs, `check_migration_source`, `check_catalog_schema_constants`, `accessibility-check`: alles grün.

## Tests

- `UserCardDetailsSanitizerTest`: `gradeMustMatchSlabNotation` (10/9.5/9/1/5.5 gültig; 0/11/9.75/10.5/abc/„9.“/„1234567“/„“ → null), `germanCommaGradeIsNormalizedToDot` („9,5“→„9.5“, „9,“→null), `invalidGradeKeepsCompanyAndCertNumber`, `certNumberIsLengthCapped` (statt des alten Kappungs-Tests).
- `CollectionExporterTest`: `testMergeNormalizesInvalidImportedGrading` (ACE/„sehr gut“ verworfen, Bestands-Grading bleibt), `testMergeNormalizesImportedGradeNotation` („9,5“→„9.5“).
