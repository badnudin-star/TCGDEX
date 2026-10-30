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

Die App lädt `manifest.json` von dieser Release-Adresse. Die Adresse steht an zwei Stellen und muss auf dieses Repository zeigen (`NAME/REPO` ersetzen):
- `app/src/main/java/com/pokemontcg/sammlung/data/repository/SyncRepository.kt` (`CATALOG_MANIFEST_URL`)
- `tools/catalog-builder/build_catalog.py` (Feld `url` im Manifest)

## Dokumentation
Siehe Ordner `docs/`, Quellen und Lizenzen in `docs/SOURCES.md`.

## Hinweis
Inoffizielles Fan-Projekt, nicht mit The Pokémon Company, Nintendo, Creatures oder GAME FREAK verbunden.
Pokémon und Pokémon-Namen sind Marken ihrer Inhaber. Kartenbilder sind nicht Teil dieses Repositories.
