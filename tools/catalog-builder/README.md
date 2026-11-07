# Katalog-Pipeline

Dieses Werkzeug baut den Offline-Katalog für die Android-App. Es läuft **nur beim expliziten lokalen Aufruf oder in der GitHub Action**; die App selbst führt kein Scraping aus.

## Quellen und Umfang

- **TCGdex API:** Sets und vollständige Kartendetails in `en`, `fr`, `es`, `es-mx`, `it`, `pt`, `pt-br`, `pt-pt`, `de`, `nl`, `pl`, `ru`, `ja`, `ko`, `zh-tw`, `id`, `th`, `zh-cn`.
- **PokéAPI:** Pokémon-Spezies und lokalisierte Namen als Snapshot.
- **Bulbapedia MediaWiki API:** optionaler, serieller Abgleich von Set-Titeln. Diese Titel werden nur im Coverage-Bericht aufgeführt und nicht als Kartendatensätze übernommen.

Die HTTP-Aufrufe verwenden einen eigenen User-Agent, Timeout, Backoff und höchstens vier parallele Detailabfragen. Fehler pro Sprache/Karte werden protokolliert; der Coverage-Bericht zeigt tatsächliche Zähler. Ein Prozentwert wird nicht erfunden, wenn keine belastbare offizielle Bezugsgröße verfügbar ist.

## Lokal bauen

Voraussetzungen: Python 3.11+ und Internetzugang zu den genannten APIs. Die Runtime-Abhängigkeiten sind auf `requests` begrenzt; für die Tests wird zusätzlich `pytest` benötigt.

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r tools/catalog-builder/requirements-dev.txt
python -m pytest tools/catalog-builder -q
# alternativ ohne pytest:
python -m unittest discover -s tools/catalog-builder -p 'test_*.py' -v
python tools/catalog-builder/build_catalog.py
```

Für einen Lauf ohne den optionalen Bulbapedia-Abgleich:

```bash
python tools/catalog-builder/build_catalog.py --skip-bulbapedia
```

Die Ausgabe enthält `catalog.sqlite`, `manifest.json` und `coverage_report.md`. Die Datenbank wird aus `app/schemas/com.pokemontcg.sammlung.data.local.AppDatabase/17.json` erstellt und enthält ausschließlich die Katalogtabellen `sets`, `cards`, `pokemon`, `card_pokemon`, `pokemon_names` sowie `room_master_table` für Room. Tabellen mit Sammlungs- oder sonstigen Nutzerdaten sind ausgeschlossen.

## Start-Katalog für die App erzeugen

Nach einem erfolgreichen Build einen kompakten deutschen/englischen Startkatalog erzeugen:

```bash
python tools/catalog-builder/make_start_catalog.py \
  --source tools/catalog-builder/catalog.sqlite \
  --output app/src/main/assets/start_catalog.sqlite
```

Das Skript kopiert die verifizierte Datenbank, behält nur `de`- und `en`-Karten sowie die zugehörigen Sets und Pokémon-Namen, prüft `PRAGMA integrity_check` und entfernt verwaiste Verknüpfungen. Danach die App neu bauen. Die Datei ist ein Build-Artefakt und sollte nur nach einem erfolgreichen Schema- und Coverage-Check aktualisiert werden. Fehlt `start_catalog.sqlite`, behandelt die App dies als optionalen Startkatalog und kann den normalen Sync fortsetzen.

`app/src/main/assets/start_catalog.sqlite` ist in `.gitignore` ausdrücklich von der allgemeinen `*.sqlite`-Regel ausgenommen, damit der Startkatalog im Repository und damit im APK landet. Ohne diese Ausnahme hätte ein frisch gebautes APK keinen Offline-Katalog.

## Automatisierter Build

`.github/workflows/catalog.yml` läuft wöchentlich und manuell. Er führt die Offline-Tests aus, baut Datenbank und Berichte und veröffentlicht diese in der GitHub-Release `catalog-latest`. Der Manifest-Link verwendet `releases/download/catalog-latest/catalog.sqlite`, passend zum festen Release-Tag.

Der Workflow verwendet eine gemeinsame GitHub-Actions-Concurrency-Gruppe (`catalog-latest-release`) ohne Abbruch des laufenden Jobs. Dadurch werden geplante und manuelle Veröffentlichungen desselben Tags nicht parallel ausgeführt.

## Hinweise zur Datenintegrität

- Die SHA-256-Prüfsumme und Dateigröße werden über die tatsächlich erzeugte SQLite-Datei berechnet.
- Der Android-Importer prüft Integrität und notwendige Tabellen vor dem Import und kopiert ausschließlich Katalogtabellen in einer Transaktion.
- Der Builder schreibt keine Sammlungsdaten und greift nicht auf die UserDatabase zu.
- Deutschsprachige Karten behalten zur Kompatibilität mit bestehenden Sammlungsreferenzen ihre TCGdex-ID. Andere Sprachvarianten erhalten ein Sprachpräfix (z. B. `en:`), damit sie in der Tabelle mit Primärschlüssel `cards.id` nebeneinander existieren können. Externe Exporte mit nicht-deutschen Karten-IDs müssen dieselbe Konvention verwenden.

## Veröffentlichung nur bei vollständigem Lauf

Der Builder bricht mit Exit-Code `2` ab und erzeugt **kein** `manifest.json` oder `coverage_report.md`, wenn eine Sprache nicht geladen werden konnte, ein Sprachabruf keine Sets zurückgibt, mindestens ein Set unvollständige Kartendetails hat oder der PokéAPI-Snapshot fehlschlägt bzw. null Pokémon liefert. Ein Set-Listing, das kein JSON-Array ist, wird als API-Fehler behandelt und nicht als leere Liste interpretiert. So kann ein unvollständiger Lauf nicht versehentlich als erfolgreicher Katalog veröffentlicht werden. Der Checkpoint bleibt erhalten, damit der Lauf nach Behebung der Ursache mit `--resume` fortgesetzt werden kann. Vor einem neuen Lauf werden alte lokale Erfolgsmanifest- und Berichtsdateien entfernt, damit keine veralteten Ergebnisse wie aktuelle aussehen.

### Partial set responses

A set is not marked complete when the set-detail response contains fewer cards than the `cardCount.total`/`cardCount.official` value advertised by TCGdex. The incomplete set remains eligible for a later `--resume` run, and the publication gate rejects the run. This check is intentionally conservative: if the upstream count semantics change, verify them against a live sample before relaxing the guard.

### Wiederherstellung nach unterbrochener Veröffentlichung

Vor dem Austausch der drei veröffentlichten Artefakte (`catalog.sqlite`, `coverage_report.md`, `manifest.json`) erstellt der Builder ein Backup und schreibt `.catalog-publish-transaction.json` atomar. Wird der Prozess während des Dateiaustauschs beendet, stellt der nächste Builder-Lauf vor dem weiteren Aufbau den vorherigen Stand aus diesem Backup wieder her. Das Manifest wird weiterhin zuletzt ausgetauscht. Diese Wiederherstellung schützt gegen Prozessabbrüche, garantiert aber keine vollständige Haltbarkeit bei Stromausfall oder defektem Datenträger.

### Recovery journal safety

The publication recovery routine only accepts backup directories created with the
`.catalog-publish-backup-` prefix directly inside the output directory. Symlinked
backup directories, path traversal names, non-boolean existence flags, and unexpected
artifact lists are rejected before any published artifact is modified.

### Artifact verification

Before publishing, `verify_catalog_artifacts.py` validates the manifest metadata types,
requires the manifest root to be a JSON object, rejects duplicate JSON keys at every nesting level, and requires the Room schema root to contain a
`database` object, requires an absolute HTTPS download URL without embedded credentials
or a fragment, and compares each language's declared card and set counts with the actual
SQLite contents. It also rejects unsupported language codes and inconsistent coverage
percentages. A hash and file size that match are not sufficient if the manifest's coverage
claims do not describe the database. Malformed JSON structures are rejected with a clear
validation error instead of failing later with an attribute/type error.


### Exact table schema verification

The artifact verifier compares the actual column order, declared SQLite affinity,
`NOT NULL` flags, default values, and primary-key positions of every catalog table
with the current exported Room schema. It also checks the names, uniqueness and
ordered columns of every declared Room index, rejecting missing or unexpected indexes.
Primary-key autoindexes are covered by the table primary-key signature. A matching
Room identity hash is not enough if the physical table or index layout has drifted.


### Unexpected SQLite objects

The artifact verifier also rejects user-defined SQLite views and triggers. The offline catalog is expected to contain only the declared Room tables and indexes; extra executable schema objects are not accepted even when the database hash and manifest have been regenerated.


### Relationship integrity verification

The artifact verifier checks that every `card_pokemon.cardId` resolves to a catalog card, every `card_pokemon.dexId` resolves to a Pokémon record, and every `pokemon_names.dexId` resolves to a Pokémon record. Localized Pokémon names must also use one of the configured language codes. These checks are explicit because the Room schema does not declare SQL foreign keys for these relationships.


### Coverage-Zähler

Die veröffentlichten Zähler `sets` und `cards` werden aus der fertigen SQLite-Datenbank berechnet, nicht aus API-Schleifenzählern. `sets` zählt die unterschiedlichen Sets, für die in der jeweiligen Sprache tatsächlich mindestens eine Karte gespeichert ist. Die Prüfung auf fehlgeschlagene oder unvollständige Set-Abfragen nutzt weiterhin die Verarbeitungsstatistik und bleibt unabhängig davon streng.


Manifest-JSON wird strikt validiert: doppelte Schlüssel sowie die nicht standardkonformen Konstanten `NaN`, `Infinity` und `-Infinity` werden abgewiesen.

### Release mapping verification

The Android validation workflow runs `scripts/verify-release-mapping.sh` after
`assembleRelease`. The script checks that the Gson/Retrofit API interfaces and DTO
classes in `com.pokemontcg.sammlung.data.api` keep their class names in R8's
`app/build/outputs/mapping/release/mapping.txt`. This catches a release mapping that
would violate the reflection/serialization keep-rule contract. The check only runs
when an actual release build has produced the mapping file; local fixture checks do
not replace a real R8 release build or API parsing smoke test.


### Android instrumentation tests in CI

The `android-validation.yml` workflow runs `connectedDebugAndroidTest` on an API 35 Google APIs x86_64 emulator after building the debug and release variants. This executes the catalog importer and database migration instrumentation tests in CI; local workflow validation alone does not prove that the emulator job succeeds.

### Automated validation workflow

The `android-validation.yml` GitHub Actions workflow runs the offline catalog-builder
unit suite, Python syntax compilation, and `scripts/wiring-check.sh` before invoking
Gradle. It then runs JVM tests, builds debug and release APKs, runs the Android
instrumentation suite on an API 35 emulator, and checks the R8 release mapping.
The job has a 60-minute limit to allow for emulator startup and a cold Gradle cache.
Build outputs and test reports are uploaded even when an earlier step fails, when
those files have been produced. A local YAML parse and passing Python suite validate
configuration and builder logic, but do not substitute for a successful hosted Android
build or emulator run.

### Compose accessibility guardrails

The Android validation workflow also runs `python scripts/accessibility-check.py`.
The static check verifies the launcher icon and round icon manifest references, rejects
hard-coded user-facing `Text("…")` strings, checks that each `TcgImage` call supplies a
`contentDescription`, and limits `contentDescription = null` to an explicit allowlist of
decorative icons with adjacent text labels. This is a source-level guardrail: it does not
replace manual TalkBack checks, 200% font scaling, contrast checks, or touch-target tests
on an emulator/device.
