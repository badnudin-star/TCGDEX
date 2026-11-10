# Offizielle Set-Logos 2026-11-07 (Runde 17)

Schritt 1 der Ergänzungs-Route: Die Sets der aktuellen Ära bekommen funktionierende
offizielle Logos. Rein pipeline-seitig — keine App-, Schema- oder APK-Änderung
(Schema bleibt v19); die App lädt die Logos beim nächsten Katalog-Sync über die
bestehende `setLogoUrl`-Spalte (Coil, Badge-Fallback unverändert).

## Ausgangslage

TCGdex liefert für fast jedes Set eine `logo`-URL auf `assets.tcgdex.net` — der CDN
rendert diese Bilder jedoch nachweislich nicht (404). Ergebnis: Die App zeigt für
praktisch alle Sets nur das Text-Badge. Offizielle Set-Logos existieren nur für die
aktuelle Ära (Swert & Schild und älter: NoSuchKey), und eine offizielle deutsche
Kartendatenbank gibt es nicht — wohl aber **deutsch lokalisierte Set-Logos** auf der
offiziellen Galerie-Seite `tcg.pokemon.com/de-de/all-galleries/`.

## Umsetzung

### 1. `tools/catalog-builder/set_logo_overrides.json` (neu)

Mapping `TCGdex-Set-ID → offizielles Logo-PNG` auf dem CloudFront-CDN von The Pokémon
Company International (`d1i787aglh9bmb.cloudfront.net/assets/img/global/cards/de-de/…`),
23 Set-IDs aus 22 Galerien (sv10.5b + sv10.5w teilen das gemeinsame
Schwarze-Blitze/Weiße-Flammen-Logo; `30th` enthalten, `30th-c` ohne eigenes Logo).
Set-ID-Mapping generiert aus dem MIT-lizenzierten `data/galleries/index.json` von
setkeeper; die Logo-URLs stammen von der offiziellen deutschen Galerie-Seite
(Attribution in `_source`/`_note` der Datei).

### 2. `build_catalog.py`: Override-Pass

`apply_set_logo_overrides()` läuft nach dem Sets-Upsert und **vor** dem
Image-Fallback-Pass in `populate_database()`:

- `sets.logoUrl` wird für gemappte Sets **bedingungslos** überschrieben (die
  TCGdex-URLs sind tot, COALESCE würde sie erhalten) und auf `cards.setLogoUrl`
  gespiegelt — auch da, wo der Karten-Snapshot noch die tote URL trägt.
- Danach füllt der bestehende Mirror-Pass NULL-Spalten aus der Sets-Tabelle,
  englische Gap-Fill-Ersatzkarten erben die offiziellen URLs automatisch.
- Idempotent, läuft auf `--resume` gefahrlos erneut; fehlende/defekte JSON-Datei
  degradiert zu einer Warning (Sets behalten ihre TCGdex-URLs).

### 3. `collect_set_images.py`: Host-Filter + NULL-Schutz

- Neue Konstante `TCGDEX_CDN_HOST` + `_is_selfhost_candidate()`: Download und
  Self-Hosting nur noch für `assets.tcgdex.net`-URLs. Die offiziellen CloudFront-Links
  sind **Verweise, keine Kopien** — sie werden nicht heruntergeladen (Recht: direkte
  Verlinkung statt Redistribution), nicht als Kandidaten gezählt und nicht umgeschrieben.
- Karten-UPDATE jetzt mit `COALESCE`: Eine Zeile, die nur ein neues Symbol bekommt
  (z. B. Bulbapedia-Fallback), verliert ihr offizielles Logo nicht mehr (vorher wurde
  `setLogoUrl` bedingungslos mit NULL überschrieben).
- Neue Statistik `externalReferences` („N Sets keep an external reference").

## Erwartetes Pipeline-Verhalten

Der TCGdex-Teil von `collect_set_images` bleibt wie bisher bei „0 Bilder + Warning"
(CDN liefert die Set-Symbole nicht) — das ist der bekannte Zustand, Badge-Fallback
deckt die Symbole ab. Neu: 23 Sets mit offiziellem de-de-Logo als funktionierendem
Link in `sets.logoUrl` und `cards.setLogoUrl`.

## Tests

- `test_build_catalog.py`: Override lädt, überschreibt Sets + Karten-Snapshot,
  lässt ungemappte Sets unberührt, ist idempotent, Mirror-Pass füllt NULL-Zeilen.
- `test_collect_set_images.py`: Externe Hosts werden nie kontaktiert/nicht gezählt,
  Symbol-Rewrite erhält das externe Logo auf den Karten (Regressionsschutz),
  Host-Check-Helper.
- Suite: 85 + 16 Tests, grün.
