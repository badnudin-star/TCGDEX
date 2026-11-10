#!/usr/bin/env python3
"""Self-host the set symbol and logo images and rewrite the catalog URLs.

Why: TCGdex renders set images only for a subset of sets and serves them from its own
CDN. This script makes the app independent of that CDN:

1. Downloads every set logo/symbol the catalog still references at assets.tcgdex.net
   into the repo directory `set-images/` (committed by the catalog workflow).
2. For sets without a symbol, looks up the Bulbapedia Archives MediaWiki API
   (fair-use scans of the printed marks). Archives names those files
   "File:SetSymbol<Set>.png" with "&" spelled out as "and" — e.g.
   "SetSymbolScarlet and Violet.png" — so the lookup uses exactly that convention.
   Archives has no TCG set logos, so there is no logo fallback.
3. Rewrites sets.logoUrl/symbolUrl (and the mirrored card columns) to stable
   jsDelivr URLs pointing at the self-hosted files. Images already present in
   set-images/ are reused without another download, which keeps weekly runs
   incremental.
4. Patches sha256/size in manifest.json because the catalog database changed.

Failure containment (learned the hard way: a 2026-11 CI run crawled ~500 Bulbapedia
lookups for hours because every request failed with retries):

- Every download is throttled and retried only twice with a short timeout; a full
  retry storm across ~500 requests would still take hours when a host is down.
- A per-host circuit breaker disables a host for the rest of the run after
  MAX_CONSECUTIVE_FAILURES consecutive failures. HTTP 404 does not count: on the
  TCGdex CDN most referenced set images simply do not exist, which is a stable
  answer, not an outage (and it is not retried either).
- A global time budget (default 20 minutes) stops all further requests; the run
  then finishes with whatever was collected so far.
- Missing images never abort the pipeline (the app has a text badge fallback);
  a ::warning annotation with the first error per host keeps the cause visible.

Run after build_catalog.py: python tools/catalog-builder/collect_set_images.py
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
import time
import urllib.error
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
# Only URLs on the TCGdex CDN are candidates for self-hosting. Other hosts in
# logoUrl/symbolUrl are deliberate external references (official Pokémon CDN logos
# from set_logo_overrides.json): linked, never downloaded or rewritten.
TCGDEX_CDN_HOST = "assets.tcgdex.net"
DOWNLOAD_TIMEOUT = 10
DOWNLOAD_ATTEMPTS = 2
DOWNLOAD_BACKOFF_SECONDS = 1.0
# Small pause between downloads so the CDN does not answer bursts with 429s.
THROTTLE_SECONDS = 0.25
# After this many consecutive failures a host is disabled for the rest of the run.
MAX_CONSECUTIVE_FAILURES = 6
# Hard stop for all network traffic so the workflow step stays in the minutes range
# even when both hosts are slow. Existing files are still reused afterwards.
DEFAULT_MAX_SECONDS = 1200.0


class _Skipped(Exception):
    """Raised when a host is circuit-broken or the time budget is exhausted."""


class _NotFound(Exception):
    """HTTP 404 - the image does not exist at that URL (common on the TCGdex CDN)."""


# Per-run network state (reset by collect()).
_download_errors: dict[str, list[str]] = {}
_host_failures: dict[str, int] = {}
_disabled_hosts: set[str] = set()
_budget_deadline: float | None = None
_budget_exhausted = False
_skipped_requests = 0
_not_found_requests = 0


def _sleep(seconds: float) -> None:
    """Hook for tests: throttling/backoff without real waiting."""
    if seconds > 0:
        time.sleep(seconds)


def _reset_network_state() -> None:
    global _download_errors, _host_failures, _disabled_hosts
    global _budget_deadline, _budget_exhausted, _skipped_requests, _not_found_requests
    _download_errors = {}
    _host_failures = {}
    _disabled_hosts = set()
    _budget_deadline = None
    _budget_exhausted = False
    _skipped_requests = 0
    _not_found_requests = 0


def _host_of(url: str) -> str:
    return urllib.parse.urlparse(url).netloc


def _record_error(url: str, exc: Exception) -> None:
    errors = _download_errors.setdefault(_host_of(url), [])
    if len(errors) < 2:
        errors.append(f"{url} -> {exc}")


def _http_get(url: str) -> bytes:
    last_error: Exception | None = None
    for attempt in range(DOWNLOAD_ATTEMPTS):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(request, timeout=DOWNLOAD_TIMEOUT) as response:
                return response.read()
        except urllib.error.HTTPError as exc:
            if exc.code == 404:
                # "Does not exist" is a stable answer: retrying cannot help.
                raise _NotFound(f"{url} -> HTTP Error 404") from exc
            last_error = exc
        except Exception as exc:  # noqa: BLE001 - urllib raises many error types
            last_error = exc
        if attempt + 1 < DOWNLOAD_ATTEMPTS:
            _sleep(DOWNLOAD_BACKOFF_SECONDS * (attempt + 1))
    raise last_error if last_error else RuntimeError(f"download failed: {url}")


def _fetch(url: str) -> bytes:
    """HTTP GET with circuit breaker, time budget, and per-host error recording."""
    global _skipped_requests, _budget_exhausted, _not_found_requests
    host = _host_of(url)
    if _budget_deadline is not None and time.monotonic() >= _budget_deadline:
        _budget_exhausted = True
        _skipped_requests += 1
        raise _Skipped("time budget exhausted")
    if host in _disabled_hosts:
        _skipped_requests += 1
        raise _Skipped(f"{host} disabled after {MAX_CONSECUTIVE_FAILURES} consecutive failures")
    try:
        data = _http_get(url)
    except _NotFound:
        # Most TCGdex set-image URLs simply do not exist; that is not an outage.
        _not_found_requests += 1
        raise
    except Exception as exc:  # noqa: BLE001
        failures = _host_failures.get(host, 0) + 1
        _host_failures[host] = failures
        if failures >= MAX_CONSECUTIVE_FAILURES and host not in _disabled_hosts:
            _disabled_hosts.add(host)
            print(f"  circuit breaker: {host} disabled after {failures} consecutive failures")
        _record_error(url, exc)
        raise
    _host_failures[host] = 0
    return data


def _download_image(url: str) -> bytes | None:
    try:
        data = _fetch(url)
    except _Skipped as exc:
        print(f"  skipped: {url} ({exc})")
        return None
    except _NotFound:
        print(f"  not found: {url}")
        return None
    except Exception as exc:  # noqa: BLE001
        print(f"  download failed: {url} ({exc})")
        return None
    finally:
        _sleep(THROTTLE_SECONDS)
    if not data or len(data) < 64:
        _record_error(url, RuntimeError(f"empty payload ({len(data or b'')} bytes)"))
        return None
    # Only accept raster formats Coil can display without extra decoders.
    if not (data.startswith(b"\x89PNG") or data.startswith(b"\xff\xd8\xff") or data[:4] == b"RIFF"):
        print(f"  unsupported image format at {url} ({len(data)} bytes)")
        _record_error(url, RuntimeError("unsupported format"))
        return None
    return data


def _existing_image(output_dir: str, release_base: str, set_id: str, kind: str) -> str | None:
    """A previously committed image is reused instead of downloading it again."""
    path = os.path.join(output_dir, f"{set_id}.{kind}.png")
    if os.path.isfile(path) and os.path.getsize(path) >= 64:
        return f"{release_base}/{set_id}.{kind}.png"
    return None


def _save_image(output_dir: str, set_id: str, kind: str, data: bytes) -> str:
    os.makedirs(output_dir, exist_ok=True)
    filename = f"{set_id}.{kind}.png"
    path = os.path.join(output_dir, filename)
    with open(path, "wb") as handle:
        handle.write(data)
    return filename


def _bulbapedia_symbol_url(en_name: str) -> str | None:
    """Best-effort lookup of a set symbol scan in the Bulbapedia Archives.

    Archives names set symbol files "File:SetSymbol<Set>.png" with "&" spelled out
    as "and" (verified against the live API, e.g. "SetSymbolScarlet and
    Violet.png", "SetSymbolBase Set.png"). The earlier probe of
    "File:<Set> symbol.png" style titles matched nothing, which is why the
    fallback used to come up empty.
    """
    if not en_name:
        return None
    normalized = " ".join(en_name.replace("&", "and").split())
    try:
        # 1) direct file title
        titles = [f"File:SetSymbol{normalized}.png"]
        query = {"action": "query", "format": "json", "formatversion": "2",
                 "titles": "|".join(titles), "prop": "imageinfo", "iiprop": "url|mime"}
        url = BULBAPEDIA_API + "?" + urllib.parse.urlencode(query)
        data = json.loads(_fetch(url).decode("utf-8"))
        for page in data.get("query", {}).get("pages", []):
            info = (page.get("imageinfo") or [{}])[0]
            if info.get("mime") in ("image/png", "image/jpeg") and info.get("url"):
                return str(info["url"])
        # 2) prefix search catches underscore variants and suffixed reprints
        query = {"action": "query", "format": "json", "formatversion": "2",
                 "list": "allimages", "aiprefix": f"SetSymbol{normalized}", "ailimit": "50"}
        url = BULBAPEDIA_API + "?" + urllib.parse.urlencode(query)
        data = json.loads(_fetch(url).decode("utf-8"))
        # allimages "name" values have no "File:" prefix and use underscores.
        exact = f"setsymbol{normalized.replace(' ', '_')}.png".lower()
        first: str | None = None
        for image in data.get("query", {}).get("allimages", []):
            name = str(image.get("name", ""))
            url = str(image.get("url") or "")
            if not url or not name.lower().endswith((".png", ".jpg", ".jpeg")):
                continue
            if name.lower() == exact:
                return url
            if first is None:
                first = url
        return first
    except _Skipped:
        return None
    except Exception as exc:  # noqa: BLE001
        print(f"  Bulbapedia lookup failed for '{en_name}': {exc}")
        return None


def _first_errors_summary() -> str:
    if not _download_errors:
        return "n/a"
    return "; ".join(f"[{host}] " + "; ".join(errors)
                     for host, errors in sorted(_download_errors.items()))


def _is_selfhost_candidate(url: str) -> bool:
    """True only for URLs the pipeline may download and self-host (TCGdex CDN).

    Everything else in logoUrl/symbolUrl is a deliberate external reference - the
    official Pokémon CDN logos linked by set_logo_overrides.json. Those are
    referenced, never copied, so they are skipped as download candidates."""
    try:
        host = urllib.parse.urlparse(url).hostname or ""
    except ValueError:
        return False
    return host.lower() == TCGDEX_CDN_HOST


def collect(db_path: str, output_dir: str, *, skip_bulbapedia: bool = False,
            release_base: str = JSDELIVR_BASE,
            max_seconds: float | None = DEFAULT_MAX_SECONDS) -> dict[str, int]:
    global _budget_deadline
    _reset_network_state()
    if max_seconds is not None:
        _budget_deadline = time.monotonic() + max_seconds
    # Always create the directory so the workflow's `git add set-images` works even when
    # every download failed (the badge fallback covers sets without images in that case).
    os.makedirs(output_dir, exist_ok=True)
    db = sqlite3.connect(db_path)
    try:
        sets = db.execute("SELECT id, name, logoUrl, symbolUrl FROM sets").fetchall()
        abbreviations = set_abbreviations()
        stats = {"logoCandidates": 0, "symbolCandidates": 0,
                 "logoDownloaded": 0, "symbolDownloaded": 0,
                 "bulbapediaSymbols": 0, "alreadyPresent": 0,
                 "setsWithoutAnything": 0, "urlRewrites": 0,
                 "externalReferences": 0}
        for set_id, name, logo_url, symbol_url in sets:
            collected = {"logo": None, "symbol": None}
            external = False
            if logo_url:
                if _is_selfhost_candidate(logo_url):
                    stats["logoCandidates"] += 1
                    existing = _existing_image(output_dir, release_base, set_id, "logo")
                    if existing:
                        collected["logo"] = existing
                        stats["alreadyPresent"] += 1
                    else:
                        data = _download_image(logo_url)
                        if data:
                            _save_image(output_dir, set_id, "logo", data)
                            collected["logo"] = f"{release_base}/{set_id}.logo.png"
                            stats["logoDownloaded"] += 1
                else:
                    # Deliberate external reference (official Pokémon CDN logo):
                    # linked, never downloaded or rewritten.
                    external = True
            if symbol_url:
                if _is_selfhost_candidate(symbol_url):
                    stats["symbolCandidates"] += 1
                    existing = _existing_image(output_dir, release_base, set_id, "symbol")
                    if existing:
                        collected["symbol"] = existing
                        stats["alreadyPresent"] += 1
                    else:
                        data = _download_image(symbol_url)
                        if data:
                            _save_image(output_dir, set_id, "symbol", data)
                            collected["symbol"] = f"{release_base}/{set_id}.symbol.png"
                            stats["symbolDownloaded"] += 1
                else:
                    external = True
            if not skip_bulbapedia and not collected["symbol"]:
                # No logo fallback: Bulbapedia Archives has no TCG set logos.
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
                # COALESCE keeps card rows that only got one new image (e.g. a
                # Bulbapedia symbol) from losing their other set column - the
                # official externally linked logos must survive this pass.
                db.execute("UPDATE cards SET setLogoUrl=COALESCE(?, setLogoUrl), setSymbolUrl=COALESCE(?, setSymbolUrl) WHERE setId=?",
                           (collected["logo"], collected["symbol"], set_id))
                stats["urlRewrites"] += 1
            elif external:
                stats["externalReferences"] += 1
            else:
                stats["setsWithoutAnything"] += 1
        db.commit()

        remaining = db.execute(
            "SELECT COUNT(*) FROM sets WHERE (symbolUrl IS NULL OR symbolUrl='')").fetchone()[0]
        containment = ""
        if _disabled_hosts or _budget_exhausted:
            reasons = []
            if _disabled_hosts:
                reasons.append(f"hosts disabled: {', '.join(sorted(_disabled_hosts))}")
            if _budget_exhausted:
                reasons.append("time budget exhausted")
            containment = f" Skipped {_skipped_requests} requests ({'; '.join(reasons)})."
        not_found_note = (f" {_not_found_requests} requests returned 404 "
                          f"(the CDN has no such image).") if _not_found_requests else ""
        print(f"Set images collected: {stats['logoDownloaded']} logos + "
              f"{stats['symbolDownloaded']} symbols from TCGdex, {stats['bulbapediaSymbols']} "
              f"symbols from Bulbapedia, {stats['alreadyPresent']} reused from "
              f"{os.path.basename(output_dir)}/, {stats['externalReferences']} sets keep an "
              f"external reference; {stats['setsWithoutAnything']} sets remain "
              f"without any image, {remaining} sets without a symbol (badges cover them)."
              f"{not_found_note}{containment}")

        downloaded_total = (stats["logoDownloaded"] + stats["symbolDownloaded"] +
                            stats["bulbapediaSymbols"])
        referenced_total = stats["logoCandidates"] + stats["symbolCandidates"]
        covered_total = downloaded_total + stats["alreadyPresent"]
        if referenced_total > 0 and covered_total == 0:
            # Nothing could be fetched, but missing images must not block the catalog
            # release: the app renders text badges for sets without images. Emit a
            # loud, actionable warning instead of failing the pipeline.
            message = (f"collect_set_images: 0 of {referenced_total} referenced set images "
                       f"downloaded or reused; set-images/ stays as-is and the app keeps "
                       f"its badge fallback. First errors: {_first_errors_summary()}"
                       f"{not_found_note}{containment}")
            print(f"::warning title=collect_set_images found nothing::{message}")
        elif (downloaded_total > 0 or stats["alreadyPresent"] > 0) and (_disabled_hosts or _budget_exhausted):
            print(f"::warning title=collect_set_images incomplete::collected "
                  f"{downloaded_total} new + {stats['alreadyPresent']} reused images, but the "
                  f"run was cut short.{containment}")
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
    parser.add_argument("--max-seconds", type=float, default=DEFAULT_MAX_SECONDS,
                        help="Stop all downloads after this many seconds (default: %(default)s)")
    args = parser.parse_args()
    started = time.time()
    stats = collect(args.db, args.output_dir, skip_bulbapedia=args.skip_bulbapedia,
                    max_seconds=args.max_seconds)
    print(f"collect_set_images finished in {time.time() - started:.1f}s: {stats}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
