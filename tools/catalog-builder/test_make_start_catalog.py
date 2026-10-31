import os
import sqlite3
import tempfile
import unittest
import shutil

from make_start_catalog import build_start_catalog


class TestMakeStartCatalog(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.mkdtemp()
        self.source = os.path.join(self.temp_dir, "catalog.sqlite")
        self.output = os.path.join(self.temp_dir, "start_catalog.sqlite")
        with sqlite3.connect(self.source) as db:
            db.executescript(
                """
                CREATE TABLE sets (id TEXT PRIMARY KEY);
                CREATE TABLE cards (id TEXT PRIMARY KEY, setId TEXT NOT NULL, language TEXT NOT NULL);
                CREATE TABLE pokemon (dexId INTEGER PRIMARY KEY);
                CREATE TABLE card_pokemon (cardId TEXT NOT NULL, dexId INTEGER NOT NULL);
                CREATE TABLE pokemon_names (language TEXT NOT NULL, dexId INTEGER NOT NULL);
                INSERT INTO sets VALUES ('s1'), ('s2'), ('s3');
                INSERT INTO cards VALUES ('de-1', 's1', 'de'), ('en:1', 's2', 'en'), ('fr:1', 's3', 'fr');
                INSERT INTO pokemon VALUES (1), (2);
                INSERT INTO card_pokemon VALUES ('de-1', 1), ('en:1', 1), ('fr:1', 2);
                INSERT INTO pokemon_names VALUES ('de', 1), ('en', 1), ('fr', 2);
                """
            )

    def tearDown(self):
        shutil.rmtree(self.temp_dir)

    def test_starter_catalog_keeps_only_german_and_english_rows(self):
        counts = build_start_catalog(self.source, self.output)

        self.assertEqual(2, counts["cards"])
        with sqlite3.connect(self.output) as db:
            self.assertEqual([("de-1", "de"), ("en:1", "en")], db.execute(
                "SELECT id, language FROM cards ORDER BY id"
            ).fetchall())
            self.assertEqual([("s1",), ("s2",)], db.execute(
                "SELECT id FROM sets ORDER BY id"
            ).fetchall())
            self.assertEqual([("de-1",), ("en:1",)], db.execute(
                "SELECT cardId FROM card_pokemon ORDER BY cardId"
            ).fetchall())
            self.assertEqual("ok", db.execute("PRAGMA integrity_check").fetchone()[0])


if __name__ == "__main__":
    unittest.main()
