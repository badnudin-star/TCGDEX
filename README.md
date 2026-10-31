# TCG Sammlung

Android-App zum Verwalten einer Pokémon-Sammelkarten-Sammlung (Kotlin, Jetpack Compose, Room, WorkManager).
Status: in Entwicklung.

## Voraussetzungen
- Android Studio (aktuelle Version), JDK 17, Android SDK 35
- Mindestens Android 8.0 (minSdk 26)

## Bauen
```
./gradlew assembleDebug
```
Windows: `gradlew.bat assembleDebug`

## Release-Signierung
1. `keystore.properties.example` nach `keystore.properties` kopieren und ausfüllen.
2. Anleitung: `docs/KEYSTORE_INSTRUCTIONS.md`

`keystore.properties`, `*.jks` und `local.properties` stehen in der `.gitignore` und werden nie hochgeladen.

## Karten-Katalog
Der Katalog (Sets, Karten, Namen in vielen Sprachen) wird nicht in der App gebaut, sondern von einer Pipeline:
- Skript: `tools/catalog-builder/build_catalog.py` (Anleitung: `tools/catalog-builder/README.md`)
- Automatisch: GitHub Action "Build Catalog Pipeline" (jeden Sonntag und per Hand)
- Ergebnis: Release `catalog-latest` mit `catalog.sqlite` und `manifest.json`

Die App lädt `manifest.json` von dieser Release-Adresse. Die URL wird als `BuildConfig.CATALOG_MANIFEST_URL` aus `app/build.gradle.kts` gesetzt und kann beim Build überschrieben werden:
- Standard: `https://github.com/badnudin-star/TCGDEX/releases/download/catalog-latest/manifest.json`
- Eigene URL: `./gradlew assembleDebug -PcatalogManifestUrl=https://…/manifest.json`
- Leer (`-PcatalogManifestUrl=`) deaktiviert den Remote-Katalog; die App nutzt dann nur den lokalen Startkatalog.
- Die Download-URL im Manifest wird weiterhin von `tools/catalog-builder/build_catalog.py` erzeugt.

## Dokumentation
Siehe Ordner `docs/`, Quellen und Lizenzen in `docs/SOURCES.md`.

## Hinweis
Inoffizielles Fan-Projekt, nicht mit The Pokémon Company, Nintendo, Creatures oder GAME FREAK verbunden.
Pokémon und Pokémon-Namen sind Marken ihrer Inhaber. Kartenbilder sind nicht Teil dieses Repositories.
