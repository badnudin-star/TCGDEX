# Kartenauswahl: Sprachen-Merge & englischer Raritätsfilter 2026-11-08 (Runde 9)

## 1. Sprachdrucke zusammenführen — Lieblingskarte & Weitere Karten

Die Zusammenführung der Sprachvarianten aus der Wunschlisten-Katalogsuche (Runde 7) gilt jetzt
auch für die beiden Auswahl-Dialoge des Pokémon-Detail-Screens:

- `PokemonDetailViewModel` stellt neben `cards` (alle Drucke, unverändert für die Besitz-Anzeige)
  jetzt `mergedCards: List<MergedCatalogCard>` bereit — `mergeCardsByRawId` mit der bevorzugten
  App-Sprache als Repräsentant (Fallback de → en). Die Factory bekommt dafür den
  `AppPreferencesRepository`.
- **Lieblingskarte (`PrimaryCardSelectionDialog`)** und **Weitere Karten (`SecondaryCardDialog`)**
  zeigen pro Motiv genau einen Eintrag; ab zwei Drucken steht darunter „Sprachen: DE · EN · …".
  Die Freitextsuche durchsucht **jeden** Sprachdruck (lokalisierte Namen wie „Glumanda"/„Charmander"
  finden dasselbe Motiv, ebenso Sprache, Set, Nummer, Rarität).
- Besitz-Logik ist sprachunabhängig: Lieblingskarte ist „gewählt", wenn **irgendein** Druck der
  aktuelle Favorit ist; erneutes Auswählen behält den exakt gespeicherten Druck statt auf den
  Repräsentanten zu wechseln. Bei weiteren Karten zählt der Eintrag als hinzugefügt, wenn ein
  beliebiger Druck gespeichert ist; Entfernen löscht genau den gespeicherten Druck.
- `MergedCatalogCard` hat dafür das berechnete Feld `variantIds: Set<String>`.

## 2. Raritätsfilter: kanonische englische Gruppen überall

Der Raritätsfilter der Wunschliste (Runde 7: Chips mit den acht kanonischen englischen Gruppen,
SQL-seitige Expansion zu Rohwerten, wirkt über alle Sprachen, da TCGdex Raritäten generell
englisch speichert) war die Blaupause — sie fehlte noch in der Kartenauswahl:

- Beide Dialoge haben jetzt dieselben **kanonischen englischen Filter-Chips**
  (None, Common, Uncommon, Rare, Holo Rare, Ultra Rare, Illustration Rare, Promo; ab zwei
  vorhandenen Gruppen eingeblendet, Mehrfachauswahl, „Auswahl löschen"). Ein Eintrag passt,
  wenn **irgendein** seiner Sprachdrucke in der gewählten Gruppe liegt — auch japanische
  Symbol-Raritäten („One Diamond" → Uncommon usw.) laufen über `RarityGrouper`.
- Die Raritäts**anzeige** zeigt überall kanonische englische Begriffe statt der 47
  Rohwerte („Double rare" → „Holo Rare"): beide Auswahl-Dialoge und der
  Kategorie-Bestätigungsdialog (`CardCategoryDialog`); ohne Rarität wie bisher „keine".
- Damit ist die Raritätsdarstellung in der gesamten App einheitlich: Wunschlisten-Suche
  (R7), Set-Detail (R8), Set-Fortschritt und Kartenauswahl (R9).

## Technik

- `CatalogSearchGrouping.kt`: `MergedCatalogCard.variantIds`; neue reine Funktion
  `List<MergedCatalogCard>.filterMerged(searchQuery, selectedRarityGroups)` — gemeinsame
  Filterlogik beider Dialoge (Text über alle Drucke + Rarität über alle Drucke).
- Keine Schema-, DAO- oder Repository-Änderungen.
- Tests (lokal im kotlinc-JVM-Harness 36/36 grün): `variantIds`, `filterMerged` mit
  lokalisierten Namen über Sprachen hinweg, `filterMerged` mit Raritätsgruppen über
  Sprachen hinweg (inkl. japanischer Symbol-Rarität).
