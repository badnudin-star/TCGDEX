import hashlib
import json
import os
import sqlite3
import tempfile
import unittest
import shutil
from unittest import mock

from build_catalog import (
    CATALOG_TABLES, LANGUAGES, SCHEMA_JSON_PATH, build_coverage, create_database, fetch_bulbapedia_set_titles,
    fetch_sets, find_incomplete_catalog, generate_manifest, load_schema, populate_database, upsert_card, upsert_set,
    _publish_staged_artifacts, _recover_interrupted_publish,
)
from verify_catalog_artifacts import verify_catalog_artifacts


class TestCatalogBuilder(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.mkdtemp()

    def tearDown(self):
        shutil.rmtree(self.temp_dir)

    def test_staged_publish_replaces_snapshot_and_manifest_last_contract(self):
        staging = os.path.join(self.temp_dir, "staging")
        output = os.path.join(self.temp_dir, "output")
        os.makedirs(staging)
        os.makedirs(output)
        for name, value in (("catalog.sqlite", b"new-db"), ("coverage_report.md", b"new-report"), ("manifest.json", b"new-manifest")):
            with open(os.path.join(staging, name), "wb") as handle:
                handle.write(value)
            with open(os.path.join(output, name), "wb") as handle:
                handle.write(b"old-" + name.encode())
        _publish_staged_artifacts(staging, output)
        for name, value in (("catalog.sqlite", b"new-db"), ("coverage_report.md", b"new-report"), ("manifest.json", b"new-manifest")):
            with open(os.path.join(output, name), "rb") as handle:
                self.assertEqual(handle.read(), value)

    def test_staged_publish_refuses_missing_artifact_without_touching_snapshot(self):
        staging = os.path.join(self.temp_dir, "staging")
        output = os.path.join(self.temp_dir, "output")
        os.makedirs(staging)
        os.makedirs(output)
        with open(os.path.join(output, "manifest.json"), "wb") as handle:
            handle.write(b"known-good-manifest")
        with self.assertRaisesRegex(FileNotFoundError, "missing"):
            _publish_staged_artifacts(staging, output)
        with open(os.path.join(output, "manifest.json"), "rb") as handle:
            self.assertEqual(handle.read(), b"known-good-manifest")

    def test_staged_publish_rolls_back_when_replacement_fails_midway(self):
        staging = os.path.join(self.temp_dir, "staging")
        output = os.path.join(self.temp_dir, "output")
        os.makedirs(staging)
        os.makedirs(output)
        names = ("catalog.sqlite", "coverage_report.md", "manifest.json")
        for name in names:
            with open(os.path.join(staging, name), "wb") as handle:
                handle.write(("new-" + name).encode())
            with open(os.path.join(output, name), "wb") as handle:
                handle.write(("old-" + name).encode())

        real_replace = os.replace
        failed = {"value": False}
        def fail_once_on_report(source, destination):
            if (not failed["value"] and os.path.basename(source) == "coverage_report.md"
                    and os.path.basename(os.path.dirname(source)) == "staging"):
                failed["value"] = True
                raise OSError("simulated disk error")
            return real_replace(source, destination)

        with mock.patch("build_catalog.os.replace", side_effect=fail_once_on_report):
            with self.assertRaisesRegex(OSError, "simulated disk error"):
                _publish_staged_artifacts(staging, output)
        for name in names:
            with open(os.path.join(output, name), "rb") as handle:
                self.assertEqual(handle.read(), ("old-" + name).encode(), name)

    def test_recovery_restores_snapshot_after_simulated_process_interruption(self):
        output = os.path.join(self.temp_dir, "output")
        os.makedirs(output)
        names = ("catalog.sqlite", "coverage_report.md", "manifest.json")
        for name in names:
            with open(os.path.join(output, name), "wb") as handle:
                handle.write(("old-" + name).encode())
        backup = tempfile.mkdtemp(prefix=".catalog-publish-backup-", dir=output)
        for name in names:
            shutil.copy2(os.path.join(output, name), os.path.join(backup, name))
            with open(os.path.join(output, name), "wb") as handle:
                handle.write(("partially-new-" + name).encode())
        with open(os.path.join(output, ".catalog-publish-transaction.json"), "w", encoding="utf-8") as handle:
            json.dump({"backup_dir": os.path.basename(backup), "existed": {name: True for name in names},
                       "artifacts": list(names), "manifest_last": True}, handle)

        self.assertTrue(_recover_interrupted_publish(output))
        for name in names:
            with open(os.path.join(output, name), "rb") as handle:
                self.assertEqual(handle.read(), ("old-" + name).encode(), name)
        self.assertFalse(os.path.exists(os.path.join(output, ".catalog-publish-transaction.json")))
        self.assertFalse(os.path.exists(backup))

    def test_recovery_removes_new_files_that_did_not_previously_exist(self):
        output = os.path.join(self.temp_dir, "output")
        os.makedirs(output)
        backup = tempfile.mkdtemp(prefix=".catalog-publish-backup-", dir=output)
        with open(os.path.join(output, "catalog.sqlite"), "wb") as handle:
            handle.write(b"partial-new-db")
        for name in ("coverage_report.md", "manifest.json"):
            with open(os.path.join(output, name), "wb") as handle:
                handle.write(b"old-" + name.encode())
            shutil.copy2(os.path.join(output, name), os.path.join(backup, name))
        with open(os.path.join(output, ".catalog-publish-transaction.json"), "w", encoding="utf-8") as handle:
            json.dump({"backup_dir": os.path.basename(backup),
                       "existed": {"catalog.sqlite": False, "coverage_report.md": True, "manifest.json": True}}, handle)
        self.assertTrue(_recover_interrupted_publish(output))
        self.assertFalse(os.path.exists(os.path.join(output, "catalog.sqlite")))
        for name in ("coverage_report.md", "manifest.json"):
            with open(os.path.join(output, name), "rb") as handle:
                self.assertEqual(handle.read(), b"old-" + name.encode())

    def test_recovery_rejects_path_traversal_backup_name(self):
        output = os.path.join(self.temp_dir, "output")
        os.makedirs(output)
        outside = os.path.join(self.temp_dir, "outside")
        os.makedirs(outside)
        for name in ("catalog.sqlite", "coverage_report.md", "manifest.json"):
            with open(os.path.join(outside, name), "wb") as handle:
                handle.write(b"must-not-be-used")
        with open(os.path.join(output, ".catalog-publish-transaction.json"), "w", encoding="utf-8") as handle:
            json.dump({"backup_dir": "../outside", "existed": {name: True for name in
                       ("catalog.sqlite", "coverage_report.md", "manifest.json")}}, handle)
        with self.assertRaisesRegex(ValueError, "Invalid catalog publication recovery journal"):
            _recover_interrupted_publish(output)
        with open(os.path.join(outside, "catalog.sqlite"), "rb") as handle:
            self.assertEqual(handle.read(), b"must-not-be-used")

    def test_recovery_rejects_symlink_backup_directory(self):
        output = os.path.join(self.temp_dir, "output")
        os.makedirs(output)
        outside = os.path.join(self.temp_dir, "outside")
        os.makedirs(outside)
        link_name = ".catalog-publish-backup-malicious"
        os.symlink(outside, os.path.join(output, link_name))
        with open(os.path.join(output, ".catalog-publish-transaction.json"), "w", encoding="utf-8") as handle:
            json.dump({"backup_dir": link_name, "existed": {name: True for name in
                       ("catalog.sqlite", "coverage_report.md", "manifest.json")}}, handle)
        with self.assertRaisesRegex(FileNotFoundError, "missing or unsafe"):
            _recover_interrupted_publish(output)

    def test_recovery_rejects_non_boolean_existed_flags(self):
        output = os.path.join(self.temp_dir, "output")
        os.makedirs(output)
        backup = tempfile.mkdtemp(prefix=".catalog-publish-backup-", dir=output)
        existed = {name: True for name in ("catalog.sqlite", "coverage_report.md", "manifest.json")}
        existed["catalog.sqlite"] = "yes"
        with open(os.path.join(output, ".catalog-publish-transaction.json"), "w", encoding="utf-8") as handle:
            json.dump({"backup_dir": os.path.basename(backup), "existed": existed}, handle)
        with self.assertRaisesRegex(ValueError, "Incomplete catalog publication recovery journal"):
            _recover_interrupted_publish(output)

    def test_schema_loading_uses_current_room_version(self):
        schema = load_schema(SCHEMA_JSON_PATH)
        self.assertEqual(schema["database"]["version"], 17)
        self.assertGreater(len(schema["database"]["entities"]), 0)

    def test_set_listing_rejects_malformed_response(self):
        with self.assertRaisesRegex(ValueError, "not a JSON array"):
            fetch_sets("de", fetcher=lambda url: {"error": "upstream outage"})

    def test_publication_gate_rejects_empty_language_and_pokemon_snapshot(self):
        summary = {
            "languages": {lang: {"sets": 1, "cards": 1} for lang in LANGUAGES},
            "failedSetsByLanguage": {}, "pokemonError": None, "pokemonCount": 1,
        }
        summary["languages"]["zh-cn"]["sets"] = 0
        summary["pokemonCount"] = 0
        failures = find_incomplete_catalog(summary)
        self.assertIn("zh-cn", failures)
        self.assertIn("zero sets", failures["zh-cn"])
        self.assertIn("pokeapi", failures)
        self.assertIn("zero Pokémon", failures["pokeapi"])

    def test_database_has_exactly_catalog_tables_and_room_identity(self):
        db_file = os.path.join(self.temp_dir, "catalog.sqlite")
        conn = create_database(db_file, load_schema(SCHEMA_JSON_PATH))
        tables = {row[0] for row in conn.execute("SELECT name FROM sqlite_master WHERE type='table'")}
        self.assertEqual(tables, set(CATALOG_TABLES) | {"room_master_table"})
        identity = conn.execute("SELECT identity_hash FROM room_master_table WHERE id=42").fetchone()
        self.assertTrue(identity and len(identity[0]) == 32)
        conn.close()

    def test_card_rows_keep_language_and_multi_pokemon_links(self):
        db_file = os.path.join(self.temp_dir, "catalog.sqlite")
        conn = create_database(db_file, load_schema(SCHEMA_JSON_PATH))
        card = {
            "id": "sv1-1", "localId": "1", "name": "Bulbasaur", "category": "Pokemon",
            "rarity": "Common", "image": "https://assets.tcgdex.net/en/sv/sv01/1",
            "dexId": [1, 2], "set": {"id": "sv1", "name": "Scarlet & Violet", "serie": {"name": "Scarlet & Violet"},
                "cardCount": {"official": 198, "total": 258}, "releaseDate": "2023-03-31"},
            "pricing": {"cardmarket": {"avg": 0.2, "low": 0.1, "trend": 0.25}}
        }
        upsert_card(conn, "en", card, {"id": "sv1", "name": "Scarlet & Violet"})
        row = conn.execute("SELECT id, language, marketPrice, sortNumber FROM cards").fetchone()
        self.assertEqual(row, ("en:sv1-1", "en", 0.2, 1))
        links = conn.execute("SELECT dexId FROM card_pokemon ORDER BY dexId").fetchall()
        self.assertEqual(links, [(1,), (2,)])
        conn.close()

    def test_coverage_uses_final_database_counts_when_a_listed_set_has_no_cards(self):
        db_file = os.path.join(self.temp_dir, "catalog.sqlite")
        conn = create_database(db_file, load_schema(SCHEMA_JSON_PATH))
        # A set can be listed and processed without contributing any card rows.
        upsert_set(conn, "de", {"id": "empty-set", "name": "Empty Set", "cardCount": {"official": 0, "total": 0}})
        conn.commit()
        conn.close()
        summary = {"languages": {language: {"sets": 0, "cards": 0, "expectedCards": 0} for language in LANGUAGES}}
        summary["languages"]["de"] = {"sets": 1, "cards": 0, "expectedCards": 0}

        coverage = build_coverage(db_file, summary)

        self.assertEqual(coverage["de"], {"sets": 0, "cards": 0, "expectedCards": 0, "coveragePercent": None})
        self.assertEqual(coverage["en"]["sets"], 0)

    def test_manifest_hash_size_and_coverage_are_real(self):
        db_file = os.path.join(self.temp_dir, "catalog.sqlite")
        conn = create_database(db_file, load_schema(SCHEMA_JSON_PATH))
        conn.close()
        manifest_file = os.path.join(self.temp_dir, "manifest.json")
        report_file = os.path.join(self.temp_dir, "coverage_report.md")
        coverage = {lang: {"sets": 0, "cards": 0, "coveragePercent": None} for lang in LANGUAGES}
        manifest = generate_manifest(db_file, manifest_file, report_file, 17, coverage)
        self.assertEqual(manifest["schemaVersion"], 17)
        self.assertRegex(manifest["version"], r"^\d{4}\.\d{2}\.\d{2}\.\d{6}\.\d{3}$")
        self.assertEqual(manifest["size"], os.path.getsize(db_file))
        with open(db_file, "rb") as handle:
            expected_hash = hashlib.sha256(handle.read()).hexdigest()
        with open(manifest_file, encoding="utf-8") as handle:
            written_manifest = json.load(handle)
        with open(report_file, encoding="utf-8") as handle:
            report = handle.read()
        self.assertEqual(manifest["sha256"], expected_hash)
        self.assertIsNone(written_manifest["coverage"]["de"]["coveragePercent"])
        self.assertIn("Abdeckung je Sprache", report)
        self.assertIn("Sets mit Karten", report)

    def test_artifact_verifier_accepts_valid_database_and_manifest(self):
        db_file = os.path.join(self.temp_dir, "catalog.sqlite")
        conn = create_database(db_file, load_schema(SCHEMA_JSON_PATH))
        conn.close()
        manifest_file = os.path.join(self.temp_dir, "manifest.json")
        report_file = os.path.join(self.temp_dir, "coverage_report.md")
        coverage = {lang: {"sets": 0, "cards": 0, "expectedCards": 0, "coveragePercent": None} for lang in LANGUAGES}
        generate_manifest(db_file, manifest_file, report_file, 17, coverage)
        result = verify_catalog_artifacts(db_file, manifest_file, SCHEMA_JSON_PATH)
        self.assertEqual(result["integrity"], "ok")
        self.assertEqual(result["languages"], len(LANGUAGES))

    def test_artifact_verifier_rejects_extra_column_even_with_matching_hash(self):
        db_file = os.path.join(self.temp_dir, "catalog.sqlite")
        conn = create_database(db_file, load_schema(SCHEMA_JSON_PATH))
        conn.execute("ALTER TABLE cards ADD COLUMN injected TEXT")
        conn.commit()
        conn.close()
        manifest_file = os.path.join(self.temp_dir, "manifest.json")
        report_file = os.path.join(self.temp_dir, "coverage_report.md")
        coverage = {lang: {"sets": 0, "cards": 0, "expectedCards": 0, "coveragePercent": None} for lang in LANGUAGES}
        generate_manifest(db_file, manifest_file, report_file, 17, coverage)
        with self.assertRaisesRegex(ValueError, "Catalog table schema mismatch for cards"):
            verify_catalog_artifacts(db_file, manifest_file, SCHEMA_JSON_PATH)

    def test_artifact_verifier_rejects_missing_room_index(self):
        db_file = os.path.join(self.temp_dir, "catalog.sqlite")
        conn = create_database(db_file, load_schema(SCHEMA_JSON_PATH))
        conn.execute("DROP INDEX index_cards_setId")
        conn.commit()
        conn.close()
        manifest_file = os.path.join(self.temp_dir, "manifest.json")
        report_file = os.path.join(self.temp_dir, "coverage_report.md")
        coverage = {lang: {"sets": 0, "cards": 0, "expectedCards": 0, "coveragePercent": None} for lang in LANGUAGES}
        generate_manifest(db_file, manifest_file, report_file, 17, coverage)
        with self.assertRaisesRegex(ValueError, "Catalog index schema mismatch for cards"):
            verify_catalog_artifacts(db_file, manifest_file, SCHEMA_JSON_PATH)

    def test_artifact_verifier_rejects_unexpected_catalog_index(self):
        db_file = os.path.join(self.temp_dir, "catalog.sqlite")
        conn = create_database(db_file, load_schema(SCHEMA_JSON_PATH))
        conn.execute("CREATE INDEX injected_catalog_index ON cards(name)")
        conn.commit()
        conn.close()
        manifest_file = os.path.join(self.temp_dir, "manifest.json")
        report_file = os.path.join(self.temp_dir, "coverage_report.md")
        coverage = {lang: {"sets": 0, "cards": 0, "expectedCards": 0, "coveragePercent": None} for lang in LANGUAGES}
        generate_manifest(db_file, manifest_file, report_file, 17, coverage)
        with self.assertRaisesRegex(ValueError, "Catalog index schema mismatch for cards"):
            verify_catalog_artifacts(db_file, manifest_file, SCHEMA_JSON_PATH)

    def test_artifact_verifier_rejects_unexpected_view(self):
        db_file = os.path.join(self.temp_dir, "catalog.sqlite")
        conn = create_database(db_file, load_schema(SCHEMA_JSON_PATH))
        conn.execute("CREATE VIEW injected_catalog_view AS SELECT id FROM cards")
        conn.commit()
        conn.close()
        manifest_file = os.path.join(self.temp_dir, "manifest.json")
        report_file = os.path.join(self.temp_dir, "coverage_report.md")
        coverage = {lang: {"sets": 0, "cards": 0, "expectedCards": 0, "coveragePercent": None} for lang in LANGUAGES}
        generate_manifest(db_file, manifest_file, report_file, 17, coverage)
        with self.assertRaisesRegex(ValueError, "unexpected views or triggers"):
            verify_catalog_artifacts(db_file, manifest_file, SCHEMA_JSON_PATH)

    def test_artifact_verifier_rejects_unexpected_trigger(self):
        db_file = os.path.join(self.temp_dir, "catalog.sqlite")
        conn = create_database(db_file, load_schema(SCHEMA_JSON_PATH))
        conn.execute("CREATE TRIGGER injected_catalog_trigger AFTER INSERT ON cards BEGIN SELECT 1; END")
        conn.commit()
        conn.close()
        manifest_file = os.path.join(self.temp_dir, "manifest.json")
        report_file = os.path.join(self.temp_dir, "coverage_report.md")
        coverage = {lang: {"sets": 0, "cards": 0, "expectedCards": 0, "coveragePercent": None} for lang in LANGUAGES}
        generate_manifest(db_file, manifest_file, report_file, 17, coverage)
        with self.assertRaisesRegex(ValueError, "unexpected views or triggers"):
            verify_catalog_artifacts(db_file, manifest_file, SCHEMA_JSON_PATH)

    def test_artifact_verifier_rejects_orphan_card_set_relationship(self):
        db_file = os.path.join(self.temp_dir, "catalog.sqlite")
        conn = create_database(db_file, load_schema(SCHEMA_JSON_PATH))
        upsert_card(conn, "en", {
            "id": "orphan-card", "localId": "1", "name": "Orphan Card", "category": "Pokemon", "dexId": []
        }, {"id": "missing-set", "name": "Missing Set"})
        conn.commit()
        conn.close()
        manifest_file = os.path.join(self.temp_dir, "manifest.json")
        report_file = os.path.join(self.temp_dir, "coverage_report.md")
        coverage = {lang: {"sets": 0, "cards": 0, "expectedCards": 0, "coveragePercent": None} for lang in LANGUAGES}
        generate_manifest(db_file, manifest_file, report_file, 17, coverage)
        with self.assertRaisesRegex(ValueError, "Catalog relationship integrity failed for cards.setId"):
            verify_catalog_artifacts(db_file, manifest_file, SCHEMA_JSON_PATH)

    def test_artifact_verifier_rejects_orphan_card_pokemon_dex_id(self):
        db_file = os.path.join(self.temp_dir, "catalog.sqlite")
        conn = create_database(db_file, load_schema(SCHEMA_JSON_PATH))
        upsert_set(conn, "en", {"id": "test-set", "name": "Test Set"})
        upsert_card(conn, "en", {
            "id": "test-1", "localId": "1", "name": "Test Card", "category": "Pokemon", "dexId": []
        }, {"id": "test-set", "name": "Test Set"})
        conn.execute("INSERT INTO card_pokemon(cardId, dexId) VALUES ('en:test-1', 999999)")
        conn.commit()
        conn.close()
        manifest_file = os.path.join(self.temp_dir, "manifest.json")
        report_file = os.path.join(self.temp_dir, "coverage_report.md")
        coverage = {lang: {"sets": 0, "cards": 0, "expectedCards": 0, "coveragePercent": None} for lang in LANGUAGES}
        generate_manifest(db_file, manifest_file, report_file, 17, coverage)
        with self.assertRaisesRegex(ValueError, "Catalog relationship integrity failed for card_pokemon.dexId"):
            verify_catalog_artifacts(db_file, manifest_file, SCHEMA_JSON_PATH)

    def test_artifact_verifier_rejects_orphan_card_pokemon_relationship(self):
        db_file = os.path.join(self.temp_dir, "catalog.sqlite")
        conn = create_database(db_file, load_schema(SCHEMA_JSON_PATH))
        conn.execute("INSERT INTO card_pokemon(cardId, dexId) VALUES ('missing-card', 1)")
        conn.commit()
        conn.close()
        manifest_file = os.path.join(self.temp_dir, "manifest.json")
        report_file = os.path.join(self.temp_dir, "coverage_report.md")
        coverage = {lang: {"sets": 0, "cards": 0, "expectedCards": 0, "coveragePercent": None} for lang in LANGUAGES}
        generate_manifest(db_file, manifest_file, report_file, 17, coverage)
        with self.assertRaisesRegex(ValueError, "Catalog relationship integrity failed for card_pokemon.cardId"):
            verify_catalog_artifacts(db_file, manifest_file, SCHEMA_JSON_PATH)

    def test_artifact_verifier_rejects_orphan_pokemon_name_relationship(self):
        db_file = os.path.join(self.temp_dir, "catalog.sqlite")
        conn = create_database(db_file, load_schema(SCHEMA_JSON_PATH))
        conn.execute("INSERT INTO pokemon_names(language, name, dexId) VALUES ('en', 'Missingmon', 999999)")
        conn.commit()
        conn.close()
        manifest_file = os.path.join(self.temp_dir, "manifest.json")
        report_file = os.path.join(self.temp_dir, "coverage_report.md")
        coverage = {lang: {"sets": 0, "cards": 0, "expectedCards": 0, "coveragePercent": None} for lang in LANGUAGES}
        generate_manifest(db_file, manifest_file, report_file, 17, coverage)
        with self.assertRaisesRegex(ValueError, "Catalog relationship integrity failed for pokemon_names.dexId"):
            verify_catalog_artifacts(db_file, manifest_file, SCHEMA_JSON_PATH)

    def test_artifact_verifier_rejects_manifest_card_coverage_mismatch(self):
        db_file = os.path.join(self.temp_dir, "catalog.sqlite")
        conn = create_database(db_file, load_schema(SCHEMA_JSON_PATH))
        upsert_set(conn, "en", {"id": "test-set", "name": "Test Set"})
        upsert_card(conn, "en", {
            "id": "test-1", "localId": "1", "name": "Test Card", "category": "Pokemon",
            "set": {"id": "test-set", "name": "Test Set"}, "dexId": []
        }, {"id": "test-set", "name": "Test Set"})
        conn.commit()
        conn.close()
        manifest_file = os.path.join(self.temp_dir, "manifest.json")
        report_file = os.path.join(self.temp_dir, "coverage_report.md")
        coverage = {lang: {"sets": 0, "cards": 0, "expectedCards": 0, "coveragePercent": None} for lang in LANGUAGES}
        coverage["en"]["cards"] = 2
        generate_manifest(db_file, manifest_file, report_file, 17, coverage)
        with self.assertRaisesRegex(ValueError, "Coverage card count for en"):
            verify_catalog_artifacts(db_file, manifest_file, SCHEMA_JSON_PATH)


    def test_artifact_verifier_accepts_consistent_nonempty_coverage(self):
        db_file = os.path.join(self.temp_dir, "catalog.sqlite")
        conn = create_database(db_file, load_schema(SCHEMA_JSON_PATH))
        upsert_set(conn, "en", {"id": "test-set", "name": "Test Set"})
        upsert_card(conn, "en", {
            "id": "test-1", "localId": "1", "name": "Test Card", "category": "Pokemon",
            "set": {"id": "test-set", "name": "Test Set"}, "dexId": []
        }, {"id": "test-set", "name": "Test Set"})
        conn.commit()
        conn.close()
        manifest_file = os.path.join(self.temp_dir, "manifest.json")
        report_file = os.path.join(self.temp_dir, "coverage_report.md")
        coverage = {lang: {"sets": 0, "cards": 0, "expectedCards": 0, "coveragePercent": None} for lang in LANGUAGES}
        coverage["en"] = {"sets": 1, "cards": 1, "expectedCards": 1, "coveragePercent": 100.0}
        generate_manifest(db_file, manifest_file, report_file, 17, coverage)
        result = verify_catalog_artifacts(db_file, manifest_file, SCHEMA_JSON_PATH)
        self.assertEqual(result["integrity"], "ok")

    def test_artifact_verifier_rejects_manifest_set_coverage_mismatch(self):
        db_file = os.path.join(self.temp_dir, "catalog.sqlite")
        conn = create_database(db_file, load_schema(SCHEMA_JSON_PATH))
        upsert_set(conn, "en", {"id": "test-set", "name": "Test Set"})
        upsert_card(conn, "en", {
            "id": "test-1", "localId": "1", "name": "Test Card", "category": "Pokemon",
            "set": {"id": "test-set", "name": "Test Set"}, "dexId": []
        }, {"id": "test-set", "name": "Test Set"})
        conn.commit()
        conn.close()
        manifest_file = os.path.join(self.temp_dir, "manifest.json")
        report_file = os.path.join(self.temp_dir, "coverage_report.md")
        coverage = {lang: {"sets": 0, "cards": 0, "expectedCards": 0, "coveragePercent": None} for lang in LANGUAGES}
        coverage["en"]["cards"] = 1
        coverage["en"]["sets"] = 0
        generate_manifest(db_file, manifest_file, report_file, 17, coverage)
        with self.assertRaisesRegex(ValueError, "Coverage set count for en"):
            verify_catalog_artifacts(db_file, manifest_file, SCHEMA_JSON_PATH)

    def test_artifact_verifier_rejects_inconsistent_coverage_percentage(self):
        db_file = os.path.join(self.temp_dir, "catalog.sqlite")
        conn = create_database(db_file, load_schema(SCHEMA_JSON_PATH))
        upsert_set(conn, "en", {"id": "test-set", "name": "Test Set"})
        upsert_card(conn, "en", {
            "id": "test-1", "localId": "1", "name": "Test Card", "category": "Pokemon",
            "set": {"id": "test-set", "name": "Test Set"}, "dexId": []
        }, {"id": "test-set", "name": "Test Set"})
        conn.commit()
        conn.close()
        manifest_file = os.path.join(self.temp_dir, "manifest.json")
        report_file = os.path.join(self.temp_dir, "coverage_report.md")
        coverage = {lang: {"sets": 0, "cards": 0, "expectedCards": 0, "coveragePercent": None} for lang in LANGUAGES}
        coverage["en"] = {"sets": 1, "cards": 1, "expectedCards": 4, "coveragePercent": 99.0}
        generate_manifest(db_file, manifest_file, report_file, 17, coverage)
        with self.assertRaisesRegex(ValueError, "Coverage percentage for en"):
            verify_catalog_artifacts(db_file, manifest_file, SCHEMA_JSON_PATH)

    def test_artifact_verifier_rejects_non_object_manifest_root(self):
        db_file = os.path.join(self.temp_dir, "catalog.sqlite")
        conn = create_database(db_file, load_schema(SCHEMA_JSON_PATH))
        conn.close()
        manifest_file = os.path.join(self.temp_dir, "manifest.json")
        with open(manifest_file, "w", encoding="utf-8") as handle:
            json.dump(["not", "an", "object"], handle)
        with self.assertRaisesRegex(ValueError, "manifest root must be a JSON object"):
            verify_catalog_artifacts(db_file, manifest_file, SCHEMA_JSON_PATH)

    def test_artifact_verifier_rejects_duplicate_top_level_manifest_keys(self):
        db_file = os.path.join(self.temp_dir, "catalog.sqlite")
        conn = create_database(db_file, load_schema(SCHEMA_JSON_PATH))
        conn.close()
        manifest_file = os.path.join(self.temp_dir, "manifest.json")
        with open(manifest_file, "w", encoding="utf-8") as handle:
            handle.write('{"schemaVersion":17,"schemaVersion":17}')
        with self.assertRaisesRegex(ValueError, "duplicate JSON key: schemaVersion"):
            verify_catalog_artifacts(db_file, manifest_file, SCHEMA_JSON_PATH)

    def test_artifact_verifier_rejects_duplicate_nested_manifest_keys(self):
        db_file = os.path.join(self.temp_dir, "catalog.sqlite")
        conn = create_database(db_file, load_schema(SCHEMA_JSON_PATH))
        conn.close()
        manifest_file = os.path.join(self.temp_dir, "manifest.json")
        report_file = os.path.join(self.temp_dir, "coverage_report.md")
        coverage = {lang: {"sets": 0, "cards": 0, "expectedCards": 0, "coveragePercent": None} for lang in LANGUAGES}
        generate_manifest(db_file, manifest_file, report_file, 17, coverage)
        with open(manifest_file, "r", encoding="utf-8") as handle:
            manifest_text = handle.read()
        manifest_text = manifest_text.replace('"sets": 0', '"sets": 0, "sets": 0', 1)
        with open(manifest_file, "w", encoding="utf-8") as handle:
            handle.write(manifest_text)
        with self.assertRaisesRegex(ValueError, "duplicate JSON key: sets"):
            verify_catalog_artifacts(db_file, manifest_file, SCHEMA_JSON_PATH)

    def test_artifact_verifier_rejects_nonstandard_json_constants(self):
        db_file = os.path.join(self.temp_dir, "catalog.sqlite")
        conn = create_database(db_file, load_schema(SCHEMA_JSON_PATH))
        conn.close()
        manifest_file = os.path.join(self.temp_dir, "manifest.json")
        for constant in ("NaN", "Infinity", "-Infinity"):
            with self.subTest(constant=constant):
                with open(manifest_file, "w", encoding="utf-8") as handle:
                    handle.write('{"invalid":' + constant + '}')
                with self.assertRaisesRegex(ValueError, "non-standard JSON constant"):
                    verify_catalog_artifacts(db_file, manifest_file, SCHEMA_JSON_PATH)

    def test_artifact_verifier_rejects_invalid_manifest_metadata(self):
        db_file = os.path.join(self.temp_dir, "catalog.sqlite")
        conn = create_database(db_file, load_schema(SCHEMA_JSON_PATH))
        conn.close()
        manifest_file = os.path.join(self.temp_dir, "manifest.json")
        report_file = os.path.join(self.temp_dir, "coverage_report.md")
        coverage = {lang: {"sets": 0, "cards": 0, "expectedCards": 0, "coveragePercent": None} for lang in LANGUAGES}
        generate_manifest(db_file, manifest_file, report_file, 17, coverage)
        with open(manifest_file, encoding="utf-8") as handle:
            manifest = json.load(handle)
        manifest["builtAt"] = True
        manifest["url"] = "http://example.invalid/catalog.sqlite"
        with open(manifest_file, "w", encoding="utf-8") as handle:
            json.dump(manifest, handle)
        with self.assertRaisesRegex(ValueError, "builtAt"):
            verify_catalog_artifacts(db_file, manifest_file, SCHEMA_JSON_PATH)

    def test_artifact_verifier_rejects_tampered_database_hash(self):
        db_file = os.path.join(self.temp_dir, "catalog.sqlite")
        conn = create_database(db_file, load_schema(SCHEMA_JSON_PATH))
        conn.close()
        manifest_file = os.path.join(self.temp_dir, "manifest.json")
        report_file = os.path.join(self.temp_dir, "coverage_report.md")
        coverage = {lang: {"sets": 0, "cards": 0, "expectedCards": 0, "coveragePercent": None} for lang in LANGUAGES}
        generate_manifest(db_file, manifest_file, report_file, 17, coverage)
        with open(db_file, "ab") as handle:
            handle.write(b"tampered")
        with self.assertRaisesRegex(ValueError, "SHA-256"):
            verify_catalog_artifacts(db_file, manifest_file, SCHEMA_JSON_PATH)

    def test_population_is_testable_without_network(self):
        db_file = os.path.join(self.temp_dir, "catalog.sqlite")
        conn = create_database(db_file, load_schema(SCHEMA_JSON_PATH))
        def fake_fetcher(url, params=None):
            if url.endswith("/sets") and "/v2/" in url:
                if "/en/" in url or "/de/" in url:
                    return [{"id": "test-set", "name": "Test Set", "cardCount": {"official": 1, "total": 1}}]
                return []
            if url.endswith("/en/sets/test-set") or url.endswith("/de/sets/test-set"):
                return {"id": "test-set", "name": "Test Set", "cards": [{"id": "test-set-1"}]}
            if url.endswith("/en/cards/test-set-1") or url.endswith("/de/cards/test-set-1"):
                lang = "/de/" in url
                return {"id": "test-set-1", "localId": "1", "name": "Testkarte" if lang else "Test Card",
                        "category": "Pokemon", "dexId": [1], "set": {"id": "test-set", "name": "Test Set"}}
            if "pokemon-species/" in url and url.endswith("pokemon-species/"):
                return {"results": []}
            if "bulbapedia" in url:
                return {"query": {"categorymembers": []}}
            raise AssertionError(f"Unexpected test URL: {url}")
        summary = populate_database(conn, fetcher=fake_fetcher, include_bulbapedia=False)
        self.assertEqual(summary["languages"]["en"]["cards"], 1)
        self.assertEqual(summary["languages"]["de"]["cards"], 1)
        self.assertEqual(conn.execute("SELECT COUNT(*) FROM cards").fetchone()[0], 2)
        conn.close()

    def test_resume_skips_completed_sets_and_keeps_checkpoint(self):
        db_file = os.path.join(self.temp_dir, "catalog.sqlite")
        checkpoint = os.path.join(self.temp_dir, "catalog-progress.json")
        conn = create_database(db_file, load_schema(SCHEMA_JSON_PATH))
        card_detail_calls = []

        def fake_fetcher(url, params=None):
            if url.endswith("/sets") and "/v2/" in url:
                if "/en/" in url or "/de/" in url:
                    return [{"id": "test-set", "name": "Test Set", "cardCount": {"official": 1, "total": 1}}]
                return []
            if url.endswith("/en/sets/test-set") or url.endswith("/de/sets/test-set"):
                return {"id": "test-set", "name": "Test Set", "cards": [{"id": "test-set-1"}]}
            if url.endswith("/en/cards/test-set-1") or url.endswith("/de/cards/test-set-1"):
                card_detail_calls.append(url)
                return {"id": "test-set-1", "localId": "1", "name": "Test Card",
                        "category": "Pokemon", "dexId": [1], "set": {"id": "test-set", "name": "Test Set"}}
            if "pokemon-species/" in url and url.endswith("pokemon-species/"):
                return {"results": []}
            if "bulbapedia" in url:
                return {"query": {"categorymembers": []}}
            raise AssertionError(f"Unexpected test URL: {url}")

        kwargs = {"fetcher": fake_fetcher, "include_bulbapedia": False,
                  "checkpoint_path": checkpoint, "schema_version": 17}
        populate_database(conn, **kwargs)
        first_count = len(card_detail_calls)
        self.assertEqual(first_count, 2)
        with open(checkpoint, encoding="utf-8") as handle:
            saved = json.load(handle)
        self.assertEqual(saved["schemaVersion"], 17)
        self.assertEqual(set(saved["completedSets"]), {"en", "de"})

        populate_database(conn, **kwargs)
        self.assertEqual(len(card_detail_calls), first_count)
        self.assertEqual(conn.execute("SELECT COUNT(*) FROM cards").fetchone()[0], 2)
        conn.close()

    def test_failed_card_detail_marks_set_incomplete_and_does_not_checkpoint_it(self):
        db_file = os.path.join(self.temp_dir, "catalog.sqlite")
        checkpoint = os.path.join(self.temp_dir, "catalog-progress.json")
        conn = create_database(db_file, load_schema(SCHEMA_JSON_PATH))

        def fake_fetcher(url, params=None):
            if url.endswith("/sets") and "/v2/" in url:
                return [{"id": "test-set", "name": "Test Set", "cardCount": {"official": 1, "total": 1}}] if "/en/" in url else []
            if url.endswith("/en/sets/test-set"):
                return {"id": "test-set", "name": "Test Set", "cards": [{"id": "test-set-1"}]}
            if url.endswith("/en/cards/test-set-1"):
                raise RuntimeError("simulated API outage")
            if url.endswith("pokemon-species/"):
                return {"results": []}
            raise AssertionError(f"Unexpected test URL: {url}")

        summary = populate_database(conn, fetcher=fake_fetcher, include_bulbapedia=False,
                                    checkpoint_path=checkpoint, schema_version=17)
        self.assertEqual(summary["languages"]["en"]["failedSets"], 1)
        self.assertEqual(summary["failedSetsByLanguage"]["en"], 1)
        with open(checkpoint, encoding="utf-8") as handle:
            saved = json.load(handle)
        self.assertNotIn("test-set", saved["completedSets"].get("en", []))
        conn.close()

    def test_truncated_set_detail_does_not_mark_set_complete(self):
        db_file = os.path.join(self.temp_dir, "catalog.sqlite")
        checkpoint = os.path.join(self.temp_dir, "catalog-progress.json")
        conn = create_database(db_file, load_schema(SCHEMA_JSON_PATH))

        def fake_fetcher(url, params=None):
            if url.endswith("/sets") and "/v2/" in url:
                return [{"id": "partial-set", "name": "Partial Set", "cardCount": {"official": 2, "total": 2}}] if "/en/" in url else []
            if url.endswith("/en/sets/partial-set"):
                # API returns only one card even though the set declares two.
                return {"id": "partial-set", "name": "Partial Set", "cards": [{"id": "partial-set-1"}]}
            if url.endswith("/en/cards/partial-set-1"):
                return {"id": "partial-set-1", "localId": "1", "name": "Partial Card", "category": "Pokemon", "dexId": [1], "set": {"id": "partial-set", "name": "Partial Set"}}
            if url.endswith("pokemon-species/"):
                return {"results": []}
            raise AssertionError(f"Unexpected test URL: {url}")

        summary = populate_database(conn, fetcher=fake_fetcher, include_bulbapedia=False,
                                    checkpoint_path=checkpoint, schema_version=17)
        self.assertEqual(summary["languages"]["en"]["failedSets"], 1)
        with open(checkpoint, encoding="utf-8") as handle:
            saved = json.load(handle)
        self.assertNotIn("partial-set", saved["completedSets"].get("en", []))
        conn.close()

    def test_resume_rejects_checkpoint_for_other_schema_version(self):
        checkpoint = os.path.join(self.temp_dir, "catalog-progress.json")
        with open(checkpoint, "w", encoding="utf-8") as handle:
            json.dump({"schemaVersion": 16, "completedSets": {}}, handle)
        from build_catalog import _load_checkpoint
        with self.assertRaisesRegex(ValueError, "schema version"):
            _load_checkpoint(checkpoint, 17)

    def test_bulbapedia_pagination_is_serial_and_optional(self):
        calls = []
        def fake_fetcher(url, params=None):
            calls.append(dict(params))
            if len(calls) == 1:
                return {"query": {"categorymembers": [{"title": "Set A"}]}, "continue": {"cmcontinue": "next"}}
            return {"query": {"categorymembers": [{"title": "Set B"}]}}
        titles = fetch_bulbapedia_set_titles(fetcher=fake_fetcher)
        self.assertEqual(titles, {"Set A", "Set B"})
        self.assertEqual(len(calls), 2)
        self.assertEqual(calls[1]["cmcontinue"], "next")

if __name__ == "__main__":
    unittest.main()
