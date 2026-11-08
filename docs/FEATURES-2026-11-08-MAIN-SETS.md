# Hauptscreen & Set-Verwaltung 2026-11-08 (Runde 8)

Acht Nutzerwünsche aus einer Runde; alle app-seitig, ohne Schemaänderung (bleibt v18).

## 1. Update-Info nur, wenn wirklich ein Update läuft

**Ursache:** Der Sync-Banner auf dem Hauptscreen zeigte dauerhaft „Kartendaten werden
aktualisiert …". `observeSyncWork()` beobachtet alle Works mit dem Tag `catalog_sync` —
der tägliche Periodic-Work (03:00 Uhr) trägt denselben Tag und ist **zwischen seinen Läufen
permanent ENQUEUED**; `deriveSyncUiState` wertete ENQUEUED als aktiv.

**Fix:** Der Periodic-Work bekommt zusätzlich den Tag `UpdateScheduler.PERIODIC_SYNC_TAG`
(`catalog_sync_periodic`). Aktiv = `RUNNING` **oder** ENQUEUED/BLOCKED ohne Periodic-Tag —
manuell gestartete Syncs (inkl. Wiederholungs-Fortsetzung) zeigen den Banner weiterhin,
das wartende Tages-Update nicht mehr.

## 2. Titel „TCG Sammlung" ausgeblendet

`MainScreen`-TopBar ohne Titel (`title = {}`); String-Ressource `ui_tcg_sammlung_c2e767`
entfernt. Launcher-Name und „Über"-Dialog bleiben unberührt.

## 3. „Pokémons" → „Pokémon"

Bottom-Nav-Label `ui_pok_mons_57f3d1` korrigiert (korrektes deutsches Wort: „Pokémon").

## 4. Set-Fortschritt als unten auswählbarer Tab

Der bisherige Sets-Screen (Top-Bar-Icon + eigene Route „sets") ist jetzt der dritte Eintrag
der Bottom-Navigation: **Pokémon | Preisentwicklung | Set-Fortschritt**.

- `SetProgressScreen.kt` → `SetProgressTab` (ohne eigenes Scaffold/Back-Button; Sprachfilter
  und Sortiermenü liegen in der Tab-Inhaltsfläche).
- `SetProgressViewModel` wird in der „main"-Route erzeugt und an `MainScreen` übergeben;
  Route „sets" und Top-Bar-Icon entfallen.
- Kommende Releases (Erscheinungskalender) bleiben im Tab.

## 5. Set-Sprachen anzeigen

Sets sind im Katalog bereits global (eine Zeile pro Set-ID; deutsche Fassung gewinnt bei
`INSERT OR IGNORE`). In welchen Sprachen ein Set **existiert**, ergibt sich aus den
Sprachdrucken seiner Karten: neue DAO-Abfrage `getLanguagesBySet()` (`SELECT DISTINCT setId,
language FROM cards`) → `Map<setId, List<language>>`, angezeigt als „Sprachen: DE · EN · FR"
pro Set-Zeile (gleiche Darstellung wie die Karten in der Wunschlisten-Suche seit Runde 7).

## 6. Beide Set-Kennzeichen zeigen

Bisher zeigte die Set-Zeile nur ein Bild (`symbolUrl ?: logoUrl`). Jetzt zeigt sie **beide**:
das kleine Symbol, das auf den Karten abgedruckt ist (30 dp), und das große Set-Logo
(bis 90 dp breit). Fehlt eines (ältere Promo-Sets), wird nur das vorhandene gezeigt.

## 7. Sets anklickbar → komplette Kartenliste

Neue Route `set/{setId}` mit `SetDetailScreen` + `SetDetailViewModel`:

- **Alle Karten des Sets** — Pokémon, Trainer, Energien — aus `getCardsForSet(setId)`
  (Flow, nach `sortNumber`/Kartennummer sortiert), Sprachdrucke pro Karte zu einem Eintrag
  gemergt (`mergeCardsByRawId`, Repräsentant wie immer: bevorzugte Sprache → de → en;
  Sprachen der Karte ab zwei Drucken als Zeile).
- Header: Symbol + Logo, Reihe/Erscheinungsdatum, Set-Sprachen, Fortschrittsbalken
  „Im Besitz: X von Y Karten".
- Kartenbild antippen öffnet den Vollbild-Dialog (wie Wunschliste).
- Seltenheit kanonisiert (RarityGrouper aus Runde 7).

## 8. Sichtbar machen, welche Karten man schon hat

Pro Karte in der Set-Liste ein Badge „Im Besitz" (bzw. „Im Besitz · 2×" bei Mehrfachbesitz),
grün hinterlegt mit Häkchen — Besitz wird über die Raw-ID erkannt, d. h. ein beliebiger
Sprachdruck zählt als besessen. Der Fortschrittsbalken im Header zählt gemergte Karten.

## Technik

- `Daos.kt`: `getCardsForSet(setId): Flow<List<CardEntity>>`, `getLanguagesBySet(): Flow<List<SetLanguageRow>>`, Datenklasse `SetLanguageRow`.
- `CatalogRepository`: `getCardsForSet`, `getLanguagesBySet` (gruppiert zu Map).
- `CatalogSearchGrouping.kt`: `representativeRank` öffentlich, neue reine Funktion `mergeCardsByRawId` (wiederverwendet `MergedCatalogCard`).
- Accessibility-Guard: dekoratives Häkchen-Icon neben dem „Im Besitz"-Text allowlisted (Zähler exakt).
- Tests: `mergeCardsByRawId` (Reihenfolge, Repräsentant, Duplikate) in `CatalogSearchGroupingTest`; lokal mit kotlinc-JVM-Harness alle 32 Tests der betroffenen Klassen grün.
