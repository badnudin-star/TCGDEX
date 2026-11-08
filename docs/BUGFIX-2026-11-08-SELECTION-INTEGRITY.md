# Bug-Review Runde 10: Kartenauswahl-Datenintegrität 2026-11-08

Systematische Bug-Jagd über den Codebestand (GitHub: keine offenen Issues, CI grün).
Drei reale Fehler gefunden und behoben; alle betreffen die Sammlungs-Zeilen in
`user_cards` (eine Zeile pro Karte/Sprache, Zeilen-ID `id` ist der einzige eindeutige
Schlüssel — `cardId` kann mehrfach vorkommen, z. B. nach einem Import derselben Karte
in zwei Sprachen).

## Bug 1 (behoben): Lieblingskarte ließ sich über „Weitere Karten" mutieren

Im Dialog „Weitere Karten verwalten" wurde das Motiv der Lieblingskarte ohne Status
angezeigt (Besitz-Lookup filterte `isPrimary` raus) — inklusive Common/Vollbild-Chips.
Ein Klick darauf lief auf `addSecondaryCard` → `findUserCard` findet die **primäre**
Zeile → `quantity + 1` (bei jedem Klick erneut) und im Zweifel ein Kategorie-Rewrite
(NONE → Common/Vollbild). Da `deriveCategoryByDex` die Primärkarte gewinnt, hätte das
still die Filter des Hauptbildschirms verändert.

Fix ( doppelt abgesichert):
- `SecondaryCardDialog`: Lieblingskarten-Motive bekommen Stern + Label „Lieblingskarte"
  und **keine** Aktions-Chips mehr (nur informativ; ist zusätzlich eine weitere Zeile
  vorhanden, gibt es weiterhin den Lösch-Button genau dafür).
- `CollectionRepository.addSecondaryCard`: Repository-Guard — existierende Zeile mit
  `isPrimary` wird nie über diesen Pfad angetastet (frühzeitiges Return + Log).

## Bug 2 (behoben): Löschen entfernte alle Sprachzeilen desselben Drucks

„Entfernen" (Dialog und Detail-Screen) löschte über `DELETE … WHERE cardId = ?` —
alle Zeilen des Drucks, also z. B. DE- und EN-Besitz derselben Karte gleichzeitig,
obwohl nur eine sichtbare Zeile gemeint war. Ein Import kann solche Mehrfachzeilen
erzeugen (Import-Schlüssel ist `cardId to language`).

Fix: Löschen konsequent über die **Zeilen-ID** (`deleteById(rowId)`), Kette durchgezogen:
`SecondaryCardDialog.onRemoveCard(Long)` → `PokemonDetailScreen.onRemoveSecondary(Long)` →
`PokemonDetailViewModel.removeSecondaryCard(rowId)` → `CollectionRepository.removeUserCardById`.
Die cardId-basierte DAO/Repository-Methode wurde ersatzlos entfernt.

## Bug 3 (behoben): Editor bearbeitete bei Mehrfachzeilen immer die erste

Der Karten-Editor (Menge/Zustand/Sprache/Notizen) suchte die zu bearbeitende Zeile per
`cardId` — bei mehreren Sprachzeilen desselben Drucks landete jede Änderung in der ersten
Zeile. Außerdem las `openCardEditor` die Kategorie über einen cardId-Lookup.

Fix: Der Editor-Kontext ist jetzt das Paar `(CardEntity, UserCardEntityV2)`; persistiert
wird über `updateCardDetails(rowId, …)` mit eindeutiger Zeilen-ID. `updatePrimaryCategory`
nutzt ebenfalls `primary.id`.

## Beobachtet, bewusst NICHT geändert

- **Editor-Primär-Toggle wirkt sofort** (nicht erst über „Bestätigen"): Wer den
  Lieblingskarten-Schalter umlegt und danach „Abbrechen" wählt, hat die Änderung bereits.
  Ein Umstellen auf Bestätigen hätte ein Race zwischen `setAsPrimaryCard` und
  `updateCardDetails` (beides separate Coroutinen, gleicher Klick) eingeführt und
  Mengen-Änderungen verwerfen können. Switches mit sofortiger Wirkung sind gängiges
  Muster; Detailfelder gelten weiterhin erst mit „Bestätigen".

## Qualitätssicherung

- Keine Schema-Änderung (Room v18 unverändert, Identity-Hash geprüft).
- Guards: accessibility, wiring, Room-Identity, Migration-Source, Katalog-Konstanten — grün.
- CI (Android Validation) kompiliert die komplette Signatur-Kette (Dialog → Screen →
  ViewModel → Repository → DAO).
