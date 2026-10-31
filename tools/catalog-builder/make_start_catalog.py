#!/usr/bin/env python3
"""Create a compact German/English starter catalog from a verified full catalog.

The full catalog remains the source of truth. This script only removes rows that are
not needed for the offline starter snapshot; it never adds user data.
"""
from __future__ import annotations

import argparse
import os
import shutil
import sqlite3

REQUIRED_TABLES = ("sets", "cards", "pokemon", "card_pokemon", "pokemon_names")
STARTER_LANGUAGES = ("de", "en")


def _table_names(db: sqlite3.Connection) -> set[str]:
    return {row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type='table'")}


def build_start_catalog(source: str, output: str) -> dict[str, int]:
    if not os.path.isfile(source):
        raise FileNotFoundError(f"Source catalog does not exist: {source}")

    output_dir = os.path.dirname(os.path.abspath(output))
    os.makedirs(output_dir, exist_ok=True)
    temp_path = f"{output}.tmp"
    shutil.copy2(source, temp_path)
    try:
        with sqlite3.connect(temp_path) as db:
            missing = [table for table in REQUIRED_TABLES if table not in _table_names(db)]
            if missing:
                raise ValueError(f"Source catalog is missing tables: {', '.join(missing)}")

            placeholders = ", ".join("?" for _ in STARTER_LANGUAGES)
            db.execute(
                f"DELETE FROM cards WHERE language NOT IN ({placeholders})",
                STARTER_LANGUAGES,
            )
            db.execute(
                "DELETE FROM card_pokemon WHERE cardId NOT IN (SELECT id FROM cards)"
            )
            db.execute(
                "DELETE FROM sets WHERE id NOT IN (SELECT DISTINCT setId FROM cards)"
            )
            db.execute(
                f"DELETE FROM pokemon_names WHERE language NOT IN ({placeholders})",
                STARTER_LANGUAGES,
            )
            integrity = db.execute("PRAGMA integrity_check").fetchone()
            if not integrity or str(integrity[0]).lower() != "ok":
                raise ValueError("Starter catalog failed PRAGMA integrity_check")
            counts = {
                "sets": db.execute("SELECT COUNT(*) FROM sets").fetchone()[0],
                "cards": db.execute("SELECT COUNT(*) FROM cards").fetchone()[0],
                "pokemon": db.execute("SELECT COUNT(*) FROM pokemon").fetchone()[0],
                "pokemon_names": db.execute("SELECT COUNT(*) FROM pokemon_names").fetchone()[0],
            }
            if counts["cards"] <= 0 or counts["sets"] <= 0:
                raise ValueError("Starter catalog is empty")
            db.commit()
            db.execute("VACUUM")
        os.replace(temp_path, output)
        return counts
    finally:
        if os.path.exists(temp_path):
            os.remove(temp_path)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", default="tools/catalog-builder/catalog.sqlite")
    parser.add_argument("--output", default="app/src/main/assets/start_catalog.sqlite")
    args = parser.parse_args()

    counts = build_start_catalog(args.source, args.output)
    print(f"Starter catalog written to {args.output}")
    for key, value in counts.items():
        print(f"  {key}: {value}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
