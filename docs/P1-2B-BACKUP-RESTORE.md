# P1-2b: Backup-Regeln und Restore-Prüfung

## Inhalt der Sicherung

- `user_database`, `user_database-wal` und `user_database-shm`: die aktive V2-Sammlung.
- `sharedpref/`: allgemeine App-Preferences. Ein Migrationsstatus wird nicht mehr gespeichert, seit die Legacy-Migration entfernt ist (siehe `docs/FEATURES-2026-11-07.md`).
- `files/datastore/`: Preferences DataStore, aktuell `user_settings.preferences_pb`.
- `tcg_sammlung_database` einschließlich `-journal`, `-wal` und `-shm` wird ausgeschlossen. Die Datei enthält den herunterladbaren Kartenkatalog (und eine leere `user_cards`-Tabelle, die nur aus Schema-Hash-Gründen erhalten bleibt); die Sammlung liegt ausschließlich in `user_database`.
- Es gibt keine Backup-Regel für `catalog_database`; dieser nicht vorhandene Datenbankname wird nicht referenziert.

Android dokumentiert, dass bei einer Kombination aus Include- und Exclude-Regeln Excludes Vorrang haben und Verzeichnispfade rekursiv wirken: https://developer.android.com/identity/data/autobackup

## Restore-Test mit `adb shell bmgr`

> Nur mit Testgerät/Emulator und Testdaten durchführen. Vorher zusätzlich einen JSON-Export der Sammlung anlegen. `bmgr`-Befehle und verfügbare Transportnamen hängen von Android-Version und Gerät ab; verschlüsselte Backups können nicht mit `bmgr restore` direkt wiederhergestellt werden.

1. App starten, mindestens eine Sammlungskarte und eine Wunschlistenkarte eintragen und eine Einstellung ändern. Anzahl und Kategorien notieren.
2. Backup-Transport prüfen und aktivieren:

   ```bash
   adb shell bmgr list transports
   adb shell bmgr enable true
   adb shell bmgr backupnow com.pokemontcg.sammlung
   ```

   Erwartung: Die Ausgabe für `com.pokemontcg.sammlung` meldet `Success`. Bei Fehlern `adb logcat -d | grep -iE 'Backup|BackupManager|PFTBT'` prüfen.

3. Backup-Sets auflisten und den Token des gewünschten Test-Backups notieren:

   ```bash
   adb shell bmgr list sets
   ```

4. App-Daten auf dem Testgerät löschen (dies ist destruktiv; JSON-Export vorher sicher auf den Rechner kopieren):

   ```bash
   adb shell pm clear com.pokemontcg.sammlung
   ```

5. Wenn der Transport einen wiederherstellbaren, nicht verschlüsselten Satz anbietet, explizit wiederherstellen (TOKEN durch den Wert aus `list sets` ersetzen):

   ```bash
   adb shell bmgr restore TOKEN com.pokemontcg.sammlung
   ```

   Alternativ kann bei einem unterstützten Cloud-Transport nach Deinstallation/Neuinstallation die Systemwiederherstellung ausgelöst werden. Das hängt von den Geräteeinstellungen und dem Transport ab.

6. App öffnen und prüfen: dieselben Sammlungseinträge, Kategorien, Primärmarkierungen, Wunschliste und Einstellungen wie vor dem Test. Die Katalog-Datenbank muss nicht wiederhergestellt werden — der Katalog lädt sich nach dem Restore neu, und die Sammlung darf nicht leer starten.
7. Nach dem Test JSON-Export und dokumentierte Vorher-/Nachher-Zahlen vergleichen. Den Test als bestanden markieren, wenn alle Werte übereinstimmen.

## Grenzen

XML-Syntax und Regeln können statisch geprüft werden; ein erfolgreicher Geräte-Backup-/Restore-Lauf wurde hier nicht ausgeführt. Insbesondere muss auf dem Zielgerät bestätigt werden, dass der aktive Transport die Regeln verwendet und DataStore sowie SQLite-Daten tatsächlich wiederherstellt.
