import json
import os
import sqlite3
import sys
import tempfile
import unittest
from unittest import mock

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import build_catalog
import collect_set_images

PNG_MAGIC = b"\x89PNG\r\n\x1a\n" + b"\x00" * 100


def _make_db(path: str) -> None:
    conn = sqlite3.connect(path)
    conn.executescript(
        """
        CREATE TABLE sets (id TEXT PRIMARY KEY, name TEXT, logoUrl TEXT, symbolUrl TEXT);
        CREATE TABLE cards (id TEXT PRIMARY KEY, setId TEXT, setLogoUrl TEXT, setSymbolUrl TEXT, setAbbreviation TEXT);
        INSERT INTO sets VALUES ('sv01', 'Karmesin & Purpur', 'https://assets.tcgdex.net/en/sv01/logo', 'https://assets.tcgdex.net/en/sv01/symbol');
        INSERT INTO sets VALUES ('base1', 'Grundset', NULL, NULL);
        INSERT INTO cards VALUES ('sv01-1', 'sv01', 'https://assets.tcgdex.net/en/sv01/logo/high.png', NULL, NULL);
        INSERT INTO cards VALUES ('base1-1', 'base1', NULL, NULL, NULL);
        """
    )
    conn.commit()
    conn.close()


class CollectSetImagesTest(unittest.TestCase):
    def setUp(self):
        # Keep throttling/backoff sleeps out of the unit tests.
        sleep_patcher = mock.patch.object(collect_set_images, "_sleep", lambda seconds: None)
        sleep_patcher.start()
        self.addCleanup(sleep_patcher.stop)
        collect_set_images._reset_network_state()
        self.temp_dir = tempfile.TemporaryDirectory()
        self.db_path = os.path.join(self.temp_dir.name, "catalog.sqlite")
        self.output_dir = os.path.join(self.temp_dir.name, "set-images")
        _make_db(self.db_path)
        # Manifest must be patched next to the *script*, so keep it in a temp copy of HERE:
        self.manifest_dir = tempfile.TemporaryDirectory()
        patcher = mock.patch.object(collect_set_images, "HERE", self.manifest_dir.name)
        self.mocked_here = patcher.start()
        self.addCleanup(patcher.stop)
        with open(os.path.join(self.manifest_dir.name, "manifest.json"), "w", encoding="utf-8") as handle:
            json.dump({"schemaVersion": 19, "sha256": "0" * 64, "size": 0}, handle)
        # Deterministic abbreviation mapping without touching the committed JSON:
        abbrev_patcher = mock.patch.object(
            build_catalog, "_SET_ABBREVIATIONS_CACHE",
            {"base1": {"abbreviation": "BS", "enName": "Base Set"}})
        abbrev_patcher.start()
        self.addCleanup(abbrev_patcher.stop)

    def tearDown(self):
        self.temp_dir.cleanup()
        self.manifest_dir.cleanup()

    def test_downloads_rewrite_urls_and_patch_manifest(self):
        def fake_download(url: str) -> bytes | None:
            if "sv01" in url:
                return PNG_MAGIC
            return None  # base1 has no tcgdex images

        with mock.patch.object(collect_set_images, "_download_image", side_effect=fake_download), \
                mock.patch.object(collect_set_images, "_bulbapedia_symbol_url", return_value=None):
            stats = collect_set_images.collect(self.db_path, self.output_dir, skip_bulbapedia=True)

        self.assertEqual(stats["logoDownloaded"], 1)
        self.assertEqual(stats["symbolDownloaded"], 1)
        self.assertTrue(os.path.isfile(os.path.join(self.output_dir, "sv01.logo.png")))
        self.assertTrue(os.path.isfile(os.path.join(self.output_dir, "sv01.symbol.png")))
        # base1 gets no files but still counts as "without anything".
        self.assertEqual(stats["setsWithoutAnything"], 1)

        conn = sqlite3.connect(self.db_path)
        set_row = conn.execute("SELECT logoUrl, symbolUrl FROM sets WHERE id='sv01'").fetchone()
        card_row = conn.execute("SELECT setLogoUrl, setSymbolUrl FROM cards WHERE setId='sv01'").fetchone()
        base_row = conn.execute("SELECT logoUrl, symbolUrl FROM sets WHERE id='base1'").fetchone()
        conn.close()
        base = collect_set_images.JSDELIVR_BASE
        self.assertEqual(set_row, (f"{base}/sv01.logo.png", f"{base}/sv01.symbol.png"))
        self.assertEqual(card_row, (f"{base}/sv01.logo.png", f"{base}/sv01.symbol.png"))
        self.assertEqual(base_row, (None, None))

        # Manifest sha256/size match the rewritten database.
        with open(os.path.join(self.manifest_dir.name, "manifest.json"), encoding="utf-8") as handle:
            manifest = json.load(handle)
        self.assertEqual(manifest["size"], os.path.getsize(self.db_path))
        import hashlib
        digest = hashlib.sha256()
        with open(self.db_path, "rb") as handle:
            for block in iter(lambda: handle.read(65536), b""):
                digest.update(block)
        self.assertEqual(manifest["sha256"], digest.hexdigest())

    def test_bulbapedia_symbol_fallback_fills_missing_symbols(self):
        def fake_download(url: str) -> bytes | None:
            if "sv01" in url:
                return PNG_MAGIC  # TCGdex serves the German set's images
            if "archives.example" in url:
                return PNG_MAGIC  # the Bulbapedia archives symbol scan
            return None

        def fake_bulba(en_name: str) -> str | None:
            if en_name == "Base Set":
                return "https://archives.example/SetSymbolBase_Set.png"
            return None

        with mock.patch.object(collect_set_images, "_download_image", side_effect=fake_download), \
                mock.patch.object(collect_set_images, "_bulbapedia_symbol_url", side_effect=fake_bulba):
            stats = collect_set_images.collect(self.db_path, self.output_dir)

        # Only base1 (no symbol anywhere) goes through the Bulbapedia fallback; there is
        # no logo fallback because Bulbapedia Archives has no TCG set logos.
        self.assertEqual(stats["bulbapediaSymbols"], 1)
        conn = sqlite3.connect(self.db_path)
        row = conn.execute("SELECT logoUrl, symbolUrl FROM sets WHERE id='base1'").fetchone()
        conn.close()
        base = collect_set_images.JSDELIVR_BASE
        self.assertEqual(row, (None, f"{base}/base1.symbol.png"))
        self.assertTrue(os.path.isfile(os.path.join(self.output_dir, "base1.symbol.png")))
        self.assertFalse(os.path.exists(os.path.join(self.output_dir, "base1.logo.png")))

    def test_all_downloads_failing_fails_loudly_with_annotation(self):
        with mock.patch.object(collect_set_images, "_http_get",
                               side_effect=OSError("HTTP Error 429")), \
                mock.patch.object(collect_set_images, "_bulbapedia_symbol_url", return_value=None):
            with self.assertRaises(ValueError) as ctx:
                collect_set_images.collect(self.db_path, self.output_dir)
        message = str(ctx.exception)
        self.assertIn("0 of 2", message)
        # The annotation names the host and the first error so CI shows the cause.
        self.assertIn("[assets.tcgdex.net]", message)
        self.assertIn("HTTP Error 429", message)

    def test_no_referenced_images_never_fails(self):
        # A catalog without any image URLs (e.g. fresh subsets) must not trip the guard.
        db_path = os.path.join(self.temp_dir.name, "empty.sqlite")
        conn = sqlite3.connect(db_path)
        conn.executescript(
            "CREATE TABLE sets (id TEXT PRIMARY KEY, name TEXT, logoUrl TEXT, symbolUrl TEXT);"
            "CREATE TABLE cards (id TEXT PRIMARY KEY, setId TEXT, setLogoUrl TEXT, setSymbolUrl TEXT, setAbbreviation TEXT);"
            "INSERT INTO sets VALUES ('x', 'X', NULL, NULL);")
        conn.commit()
        conn.close()
        with mock.patch.object(collect_set_images, "_download_image", return_value=None), \
                mock.patch.object(collect_set_images, "_bulbapedia_symbol_url", return_value=None):
            stats = collect_set_images.collect(db_path, self.output_dir)
        self.assertEqual(stats["urlRewrites"], 0)

    def test_existing_files_are_reused_without_downloads(self):
        # A previous run committed sv01.logo.png / sv01.symbol.png: they are reused
        # instead of re-downloaded, and the URLs are still rewritten to jsDelivr.
        os.makedirs(self.output_dir, exist_ok=True)
        for kind in ("logo", "symbol"):
            with open(os.path.join(self.output_dir, f"sv01.{kind}.png"), "wb") as handle:
                handle.write(PNG_MAGIC)

        def fail_if_called(url: str) -> bytes | None:
            raise AssertionError(f"must not download when the file exists: {url}")

        with mock.patch.object(collect_set_images, "_download_image", side_effect=fail_if_called), \
                mock.patch.object(collect_set_images, "_bulbapedia_symbol_url", return_value=None):
            stats = collect_set_images.collect(self.db_path, self.output_dir, skip_bulbapedia=True)

        self.assertEqual(stats["alreadyPresent"], 2)
        self.assertEqual(stats["logoDownloaded"], 0)
        self.assertEqual(stats["symbolDownloaded"], 0)
        conn = sqlite3.connect(self.db_path)
        row = conn.execute("SELECT logoUrl, symbolUrl FROM sets WHERE id='sv01'").fetchone()
        conn.close()
        base = collect_set_images.JSDELIVR_BASE
        self.assertEqual(row, (f"{base}/sv01.logo.png", f"{base}/sv01.symbol.png"))

    def test_circuit_breaker_disables_failing_hosts_quickly(self):
        # With a one-strike breaker a dead host must not be hammered for every
        # remaining URL — that is what made the 2026-11 CI run crawl for hours.
        calls = []

        def failing_get(url: str) -> bytes:
            calls.append(url)
            raise OSError("connection refused")

        with mock.patch.object(collect_set_images, "_http_get", side_effect=failing_get), \
                mock.patch.object(collect_set_images, "MAX_CONSECUTIVE_FAILURES", 1):
            with self.assertRaises(ValueError) as ctx:
                collect_set_images.collect(self.db_path, self.output_dir)

        # sv01 logo fails once -> assets.tcgdex.net disabled, sv01 symbol skipped.
        # base1 symbol lookup fails once -> archives.bulbagarden.net disabled.
        self.assertEqual(len(calls), 2, calls)
        self.assertEqual(
            collect_set_images._disabled_hosts,
            {"assets.tcgdex.net", "archives.bulbagarden.net"})
        self.assertIn("hosts disabled", str(ctx.exception))

    def test_time_budget_stops_all_downloads(self):
        calls = []

        def counting_get(url: str) -> bytes:
            calls.append(url)
            return PNG_MAGIC

        with mock.patch.object(collect_set_images, "_http_get", side_effect=counting_get):
            with self.assertRaises(ValueError) as ctx:
                collect_set_images.collect(self.db_path, self.output_dir, max_seconds=0)
        # Budget exhausted before the first request: no network traffic at all.
        self.assertEqual(calls, [])
        self.assertIn("time budget exhausted", str(ctx.exception))

    def test_http_get_retries_with_backoff_before_succeeding(self):
        attempts = {"n": 0}

        def flaky_urlopen(request, timeout=None):
            attempts["n"] += 1
            if attempts["n"] < 2:
                raise OSError("boom")
            return mock.MagicMock(
                __enter__=lambda self: mock.MagicMock(read=lambda: PNG_MAGIC),
                __exit__=lambda self, *a: False)

        with mock.patch.object(collect_set_images.urllib.request, "urlopen", side_effect=flaky_urlopen):
            data = collect_set_images._http_get("https://assets.tcgdex.net/en/sv01/symbol/high.png")
        self.assertEqual(data, PNG_MAGIC)
        self.assertEqual(attempts["n"], 2)

    def test_download_rejects_non_image_payloads(self):
        with mock.patch.object(collect_set_images, "_http_get", return_value=b"<html>not an image</html>"):
            self.assertIsNone(collect_set_images._download_image("https://example.com/symbol"))
        with mock.patch.object(collect_set_images, "_http_get", return_value=b""):
            self.assertIsNone(collect_set_images._download_image("https://example.com/symbol"))
        with mock.patch.object(collect_set_images, "_http_get", return_value=PNG_MAGIC):
            self.assertEqual(collect_set_images._download_image("https://example.com/symbol"), PNG_MAGIC)

    def test_bulbapedia_direct_title_match_wins(self):
        def fake_fetch(url: str) -> bytes:
            self.assertIn("titles=File%3ASetSymbolBase+Set.png", url.replace("%20", "+"))
            return json.dumps({"query": {"pages": [{
                "title": "File:SetSymbolBase Set.png", "missing": False,
                "imageinfo": [{"mime": "image/png", "url": "https://archives.example/SetSymbolBase_Set.png"}]
            }]}}).encode("utf-8")

        with mock.patch.object(collect_set_images, "_fetch", side_effect=fake_fetch):
            self.assertEqual(collect_set_images._bulbapedia_symbol_url("Base Set"),
                             "https://archives.example/SetSymbolBase_Set.png")

    def test_bulbapedia_ampersand_is_spelled_out(self):
        # "Scarlet & Violet" is filed as "SetSymbolScarlet and Violet.png".
        seen = {}

        def fake_fetch(url: str) -> bytes:
            if "titles=" in url:
                seen["titles"] = url
                return json.dumps({"query": {"pages": [{
                    "title": "File:SetSymbolScarlet and Violet.png", "missing": False,
                    "imageinfo": [{"mime": "image/png", "url": "https://archives.example/sv.png"}]
                }]}}).encode("utf-8")
            seen["prefix"] = url
            raise AssertionError("unexpected prefix search for a direct hit")

        with mock.patch.object(collect_set_images, "_fetch", side_effect=fake_fetch):
            self.assertEqual(collect_set_images._bulbapedia_symbol_url("Scarlet & Violet"),
                             "https://archives.example/sv.png")
        self.assertIn("SetSymbolScarlet+and+Violet", seen["titles"].replace("%20", "+"))

    def test_bulbapedia_prefix_search_prefers_the_exact_file(self):
        def fake_fetch(url: str) -> bytes:
            if "titles=" in url:
                return json.dumps({"query": {"pages": [
                    {"title": "File:SetSymbolBase Set.png", "missing": True}]}}).encode("utf-8")
            if "allimages" in url:
                self.assertIn("aiprefix=SetSymbolBase+Set", url.replace("%20", "+"))
                return json.dumps({"query": {"allimages": [
                    # "Base Set 2" sorts first but belongs to a different set.
                    {"name": "SetSymbolBase_Set_2.png", "url": "https://archives.example/set2.png"},
                    {"name": "SetSymbolBase_Set.png", "url": "https://archives.example/symbol.png"},
                ]}}).encode("utf-8")
            raise AssertionError("unexpected url: " + url)

        with mock.patch.object(collect_set_images, "_fetch", side_effect=fake_fetch):
            self.assertEqual(collect_set_images._bulbapedia_symbol_url("Base Set"),
                             "https://archives.example/symbol.png")

    def test_bulbapedia_lookup_never_raises(self):
        with mock.patch.object(collect_set_images, "_fetch", side_effect=OSError("blocked")):
            self.assertIsNone(collect_set_images._bulbapedia_symbol_url("Base Set"))
        self.assertIsNone(collect_set_images._bulbapedia_symbol_url(""))


if __name__ == "__main__":
    unittest.main()
