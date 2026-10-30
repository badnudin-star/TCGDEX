#!/usr/bin/env python3
"""Verify catalog artifacts before they are published to a release."""
from __future__ import annotations

import argparse
from contextlib import closing
import hashlib
import json
import math
import os
import sqlite3
from typing import Any
from urllib.parse import urlparse

from build_catalog import CATALOG_TABLES, LANGUAGES, SCHEMA_JSON_PATH, load_schema


def _reject_duplicate_json_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    """Build a JSON object while rejecting ambiguous duplicate keys at every depth."""
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"Catalog manifest contains duplicate JSON key: {key}")
        result[key] = value
    return result


def _reject_nonstandard_json_constant(value: str) -> None:
    """Reject NaN and infinities, which Python accepts although JSON does not."""
    raise ValueError(f"Catalog manifest contains non-standard JSON constant: {value}")


def verify_catalog_artifacts(db_path: str, manifest_path: str, schema_path: str = SCHEMA_JSON_PATH) -> dict[str, Any]:
    if not os.path.isfile(db_path) or os.path.getsize(db_path) <= 0:
        raise ValueError("Catalog database is missing or empty")
    if not os.path.isfile(manifest_path):
        raise ValueError("Catalog manifest is missing")

    try:
        with open(manifest_path, "r", encoding="utf-8") as handle:
            manifest = json.load(
                handle, object_pairs_hook=_reject_duplicate_json_keys,
                parse_constant=_reject_nonstandard_json_constant,
            )
    except json.JSONDecodeError as exc:
        raise ValueError(f"Catalog manifest is not valid JSON: {exc.msg}") from exc
    if not isinstance(manifest, dict):
        raise ValueError("Catalog manifest root must be a JSON object")
    schema = load_schema(schema_path)
    if not isinstance(schema, dict) or not isinstance(schema.get("database"), dict):
        raise ValueError("Room schema must contain a database object")
    expected_version = schema["database"]["version"]
    if type(manifest.get("schemaVersion")) is not int or manifest["schemaVersion"] != expected_version:
        raise ValueError("Manifest schemaVersion does not match the current Room schema")
    if not isinstance(manifest.get("version"), str) or not manifest["version"].strip():
        raise ValueError("Manifest version must be a non-empty string")
    if type(manifest.get("builtAt")) is not int or manifest["builtAt"] <= 0:
        raise ValueError("Manifest builtAt must be a positive integer timestamp")
    if type(manifest.get("size")) is not int or manifest["size"] <= 0:
        raise ValueError("Manifest size must be a positive integer")
    manifest_url = manifest.get("url")
    parsed_url = urlparse(manifest_url) if isinstance(manifest_url, str) else None
    if (parsed_url is None or parsed_url.scheme.lower() != "https" or not parsed_url.hostname
            or parsed_url.username is not None or parsed_url.password is not None
            or bool(parsed_url.fragment)):
        raise ValueError("Manifest URL must be an absolute HTTPS URL without credentials or fragment")

    digest = hashlib.sha256()
    with open(db_path, "rb") as handle:
        for block in iter(lambda: handle.read(65536), b""):
            digest.update(block)
    if manifest.get("sha256") != digest.hexdigest():
        raise ValueError("Manifest SHA-256 does not match catalog.sqlite")
    if manifest.get("size") != os.path.getsize(db_path):
        raise ValueError("Manifest size does not match catalog.sqlite")

    expected_tables = set(CATALOG_TABLES) | {"room_master_table"}
    entities = {item["tableName"]: item for item in schema["database"]["entities"]}
    with closing(sqlite3.connect(db_path)) as db:
        integrity = db.execute("PRAGMA integrity_check").fetchone()
        if not integrity or integrity[0] != "ok":
            raise ValueError(f"SQLite integrity_check failed: {integrity[0] if integrity else 'no result'}")
        unexpected_objects = db.execute(
            "SELECT type, name FROM sqlite_master "
            "WHERE type IN ('view', 'trigger') AND name NOT LIKE 'sqlite_%' "
            "ORDER BY type, name"
        ).fetchall()
        if unexpected_objects:
            raise ValueError(f"Catalog contains unexpected views or triggers: {unexpected_objects}")
        tables = {row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type='table'")}
        if tables != expected_tables:
            raise ValueError(f"Catalog table set mismatch: expected {sorted(expected_tables)}, got {sorted(tables)}")
        for table in CATALOG_TABLES:
            entity = entities.get(table)
            if not isinstance(entity, dict):
                raise ValueError(f"Required catalog table missing from Room schema: {table}")
            actual_columns = db.execute(f"PRAGMA table_info(\"{table}\")").fetchall()
            expected_fields = entity.get("fields", [])
            expected_pk = entity.get("primaryKey", {}).get("columnNames", [])
            pk_positions = {name: index + 1 for index, name in enumerate(expected_pk)}
            expected_signature = [
                (field.get("columnName"), str(field.get("affinity", "")).upper(),
                 1 if field.get("notNull") else 0, field.get("defaultValue"),
                 pk_positions.get(field.get("columnName"), 0))
                for field in expected_fields
            ]
            actual_signature = [
                (row[1], str(row[2] or "").upper(), int(row[3]), row[4], int(row[5]))
                for row in actual_columns
            ]
            if actual_signature != expected_signature:
                raise ValueError(
                    f"Catalog table schema mismatch for {table}: expected {expected_signature}, got {actual_signature}"
                )

            expected_indices = {
                index["name"]: (bool(index.get("unique", False)), tuple(index.get("columnNames", [])))
                for index in entity.get("indices", [])
            }
            actual_indices = {}
            for index_row in db.execute(f"PRAGMA index_list(\"{table}\")").fetchall():
                index_name = index_row[1]
                if index_name.startswith("sqlite_autoindex_"):
                    continue  # Primary-key autoindexes are validated by the column signature above.
                index_columns = tuple(
                    row[2] for row in db.execute(f"PRAGMA index_info(\"{index_name}\")").fetchall()
                )
                actual_indices[index_name] = (bool(index_row[2]), index_columns)
            if actual_indices != expected_indices:
                raise ValueError(
                    f"Catalog index schema mismatch for {table}: expected {expected_indices}, got {actual_indices}"
                )
        identity = db.execute("SELECT identity_hash FROM room_master_table WHERE id=42").fetchone()
        actual_cards_by_language = {
            row[0]: row[1]
            for row in db.execute("SELECT language, COUNT(*) FROM cards GROUP BY language")
        }
        actual_sets_by_language = {
            row[0]: row[1]
            for row in db.execute("SELECT language, COUNT(DISTINCT setId) FROM cards GROUP BY language")
        }
        relationship_checks = (
            ("cards.setId", "SELECT COUNT(*) FROM cards c LEFT JOIN sets s ON s.id = c.setId WHERE s.id IS NULL"),
            ("card_pokemon.cardId", "SELECT COUNT(*) FROM card_pokemon cp LEFT JOIN cards c ON c.id = cp.cardId WHERE c.id IS NULL"),
            ("card_pokemon.dexId", "SELECT COUNT(*) FROM card_pokemon cp LEFT JOIN pokemon p ON p.dexId = cp.dexId WHERE p.dexId IS NULL"),
            ("pokemon_names.dexId", "SELECT COUNT(*) FROM pokemon_names pn LEFT JOIN pokemon p ON p.dexId = pn.dexId WHERE p.dexId IS NULL"),
        )
        for relationship, query in relationship_checks:
            orphan_count = db.execute(query).fetchone()[0]
            if orphan_count:
                raise ValueError(f"Catalog relationship integrity failed for {relationship}: {orphan_count} orphan rows")
        unsupported_name_languages = {
            row[0] for row in db.execute("SELECT DISTINCT language FROM pokemon_names")
        } - set(LANGUAGES)
        if unsupported_name_languages:
            raise ValueError(f"Catalog contains unsupported pokemon_names languages: {sorted(unsupported_name_languages)}")
    expected_identity = schema["database"].get("identityHash")
    if not identity or identity[0] != expected_identity:
        raise ValueError("Catalog Room identity hash does not match the current schema")
    unknown_languages = set(actual_cards_by_language) - set(LANGUAGES)
    if unknown_languages:
        raise ValueError(f"Catalog contains unsupported card languages: {sorted(unknown_languages)}")

    coverage = manifest.get("coverage")
    if not isinstance(coverage, dict) or set(coverage) != set(LANGUAGES):
        raise ValueError("Manifest coverage must contain exactly the configured languages")
    for language, stats in coverage.items():
        if not isinstance(stats, dict):
            raise ValueError(f"Coverage entry for {language} is malformed")
        for key in ("sets", "cards", "expectedCards"):
            if type(stats.get(key)) is not int or stats[key] < 0:
                raise ValueError(f"Coverage field {language}.{key} must be a non-negative integer")
        actual_cards = actual_cards_by_language.get(language, 0)
        if stats["cards"] != actual_cards:
            raise ValueError(
                f"Coverage card count for {language} is {stats['cards']}, but catalog.sqlite contains {actual_cards}"
            )
        actual_sets = actual_sets_by_language.get(language, 0)
        if stats["sets"] != actual_sets:
            raise ValueError(
                f"Coverage set count for {language} is {stats['sets']}, but catalog.sqlite contains {actual_sets}"
            )
        coverage_percent = stats.get("coveragePercent")
        expected_cards = stats["expectedCards"]
        if expected_cards == 0:
            if coverage_percent is not None:
                raise ValueError(f"Coverage field {language}.coveragePercent must be null when expectedCards is zero")
        else:
            expected_percent = round(min(100.0, actual_cards * 100.0 / expected_cards), 2)
            if (type(coverage_percent) not in (int, float) or not math.isfinite(coverage_percent)
                    or coverage_percent != expected_percent):
                raise ValueError(
                    f"Coverage percentage for {language} is inconsistent: expected {expected_percent}, got {coverage_percent}"
                )

    return {"database": os.path.abspath(db_path), "size": manifest["size"],
            "sha256": manifest["sha256"], "schemaVersion": expected_version,
            "languages": len(coverage), "integrity": "ok"}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", default=os.path.dirname(os.path.abspath(__file__)))
    parser.add_argument("--schema", default=SCHEMA_JSON_PATH)
    args = parser.parse_args()
    db_path = os.path.join(args.output_dir, "catalog.sqlite")
    manifest_path = os.path.join(args.output_dir, "manifest.json")
    try:
        result = verify_catalog_artifacts(db_path, manifest_path, args.schema)
    except (OSError, ValueError, json.JSONDecodeError, sqlite3.Error) as exc:
        print(f"ERROR: catalog artifact verification failed: {exc}")
        return 2
    print("Catalog artifact verification passed:")
    for key, value in result.items():
        print(f"- {key}: {value}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
