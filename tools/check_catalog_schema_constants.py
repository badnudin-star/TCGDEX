#!/usr/bin/env python3
"""Ensure the catalog importer constants match the committed Room schema.

`CatalogImporter` duplicates the Room schema version and identity hash of the
newest committed `AppDatabase` schema. The catalog pipeline writes that hash into
every published `catalog.sqlite` and the importer rejects a catalog whose hash
differs. Without this guard a schema bump that forgets the constants only shows up
on user devices as "Catalog schema identity mismatch".
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCHEMA_DIR = ROOT / "app/schemas/com.pokemontcg.sammlung.data.local.AppDatabase"
DATABASE_SOURCE = ROOT / "app/src/main/java/com/pokemontcg/sammlung/data/local/AppDatabase.kt"
IMPORTER_SOURCE = ROOT / "app/src/main/java/com/pokemontcg/sammlung/data/importer/CatalogImporter.kt"


def newest_schema(schema_dir: Path = SCHEMA_DIR) -> tuple[int, Path]:
    """Return (version, path) of the highest numbered committed schema file."""
    versions = sorted(
        (int(path.stem), path) for path in schema_dir.glob("*.json") if path.stem.isdigit()
    )
    if not versions:
        raise FileNotFoundError(f"No Room schema JSON found in {schema_dir}")
    return versions[-1]


def read_expected(schema_dir: Path = SCHEMA_DIR) -> tuple[int, str]:
    version, path = newest_schema(schema_dir)
    data = json.loads(path.read_text(encoding="utf-8"))
    database = data.get("database") or {}
    if int(database.get("version", -1)) != version:
        raise ValueError(f"{path.name} declares version {database.get('version')} but is named {version}")
    identity = database.get("identityHash")
    if not isinstance(identity, str) or not re.fullmatch(r"[0-9a-f]{32}", identity):
        raise ValueError(f"{path.name} has no valid identityHash")
    return version, identity


def check(
    database_source: str,
    importer_source: str,
    expected_version: int,
    expected_hash: str,
) -> list[str]:
    """Return all mismatches between the Kotlin sources and the committed schema."""
    errors: list[str] = []

    room_version = re.search(r"version\s*=\s*(\d+)\s*,", database_source)
    if room_version is None:
        errors.append("AppDatabase.kt: @Database version not found")
    elif int(room_version.group(1)) != expected_version:
        errors.append(
            f"AppDatabase.kt: @Database version {room_version.group(1)} != newest schema {expected_version}"
        )

    importer_version = re.search(
        r"const val CURRENT_CATALOG_SCHEMA_VERSION\s*=\s*(\d+)", importer_source
    )
    if importer_version is None:
        errors.append("CatalogImporter.kt: CURRENT_CATALOG_SCHEMA_VERSION not found")
    elif int(importer_version.group(1)) != expected_version:
        errors.append(
            "CatalogImporter.kt: CURRENT_CATALOG_SCHEMA_VERSION "
            f"{importer_version.group(1)} != newest schema {expected_version}"
        )

    importer_hash = re.search(
        r'const val EXPECTED_ROOM_IDENTITY_HASH\s*=\s*"([^"]+)"', importer_source
    )
    if importer_hash is None:
        errors.append("CatalogImporter.kt: EXPECTED_ROOM_IDENTITY_HASH not found")
    elif importer_hash.group(1).lower() != expected_hash.lower():
        errors.append(
            f"CatalogImporter.kt: EXPECTED_ROOM_IDENTITY_HASH {importer_hash.group(1)} "
            f"!= schema {expected_hash}"
        )

    return errors


def main() -> int:
    expected_version, expected_hash = read_expected()
    errors = check(
        DATABASE_SOURCE.read_text(encoding="utf-8"),
        IMPORTER_SOURCE.read_text(encoding="utf-8"),
        expected_version,
        expected_hash,
    )
    if errors:
        print("Catalog schema constant check: FAIL")
        for error in errors:
            print(f"- {error}")
        return 1
    print(
        f"Catalog schema constant check: PASS (version {expected_version}, "
        f"identity {expected_hash})"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
