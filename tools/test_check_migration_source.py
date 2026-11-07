"""Regression tests for the static production migration source guard."""
import importlib.util
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("check_migration_source.py")
spec = importlib.util.spec_from_file_location("check_migration_source", MODULE_PATH)
guard = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(guard)
SOURCE = guard.SOURCE.read_text(encoding="utf-8")


class MigrationSourceGuardTest(unittest.TestCase):
    def test_current_production_migration_passes(self):
        self.assertEqual([], guard.check_source(SOURCE))

    def test_missing_migration_is_rejected(self):
        errors = guard.check_source(SOURCE.replace(
            "val MIGRATION_16_17 = object : Migration(16, 17)",
            "val MIGRATION_16_17_REMOVED = object : Migration(16, 17)",
            1,
        ))
        self.assertIn("production MIGRATION_16_17 declaration is missing", errors)

    def test_catalog_merge_must_be_non_destructive(self):
        mutated = SOURCE.replace("INSERT OR IGNORE INTO cards", "INSERT INTO cards", 1)
        errors = guard.check_source(mutated)
        self.assertTrue(any("catalog merge is non-destructive" in error for error in errors))

    def test_destructive_fallback_is_rejected(self):
        mutated = SOURCE.replace(
            "val ALL_MIGRATIONS", "builder.fallbackToDestructiveMigration()\n\n        val ALL_MIGRATIONS", 1
        )
        errors = guard.check_source(mutated)
        self.assertIn("destructive Room migration fallback is forbidden", errors)

    def test_migration_must_be_registered(self):
        start = SOURCE.find("val ALL_MIGRATIONS")
        end = SOURCE.find("@Volatile private var INSTANCE", start)
        mutated = SOURCE[:start] + SOURCE[start:end].replace("            MIGRATION_16_17,\n", "", 1) + SOURCE[end:]
        errors = guard.check_source(mutated)
        self.assertIn("MIGRATION_16_17 is not registered in ALL_MIGRATIONS", errors)


if __name__ == "__main__":
    unittest.main(verbosity=2)
