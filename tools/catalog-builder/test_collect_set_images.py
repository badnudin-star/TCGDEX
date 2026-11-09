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
        collect_set_images._download_errors.clear()
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
                mock.patch.object(collect_set_images, "_bulbapedia_image_url", return_value=None):
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

    def test_bulbapedia_symbol_and_logo_fallbacks_fill_missing_images(self):
        def fake_download(url: str) -> bytes | None:
            if "sv01" in url:
                return PNG_MAGIC  # TCGdex serves the German set's images
            if "bulbagarden" in url or "archives.example" in url:
                return PNG_MAGIC  # the Bulbapedia archives URLs
            return None

        def fake_bulba(en_name: str, kind: str) -> str | None:
            if en_name == "Base Set":
                return f"https://archives.example/{en_name}-{kind}.png"
            return None

        with mock.patch.object(collect_set_images, "_download_image", side_effect=fake_download), \
                mock.patch.object(collect_set_images, "_bulbapedia_image_url", side_effect=fake_bulba):
            stats = collect_set_images.collect(self.db_path, self.output_dir)

        # Only base1 (no image anywhere) goes through the Bulbapedia fallbacks.
        self.assertEqual(stats["bulbapediaSymbols"], 1)
        self.assertEqual(stats["bulbapediaLogos"], 1)
        conn = sqlite3.connect(self.db_path)
        row = conn.execute("SELECT logoUrl, symbolUrl FROM sets WHERE id='base1'").fetchone()
        conn.close()
        base = collect_set_images.JSDELIVR_BASE
        self.assertEqual(row, (f"{base}/base1.logo.png", f"{base}/base1.symbol.png"))
        self.assertTrue(os.path.isfile(os.path.join(self.output_dir, "base1.symbol.png")))
        self.assertTrue(os.path.isfile(os.path.join(self.output_dir, "base1.logo.png")))

    def test_all_downloads_failing_fails_loudly_with_annotation(self):
        with mock.patch.object(collect_set_images, "_download_image", return_value=None), \
                mock.patch.object(collect_set_images, "_bulbapedia_image_url", return_value=None):
            collect_set_images._download_errors.append("https://assets.tcgdex.net/en/sv01/logo -> HTTP Error 429")
            with self.assertRaisesRegex(ValueError, "0 of 2"):
                collect_set_images.collect(self.db_path, self.output_dir)

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
                mock.patch.object(collect_set_images, "_bulbapedia_image_url", return_value=None):
            stats = collect_set_images.collect(db_path, self.output_dir)
        self.assertEqual(stats["urlRewrites"], 0)

    def test_http_get_retries_with_backoff_before_succeeding(self):
        attempts = {"n": 0}

        def flaky_urlopen(request, timeout=None):
            attempts["n"] += 1
            if attempts["n"] < 3:
                raise OSError("boom")
            return mock.MagicMock(
                __enter__=lambda self: mock.MagicMock(read=lambda: PNG_MAGIC),
                __exit__=lambda self, *a: False)

        with mock.patch.object(collect_set_images.urllib.request, "urlopen", side_effect=flaky_urlopen):
            data = collect_set_images._http_get("https://assets.tcgdex.net/en/sv01/symbol/high.png")
        self.assertEqual(data, PNG_MAGIC)
        self.assertEqual(attempts["n"], 3)

    def test_download_rejects_non_image_payloads(self):
        with mock.patch.object(collect_set_images, "_http_get", return_value=b"<html>not an image</html>"):
            self.assertIsNone(collect_set_images._download_image("https://example.com/symbol"))
        with mock.patch.object(collect_set_images, "_http_get", return_value=b""):
            self.assertIsNone(collect_set_images._download_image("https://example.com/symbol"))
        with mock.patch.object(collect_set_images, "_http_get", return_value=PNG_MAGIC):
            self.assertEqual(collect_set_images._download_image("https://example.com/symbol"), PNG_MAGIC)

    def test_bulbapedia_direct_title_match_wins(self):
        def fake_get(url: str) -> bytes:
            query = url.split("?", 1)[1]
            if "titles=" in query:
                return json.dumps({"query": {"pages": [{
                    "title": "File:Base Set symbol.png", "missing": False,
                    "imageinfo": [{"mime": "image/png", "url": "https://archives.example/symbol.png"}]
                }]}}).encode("utf-8")
            raise AssertionError("unexpected prefix search for a direct hit: " + url)

        with mock.patch.object(collect_set_images, "_http_get", side_effect=fake_get):
            self.assertEqual(collect_set_images._bulbapedia_image_url("Base Set", "symbol"),
                             "https://archives.example/symbol.png")

    def test_bulbapedia_prefix_search_is_the_fallback(self):
        def fake_get(url: str) -> bytes:
            query = url.split("?", 1)[1]
            if "titles=" in query:
                return json.dumps({"query": {"pages": [{"title": "File:Base Set symbol.png", "missing": True}]}}).encode("utf-8")
            if "allimages" in query:
                return json.dumps({"query": {"allimages": [
                    {"name": "File:Base Set booster pack.jpg", "url": "https://archives.example/pack.jpg"},
                    {"name": "File:Base Set symbol.png", "url": "https://archives.example/symbol.png"},
                    {"name": "File:Base Set logo.png", "url": "https://archives.example/logo.png"},
                ]}}).encode("utf-8")
            raise AssertionError("unexpected url: " + url)

        with mock.patch.object(collect_set_images, "_http_get", side_effect=fake_get):
            self.assertEqual(collect_set_images._bulbapedia_image_url("Base Set", "symbol"),
                             "https://archives.example/symbol.png")
            self.assertEqual(collect_set_images._bulbapedia_image_url("Base Set", "logo"),
                             "https://archives.example/logo.png")

    def test_bulbapedia_lookup_never_raises(self):
        with mock.patch.object(collect_set_images, "_http_get", side_effect=OSError("blocked")):
            self.assertIsNone(collect_set_images._bulbapedia_image_url("Base Set", "symbol"))
            self.assertIsNone(collect_set_images._bulbapedia_image_url("Base Set", "logo"))
        self.assertIsNone(collect_set_images._bulbapedia_image_url("", "symbol"))
        self.assertIsNone(collect_set_images._bulbapedia_image_url("Base Set", "poster"))


if __name__ == "__main__":
    unittest.main()
