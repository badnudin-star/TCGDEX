#!/usr/bin/env python3
"""Static guard ensuring the production Room migration retains required operations.

This complements (but does not replace) Room instrumentation tests: it checks the
actual Kotlin source so the offline SQLite simulation cannot silently drift away
from the production Migration implementation.
"""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/pokemontcg/sammlung/data/local/AppDatabase.kt"

REQUIRED_FRAGMENTS = {
    "pokemon table rebuild": "CREATE TABLE IF NOT EXISTS pokemon_new",
    "legacy category Common mapping": "WHEN 'Common' THEN 'COMMON'",
    "legacy category Fullart mapping": "WHEN 'Fullart' THEN 'FULLART'",
    "legacy category fallback": "ELSE 'NONE' END",
    "card detail flag column": "ALTER TABLE cards ADD COLUMN detailLoaded INTEGER NOT NULL DEFAULT 0",
    "mark existing detail cards": "UPDATE cards SET detailLoaded = 1",
    "catalog merge is non-destructive": "INSERT OR IGNORE INTO cards",
    "remove legacy catalog table": "DROP TABLE IF EXISTS catalog_cards",
    "create sync checkpoint": "CREATE TABLE IF NOT EXISTS sync_checkpoint",
}


def check_source(source: str) -> list[str]:
    """Return all guard failures for supplied AppDatabase.kt source text."""
    start = source.find("val MIGRATION_16_17 = object : Migration(16, 17)")
    if start < 0:
        return ["production MIGRATION_16_17 declaration is missing"]
    end = source.find("/** The exact migration chain", start)
    if end < 0:
        return ["could not locate end of MIGRATION_16_17 block"]
    migration = source[start:end]
    errors = [
        f"missing production migration requirement: {label}"
        for label, fragment in REQUIRED_FRAGMENTS.items()
        if fragment not in migration
    ]
    chain_start = source.find("val ALL_MIGRATIONS")
    instance_start = source.find("@Volatile private var INSTANCE")
    if chain_start < 0 or instance_start < 0 or chain_start >= instance_start:
        errors.append("could not locate ALL_MIGRATIONS registration block")
    elif "MIGRATION_16_17" not in source[chain_start:instance_start]:
        errors.append("MIGRATION_16_17 is not registered in ALL_MIGRATIONS")
    if re.search(r"fallbackToDestructiveMigration\s*\(", source):
        errors.append("destructive Room migration fallback is forbidden")
    return errors


def main() -> int:
    source = SOURCE.read_text(encoding="utf-8")
    errors = check_source(source)
    if errors:
        print("Production migration source check: FAIL")
        for error in errors:
            print(f"- {error}")
        return 1
    print(
        f"Production migration source check: PASS "
        f"({len(REQUIRED_FRAGMENTS)} requirements, migration registered, no destructive fallback)"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
