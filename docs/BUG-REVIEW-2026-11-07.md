# Bug-Review GitHub / Codebase — 2026-11-07

Repository: `badnudin-star/TCGDEX` (Branch-Basis `main`, Commit `c245e03`)

## 1. GitHub-Status

| Prüfung | Ergebnis |
| --- | --- |
| Offene Issues | **0** |
| Geschlossene Issues | 0 (Issue-Tracker ungenutzt) |
| Offene/Geschlossene PRs | 0 |
| Releases | `catalog-latest` (2026-11-05, 60,7 MB Katalog + Manifest + Reports) |
| Letzter Android-Validation-Lauf | grün (2026-11-05, 4m42s) |

Es existieren also **keine gemeldeten Bugs** im Tracker — der Tracker ist leer. Die folgenden
Punkte sind Ergebnisse einer eigenen Code-/CI-Prüfung.

### Fehlgeschlagene Workflow-Läufe (historisch)

| Lauf | Workflow | Ursache |
| --- | --- | --- |
| 41295192231 (01.11.) | Build Catalog Pipeline | Exit 2 = Publikations-Gate „Katalog unvollständig", danach per `--resume` erfolgreich |
| 41899876109 (04.11.) | Build Catalog Pipeline | Exit 1 = unerwarteter Fehler im Build-Schritt, danach erfolgreich |
| 41134286000, 41230315492 | Android Validation | „Timeout waiting for emulator to boot" / exit 1 im Emulator-Schritt |
| 41233612673 | Android Validation | Emulator-Schritt |

Die Emulator-Fehler sind Infrastruktur-Flakes, aber sie haben eine **Regression verdeckt**
(siehe Befund 2).

### Lokal reproduzierbar: alle Repo-Checks grün

```
Production migration source check: PASS
verify_migration_16_17: PASS
scripts/accessibility-check.py: OK (84 Kotlin-Dateien)
tools/catalog-builder: 61 Tests OK
```

---

## 2. Echte Befunde im Code (nach Schwere)

### 🔴 A — „Als Lieblingskarte setzen" erzeugt doppelte Sammlungszeilen

`app/src/main/java/com/pokemontcg/sammlung/data/repository/CollectionRepository.kt:46`

```kotlin
suspend fun setAsPrimaryCard(card: CardEntity, pokemonId: String) {
    userDb.userCardDao().clearPrimary(dexId, pokemonId)
    userDb.userCardDao().insertUserCard(UserCardEntityV2(... quantity = 1, isPrimary = true ...))
}
```

`user_cards` hat `id INTEGER PRIMARY KEY AUTOINCREMENT` und **keinen Unique-Index auf
`(cardId, language)`** (`app/schemas/.../UserDatabase/2.json`). `@Insert(REPLACE)` greift nur bei
PK-Konflikt; mit `id = 0` wird immer eine **neue Zeile** eingefügt.

Folgen, wenn eine bereits vorhandene Karte zur Lieblingskarte gemacht wird (z. B. über den
Schalter im Karten-Editor, `PokemonDetailScreen.kt:113`):

- zweite Zeile für dieselbe `cardId`, alte Zeile bleibt als „weitere Karte" sichtbar,
- `quantity` der neuen Zeile wird hart auf `1` gesetzt, Menge/Kategorie/Zustand/Notizen der
  bestehenden Zeile gehen verloren,
- `MainScreen.kt:60` summiert `quantity` über alle Zeilen je `cardId` → **doppelt gezählte
  Sammlungsmenge** und falscher Gesamtwert,
- Export/`CollectionExporter` exportiert beide Zeilen,
- bei wiederholtem Umschalten wachsen die Duplikate weiter an (kein Dedupe beim Lesen).

Erwartetes Verhalten: vorhandene Zeile per `id` aktualisieren (`isPrimary = true`,
`clearPrimary` für die anderen) statt neu einzufügen; zusätzlich Unique-Index
`(cardId, language)` per Room-Migration.

### 🔴 B — Start-Katalog kann nie ins Repo gelangen (Offline-Erststart kaputt)

`README`/`tools/catalog-builder/README.md` verlangen
`python tools/catalog-builder/make_start_catalog.py --output app/src/main/assets/start_catalog.sqlite`.

`.gitignore:48` ignoriert aber `*.sqlite` und whitelistet nur
`!app/src/androidTest/assets/*.sqlite`. Ergebnis:

- `app/src/main/assets/` existiert im Repo nicht,
- `SyncRepository.importStartCatalogIfNeeded()` (`SyncRepository.kt:61`) findet das Asset nie,
- ein frisch gebautes APK hat **keinen** Offline-Katalog — der im Code/README als
  „funktioniert im Flugmodus" beschriebene Erststart zeigt stattdessen einen leeren Pokédex.

Fix: Whitelist `!app/src/main/assets/start_catalog.sqlite` ergänzen (oder Asset bewusst als
Download-Artefakt deklarieren und die Doku korrigieren).

### 🟠 C — Instrumentierte Tests laufen in CI nicht mehr

`.github/workflows/android-validation.yml` enthält keinen Emulator-Schritt mehr (der frühere
Schritt „Run instrumented tests on Android emulator" wurde beim Umschreiben entfernt), es werden
aber weiterhin `androidTestImplementation`-Abhängigkeiten und drei instrumentierte Testklassen
gepflegt:

- `CatalogImporterTest.kt` (Import, Schema-/Hash-Validierung, Redirects),
- `DatabaseMigrationTest.kt` (echte Room-Migrationen),
- `PokemonNameSyncTest.kt`.

Diese Tests werden damit **nirgends ausgeführt**. Genau sie prüfen das riskanteste Stück Code
(Katalog-Import + Migration 16→17). Empfehlung: eigener Workflow mit
`reactivecircus/android-emulator-runner` (oder Firebase-Test-Lab), damit der Flake von
Schritt 17 den eigentlichen Test nicht mehr maskiert.

### 🟠 D — `pokemonId` wird beim Import nie gesetzt → Sammelfortschritt falsch

`CollectionExporter.kt:397`: importierte Karten bekommen `pokemonId = ""`.
`GenerationGrouping.kt:33` zählt gesammelte Pokémon aber über `it.pokemonId`:

```kotlin
val collectedPokemonIds = userCards.map { it.pokemonId }.toSet()
collectedCount = allMembers.count { it.id in collectedPokemonIds }
```

Nach einem Import (Backup wiederherstellen) zeigt die Pokédex-Generationen-Übersicht daher
**0 gesammelt**, obwohl die Karten vorhanden sind. Fix: `pokemonDexId.toString()` als `pokemonId`
schreiben (so machen es Detail-Screen und Scanner bereits) oder in der Gruppierung auf
`pokemonDexId` ausweichen.

### 🟡 E — Werteverlauf erfindet Preise vor dem ersten Messpunkt

`util/PriceHistoryUtils.kt:30`

```kotlin
val pointBeforeDay = cardHistory.filter { it.timestamp <= dayEndTs }.maxByOrNull { it.timestamp }
    ?: cardHistory.minByOrNull { it.timestamp }
```

Für Tage **vor** dem ersten aufgezeichneten Preis wird der *früheste* Preis rückwärts
fortgeschrieben. Ein am 20.10. gekaufter Artikel zeigt im „30 Tage"-Chart also eine flache Linie
ab dem 08.10., also Historie, die es nie gab. Verhalten ist per Test fixiert
(`testBuildValueSeriesNewCardUsesFirstPriceBeforeFirstPoint`), sollte aber bewusst entschieden
werden: entweder erst ab dem ersten Punkt zeichnen oder im Chart „vor Tracking" kennzeichnen.

### 🟡 F — Gesamtwert ignoriert die Generationen-Filter

`MainScreen.kt:221` zeigt bei aktivem Filter „Gesamtwert inklusive ausgeblendeter Generationen",
`MainViewModel.totalCollectionValue` summiert aber immer **alle** Karten. Der Hinweistext ist
korrekt, aber der angezeigte Wert weicht vom sichtbaren Bestand ab — für Nutzer schwer
nachvollziehbar (Filter-Chips suggerieren eine Einschränkung).

### 🟡 G — `EXPECTED_ROOM_IDENTITY_HASH` ist ein stiller Drift-Punkt

`CatalogImporter.kt:103` dupliziert den Hash aus `app/schemas/.../AppDatabase/17.json`,
`CURRENT_CATALOG_SCHEMA_VERSION = 17` dupliziert die Version. Die Pipeline prüft den Hash beim
Bauen, aber **kein CI-Schritt vergleicht die Kotlin-Konstanten mit dem Schema-JSON**. Bei einem
Schema-Bump ohne Anpassung dieser Konstanten lehnt die App jeden neuen Katalog mit
„Catalog schema identity mismatch" ab — der Fehler fällt erst beim Nutzer auf.
Empfehlung: kleiner Guard (wie `tools/verify_migration_16_17.py`) im Android-Validation-Workflow.

### 🔵 H — Kleinere Punkte

- `MainActivity.kt:192`: `cardMap` der Wunschliste wird nur aktualisiert, wenn `ids.isNotEmpty()`;
  eine geleerte Wunschliste lässt veraltete Karten im `cardMap` zurück.
- `wiring-check.sh` meldet `MainActivity` als „UNREFERENZIERT" (False Positive, Verweis erfolgt im
  Manifest) — der Check erzeugt dadurch dauerhaft Rauschen.
- `UpdateScheduler.schedulePeriodicSync` nutzt `PeriodicWorkRequest` + `setInitialDelay`; die
  eingestellte Sync-Uhrzeit wird von WorkManager nur angenähert (Doze/Drift), nicht garantiert.
- Vier `!!`-Stellen (`SettingsScreen.kt`, `WishlistScreen.kt`, `CollectionExporter.kt`,
  `MainActivity.kt`) sind durch vorherige Null-Checks abgesichert, aber empfindlich gegen spätere
  Refactorings.
- CI-Warnungen: `actions/checkout@v4`, `setup-python@v5`, `upload-artifact@v4`,
  `setup-java@v4`, `gradle/actions/setup-gradle@v4` laufen auf deprecated Node.js 20;
  `setup-java` sollte auf `v5` (Ubuntu-26-Migration ab 19.10.2026 beachten).

---

## 3. Empfohlene Reihenfolge

1. **A** (Datenverlust/Duplikate in der Sammlung) und **B** (Offline-Erststart) — Nutzerdaten bzw.
   Kernversprechen betroffen.
2. **D** (Sammelfortschritt nach Import) — falsche Zahlen sichtbar im UI.
3. **C** (instrumentierte Tests wieder in CI) und **G** (Schema-Hash-Guard) — verhindern, dass A/D
   unbemerkt zurückkommen.
4. **E/F** (Chart-Historie, Filter-Gesamtwert) — Anzeige-/Erwartungsfragen, vorher Produktentscheidung.
5. **H** — Aufräumen.

---

## 4. Umgesetzt (2026-11-07)

| Befund | Fix | Dateien |
| --- | --- | --- |
| A | `setAsPrimaryCard` aktualisiert eine vorhandene Zeile (Menge, Details, ID bleiben) statt eine zweite anzulegen; `clearOtherPrimaryRows` entzieht der alten Zeile den Favoriten-Flag. Reine Entscheidungslogik in `PrimaryCardAssignment.kt` + neuer Unit-Test. `addSecondaryCard` legt ebenfalls keine Duplikate mehr an und übernimmt die Kartensprache (`language`-Parameter, Default `de`). | `CollectionRepository.kt`, `UserCardDaoV2.kt`, `PrimaryCardAssignment.kt`, `PokemonDetailViewModel.kt`, `PrimaryCardAssignmentTest.kt` |
| B | `.gitignore` nimmt `app/src/main/assets/start_catalog.sqlite` explizit aus; `app/src/main/assets/.gitkeep` hält den Ordner. CI-Step prüft, dass das Asset nicht ignoriert wird. Doku in `tools/catalog-builder/README.md` ergänzt. | `.gitignore`, `app/src/main/assets/`, `android-validation.yml` |
| C | Emulator-Schritt mit `reactivecircus/android-emulator-runner@v2` (API 34, x86_64, headless, `disable-animations`) führt `connectedDebugAndroidTest` wieder aus. | `android-validation.yml` |
| D | Import schreibt `pokemonId = pokemonDexId`; die Generationen-Gruppierung fällt zusätzlich auf `pokemonDexId` zurück (repariert auch Altbestände). Test ergänzt. | `CollectionExporter.kt`, `GenerationGrouping.kt`, `GenerationGroupingTest.kt` |
| G | Neuer Guard `tools/check_catalog_schema_constants.py` (+ 5 Unit-Tests) vergleicht `@Database(version)`, `CURRENT_CATALOG_SCHEMA_VERSION` und `EXPECTED_ROOM_IDENTITY_HASH` mit dem neuesten Room-Schema; in CI eingehängt. | `tools/check_catalog_schema_constants.py`, `tools/test_check_catalog_schema_constants.py`, `android-validation.yml` |
| H | Verwaiste Wunschlisten-Karten werden nicht mehr zurückgelassen (`cardMap` wird immer neu gesetzt, gebatchte Query); `wiring-check.sh` meldet die nur über das Manifest referenzierten Komponenten nicht mehr als tot (und dokumentiert sie statt sie still zu unterdrücken). | `MainActivity.kt`, `scripts/wiring-check.sh` |

Lokal geprüft (kein JDK/SDK in dieser Umgebung, Kotlin-Unit-Tests daher nicht ausführbar):
`check_catalog_schema_constants.py` (5 Tests), 61 Python-Tests der Katalog-Pipeline,
`verify_migration_16_17.py`, `check_migration_source.py`, `accessibility-check.py` (85 Dateien),
`wiring-check.sh` (mit Gegenprobe: ungenutzte Testklasse wird weiterhin gemeldet).

Offen (Produktentscheidung nötig): **E** (Chart zeigt vor dem ersten Messpunkt eine erfundene
flache Historie) und **F** (Gesamtwert ignoriert den Generationen-Filter).

---

## 5. Zweite Review-Runde und Umsetzung (2026-11-07)

### 5.1 Umgesetzt

| Befund | Fix | Dateien |
| --- | --- | --- |
| 1 – Tauschliste ohne Kartendaten | **Funktion komplett entfernt** (Nutzerentscheidung: „die benötige ich gar nicht"). Damit entfallen der Export-Callback, der Button im Preisentwicklungs-Tab, 13 tote String-Ressourcen und der zugehörige Test; `TradeListExporter.kt` ist gelöscht. Der Scanner-Regex-Test bleibt erhalten. | `TradeListExporter.kt` (gelöscht), `MainScreen.kt`, `PriceDevelopmentTab.kt`, `strings.xml`, `Phase3Test.kt` |
| 3 – Migration ohne Quellschema-Prüfung | Vor dem Lesen wird die Legacy-DB geprüft (`sqlite_master` + `PRAGMA table_info(user_cards)`). Ist sie unbrauchbar (z. B. 0-Byte-Datei), wird die Migration als „keine Legacy-DB" übersprungen statt als `Error` den Start zu blockieren. Entscheidungslogik als reine Funktion + 4 Unit-Tests. | `UserMigrationHelper.kt`, `UserMigrationTest.kt` |
| 4 – Berechtigungsdialog bei jedem Start | Der Rationale-Dialog erscheint nur noch, wenn Preisalarme in den Einstellungen aktiviert sind (`MainUiState.priceAlertsEnabled`, Default `false`). | `MainViewModel.kt`, `MainScreen.kt` |
| 5 – Zeitscheibe greift beim Download nicht | `copyStreamLimitedWithProgress` prüft jetzt pro 8-KB-Block eine Stopp-Bedingung, gibt `false` zurück und der Teil-Download wird verworfen (`CatalogImportResult.Deferred` → Folgelauf). Durchgereicht über `SyncRepository.importRemoteCatalog(shouldStop = …)` aus `ResumableCatalogWorker` (`isStopped` oder 8-Minuten-Budget). 2 Unit-Tests. | `CatalogImporter.kt`, `SyncRepository.kt`, `ResumableCatalogWorker.kt`, `CatalogImporterTest.kt` |
| 6 – Kamera-OCR auf Querformat festgenagelt | `setTargetResolution(Size(1280, 720))` ist durch einen `ResolutionSelector` mit 4:3-AspectRatioStrategy und `ResolutionStrategy(1280×1280)` ersetzt; das Analysebild wird nicht mehr in eine Querformat-Box gezwungen. | `CardScannerScreen.kt` |
| 7 – Performance | `recordPricePoints` lädt Karten gebatcht statt mit einer Query pro Karte; `SetProgressViewModel` berechnet die Katalog-Zählung nur noch bei Katalog-, nicht bei Sammlungsänderungen; `SettingsScreen` ruft `getAllCardIds()` ohne redundantes `.toSet()` auf. | `PriceRepository.kt`, `SetProgressViewModel.kt`, `SettingsScreen.kt` |

### 5.2 Nachprüfung: Befund 2 (Backup/Restore) war falsch

Die zweite Runde hatte notiert, die Sammlung gehe beim Restore verloren. Das ist **widerlegt**:
Die Sammlung liegt in `user_database` (`UserDatabase`, Tabellen `user_cards`, `price_history`,
`wishlist`), und genau diese Datei inklusive `-wal`/`-shm` ist in `backup_rules.xml` und
`data_extraction_rules.xml` **included**. Ausgeschlossen ist nur `tcg_sammlung_database` — das ist
der 60-MB-Katalog plus die Legacy-Tabelle, die nach der Migration nicht mehr gebraucht wird.
Nach einem Restore muss der Katalog neu geladen werden (Download bzw. Start-Katalog), die eigene
Sammlung bleibt erhalten. Keine Änderung nötig.

### 5.3 Offen

- **E** (`PriceHistoryUtils.kt`, Fortschreibung vor dem ersten Messpunkt) und **F** (Gesamtwert
  ignoriert den Generationen-Filter) — weiterhin Produktentscheidung.
- `start_catalog.sqlite` ist nicht eingecheckt; per `tools/catalog-builder/make_start_catalog.py`
  erzeugen (CI prüft nur, dass das Asset nicht ignoriert wird).
