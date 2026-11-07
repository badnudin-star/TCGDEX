# N-4 – Room-Migrationstestplan

## Entscheidung zur v14-Basis

Es liegt keine echte v14-Datenbankdatei im Projektarchiv vor. Die Instrumentationstests verwenden deshalb die vorhandene `app/schemas/.../AppDatabase/14.json` als rekonstruierte Schema-Fixture. Sie bildet den Stand vor `MIGRATION_14_15` ab: `cards.sortNumber`, `card_pokemon`, `pokemon_names` und die in 14→15 angelegten Indizes fehlen. Der Test fügt anschließend Beispieldaten ein und öffnet die Datei über `Room.databaseBuilder` mit derselben vollständigen Migrationskette wie die App. Das ist ein reproduzierbarer Schema-Migrationstest, ersetzt aber keinen zusätzlichen Test mit einer aus einer realen Altinstallation kopierten Datenbank.

## Abgedeckte Tests

- `testAppDatabaseMigration14To15`: erzeugt die v14-Fixture und validiert 14→15.
- `testAppDatabaseMigration15To16`: validiert 15→16 und den Backfill des Update-Zeitstempels.
- `testAppDatabaseMigration16To17`: validiert 16→17 und die Umwandlung von Kartentypen sowie Detail-/Sync-Schema.
- `testAppDatabaseMigration17To18DropsLegacyUserCards`: validiert 17→18, das Entfernen der Legacy-Tabelle `user_cards` (mit `validateDroppedTables`) und dass Katalogzeilen den Drop überstehen.
- `testRoomBuilderMigratesRealV14FixtureThroughLatestSchema`: öffnet v14 über die produktive komplette Migrationskette, prüft Sortierung, mehrere Pokémon-Zuordnungen und dass die Kette ohne die Legacy-Tabelle endet.
- `testV14OpenFailsWhenRequiredMigrationIsRemoved`: entfernt absichtlich 14→15 aus der Testkette und erwartet, dass Room das Öffnen der v14-Datenbank verweigert.
- `testUserDatabaseMigration1To2`: prüft, dass ein vorhandener Sammlungseintrag mit seinen Feldern erhalten bleibt.

## Ausführung

```bash
./gradlew connectedDebugAndroidTest
```

Abnahme erwartet: `BUILD SUCCESSFUL` und alle Tests in `DatabaseMigrationTest` grün. Die Tests wurden in der Arbeitsumgebung dieses Schritts nicht ausgeführt, weil der Gradle-Wrapper die Distribution `gradle-8.7-bin.zip` von `services.gradle.org` laden müsste und DNS dort mit `UnknownHostException` scheitert. Daher ist der Status bis zur Ausführung auf einem Android-Gerät/Emulator **implementiert, aber nicht ausgeführt**.

## Entfernt (2026-11-07)

`testLegacyToV2EndToEndMigrationFixture` ist zusammen mit der Legacy-Migration selbst gelöscht
(`UserMigrationHelper.kt`, siehe `docs/FEATURES-2026-11-07.md`). Die Room-Migrationstests für
`AppDatabase` 14→17 und `UserDatabase` 1→2 bleiben unverändert bestehen.

## Schema 18 und der bewusste Datenverlust (2026-11-07)

`MIGRATION_17_18` löscht die Legacy-Tabelle `user_cards` der `AppDatabase` per
`DROP TABLE IF EXISTS`. Das ist gewollt und dokumentiert: Die Sammlung lebt seit Schema 17
ausschließlich in der separaten `UserDatabase`; die alten App-`user_cards`-Zeilen waren danach
weder lesbar noch schreibbar und werden bewusst verworfen. Geräte, die noch nie migrierte
Altdaten in dieser Tabelle tragen, verlieren genau diese Zeilen — ihre echte Sammlung in der
`UserDatabase` ist nicht betroffen. Der Offline-Check `tools/verify_migration_17_18.py`
(im Android-Validation-Workflow registriert) simuliert den Drop gegen `18.json`; Details zur
Entstehung von `18.json` und zum Identity-Hash stehen in `docs/BUG-REVIEW-2026-11-07.md`
(Abschnitt 5.5).

