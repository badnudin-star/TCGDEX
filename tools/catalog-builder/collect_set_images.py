#!/usr/bin/env python3
"""Self-host the set symbol and logo images and rewrite the catalog URLs.

Why: TCGdex renders set images only for a subset of sets and serves them from its own
CDN. This script makes the app independent of that CDN:

1. Downloads every set logo/symbol the catalog still references at assets.tcgdex.net
   into the repo directory `set-images/` (committed by the catalog workflow).
2. For sets without any symbol, tries the Bulbapedia Archives MediaWiki API (fair-use
   scans of the printed symbols) as a best-effort fallback. Failures are non-fatal.
3. Rewrites sets.logoUrl/symbolUrl (and the mirrored card columns) to stable
   jsDelivr URLs pointing at the self-hosted files.
4. Patches sha256/size in manifest.json because the catalog database changed.

Run after build_catalog.py: python tools/catalog-builder/collect_set_images.py
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
import time
import urllib.parse
import urllib.request

import sqlite3

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
sys.path.insert(0, HERE)

from build_catalog import set_abbreviations  # noqa: E402  (MIT tcgdex cards-database mapping)

REPO_IMAGE_DIR = os.path.join(ROOT, "set-images")
BULBAPEDIA_API = "https://archives.bulbagarden.net/w/api.php"
USER_AGENT = "TCG-Sammlung catalog pipeline (github.com/badnudin-star/TCGDEX)"
JSDELIVR_BASE = "https://cdn.jsdelivr.net/gh/badnudin-star/TCGDEX@main/set-images"
DOWNLOAD_TIMEOUT = 25


def _http_get(url: str) -> bytes:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=DOWNLOAD_TIMEOUT) as response:
        return response.read()


def _download_image(url: str) -> bytes | None:
    try:
        data = _http_get(url)
    except Exception as exc:
        print(f"  download failed: {url} ({exc})")
        return None
    if not data or len(data) < 64:
        return None
    # Only accept raster formats Coil can display without extra decoders.
    if not (data.startswith(b"\x89PNG") or data.startswith(b"\xff\xd8\xff") or data[:4] == b"RIFF"):
        print(f"  unsupported image format at {url} ({len(data)} bytes)")
        return None
    return data


def _save_image(output_dir: str, set_id: str, kind: str, data: bytes) -> str:
    os.makedirs(output_dir, exist_ok=True)
    filename = f"{set_id}.{kind}.png"
    path = os.path.join(output_dir, filename)
    with open(path, "wb") as handle:
        handle.write(data)
    return filename


def _bulbapedia_symbol_url(en_name: str) -> str | None:
    """Best-effort lookup of a set symbol scan in the Bulbapedia Archives."""
    if not en_name:
        return None
    try:
        # 1) direct file title candidates
        titles = [f"File:{en_name} symbol.png", f"File:{en_name} Symbol.png",
                  f"File:{en_name} set symbol.png"]
        query = {"action": "query", "format": "json", "formatversion": "2",
                 "titles": "|".join(titles), "prop": "imageinfo", "iiprop": "url|mime"}
        url = BULBAPEDIA_API + "?" + urllib.parse.urlencode(query)
        data = json.loads(_http_get(url).decode("utf-8"))
        for page in data.get("query", {}).get("pages", []):
            info = (page.get("imageinfo") or [{}])[0]
            if info.get("mime") in ("image/png", "image/jpeg") and info.get("url"):
                return str(info["url"])
        # 2) prefix search for anything with "symbol" in the name
        query = {"action": "query", "format": "json", "formatversion": "2",
                 "list": "allimages", "aiprefix": en_name, "ailimit": "50"}
        url = BULBAPEDIA_API + "?" + urllib.parse.urlencode(query)
        data = json.loads(_http_get(url).decode("utf-8"))
        for image in data.get("query", {}).get("allimages", []):
            name = str(image.get("name", "")).lower()
            if "symbol" in name and name.endswith((".png", ".jpg", ".jpeg")):
                return str(image.get("url") or "") or None
    except Exception as exc:
        print(f"  Bulbapedia lookup failed for '{en_name}': {exc}")
    return None


def collect(db_path: str, output_dir: str, *, skip_bulbapedia: bool = False,
            release_base: str = JSDELIVR_BASE) -> dict[str, int]:
    # Always create the directory so the workflow's `git add set-images` works even when
    # every download failed (the badge fallback covers sets without images in that case).
    os.makedirs(output_dir, exist_ok=True)
    db = sqlite3.connect(db_path)
    try:
        sets = db.execute("SELECT id, name, logoUrl, symbolUrl FROM sets").fetchall()
        abbreviations = set_abbreviations()
        stats = {"logoDownloaded": 0, "symbolDownloaded": 0, "bulbapediaSymbols": 0,
                 "setsWithoutAnything": 0, "urlRewrites": 0}
        for set_id, name, logo_url, symbol_url in sets:
            collected = {"logo": None, "symbol": None}
            if logo_url:
                data = _download_image(logo_url)
                if data:
                    _save_image(output_dir, set_id, "logo", data)
                    collected["logo"] = f"{release_base}/{set_id}.logo.png"
                    stats["logoDownloaded"] += 1
            if symbol_url:
                data = _download_image(symbol_url)
                if data:
                    _save_image(output_dir, set_id, "symbol", data)
                    collected["symbol"] = f"{release_base}/{set_id}.symbol.png"
                    stats["symbolDownloaded"] += 1
            if not collected["symbol"] and not skip_bulbapedia:
                en_name = abbreviations.get(set_id, {}).get("enName") or ""
                url = _bulbapedia_symbol_url(en_name)
                if url:
                    data = _download_image(url)
                    if data:
                        _save_image(output_dir, set_id, "symbol", data)
                        collected["symbol"] = f"{release_base}/{set_id}.symbol.png"
                        stats["bulbapediaSymbols"] += 1
            if collected["logo"] or collected["symbol"]:
                db.execute("UPDATE sets SET logoUrl=COALESCE(?, logoUrl), symbolUrl=COALESCE(?, symbolUrl) WHERE id=?",
                           (collected["logo"], collected["symbol"], set_id))
                if collected["logo"] or collected["symbol"]:
                    db.execute("UPDATE cards SET setLogoUrl=?, setSymbolUrl=? WHERE setId=?",
                               (collected["logo"], collected["symbol"], set_id))
                stats["urlRewrites"] += 1
            else:
                stats["setsWithoutAnything"] += 1
        db.commit()

        remaining = db.execute(
            "SELECT COUNT(*) FROM sets WHERE (symbolUrl IS NULL OR symbolUrl='')").fetchone()[0]
        print(f"Set images collected: {stats['logoDownloaded']} logos + "
              f"{stats['symbolDownloaded']} symbols from TCGdex, {stats['bulbapediaSymbols']} "
              f"symbols from Bulbapedia; {stats['setsWithoutAnything']} sets remain without any "
              f"image, {remaining} sets without a symbol (badges cover them).")
    finally:
        db.close()

    manifest_path = os.path.join(HERE, "manifest.json")
    if os.path.isfile(manifest_path):
        digest = hashlib.sha256()
        with open(db_path, "rb") as handle:
            for block in iter(lambda: handle.read(65536), b""):
                digest.update(block)
        with open(manifest_path, "r", encoding="utf-8") as handle:
            manifest = json.load(handle)
        manifest["sha256"] = digest.hexdigest()
        manifest["size"] = os.path.getsize(db_path)
        with open(manifest_path, "w", encoding="utf-8") as handle:
            json.dump(manifest, handle, indent=2, ensure_ascii=False)
        print("manifest.json patched (sha256/size) after URL rewrite.")
    return stats


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--db", default=os.path.join(HERE, "catalog.sqlite"))
    parser.add_argument("--output-dir", default=REPO_IMAGE_DIR)
    parser.add_argument("--skip-bulbapedia", action="store_true",
                        help="Skip the fair-use Bulbapedia fallback for sets without symbols")
    args = parser.parse_args()
    started = time.time()
    stats = collect(args.db, args.output_dir, skip_bulbapedia=args.skip_bulbapedia)
    print(f"collect_set_images finished in {time.time() - started:.1f}s: {stats}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
