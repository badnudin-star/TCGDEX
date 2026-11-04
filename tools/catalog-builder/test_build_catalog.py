import hashlib
import json
import os
import sqlite3
import tempfile
import unittest
import shutil
from unittest import mock

import build_catalog
from build_catalog import (
    NotFoundError, CATALOG_TABLES, LANGUAGES, SCHEMA_JSON_PATH, build_coverage, create_database, fetch_bulbapedia_set_titles,
    fetch_sets, find_incomplete_catalog, generate_manifest, load_schema, populate_database, upsert_card, upsert_set,
    _publish_staged_artifacts, _recover_interrupted_publish,
)
from verify_catalog_artifacts import verify_catalog_artifacts


class TestCatalogBuilder(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.mkdtemp()
        retry_patch = mock.patch.object(build_catalog, "CARD_RETRY_DELAY", 0)
        retry_patch.start()
        self.addCleanup(retry_patch.stop)

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
        summary["languages"]["de"]["sets"] = 0
        summary["pokemonCount"] = 0
        failures = find_incomplete_catalog(summary)
        self.assertIn("de", failures)
        self.assertIn("zero sets", failures["de"])
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

    def test_card_detail_404_falls_back_to_listing_data_and_completes_set(self):
        db_file = os.path.join(self.temp_dir, "catalog.sqlite")
        checkpoint = os.path.join(self.temp_dir, "catalog-progress.json")
        conn = create_database(db_file, load_schema(SCHEMA_JSON_PATH))

        def fake_fetcher(url, params=None):
            if url.endswith("/sets") and "/v2/" in url:
                return [{"id": "exu", "name": "Unseen Forces", "cardCount": {"official": 2, "total": 2}}] if "/en/" in url else []
            if url.endswith("/en/sets/exu"):
                return {"id": "exu", "name": "Unseen Forces", "cards": [
                    {"id": "exu-1", "localId": "1", "name": "Ampharos", "image": "https://assets.tcgdex.net/en/ex/exu/1"},
                    {"id": "exu-?", "localId": "?", "name": "Unown", "image": "https://assets.tcgdex.net/en/ex/exu/%3F"}]}
            if url.endswith("/en/cards/exu-1"):
                return {"id": "exu-1", "localId": "1", "name": "Ampharos", "category": "Pokemon", "dexId": [181],
                        "set": {"id": "exu", "name": "Unseen Forces"}}
            if url.endswith("/en/cards/exu-%3F"):
                raise NotFoundError("Not found (404): " + url)
            if url.endswith("pokemon-species/"):
                return {"results": []}
            raise AssertionError(f"Unexpected test URL: {url}")

        summary = populate_database(conn, fetcher=fake_fetcher, include_bulbapedia=False,
                                    checkpoint_path=checkpoint, schema_version=17)
        self.assertEqual(summary["languages"]["en"]["failedSets"], 0)
        self.assertEqual(summary["languages"]["en"]["cards"], 2)
        with open(checkpoint, encoding="utf-8") as handle:
            saved = json.load(handle)
        self.assertIn("exu", saved["completedSets"]["en"])
        row = conn.execute("SELECT name, number, detailLoaded FROM cards WHERE id LIKE '%exu-?'").fetchone()
        self.assertEqual(row, ("Unown", "?", 0))
        full = conn.execute("SELECT detailLoaded FROM cards WHERE id LIKE '%exu-1'").fetchone()
        self.assertEqual(full[0], 1)
        conn.close()

    def test_card_detail_404_without_listing_name_counts_as_api_gap(self):
        db_file = os.path.join(self.temp_dir, "catalog.sqlite")
        checkpoint = os.path.join(self.temp_dir, "catalog-progress.json")
        conn = create_database(db_file, load_schema(SCHEMA_JSON_PATH))

        def fake_fetcher(url, params=None):
            if url.endswith("/sets") and "/v2/" in url:
                return [{"id": "x", "name": "X", "cardCount": {"official": 1, "total": 1}}] if "/en/" in url else []
            if url.endswith("/en/sets/x"):
                return {"id": "x", "name": "X", "cards": [{"id": "x-1"}]}
            if url.endswith("/en/cards/x-1"):
                raise NotFoundError("Not found (404): " + url)
            if url.endswith("pokemon-species/"):
                return {"results": []}
            raise AssertionError(f"Unexpected test URL: {url}")

        summary = populate_database(conn, fetcher=fake_fetcher, include_bulbapedia=False,
                                    checkpoint_path=checkpoint, schema_version=17)
        # A card the API neither lists with a name nor serves is a permanent gap, not a transient failure.
        self.assertEqual(summary["languages"]["en"]["failedSets"], 0)
        self.assertEqual(summary["languages"]["en"]["missingCards"], 1)
        conn.close()

    def test_request_json_does_not_retry_on_404(self):
        import requests
        import build_catalog
        response = requests.Response()
        response.status_code = 404
        calls = []

        class FakeSession:
            def get(self, url, **kwargs):
                calls.append(url)
                return response

        with mock.patch.object(build_catalog.time, "sleep") as sleeper:
            with self.assertRaises(NotFoundError):
                build_catalog.request_json("https://example.invalid/x", session=FakeSession())
        self.assertEqual(len(calls), 1)
        sleeper.assert_not_called()

    def test_truncated_set_is_recorded_as_api_gap_and_remembered_on_resume(self):
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
        self.assertEqual(summary["languages"]["en"]["failedSets"], 0)
        self.assertEqual(summary["languages"]["en"]["missingCards"], 1)
        with open(checkpoint, encoding="utf-8") as handle:
            saved = json.load(handle)
        self.assertIn("partial-set", saved["completedSets"].get("en", []))
        self.assertEqual(saved["gapSets"]["en"]["partial-set"], {"missing": 1, "brief": 0})
        self.assertEqual(find_incomplete_catalog({**summary, "pokemonCount": 1, "languages": {
            lang: {"sets": 1, "cards": 1} for lang in LANGUAGES}}), {})

        # Resume: the set is skipped (no set-detail request) but the recorded gap is still reported.
        def resume_fetcher(url, params=None):
            if url.endswith("/sets") and "/v2/" in url:
                return [{"id": "partial-set", "name": "Partial Set", "cardCount": {"official": 2, "total": 2}}] if "/en/" in url else []
            if url.endswith("pokemon-species/"):
                return {"results": []}
            raise AssertionError(f"Unexpected request on resume: {url}")
        resumed = populate_database(conn, fetcher=resume_fetcher, include_bulbapedia=False,
                                    checkpoint_path=checkpoint, schema_version=17)
        self.assertEqual(resumed["languages"]["en"]["missingCards"], 1)
        conn.close()

    def _single_set_fetcher(self, set_id, cards, card_handler, declared=None):
        declared = declared if declared is not None else len(cards)

        def fake_fetcher(url, params=None):
            if url.endswith("/sets") and "/v2/" in url:
                return [{"id": set_id, "name": "Set", "cardCount": {"official": declared, "total": declared}}] if "/en/" in url else []
            if url.endswith(f"/en/sets/{set_id}"):
                return {"id": set_id, "name": "Set", "cards": cards}
            if "/en/cards/" in url:
                return card_handler(url)
            if url.endswith("pokemon-species/"):
                return {"results": []}
            raise AssertionError(f"Unexpected test URL: {url}")
        return fake_fetcher

    def _full_card(self, card_id, set_id="s"):
        return {"id": card_id, "localId": card_id.split("-")[-1], "name": "Card " + card_id, "category": "Pokemon",
                "dexId": [1], "set": {"id": set_id, "name": "Set"}}

    def test_set_detail_404_is_an_api_gap_and_does_not_block(self):
        db_file = os.path.join(self.temp_dir, "catalog.sqlite")
        checkpoint = os.path.join(self.temp_dir, "catalog-progress.json")
        conn = create_database(db_file, load_schema(SCHEMA_JSON_PATH))

        def fake_fetcher(url, params=None):
            if url.endswith("/sets") and "/v2/" in url:
                return [{"id": "SM1+", "name": "Strong Plus", "cardCount": {"official": 3, "total": 3}}] if "/ja/" in url else []
            if url.endswith("/ja/sets/SM1%2B"):
                raise NotFoundError("Not found (404): " + url)
            if url.endswith("pokemon-species/"):
                return {"results": []}
            raise AssertionError(f"Unexpected test URL: {url}")

        summary = populate_database(conn, fetcher=fake_fetcher, include_bulbapedia=False,
                                    checkpoint_path=checkpoint, schema_version=17)
        self.assertEqual(summary["languages"]["ja"]["failedSets"], 0)
        self.assertEqual(summary["languages"]["ja"]["missingCards"], 3)
        with open(checkpoint, encoding="utf-8") as handle:
            self.assertIn("SM1+", json.load(handle)["completedSets"]["ja"])
        conn.close()

    def test_persistent_server_error_with_listing_data_degrades_card_and_retries_set_later(self):
        db_file = os.path.join(self.temp_dir, "catalog.sqlite")
        checkpoint = os.path.join(self.temp_dir, "catalog-progress.json")
        conn = create_database(db_file, load_schema(SCHEMA_JSON_PATH))
        cards = [{"id": "s-1", "localId": "1", "name": "Card s-1"}, {"id": "s-2", "localId": "2", "name": "Card s-2"}]

        def handler(url):
            if url.endswith("/en/cards/s-2"):
                raise RuntimeError("Request failed after 6 attempts: 503 Server Error")
            return self._full_card("s-1")

        summary = populate_database(conn, fetcher=self._single_set_fetcher("s", cards, handler),
                                    include_bulbapedia=False, checkpoint_path=checkpoint, schema_version=17)
        stats = summary["languages"]["en"]
        self.assertEqual((stats["cards"], stats["degradedCards"], stats["failedSets"]), (2, 1, 0))
        with open(checkpoint, encoding="utf-8") as handle:
            self.assertNotIn("s", json.load(handle)["completedSets"].get("en", []))
        self.assertEqual(conn.execute("SELECT detailLoaded FROM cards WHERE id LIKE '%s-2'").fetchone()[0], 0)
        conn.close()

    def test_server_error_that_recovers_in_retry_round_keeps_full_details(self):
        db_file = os.path.join(self.temp_dir, "catalog.sqlite")
        checkpoint = os.path.join(self.temp_dir, "catalog-progress.json")
        conn = create_database(db_file, load_schema(SCHEMA_JSON_PATH))
        cards = [{"id": "s-1", "localId": "1", "name": "Card s-1"}]
        attempts = []

        def handler(url):
            attempts.append(url)
            if len(attempts) == 1:
                raise RuntimeError("503 Server Error")
            return self._full_card("s-1")

        summary = populate_database(conn, fetcher=self._single_set_fetcher("s", cards, handler),
                                    include_bulbapedia=False, checkpoint_path=checkpoint, schema_version=17)
        stats = summary["languages"]["en"]
        self.assertEqual((stats["cards"], stats["degradedCards"], stats["failedSets"]), (1, 0, 0))
        self.assertEqual(conn.execute("SELECT detailLoaded FROM cards").fetchone()[0], 1)
        with open(checkpoint, encoding="utf-8") as handle:
            self.assertIn("s", json.load(handle)["completedSets"]["en"])
        conn.close()

    def test_gate_tolerates_missing_optional_language_but_not_mass_degradation(self):
        summary = {"languages": {lang: {"sets": 1, "cards": 100, "degradedCards": 1} for lang in LANGUAGES},
                   "failedSetsByLanguage": {}, "pokemonError": None, "pokemonCount": 1}
        summary["languages"]["pt-pt"] = {"sets": 0, "cards": 0}
        self.assertEqual(find_incomplete_catalog(summary), {})
        summary["languages"]["fr"]["degradedCards"] = 50
        self.assertIn("fr", find_incomplete_catalog(summary))

    def test_ids_with_reserved_characters_are_percent_encoded_in_urls(self):
        from build_catalog import _path_segment
        self.assertEqual(_path_segment("SM1+"), "SM1%2B")
        self.assertEqual(_path_segment("exu-%3F"), "exu-%3F")
        self.assertEqual(_path_segment("sv01"), "sv01")
        seen = []
        build_catalog.fetch_set_details("ja", "SM1+", fetcher=lambda url: seen.append(url) or {})
        self.assertEqual(seen, ["https://api.tcgdex.net/v2/ja/sets/SM1%2B"])

    def test_orphan_card_links_are_removed_so_verification_does_not_block(self):
        from build_catalog import _remove_orphan_card_links
        db_file = os.path.join(self.temp_dir, "catalog.sqlite")
        conn = create_database(db_file, load_schema(SCHEMA_JSON_PATH))
        columns = conn.execute("PRAGMA table_info(pokemon)").fetchall()
        values = [1 if col[1] == "dexId" else (0 if "INT" in (col[2] or "").upper() else "") for col in columns]
        conn.execute(f"INSERT INTO pokemon VALUES ({','.join('?' * len(columns))})", values)
        conn.executemany("INSERT INTO card_pokemon(cardId,dexId) VALUES (?,?)", [("c1", 1), ("c2", 9999)])
        self.assertEqual(_remove_orphan_card_links(conn), 1)
        self.assertEqual(conn.execute("SELECT cardId FROM card_pokemon").fetchall(), [("c1",)])
        conn.close()

    def test_resume_forgets_completed_sets_that_have_no_cards(self):
        from build_catalog import _forget_completed_sets_without_cards
        db_file = os.path.join(self.temp_dir, "catalog.sqlite")
        conn = create_database(db_file, load_schema(SCHEMA_JSON_PATH))
        checkpoint = {"schemaVersion": 17, "completedSets": {"en": ["orphan", "gap"]}, "gapSets": {"en": {"gap": {"missing": 2, "brief": 0}}}}
        self.assertEqual(_forget_completed_sets_without_cards(conn, checkpoint), 1)
        self.assertEqual(checkpoint["completedSets"]["en"], ["gap"])
        conn.close()

    def test_failed_gate_keeps_partial_database_and_resume_continues_from_it(self):
        out = os.path.join(self.temp_dir, "out")
        os.makedirs(out)
        original = build_catalog.populate_database

        def quiet_populate(conn, **kwargs):
            return original(conn, fetcher=lambda url, params=None: [] if url.endswith("/sets") else {"results": []},
                            include_bulbapedia=False, checkpoint_path=kwargs["checkpoint_path"],
                            schema_version=kwargs["schema_version"])

        argv = ["build_catalog.py", "--output-dir", out, "--skip-bulbapedia"]
        with mock.patch.object(build_catalog, "populate_database", quiet_populate), \
                mock.patch.object(build_catalog, "find_incomplete_catalog", return_value={"en": "simulated outage"}), \
                mock.patch("sys.argv", argv):
            self.assertEqual(build_catalog.main(), 2)
        self.assertTrue(os.path.exists(os.path.join(out, "catalog-partial.sqlite")))
        self.assertTrue(os.path.exists(os.path.join(out, "catalog-progress.json")))
        self.assertFalse(os.path.exists(os.path.join(out, "catalog.sqlite")))
        # --resume must now work although no catalog.sqlite has ever been published.
        with mock.patch.object(build_catalog, "populate_database", quiet_populate), \
                mock.patch.object(build_catalog, "find_incomplete_catalog", return_value={}), \
                mock.patch("sys.argv", argv + ["--resume"]):
            self.assertEqual(build_catalog.main(), 0)
        self.assertTrue(os.path.exists(os.path.join(out, "catalog.sqlite")))
        self.assertFalse(os.path.exists(os.path.join(out, "catalog-partial.sqlite")))

    # ---- Lücken-Bericht und Sprach-Ersatz -------------------------------------------------------------

    def _gap_db(self):
        db = create_database(os.path.join(self.temp_dir, "catalog.sqlite"), load_schema(SCHEMA_JSON_PATH))
        return db

    def _add_card(self, db, language, set_id, number, name=None, price=None):
        card = {"id": f"{set_id}-{number}", "localId": str(number), "name": name or f"{language} card {number}",
                "category": "Pokemon", "dexId": [25], "image": "https://assets.tcgdex.net/x/" + str(number),
                "set": {"id": set_id, "name": f"Set {set_id} ({language})"}}
        if price is not None:
            card["pricing"] = {"cardmarket": {"avg": price, "low": price, "trend": price}}
        db.execute("INSERT OR IGNORE INTO pokemon VALUES (" + ",".join(
            "?" * len(db.execute("PRAGMA table_info(pokemon)").fetchall())) + ")",
            [25 if col[1] == "dexId" else (0 if "INT" in (col[2] or "").upper() else "")
             for col in db.execute("PRAGMA table_info(pokemon)").fetchall()])
        build_catalog.upsert_card(db, language, card, {"id": set_id, "name": f"Set {set_id}"})

    def test_gap_fill_copies_english_cards_without_price_and_marks_them_list_only(self):
        db = self._gap_db()
        for number in (1, 2, 3):
            self._add_card(db, "en", "S", number, price=5.0)
        self._add_card(db, "fr", "S", 1, price=7.0)
        checkpoint = {"gapSets": {"fr": {"S": {"missing": 2, "brief": 0}}}}
        plan = build_catalog.plan_gap_fill(db, checkpoint)
        self.assertEqual([(e["language"], e["setId"], e["status"], e["candidateCount"]) for e in plan],
                         [("fr", "S", "fillable", 2)])
        self.assertEqual(build_catalog.apply_gap_fill(db, plan), {"fr": 2})
        rows = db.execute("SELECT id, name, marketPrice, detailLoaded, language FROM cards "
                          "WHERE language='fr' ORDER BY number").fetchall()
        self.assertEqual([r[0] for r in rows], ["fr:S-1", "fr:S-2", "fr:S-3"])
        self.assertEqual(rows[1][1:], ("en card 2", None, 0, "fr"))
        self.assertEqual(rows[0][2:4], (7.0, 1))          # the real French card stays untouched
        self.assertEqual(db.execute("SELECT COUNT(*) FROM card_pokemon WHERE cardId='fr:S-3'").fetchone()[0], 1)
        self.assertEqual(db.execute("SELECT COUNT(*) FROM cards WHERE language='en'").fetchone()[0], 3)
        # A second run finds nothing left to fill and changes nothing.
        again = build_catalog.plan_gap_fill(db, checkpoint)
        self.assertEqual(again[0]["status"], "english_has_no_extra_cards")
        self.assertEqual(build_catalog.apply_gap_fill(db, again), {})
        db.close()

    def test_gap_fill_skips_ambiguous_and_english_less_sets(self):
        db = self._gap_db()
        for number in (1, 2, 3, 4):
            self._add_card(db, "en", "A", number)
        self._add_card(db, "ja", "A", 1)
        self._add_card(db, "ja", "SM1+", 1)      # Japanese exclusive set, no English equivalent
        checkpoint = {"gapSets": {"ja": {"A": {"missing": 1, "brief": 0}, "SM1+": {"missing": 3, "brief": 0}}}}
        plan = {e["setId"]: e for e in build_catalog.plan_gap_fill(db, checkpoint)}
        self.assertEqual(plan["A"]["status"], "ambiguous")           # 3 English candidates for 1 missing card
        self.assertEqual(plan["SM1+"]["status"], "no_english_equivalent")
        self.assertEqual(build_catalog.apply_gap_fill(db, list(plan.values())), {})
        self.assertEqual(db.execute("SELECT COUNT(*) FROM cards WHERE language='ja'").fetchone()[0], 2)
        db.close()

    def test_gap_fill_uses_plain_ids_for_german_and_ignores_english_itself(self):
        db = self._gap_db()
        for number in (1, 2):
            self._add_card(db, "en", "S", number)
        self._add_card(db, "de", "S", 1)
        checkpoint = {"gapSets": {"de": {"S": {"missing": 1, "brief": 0}}, "en": {"S": {"missing": 5, "brief": 0}}}}
        plan = build_catalog.plan_gap_fill(db, checkpoint)
        self.assertEqual([e["language"] for e in plan], ["de"])      # English gaps are never filled from English
        build_catalog.apply_gap_fill(db, plan)
        self.assertIsNotNone(db.execute("SELECT 1 FROM cards WHERE id='S-2' AND language='de'").fetchone())
        db.close()

    def test_gap_report_lists_filled_cards_and_open_sets(self):
        db = self._gap_db()
        for number in (1, 2):
            self._add_card(db, "en", "S", number)
        self._add_card(db, "fr", "S", 1)
        self._add_card(db, "ja", "SM1+", 1)
        build_catalog.upsert_set(db, "de", {"id": "S", "name": "Set S", "cardCount": {"official": 2, "total": 2}})
        build_catalog.upsert_set(db, "de", {"id": "SM1+", "name": "Set SM1+", "cardCount": {"official": 4, "total": 4}})
        checkpoint = {"gapSets": {"fr": {"S": {"missing": 1, "brief": 0}}, "ja": {"SM1+": {"missing": 3, "brief": 0}}}}
        plan = build_catalog.plan_gap_fill(db, checkpoint)
        summary = {"languages": {"fr": {"missingCards": 1, "filledFromEnglish": 1}, "ja": {"missingCards": 3}}}
        db.close()
        out = os.path.join(self.temp_dir, "report")
        os.makedirs(out)
        build_catalog.write_gap_report(os.path.join(self.temp_dir, "catalog.sqlite"), out, plan, summary)
        with open(os.path.join(out, "gap_report.md"), encoding="utf-8") as handle:
            report = handle.read()
        self.assertIn("| fr | 1 | 1 | 0 |", report)
        self.assertIn("| ja | 3 | 0 | 3 |", report)
        self.assertIn("SM1+", report)
        with open(os.path.join(out, "gaps.csv"), encoding="utf-8-sig") as handle:
            csv_text = handle.read()
        self.assertIn("fr;S;Set S;2;en card 2;filled_from_en;1", csv_text)
        self.assertIn("ja;SM1+;Set SM1+;;;no_english_equivalent;3", csv_text)

    def test_end_to_end_gap_is_recorded_filled_and_catalog_still_verifies(self):
        from verify_catalog_artifacts import verify_catalog_artifacts
        db_file = os.path.join(self.temp_dir, "catalog.sqlite")
        checkpoint = os.path.join(self.temp_dir, "catalog-progress.json")
        conn = create_database(db_file, load_schema(SCHEMA_JSON_PATH))

        def fake_fetcher(url, params=None):
            language = url.split("/v2/")[1].split("/")[0] if "/v2/" in url else ""
            if url.endswith("/sets") and language in ("en", "de", "fr"):
                return [{"id": "S", "name": "Set", "cardCount": {"official": 3, "total": 3}}]
            if url.endswith("/sets"):
                return []
            if url.endswith(f"/{language}/sets/S"):
                listed = [1, 2, 3] if language != "fr" else [1]      # French lists only one of three cards
                return {"id": "S", "name": "Set", "cards": [{"id": f"S-{n}", "localId": str(n), "name": f"{language} {n}"} for n in listed]}
            if "/cards/S-" in url:
                number = url.rsplit("-", 1)[1]
                return {"id": f"S-{number}", "localId": number, "name": f"{language} {number}", "category": "Pokemon",
                        "set": {"id": "S", "name": "Set"}}
            if url.endswith("pokemon-species/"):
                return {"results": []}
            raise AssertionError(f"Unexpected test URL: {url}")

        summary = populate_database(conn, fetcher=fake_fetcher, include_bulbapedia=False,
                                    checkpoint_path=checkpoint, schema_version=17)
        conn.close()
        self.assertEqual(summary["languages"]["fr"]["missingCards"], 2)
        plan = build_catalog.apply_language_fallback(db_file, checkpoint, 17, summary)
        self.assertEqual(summary["languages"]["fr"]["filledFromEnglish"], 2)
        self.assertEqual([e["status"] for e in plan], ["fillable"])
        coverage = build_catalog.build_coverage(db_file, summary)
        self.assertEqual(coverage["fr"]["cards"], 3)
        manifest = os.path.join(self.temp_dir, "manifest.json")
        report = os.path.join(self.temp_dir, "coverage_report.md")
        build_catalog.generate_manifest(db_file, manifest, report, 17, coverage, summary)
        verify_catalog_artifacts(db_file, manifest, SCHEMA_JSON_PATH)
        with open(report, encoding="utf-8") as handle:
            self.assertIn("Per Englisch ergänzt", handle.read())

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
