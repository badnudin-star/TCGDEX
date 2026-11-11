# Vollständiger App-Bug-Review — 2026-11-11 (Runde 2, ganze App)

Vollständiger Durchlauf über alle 99 Kotlin-Dateien (ui/, data/, util/, worker/), zusätzlich GitHub-/CI-Zustandsprüfung und ein neuer automatisierter Format-String-Check. Vorherige Runden: BUG-REVIEW-2026-11-07.md (alte Bereiche), BUG-REVIEW-2026-11-11-G-BUNDLE.md (G-Bundle).

## Ergebnis: 2 echte Funde (beide behoben)

### 🟠 1 — `PriceFormatter`: nicht thread-sichere DecimalFormat geteilt zwischen UI und Nightly-Worker

`util/PriceFormatter.kt` hielt eine einzige `DecimalFormat`-Instanz im Object. `DecimalFormat` ist laut Dokumentation **nicht synchronisiert** — gleichzeitige `format()`-Aufrufe können die interne DigitList korrumpieren (Müllpreise oder `ArrayIndexOutOfBoundsException`).

Konkret erreichbar: Die UI formatiert Preise auf dem Main-Thread (Listen, Detail-Screens), während der nächtliche 03:00-Sync in `PriceAlertManager.sendDailyPriceReport` **auf Dispatchers.IO** dieselbe Instanz für den Benachrichtigungstext benutzt. App offen + Nightly-Sync läuft = seltener, aber realer Defekt.

**Fix:** `ThreadLocal<DecimalFormat>` (anonymes `object` mit `initialValue()`, minSdk 26 wäre auch für `withInitial` ok, die anonyme Form ist überall sicher). Jeder Thread formatiert konkurrenzfrei; Semantik unverändert.

### 🟠 2 — `buildValueSeries`: O(Tage × Punkte) auf dem Main-Thread (UI-Freeze bei großen Sammlungen)

`util/PriceHistoryUtils.buildValueSeries` hat für **jeden Chart-Tag** die **komplette Historie jeder Karte** neu gefiltert (`filter { timestamp <= dayEnd }.maxByOrNull { timestamp }`). Die Berechnung läuft im Composable (`remember`), also auf dem Main-Thread.

Größenordnung: Preis-Punkte bleiben 2 Jahre (Cleanup), max. einer pro ~20 h → bis ~870 pro Karte. 1.000 Karten × 870 Punkte × 730 Tage („Gesamt"-Filter) ≈ **600 Mio. Operationen** → mehrsekündiges Einfrieren der UI beim Öffnen der Preisentwicklung.

**Fix:** Pro Karte einmal stabil sortieren, dann einen laufenden Index von Tag zu Tag weiterschalten — jede Preiszeile wird höchstens einmal besucht: **O(Punkte·log Punkte + Tage × Karten)** statt O(Tage × Punkte). Spezialfall gespiegelt: bei mehreren Punkten mit identischer Millisekunde gewinnt weiterhin der erste (exakt das frühere `filter + maxByOrNull`-Verhalten; `maxByOrNull` liefert das erste Maximum).

**Verifikation:** Python-Spiegelung beider Implementierungen (inkl. first-of-max und stabilem Sort) über 3.000 randomisierte Fälle mit Duplikat-Timestamps, unsortierter Eingabe und allen `rangeDays`-Modi: **0 Abweichungen**. Zusätzlich neuer Unit-Test `testBuildValueSeriesUsesFirstPointOfDuplicateTimestamps`.

## Automatisierter neuer Check: Format-String-Crash-Scanner

Falsche Argumentzahlen in `stringResource(R.string.x, …)` / `getQuantityString(…)` crashen erst zur Laufzeit — Compiler und Unit-Tests finden sie nicht. Ein neuer Scan (Parser für Argumentlisten über Klammern/Strings/Lambdas hinweg, Platzhalter-Regex inkl. `%3$.1f`-Formen) prüfte:

- **489 direkte Format-Aufrufe** gegen ihre Platzhalter: **0 Mismatches** ✓
- 5 Plurals (`getQuantityString`/`pluralStringResource`): alle korrekt ✓
- 2 ohne Argumente geholte format-Strings (`export_msg_exported`, `import_msg_completed`) — beide werden korrekt später per `.format(…)` befüllt ✓

Erstlauf-Fehlalarme (Konstruktor-Argumente in `GenerationModel`, verfeinerter Platzhalter-Regex) wurden einzeln verifiziert und sind keine Bugs.

## Geprüft und intakt befunden (Auswahl)

- **Repositories/Worker:** `ResumableCatalogWorker` (Checkpoint-Resume, 8-Min-Scheiben, Retry-Politik), `SyncRepository` (ETag/304, Redirect-Limits, HTTPS-Validierung), `PriceRepository` (Batch-Lookups < SQLite-Limit), `CollectionRepository` (Favoriten-Zuweisung ohne Doppelzeilen — alter Bug A bleibt gefixt), `CatalogImporter` (Merge-SQL mit geschützten IDs, Beziehungsvalidierung).
- **DataStore:** `preferencesDataStore`-Delegate ist Prozess-Singleton — mehrere `AppPreferencesRepository`-Instanzen (App/Worker/Importer) teilen sicher dieselbe Instanz.
- **Thread-Sicherheit sonst:** `AppLogger` vollständig `@Synchronized`; alle anderen `SimpleDateFormat`-Nutzungen sind lokale Instanzen; keine weiteren objekt-leveligen `var`s.
- **Scanner:** Sequence-Invalidierung + Entry-ID-Bindung in `MainActivity` (kein Geister-Dialog), Submit-/Frame-Gates, Batch-Modus.
- **UI:** MainViewModel (8-Flow-Array-combine), WishlistViewModel (debounce + flatMapLatest), SetProgress/SetDetail/Pokedex/Diagnostics/CatalogManager/About — inkl. `!!`-Guard-Prüfung (alle durch vorangehende Null-Checks gedeckt), Empty-States, LazyColumn-Keys.
- **Manifest:** nur benötigte Permissions (INTERNET, NETWORK_STATE, POST_NOTIFICATIONS, CAMERA), Kamera als nicht erforderlich (Scanner-los nutzbar), FileProvider nicht exported.
- **Kein** `runBlocking`/`GlobalScope`, kein `lateinit` außer App-Container, keine TODO/FIXME-Marker, keine `printStackTrace`, leere Catches nur als beabsichtigte Best-Effort-Cleanup-Muster.
- **GitHub-Zustand:** 0 offene Issues, 0 offene PRs, alle Workflows seit dem letzten Fix grün (APK Release + Validation).

## Tests

- `PriceHistoryAndAlertTest` +1 (`testBuildValueSeriesUsesFirstPointOfDuplicateTimestamps`).
- Alle lokalen Suiten grün: Python 16/16, Schema-Guards beider DBs (19/3), `check_migration_source`, `check_catalog_schema_constants`, `accessibility-check` (99 Dateien), strings.xml wohlgeformt.
