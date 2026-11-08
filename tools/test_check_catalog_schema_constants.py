import unittest

from check_catalog_schema_constants import check, read_expected

DATABASE_OK = """
@Database(
    entities = [PokemonEntity::class],
    version = 17,
    exportSchema = true
)
"""

IMPORTER_OK = """
        const val EXPECTED_ROOM_IDENTITY_HASH = "545f4e1ec7d1f8ad7d5926ce320dabdb"
        const val CURRENT_CATALOG_SCHEMA_VERSION = 17
"""


class CheckCatalogSchemaConstantsTest(unittest.TestCase):
    def test_committed_sources_match_newest_schema(self):
        version, identity = read_expected()
        self.assertEqual(19, version)
        self.assertEqual(32, len(identity))

    def test_matching_sources_pass(self):
        self.assertEqual([], check(DATABASE_OK, IMPORTER_OK, 17, "545f4e1ec7d1f8ad7d5926ce320dabdb"))

    def test_version_drift_is_detected(self):
        errors = check(DATABASE_OK, IMPORTER_OK, 18, "545f4e1ec7d1f8ad7d5926ce320dabdb")
        self.assertEqual(2, len(errors))
        self.assertTrue(any("AppDatabase.kt" in error for error in errors))
        self.assertTrue(any("CURRENT_CATALOG_SCHEMA_VERSION" in error for error in errors))

    def test_identity_drift_is_detected(self):
        errors = check(DATABASE_OK, IMPORTER_OK, 17, "0" * 32)
        self.assertEqual(1, len(errors))
        self.assertIn("EXPECTED_ROOM_IDENTITY_HASH", errors[0])

    def test_missing_constants_are_reported(self):
        errors = check("@Database(version = 17)", "", 17, "545f4e1ec7d1f8ad7d5926ce320dabdb")
        self.assertEqual(3, len(errors))
        self.assertTrue(any("CURRENT_CATALOG_SCHEMA_VERSION" in error for error in errors))
        self.assertTrue(any("EXPECTED_ROOM_IDENTITY_HASH" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
