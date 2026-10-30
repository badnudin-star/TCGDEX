# P3-14 – Veröffentlichungskalender

## Verhalten

- Der Kalender zeigt nur Sets mit einem gültigen ISO-Datum (`YYYY-MM-DD`), das nach dem heutigen lokalen Datum liegt.
- Sets werden nach dem nächsten Veröffentlichungstermin aufsteigend sortiert.
- Jeder Eintrag zeigt das Datum und die Anzahl der verbleibenden Kalendertage.
- Sets ohne Datum, mit ungültigem Datum oder mit einem Datum heute/in der Vergangenheit werden nicht als künftige Releases angezeigt.
- Die Aktion „Erinnerung“ übergibt Titel, Beschreibung sowie Start und Ende des Veröffentlichungstags als ganztägiges UTC-Kalenderereignis an die Kalender-App. Die App erstellt keinen eigenen Hintergrund-Erinnerungsdienst.

## Verifikation

`ReleaseCalendarModelsTest` prüft Reihenfolge, Ausschluss vergangener/heutiger Termine und die Berechnung der verbleibenden Tage mit einem festen Referenzdatum. Ein Test auf ungültige und leere Datumswerte ist ebenfalls enthalten.

Ein Unit-Test prüft zusätzlich, dass die Kalendergrenzen exakt den Veröffentlichungstag abdecken. Ein vollständiger Android-/Gradle-Test und ein Gerätetest müssen separat ausgeführt werden; der Kalender kann nur die Release-Daten anzeigen, die im lokalen Katalog vorhanden sind.


## Fehlerbehandlung der Kalender-Erinnerung (P3-14b)

Wenn keine Kalender-App die Einfügeaktion bearbeiten kann oder der Start aus Sicherheitsgründen verweigert wird, fängt die Erinnerungsaktion `ActivityNotFoundException` beziehungsweise `SecurityException` ab und zeigt einen lokalisierten Hinweis. Die App stürzt in diesen beiden Fällen nicht wegen der Kalenderübergabe ab.
