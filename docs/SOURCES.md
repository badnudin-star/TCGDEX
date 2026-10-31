# Datenquellen, Lizenzen und Nutzung

Die App verwendet im Runtime-Code ausschließlich **TCGdex** und **PokéAPI** als externe Datenquellen. Andere Quellen und Scraper sind nicht Teil des laufenden App-Codes.

## 1. TCGdex

- API: <https://api.tcgdex.net/v2/{language}/>
- Asset-CDN: <https://assets.tcgdex.net/>
- Verwendung: mehrsprachige Pokémon-TCG-Kartendaten, Sets, Bild-URLs und Cardmarket-Preisdaten in EUR.
- Dokumentation: <https://tcgdex.dev/de/rest> und <https://tcgdex.dev/de/assets>
- Lizenzhinweis: Das öffentliche Repository `tcgdex/cards-database` bezeichnet die Datenbank unter MIT-Lizenz. Das bezieht sich nicht automatisch auf die Rechte an abgebildeten Pokémon-Sammelkarten oder anderen Marken-/Bildinhalten. Die App lädt Bilder direkt vom TCGdex-CDN und spiegelt oder verteilt sie nicht weiter.
- Anfrageverhalten: Die App verwendet HTTPS-GET-Endpunkte. Konkrete Parallelitäts- und Backoff-Regeln sind im jeweiligen Sync-/Worker-Code festgelegt.

## 2. PokéAPI

- API: <https://pokeapi.co/api/v2/>
- Dokumentation und Fair-Use-Hinweise: <https://pokeapi.co/docs/v2>
- Verwendung: Pokémon-Spezies und Pokédex-Namen als ergänzende Datenquelle.
- Lizenzhinweis: Das offizielle `PokeAPI/pokeapi`-Repository weist für seine Software eine BSD-3-Clause-Lizenz aus. Die API-Dokumentation erklärt die Nutzung als offen und kostenlos, belegt aber keine pauschale CC0-Lizenz für sämtliche bereitgestellten Inhalte. Daher wird hier keine CC0-Zusage für alle Daten gemacht.
- Nutzung: Anfragen begrenzen und die offizielle Fair-Use-Policy beachten.

## 3. Bulbapedia (nur Katalog-Pipeline)

- Seite: <https://bulbapedia.bulbagarden.net/wiki/Bulbapedia:Copyrights>
- Verwendung: ausschließlich beim Build des Katalogs für den seriellen Abgleich von Set-Listen. Die App selbst lädt zur Laufzeit keine Bulbapedia-Inhalte.
- Lizenzhinweis: Bulbapedia-Inhalte stehen unter **Creative Commons Attribution-NonCommercial-ShareAlike 2.5 (CC BY-NC-SA 2.5)**, nicht unter CC BY-SA 4.0. Die Pipeline wird nicht für kommerzielle Zwecke eingesetzt.

## Marken- und Bildrechte

Pokémon und Pokémon TCG sind Marken ihrer jeweiligen Rechteinhaber. Die Nutzung der APIs bedeutet keine Verbindung zu oder Unterstützung durch Nintendo oder The Pokémon Company. Die App speichert keine externen Kartenbilder als eigene Assets und verteilt sie nicht weiter; bei fehlenden oder nicht ladbaren Bildern erscheint ein lokaler Platzhalter.

## Offizielle Referenzen

- TCGdex REST API: <https://tcgdex.dev/de/rest>
- TCGdex Asset-Verwaltung: <https://tcgdex.dev/de/assets>
- TCGdex Kartendatenbank und Lizenzangabe: <https://github.com/tcgdex/cards-database>
- PokéAPI Dokumentation: <https://pokeapi.co/docs/v2>
- PokéAPI Repository und Lizenzangabe: <https://github.com/PokeAPI/pokeapi>
