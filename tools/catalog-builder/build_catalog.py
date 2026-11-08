#!/usr/bin/env python3
"""Build a Room-compatible, language-aware TCG catalog from public APIs.

The builder intentionally stores no user collection tables. Network access is only
used while explicitly running this build tool; the Android app consumes the result.
"""
from __future__ import annotations

import argparse
import concurrent.futures
from contextlib import closing
import hashlib
import json
import os
import re
import shutil
import sqlite3
import tempfile
import time
from urllib.parse import quote
from collections import defaultdict
from typing import Any, Callable

import requests

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "../.."))
SCHEMA_JSON_PATH = os.path.join(ROOT, "app/schemas/com.pokemontcg.sammlung.data.local.AppDatabase/19.json")
OUTPUT_DIR = os.path.dirname(os.path.abspath(__file__))
OUTPUT_DB_PATH = os.path.join(OUTPUT_DIR, "catalog.sqlite")
OUTPUT_MANIFEST_PATH = os.path.join(OUTPUT_DIR, "manifest.json")
OUTPUT_REPORT_PATH = os.path.join(OUTPUT_DIR, "coverage_report.md")
PARTIAL_DB_NAME = "catalog-partial.sqlite"   # unfinished build kept for --resume when the publication gate fails
USER_AGENT = "TCGSammlungCatalogBuilder/2.0.0 (catalog maintenance; contact: repository maintainer)"
LANGUAGES = ["en", "fr", "es", "es-mx", "it", "pt", "pt-br", "pt-pt", "de", "nl", "pl", "ru", "ja", "ko", "zh-tw", "id", "th", "zh-cn"]
CATALOG_TABLES = ("sets", "cards", "pokemon", "card_pokemon", "pokemon_names")
MAX_WORKERS = 4
# PokéAPI language codes that differ from the catalog language codes. Everything else not in LANGUAGES is dropped.
POKEAPI_LANGUAGE_MAP = {"zh-hans": "zh-cn", "zh-hant": "zh-tw", "es-419": "es-mx", "ja-hrkt": "ja"}
RETRIES = 6
CARD_RETRY_ROUNDS = 2          # extra sequential passes over card requests that failed with server errors
CARD_RETRY_DELAY = 20          # seconds to wait before each extra pass
REQUIRED_LANGUAGES = ("en", "de")   # a catalog without these is never published
MAX_DEGRADED_RATIO = 0.02      # more than 2 % list-only cards in one language = suspected outage, do not publish


def load_schema(schema_path: str = SCHEMA_JSON_PATH) -> dict[str, Any]:
    with open(schema_path, "r", encoding="utf-8") as handle:
        schema = json.load(handle)
    if not isinstance(schema.get("database", {}).get("version"), int):
        raise ValueError("Room schema has no database version")
    return schema


def create_database(db_path: str, schema_data: dict[str, Any]) -> sqlite3.Connection:
    """Create exactly the catalog tables using Room's exported createSql/setupQueries."""
    if os.path.exists(db_path):
        os.remove(db_path)
    db = sqlite3.connect(db_path)
    database = schema_data["database"]
    entities = {item["tableName"]: item for item in database["entities"]}
    for query in database.get("setupQueries", []):
        db.execute(query)
    for table in CATALOG_TABLES:
        if table not in entities:
            raise ValueError(f"Required catalog table missing from Room schema: {table}")
        entity = entities[table]
        db.execute(entity["createSql"].replace("${TABLE_NAME}", table))
        for index in entity.get("indices", []):
            db.execute(index["createSql"].replace("${TABLE_NAME}", table))
    # Fail fast if a future schema accidentally adds user-owned tables to this builder.
    actual = {row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type='table'")}
    expected = set(CATALOG_TABLES) | {"room_master_table"}
    if actual != expected:
        db.close()
        raise ValueError(f"Catalog table set mismatch: expected {sorted(expected)}, got {sorted(actual)}")
    db.commit()
    return db


class NotFoundError(RuntimeError):
    """HTTP 404: the resource permanently does not exist, so retrying is pointless."""


def _retry_delay(attempt: int, exc: Exception) -> float:
    delay = min(2 ** attempt, 30)
    response = getattr(exc, "response", None)
    header = response.headers.get("Retry-After") if response is not None else None
    if header and str(header).isdigit():
        delay = max(delay, min(int(header), 60))
    return delay


def request_json(url: str, *, session: requests.Session | None = None,
                 params: dict[str, Any] | None = None, retries: int = RETRIES) -> Any:
    client = session or requests.Session()
    headers = {"User-Agent": USER_AGENT, "Accept": "application/json"}
    last_error: Exception | None = None
    for attempt in range(retries):
        try:
            response = client.get(url, params=params, headers=headers, timeout=(10, 35))
            if response.status_code == 429 or response.status_code >= 500:
                response.raise_for_status()
            response.raise_for_status()
            return response.json()
        except (requests.RequestException, ValueError) as exc:
            if (isinstance(exc, requests.HTTPError) and exc.response is not None
                    and exc.response.status_code == 404):
                raise NotFoundError(f"Not found (404): {url}") from exc
            last_error = exc
            if attempt + 1 < retries:
                time.sleep(_retry_delay(attempt, exc))
    raise RuntimeError(f"Request failed after {retries} attempts: {url}: {last_error}")


def _path_segment(value: str) -> str:
    """Percent-encode reserved characters (e.g. '+' or '?') in an ID, keeping existing %XX sequences."""
    return quote(value, safe="%")


def fetch_sets(language: str, *, fetcher: Callable[..., Any] = request_json) -> list[dict[str, Any]]:
    data = fetcher(f"https://api.tcgdex.net/v2/{language}/sets")
    if not isinstance(data, list):
        raise ValueError(f"TCGdex set listing for {language} is not a JSON array")
    return data


def fetch_set_details(language: str, set_id: str, *, fetcher: Callable[..., Any] = request_json) -> dict[str, Any]:
    data = fetcher(f"https://api.tcgdex.net/v2/{language}/sets/{_path_segment(set_id)}")
    return data if isinstance(data, dict) else {}


def fetch_card(language: str, card_id: str, *, fetcher: Callable[..., Any] = request_json) -> dict[str, Any]:
    data = fetcher(f"https://api.tcgdex.net/v2/{language}/cards/{_path_segment(card_id)}")
    return data if isinstance(data, dict) else {}


def _integer(value: Any, default: int = 0) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return default


def _sort_number(local_id: str) -> int:
    match = re.search(r"\d+", str(local_id))
    return int(match.group(0)) if match else 0


def _image_url(value: Any, size: str = "high") -> str | None:
    if not isinstance(value, str) or not value.strip():
        return None
    url = value.rstrip("/")
    if re.search(r"\.(png|jpe?g|webp)(\?.*)?$", url, re.IGNORECASE):
        return url
    return f"{url}/{size}.png"


def _language_code(language: str) -> str:
    return language.lower().replace("_", "-")


_SET_ABBREVIATIONS_CACHE: dict[str, dict[str, str]] | None = None


def set_abbreviations() -> dict[str, dict[str, str]]:
    """Official set abbreviations (e.g. "SVI") plus English names, from the MIT-licensed
    tcgdex/cards-database. Used for generated set badges when no symbol image exists and
    for the Bulbapedia symbol search."""
    global _SET_ABBREVIATIONS_CACHE
    if _SET_ABBREVIATIONS_CACHE is None:
        path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "set_abbreviations.json")
        try:
            with open(path, "r", encoding="utf-8") as handle:
                data = json.load(handle)
            _SET_ABBREVIATIONS_CACHE = {
                set_id: {"abbreviation": entry.get("abbreviation") or "",
                         "enName": entry.get("enName") or ""}
                for set_id, entry in (data.get("sets") or {}).items()
            }
        except Exception as exc:
            print(f"Warning: set_abbreviations.json unavailable ({exc}); badges stay empty")
            _SET_ABBREVIATIONS_CACHE = {}
    return _SET_ABBREVIATIONS_CACHE


def upsert_set(db: sqlite3.Connection, lang: str, item: dict[str, Any]) -> None:
    """Upserts one set. German keeps name authority; set images are aggregated across all
    languages: a NULL never overwrites an image another language delivered, and a later
    language still fills image gaps. Set images are language-neutral assets."""
    set_id = str(item.get("id") or "").strip()
    if not set_id:
        return
    card_count = item.get("cardCount") if isinstance(item.get("cardCount"), dict) else {}
    serie = item.get("serie") if isinstance(item.get("serie"), dict) else {}
    abbrev = set_abbreviations().get(set_id, {}).get("abbreviation") or None
    values = (set_id, str(item.get("name") or set_id), str(serie.get("name") or ""),
              _integer(card_count.get("official")), _integer(card_count.get("total")),
              str(item.get("releaseDate") or ""), _image_url(item.get("logo")),
              _image_url(item.get("symbol")), abbrev)
    columns = "(id,name,series,printedTotal,total,releaseDate,logoUrl,symbolUrl,setAbbreviation)"
    placeholders = "(?,?,?,?,?,?,?,?,?)"
    if lang == "de":
        db.execute(f"""INSERT INTO sets {columns} VALUES {placeholders}
            ON CONFLICT(id) DO UPDATE SET
              name=excluded.name, series=excluded.series, printedTotal=excluded.printedTotal,
              total=excluded.total, releaseDate=excluded.releaseDate,
              logoUrl=COALESCE(excluded.logoUrl, sets.logoUrl),
              symbolUrl=COALESCE(excluded.symbolUrl, sets.symbolUrl),
              setAbbreviation=COALESCE(excluded.setAbbreviation, sets.setAbbreviation)""", values)
    else:
        db.execute(f"""INSERT INTO sets {columns} VALUES {placeholders}
            ON CONFLICT(id) DO UPDATE SET
              logoUrl=COALESCE(sets.logoUrl, excluded.logoUrl),
              symbolUrl=COALESCE(sets.symbolUrl, excluded.symbolUrl),
              setAbbreviation=COALESCE(sets.setAbbreviation, excluded.setAbbreviation)""", values)


def upsert_card(db: sqlite3.Connection, lang: str, card: dict[str, Any], set_item: dict[str, Any]) -> None:
    raw_id = str(card.get("id") or "").strip()
    if not raw_id:
        return
    # TCGdex card IDs are not guaranteed to be globally unique across localized records.
    # Prefixing language preserves distinct prints and keeps language explicit.
    card_id = raw_id if _language_code(lang) == "de" else f"{_language_code(lang)}:{raw_id}"
    set_data = card.get("set") if isinstance(card.get("set"), dict) else {}
    set_id = str(set_data.get("id") or set_item.get("id") or "")
    if not set_id:
        return
    dex_ids = card.get("dexId") if isinstance(card.get("dexId"), list) else []
    dex_ids = sorted({_integer(value) for value in dex_ids if _integer(value) > 0})
    category = str(card.get("category") or "Pokemon")
    pricing = card.get("pricing") if isinstance(card.get("pricing"), dict) else {}
    cardmarket = pricing.get("cardmarket") if isinstance(pricing.get("cardmarket"), dict) else {}
    name = str(card.get("name") or raw_id)
    local_id = str(card.get("localId") or raw_id.rsplit("-", 1)[-1])
    image = _image_url(card.get("image"), "low")
    large_image = _image_url(card.get("image"), "high")
    set_name = str(set_data.get("name") or set_item.get("name") or set_id)
    set_serie = set_data.get("serie") if isinstance(set_data.get("serie"), dict) else {}
    series = str(set_serie.get("name") or "")
    set_count = set_data.get("cardCount") if isinstance(set_data.get("cardCount"), dict) else {}
    release_date = str(set_data.get("releaseDate") or set_item.get("releaseDate") or "")
    # Cardmarket values are EUR on TCGdex; missing prices remain NULL.
    set_abbrev = set_abbreviations().get(set_id, {}).get("abbreviation") or None
    values = (card_id, name, category, local_id, card.get("rarity"), set_id, set_name, series,
              image, large_image, cardmarket.get("avg"), cardmarket.get("low"), cardmarket.get("trend"),
              dex_ids[0] if dex_ids else None, None, _language_code(lang),
              ",".join(map(str, dex_ids)) if dex_ids else None,
              _image_url(set_data.get("logo") or set_item.get("logo")),
              _image_url(set_data.get("symbol") or set_item.get("symbol")),
              _integer(set_count.get("official"), _integer((set_item.get("cardCount") or {}).get("official"))),
              _integer(set_count.get("total"), _integer((set_item.get("cardCount") or {}).get("total"))),
              release_date, _sort_number(local_id), 0 if card.get("_briefOnly") else 1, set_abbrev)
    db.execute("""INSERT OR REPLACE INTO cards
      (id,name,supertype,number,rarity,setId,setName,series,imageUrl,largeImageUrl,marketPrice,lowPrice,trendPrice,
       nationalPokedexNumber,subtypes,language,nationalPokedexNumbers,setLogoUrl,setSymbolUrl,setCardCountOfficial,
       setCardCountTotal,setReleaseDate,sortNumber,detailLoaded,setAbbreviation)
       VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""", values)
    for dex_id in dex_ids:
        db.execute("INSERT OR REPLACE INTO card_pokemon(cardId,dexId) VALUES (?,?)", (card_id, dex_id))


def apply_image_fallbacks(db: sqlite3.Connection) -> dict[str, int]:
    """Fills image gaps after all languages are imported. Idempotent, safe to re-run on --resume.

    1. Card images: TCGdex has prints whose localized record carries no image although
       another language print of the same card does. Both show the identical artwork, so
       the missing URL is copied from a sibling print (German preferred, then English).
    2. Card set columns (setLogoUrl/setSymbolUrl/setAbbreviation) mirror the fully
       aggregated sets table instead of the per-language snapshot of the import.
    """
    rows = db.execute("SELECT id, language, imageUrl, largeImageUrl FROM cards").fetchall()
    by_raw_id: dict[str, list[tuple]] = {}
    for row in rows:
        raw_id = str(row[0]).split(":", 1)[-1]
        by_raw_id.setdefault(raw_id, []).append(row)

    def best_print(prints: list[tuple], index: int) -> str | None:
        with_image = [p for p in prints if p[index]]
        if not with_image:
            return None
        with_image.sort(key=lambda p: ({"de": 0, "en": 1}.get(p[1], 2), p[0]))
        return str(with_image[0][index])

    card_image_updates = 0
    for raw_id, prints in by_raw_id.items():
        missing = [p for p in prints if not p[2] or not p[3]]
        if not missing:
            continue
        image = best_print(prints, 2)
        large_image = best_print(prints, 3)
        for card_id, language, card_image, card_large in missing:
            new_image = card_image or image
            new_large = card_large or large_image
            if new_image == card_image and new_large == card_large:
                continue
            db.execute("UPDATE cards SET imageUrl=?, largeImageUrl=? WHERE id=?",
                       (new_image, new_large, card_id))
            card_image_updates += 1

    set_column_updates = db.execute("""UPDATE cards SET
        setLogoUrl = COALESCE(setLogoUrl, (SELECT logoUrl FROM sets WHERE sets.id = cards.setId)),
        setSymbolUrl = COALESCE(setSymbolUrl, (SELECT symbolUrl FROM sets WHERE sets.id = cards.setId)),
        setAbbreviation = COALESCE(setAbbreviation, (SELECT setAbbreviation FROM sets WHERE sets.id = cards.setId))
        WHERE setLogoUrl IS NULL OR setSymbolUrl IS NULL OR setAbbreviation IS NULL""").rowcount
    db.commit()
    stats = {"cardImagesFilled": card_image_updates, "cardSetColumnsFilled": max(set_column_updates, 0)}
    print(f"Image fallbacks: {stats['cardImagesFilled']} card image(s) copied across languages, "
          f"{stats['cardSetColumnsFilled']} card row(s) got set images/abbreviation from the sets table")
    return stats


def fetch_pokemon_names(*, fetcher: Callable[..., Any] = request_json) -> tuple[list[tuple], list[tuple]]:
    """Return Pokemon and localized species-name rows from a PokéAPI species snapshot."""
    listing = fetcher("https://pokeapi.co/api/v2/pokemon-species/", params={"limit": 1025, "offset": 0})
    results = listing.get("results", []) if isinstance(listing, dict) else []
    species_rows: list[tuple] = []
    name_rows: list[tuple] = []

    def load_one(item: dict[str, Any]) -> tuple[int, str, dict[str, Any]] | None:
        match = re.search(r"/pokemon-species/(\d+)/?$", str(item.get("url", "")))
        if not match:
            return None
        dex_id = int(match.group(1))
        try:
            species = fetcher(f"https://pokeapi.co/api/v2/pokemon-species/{dex_id}/")
            return dex_id, str(item.get("name") or "pokemon-{dex_id}"), species
        except Exception as exc:
            print(f"Warning: PokéAPI species {dex_id} unavailable: {exc}")
            return None

    with concurrent.futures.ThreadPoolExecutor(max_workers=MAX_WORKERS) as pool:
        futures = [pool.submit(load_one, item) for item in results]
        for future in concurrent.futures.as_completed(futures):
            result = future.result()
            if not result:
                continue
            dex_id, slug, species = result
            names = species.get("names", []) if isinstance(species.get("names"), list) else []
            localized = {}
            priority: dict[str, int] = {}
            for name_item in names:
                language = name_item.get("language") or {}
                raw_code = _language_code(str(language.get("name") or ""))
                code = POKEAPI_LANGUAGE_MAP.get(raw_code, raw_code)
                name = str(name_item.get("name") or "").strip()
                if not code or not name or code not in LANGUAGES:
                    continue
                rank = 0 if code == raw_code else 1     # a directly named language beats a mapped variant
                if code in priority and priority[code] <= rank:
                    continue
                priority[code] = rank
                localized[code] = name
            for code, name in localized.items():
                name_rows.append((code, name, dex_id))
            german = localized.get("de") or slug.replace("-", " ").title()
            species_rows.append((str(dex_id), dex_id, f"{dex_id:03d}", german,
                                 " ".join([slug] + list(localized.values())).lower(),
                                 # High-resolution official artwork via the jsDelivr CDN:
                                 # faster and rate-limit-free compared to raw.githubusercontent.
                                 f"https://cdn.jsdelivr.net/gh/PokeAPI/sprites@master/sprites/pokemon/other/official-artwork/{dex_id}.png", None, "NONE", None))
            # English slug is searchable as an alias even if the API omits an English localized name.
            name_rows.append(("en", slug, dex_id))
    return species_rows, name_rows


def fetch_bulbapedia_set_titles(*, fetcher: Callable[..., Any] = request_json) -> set[str]:
    """Serial, optional set-list cross-check via MediaWiki API; never supplies card records."""
    titles: set[str] = set()
    params: dict[str, Any] = {"action": "query", "list": "categorymembers", "cmtitle": "Category:TCG sets",
                              "cmtype": "page", "cmlimit": "max", "format": "json"}
    try:
        while True:
            data = fetcher("https://bulbapedia.bulbagarden.net/w/api.php", params=params)
            query = data.get("query", {}) if isinstance(data, dict) else {}
            for member in query.get("categorymembers", []):
                title = str(member.get("title") or "").strip()
                if title:
                    titles.add(title)
            continuation = data.get("continue") if isinstance(data, dict) else None
            if not continuation:
                break
            params.update(continuation)
    except Exception as exc:
        print(f"Warning: Bulbapedia set-list cross-check unavailable: {exc}")
    return titles


def _load_checkpoint(path: str | None, schema_version: int) -> dict[str, Any]:
    if not path or not os.path.exists(path):
        return {"schemaVersion": schema_version, "completedSets": {}, "pokemonComplete": False, "gapSets": {}}
    with open(path, "r", encoding="utf-8") as handle:
        data = json.load(handle)
    if data.get("schemaVersion") != schema_version:
        raise ValueError("Resume checkpoint schema version does not match the Room schema")
    completed = data.get("completedSets")
    if not isinstance(completed, dict):
        raise ValueError("Resume checkpoint is malformed: completedSets must be an object")
    return {"schemaVersion": schema_version,
            "completedSets": {str(lang): sorted(set(map(str, ids))) for lang, ids in completed.items() if isinstance(ids, list)},
            "pokemonComplete": bool(data.get("pokemonComplete", False)),
            "gapSets": data.get("gapSets") if isinstance(data.get("gapSets"), dict) else {}}


def _save_checkpoint(path: str | None, checkpoint: dict[str, Any]) -> None:
    if not path:
        return
    temp_path = path + ".tmp"
    with open(temp_path, "w", encoding="utf-8") as handle:
        json.dump(checkpoint, handle, indent=2, ensure_ascii=False)
        handle.flush()
        os.fsync(handle.fileno())
    os.replace(temp_path, path)


def _validate_existing_catalog(db: sqlite3.Connection, schema_data: dict[str, Any]) -> None:
    tables = {row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type='table'")}
    expected = set(CATALOG_TABLES) | {"room_master_table"}
    if tables != expected:
        raise ValueError(f"Existing catalog table set mismatch: expected {sorted(expected)}, got {sorted(tables)}")
    identity = db.execute("SELECT identity_hash FROM room_master_table WHERE id=42").fetchone()
    expected_identity = schema_data["database"].get("identityHash")
    if not identity or identity[0] != expected_identity:
        raise ValueError("Existing catalog Room identity hash does not match the current schema")


def _brief_card(card_ref: dict[str, Any]) -> dict[str, Any]:
    """Minimal card record built from the set listing (name, number, image) when details are unavailable."""
    brief_card = dict(card_ref)
    brief_card["_briefOnly"] = True
    return brief_card


def _remove_orphan_card_links(db: sqlite3.Connection) -> int:
    """Delete card->Pokémon links whose dexId is not in the PokéAPI snapshot (e.g. species PokéAPI lacks yet).

    One such link would fail the relationship check of the verification step and block the whole catalog.
    """
    cursor = db.execute("DELETE FROM card_pokemon WHERE dexId NOT IN (SELECT dexId FROM pokemon)")
    db.commit()
    return cursor.rowcount


def _forget_completed_sets_without_cards(db: sqlite3.Connection, checkpoint: dict[str, Any]) -> int:
    """Self-heal a resume: a set marked complete must have cards in the database (or be a recorded API gap)."""
    removed = 0
    gaps = checkpoint.get("gapSets", {}) if isinstance(checkpoint.get("gapSets"), dict) else {}
    for language, ids in list(checkpoint.get("completedSets", {}).items()):
        language_gaps = gaps.get(language, {}) if isinstance(gaps.get(language), dict) else {}
        kept = []
        for set_id in ids:
            has_cards = db.execute("SELECT 1 FROM cards WHERE language=? AND setId=? LIMIT 1",
                                   (_language_code(language), set_id)).fetchone()
            if has_cards or set_id in language_gaps:
                kept.append(set_id)
            else:
                removed += 1
        checkpoint["completedSets"][language] = kept
    return removed


def populate_database(db: sqlite3.Connection, *, fetcher: Callable[..., Any] = request_json,
                      include_bulbapedia: bool = True, checkpoint_path: str | None = None,
                      schema_version: int = 18) -> dict[str, Any]:
    language_stats: dict[str, dict[str, int]] = {}
    all_set_ids: set[str] = set()
    all_set_names: dict[str, str] = {}
    failed_sets_by_language: dict[str, int] = {}
    checkpoint = _load_checkpoint(checkpoint_path, schema_version)
    forgotten = _forget_completed_sets_without_cards(db, checkpoint)
    if forgotten:
        print(f"Note: {forgotten} set(s) were marked complete in the checkpoint but have no cards; fetching them again")
    completed_sets: dict[str, set[str]] = {
        language: set(checkpoint["completedSets"].get(language, [])) for language in LANGUAGES
    }
    gap_sets: dict[str, dict[str, dict[str, int]]] = checkpoint.setdefault("gapSets", {})
    for language in LANGUAGES:
        try:
            sets = fetch_sets(language, fetcher=fetcher)
            set_count = 0
            card_count = 0
            expected_cards = 0
            failed_sets = 0
            missing_cards = 0     # cards the API itself does not provide (permanent data gap)
            brief_cards = 0       # cards stored with list data only because the detail URL returns 404
            degraded_cards = 0    # cards stored with list data only because of persistent server errors
            language_gaps = gap_sets.setdefault(language, {})
            for brief in sets:
                set_id = str(brief.get("id") or "")
                if not set_id:
                    continue
                upsert_set(db, language, brief)
                brief_counts = brief.get("cardCount") if isinstance(brief.get("cardCount"), dict) else {}
                set_expected = _integer(brief_counts.get("total"), _integer(brief_counts.get("official")))
                expected_cards += set_expected
                all_set_ids.add(set_id)
                all_set_names.setdefault(set_id, str(brief.get("name") or set_id))
                if set_id in completed_sets[language]:
                    card_count += db.execute("SELECT COUNT(*) FROM cards WHERE language=? AND setId=?",
                                             (_language_code(language), set_id)).fetchone()[0]
                    set_count += 1
                    known_gap = language_gaps.get(set_id, {})
                    missing_cards += _integer(known_gap.get("missing"), 0)
                    brief_cards += _integer(known_gap.get("brief"), 0)
                    continue
                set_failed = False   # transient problem: retried on --resume and blocks publication
                try:
                    detail = fetch_set_details(language, set_id, fetcher=fetcher)
                except NotFoundError:
                    print(f"Warning: set details {language}/{set_id}: 404, no card list available")
                    detail = brief
                except Exception as exc:
                    print(f"Warning: set details {language}/{set_id}: {exc}")
                    detail = brief
                    set_failed = True
                cards = detail.get("cards", []) if isinstance(detail.get("cards"), list) else []
                set_missing = 0
                if not set_failed and set_expected > len(cards):
                    # The API declares more cards than it lists. That is a permanent data gap on the API side,
                    # not a transient failure: record it, but do not block the whole catalog on it.
                    set_missing = set_expected - len(cards)

                def get_card(card_ref: dict[str, Any]) -> tuple[dict[str, Any] | None, str]:
                    card_id = str(card_ref.get("id") or "")
                    if not card_id:
                        return None, "skip"
                    try:
                        card = fetch_card(language, card_id, fetcher=fetcher)
                        if not isinstance(card, dict) or not card.get("id"):
                            raise ValueError("Card detail response is empty or has no id")
                        return card, "ok"
                    except NotFoundError:
                        # TCGdex cannot serve some card IDs by their detail URL (e.g. "exu-?" = Unown ?).
                        if card_ref.get("name"):
                            print(f"Warning: card details {language}/{card_id}: 404, using set-listing data only")
                            return _brief_card(card_ref), "brief"
                        print(f"Warning: card details {language}/{card_id}: 404 and no listing data")
                        return None, "missing"
                    except Exception as exc:
                        print(f"Warning: card details {language}/{card_id}: {exc}")
                        return None, "failed"

                results: list[tuple[dict[str, Any] | None, str]] = []
                pending: list[dict[str, Any]] = []
                # Strict maximum of four concurrent requests.
                with concurrent.futures.ThreadPoolExecutor(max_workers=MAX_WORKERS) as pool:
                    futures = {pool.submit(get_card, item): item for item in cards}
                    for future in concurrent.futures.as_completed(futures):
                        card, status = future.result()
                        if status == "failed":
                            pending.append(futures[future])
                        else:
                            results.append((card, status))
                # Server errors (e.g. 503) are often temporary: retry them one by one after a pause.
                for _ in range(CARD_RETRY_ROUNDS):
                    if not pending:
                        break
                    time.sleep(CARD_RETRY_DELAY)
                    still_pending = []
                    for ref in pending:
                        card, status = get_card(ref)
                        if status == "failed":
                            still_pending.append(ref)
                        else:
                            results.append((card, status))
                    pending = still_pending
                for ref in pending:
                    if ref.get("name"):
                        results.append((_brief_card(ref), "degraded"))
                    else:
                        results.append((None, "failed"))

                set_brief = 0
                set_degraded = 0
                for card, status in results:
                    if status == "skip":
                        continue
                    if status == "missing":
                        set_missing += 1
                        continue
                    if status == "failed" or not card:
                        set_failed = True
                        continue
                    card.setdefault("set", {"id": set_id, "name": brief.get("name", set_id)})
                    upsert_card(db, language, card, detail or brief)
                    card_count += 1
                    if status == "brief":
                        set_brief += 1
                    elif status == "degraded":
                        set_degraded += 1
                set_count += 1
                db.commit()
                missing_cards += set_missing
                brief_cards += set_brief
                degraded_cards += set_degraded
                if set_failed:
                    failed_sets += 1
                elif set_degraded:
                    # Stored with list data only; leave unchecked so the next --resume tries this set again.
                    pass
                else:
                    completed_sets[language].add(set_id)
                    checkpoint["completedSets"][language] = sorted(completed_sets[language])
                    if set_missing or set_brief:
                        language_gaps[set_id] = {"missing": set_missing, "brief": set_brief}
                    _save_checkpoint(checkpoint_path, checkpoint)
            db.commit()
            failed_sets_by_language[language] = failed_sets
            language_stats[language] = {"sets": set_count, "cards": card_count, "expectedCards": expected_cards,
                                        "failedSets": failed_sets, "missingCards": missing_cards,
                                        "briefCards": brief_cards, "degradedCards": degraded_cards}
            print(f"[{language}] sets={set_count} cards={card_count}/{expected_cards} "
                  f"missing_at_api={missing_cards} list_only={brief_cards + degraded_cards} failed_sets={failed_sets}")
        except Exception as exc:
            language_stats[language] = {"sets": 0, "cards": 0, "expectedCards": 0, "failedSets": 0, "error": str(exc)}
            print(f"Warning: language {language} incomplete: {exc}")
    try:
        if checkpoint.get("pokemonComplete"):
            pokemon_rows = db.execute("SELECT * FROM pokemon").fetchall()
            name_rows = db.execute("SELECT * FROM pokemon_names").fetchall()
        else:
            pokemon_rows, name_rows = fetch_pokemon_names(fetcher=fetcher)
            db.executemany("INSERT OR REPLACE INTO pokemon VALUES (?,?,?,?,?,?,?,?,?)", pokemon_rows)
            db.executemany("INSERT OR REPLACE INTO pokemon_names(language,name,dexId) VALUES (?,?,?)", name_rows)
            db.commit()
            checkpoint["pokemonComplete"] = True
            _save_checkpoint(checkpoint_path, checkpoint)
    except Exception as exc:
        pokemon_rows, name_rows = [], []
        checkpoint["pokemonComplete"] = False
        _save_checkpoint(checkpoint_path, checkpoint)
        print(f"Warning: PokéAPI snapshot incomplete: {exc}")
        pokemon_error = str(exc)
    else:
        pokemon_error = None
    orphan_links = 0
    if pokemon_error is None and pokemon_rows:
        orphan_links = _remove_orphan_card_links(db)
        if orphan_links:
            print(f"Note: removed {orphan_links} card->Pokémon link(s) whose dexId is missing in the PokéAPI snapshot")
    image_fallbacks = apply_image_fallbacks(db)
    bulbapedia_titles = fetch_bulbapedia_set_titles(fetcher=fetcher) if include_bulbapedia else set()
    api_names = {name.lower() for name in all_set_names.values()}
    missing_bulba = sorted(title for title in bulbapedia_titles if title.lower() not in api_names)
    return {"languages": language_stats, "setIds": all_set_ids, "setNames": all_set_names,
            "pokemonCount": len(pokemon_rows), "pokemonNameCount": len(name_rows),
            "pokemonError": pokemon_error, "failedSetsByLanguage": failed_sets_by_language,
            "orphanLinksRemoved": orphan_links,
            "imageFallbacks": image_fallbacks,
            "bulbapediaSetTitles": len(bulbapedia_titles), "bulbapediaUnmatched": missing_bulba}


def find_incomplete_catalog(summary: dict[str, Any]) -> dict[str, str]:
    """Return language/API failures that must block publication of a catalog snapshot.

    Permanent data gaps on the API side (a language without any sets, sets that list fewer cards than they
    declare, cards without detail pages) are reported but do not block. Transient failures and missing
    required languages do.
    """
    failures: dict[str, str] = {}
    languages = summary.get("languages", {})
    for language in LANGUAGES:
        stats = languages.get(language)
        if not isinstance(stats, dict):
            failures[language] = "missing language result"
        elif stats.get("error"):
            failures[language] = str(stats["error"])
        elif _integer(stats.get("sets"), 0) <= 0:
            if language in REQUIRED_LANGUAGES:
                failures[language] = "zero sets returned; refusing to publish potentially incomplete language data"
        else:
            cards = _integer(stats.get("cards"), 0)
            degraded = _integer(stats.get("degradedCards"), 0)
            if cards > 0 and degraded / cards > MAX_DEGRADED_RATIO:
                failures[language] = (f"{degraded} of {cards} cards only have list data after repeated server errors; "
                                      "run again with --resume")
    for language, count in summary.get("failedSetsByLanguage", {}).items():
        if count:
            failures[language] = f"{count} set(s) have incomplete card details"
    if summary.get("pokemonError"):
        failures["pokeapi"] = str(summary["pokemonError"])
    if not summary.get("pokemonCount"):
        failures["pokeapi"] = failures.get("pokeapi", "zero Pokémon species returned")
    return failures


def build_coverage(db_path: str, summary: dict[str, Any]) -> dict[str, dict[str, Any]]:
    """Build published coverage from the finished database, not request counters."""
    with closing(sqlite3.connect(db_path)) as db:
        cards_by_language = {
            row[0]: row[1]
            for row in db.execute("SELECT language, COUNT(*) FROM cards GROUP BY language")
        }
        sets_by_language = {
            row[0]: row[1]
            for row in db.execute("SELECT language, COUNT(DISTINCT setId) FROM cards GROUP BY language")
        }

    coverage: dict[str, dict[str, Any]] = {}
    language_summary = summary.get("languages", {})
    for language in LANGUAGES:
        stats = language_summary.get(language, {}) if isinstance(language_summary, dict) else {}
        expected = _integer(stats.get("expectedCards"), 0) if isinstance(stats, dict) else 0
        actual_cards = cards_by_language.get(language, 0)
        coverage[language] = {
            "sets": sets_by_language.get(language, 0),
            "cards": actual_cards,
            "expectedCards": expected,
            "coveragePercent": round(min(100.0, actual_cards * 100.0 / expected), 2) if expected > 0 else None,
        }
    return coverage


def generate_manifest(db_path: str, manifest_path: str, report_path: str, schema_version: int,
                      coverage: dict[str, dict[str, int]] | None = None,
                      extra_report: dict[str, Any] | None = None) -> dict[str, Any]:
    digest = hashlib.sha256()
    with open(db_path, "rb") as handle:
        for block in iter(lambda: handle.read(65536), b""):
            digest.update(block)
    size = os.path.getsize(db_path)
    coverage = coverage or {language: {"sets": 0, "cards": 0, "coveragePercent": 0} for language in LANGUAGES}
    built_at_ms = int(time.time() * 1000)
    version = f"{time.strftime('%Y.%m.%d.%H%M%S', time.gmtime(built_at_ms / 1000))}.{built_at_ms % 1000:03d}"
    manifest = {"version": version, "schemaVersion": schema_version, "sha256": digest.hexdigest(),
                "size": size, "builtAt": built_at_ms,
                "url": "https://github.com/badnudin-star/TCGDEX/releases/download/catalog-latest/catalog.sqlite",
                "coverage": coverage}
    with open(manifest_path, "w", encoding="utf-8") as handle:
        json.dump(manifest, handle, indent=2, ensure_ascii=False)
    with open(report_path, "w", encoding="utf-8") as handle:
        handle.write("# TCG Sammlung – Katalog-Abdeckungsbericht\n\n")
        handle.write(f"- Schema-Version: {schema_version}\n- Datenbankgröße: {size} Bytes\n- SHA-256: `{manifest['sha256']}`\n")
        handle.write("\n## Abdeckung je Sprache\n\n| Sprache | Sets mit Karten | Karten geladen | Erwartete Karten | Abdeckung |\n|---|---:|---:|---:|---:|\n")
        for language in LANGUAGES:
            stats = coverage.get(language, {})
            percent = stats.get("coveragePercent")
            percent_text = f"{percent} %" if percent is not None else "unbekannt"
            expected = stats.get("expectedCards", "unbekannt")
            handle.write(f"| {language} | {stats.get('sets', 0)} | {stats.get('cards', 0)} | {expected} | {percent_text} |\n")
        language_details = extra_report.get("languages") if isinstance(extra_report, dict) else None
        if isinstance(language_details, dict):
            handle.write("\n## Bekannte Lücken je Sprache\n\n")
            handle.write("| Sprache | Karten fehlen bei der API | Per Englisch ergänzt | Nur Kurzdaten (ohne Details) | Unvollständige Sets |\n|---|---:|---:|---:|---:|\n")
            for language in LANGUAGES:
                detail_stats = language_details.get(language, {})
                if not isinstance(detail_stats, dict):
                    continue
                handle.write(f"| {language} | {detail_stats.get('missingCards', 0)} | "
                             f"{detail_stats.get('filledFromEnglish', 0)} | "
                             f"{detail_stats.get('briefCards', 0) + detail_stats.get('degradedCards', 0)} | "
                             f"{detail_stats.get('failedSets', 0)} |\n")
        if extra_report:
            handle.write(f"\n## PokéAPI\n\n- Pokémon: {extra_report.get('pokemonCount', 0)}\n- Pokémon-Namen: {extra_report.get('pokemonNameCount', 0)}\n")
            handle.write(f"\n## Bulbapedia-Abgleich (MediaWiki API)\n\n- Set-Titel abgerufen: {extra_report.get('bulbapediaSetTitles', 0)}\n")
            unmatched = extra_report.get("bulbapediaUnmatched", [])
            handle.write(f"- Nicht automatisch zugeordnete Titel: {len(unmatched)}\n")
            for title in unmatched[:300]:
                handle.write(f"  - {title}\n")
    return manifest



def _write_publish_journal(output_dir: str, journal: dict[str, Any]) -> str:
    """Persist the recovery journal atomically before changing published files."""
    journal_path = os.path.join(output_dir, ".catalog-publish-transaction.json")
    fd, temp_path = tempfile.mkstemp(prefix=".catalog-publish-journal-", dir=output_dir)
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as handle:
            json.dump(journal, handle, indent=2)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temp_path, journal_path)
    finally:
        if os.path.exists(temp_path):
            os.remove(temp_path)
    return journal_path


def _recover_interrupted_publish(output_dir: str) -> bool:
    """Restore the previous snapshot if a prior process died mid-publication."""
    journal_path = os.path.join(output_dir, ".catalog-publish-transaction.json")
    if not os.path.isfile(journal_path):
        return False
    with open(journal_path, "r", encoding="utf-8") as handle:
        journal = json.load(handle)
    backup_name = journal.get("backup_dir")
    if (not isinstance(backup_name, str)
            or os.path.basename(backup_name) != backup_name
            or not backup_name.startswith(".catalog-publish-backup-")
            or backup_name in (".catalog-publish-backup-", ".", "..")):
        raise ValueError("Invalid catalog publication recovery journal")
    backup_dir = os.path.join(output_dir, backup_name)
    if os.path.islink(backup_dir) or not os.path.isdir(backup_dir):
        raise FileNotFoundError(f"Catalog publication backup is missing or unsafe: {backup_name}")
    existed = journal.get("existed")
    names = ("catalog.sqlite", "coverage_report.md", "manifest.json")
    if (not isinstance(existed, dict)
            or set(existed) != set(names)
            or any(type(existed[name]) is not bool for name in names)):
        raise ValueError("Incomplete catalog publication recovery journal")
    artifacts = journal.get("artifacts")
    if artifacts is not None and artifacts != list(names):
        raise ValueError("Unexpected artifacts in catalog publication recovery journal")
    manifest_last = journal.get("manifest_last")
    if manifest_last is not None and manifest_last is not True:
        raise ValueError("Invalid manifest ordering in catalog publication recovery journal")
    # The backup is complete before the journal is written, so it is safe to restore.
    for name in names:
        target = os.path.join(output_dir, name)
        backup = os.path.join(backup_dir, name)
        if existed[name]:
            if not os.path.isfile(backup):
                raise FileNotFoundError(f"Required publication backup is missing: {name}")
            shutil.copy2(backup, target)
        elif os.path.exists(target):
            os.remove(target)
    os.remove(journal_path)
    shutil.rmtree(backup_dir, ignore_errors=True)
    return True


def _publish_staged_artifacts(staging_dir: str, output_dir: str) -> None:
    """Publish verified artifacts with rollback and recovery after process interruption."""
    required = ("catalog.sqlite", "coverage_report.md", "manifest.json")
    missing = [name for name in required if not os.path.isfile(os.path.join(staging_dir, name))]
    if missing:
        raise FileNotFoundError(f"Staged catalog artifacts are missing: {', '.join(missing)}")
    os.makedirs(output_dir, exist_ok=True)
    _recover_interrupted_publish(output_dir)
    ordered = ("catalog.sqlite", "coverage_report.md", "manifest.json")
    backup_dir = tempfile.mkdtemp(prefix=".catalog-publish-backup-", dir=output_dir)
    existed = {name: os.path.exists(os.path.join(output_dir, name)) for name in ordered}
    journal_path = None
    try:
        for name in ordered:
            target = os.path.join(output_dir, name)
            if existed[name]:
                shutil.copy2(target, os.path.join(backup_dir, name))
        journal = {"backup_dir": os.path.basename(backup_dir), "existed": existed,
                   "artifacts": list(ordered), "manifest_last": True}
        journal_path = _write_publish_journal(output_dir, journal)
        # Manifest remains the commit marker and is replaced last.
        try:
            for name in ordered:
                os.replace(os.path.join(staging_dir, name), os.path.join(output_dir, name))
        except Exception:
            _recover_interrupted_publish(output_dir)
            raise
        os.remove(journal_path)
        shutil.rmtree(backup_dir, ignore_errors=True)
    finally:
        # If a journal remains, preserve its backup for next-run recovery.
        if journal_path is None or not os.path.exists(journal_path):
            shutil.rmtree(backup_dir, ignore_errors=True)


FALLBACK_LANGUAGE = "en"   # source language used to fill cards that TCGdex lacks in another language


def _raw_card_id(row_id: str, language: str) -> str:
    prefix = f"{_language_code(language)}:"
    return row_id[len(prefix):] if row_id.startswith(prefix) else row_id


def _card_row_id(language: str, raw_id: str) -> str:
    code = _language_code(language)
    return raw_id if code == "de" else f"{code}:{raw_id}"


def plan_gap_fill(db: sqlite3.Connection, checkpoint: dict[str, Any]) -> list[dict[str, Any]]:
    """Work out, per language and set with a recorded API gap, which English cards could fill it.

    A set is only filled when the English card list has no more candidate cards than the number of cards
    missing in the target language; otherwise the match is ambiguous and the set is left alone.
    """
    plan: list[dict[str, Any]] = []
    gaps = checkpoint.get("gapSets", {}) if isinstance(checkpoint.get("gapSets"), dict) else {}
    source_code = _language_code(FALLBACK_LANGUAGE)
    for language in LANGUAGES:
        if language == FALLBACK_LANGUAGE:
            continue
        code = _language_code(language)
        language_gaps = gaps.get(language, {}) if isinstance(gaps.get(language), dict) else {}
        for set_id, info in sorted(language_gaps.items()):
            missing = _integer(info.get("missing"), 0) if isinstance(info, dict) else 0
            if missing <= 0:
                continue
            source_rows = db.execute("SELECT id, number, name FROM cards WHERE language=? AND setId=? "
                                     "ORDER BY sortNumber, number", (source_code, set_id)).fetchall()
            have_rows = db.execute("SELECT id, number FROM cards WHERE language=? AND setId=?",
                                   (code, set_id)).fetchall()
            have_numbers = {number for _, number in have_rows}
            have_raw = {_raw_card_id(row_id, language) for row_id, _ in have_rows}
            candidates = [(row_id, number, name) for row_id, number, name in source_rows
                          if number not in have_numbers and _raw_card_id(row_id, FALLBACK_LANGUAGE) not in have_raw]
            if not source_rows:
                status = "no_english_equivalent"
            elif not candidates:
                status = "english_has_no_extra_cards"
            elif len(candidates) > missing:
                status = "ambiguous"
            else:
                status = "fillable"
            set_row = db.execute("SELECT name FROM sets WHERE id=?", (set_id,)).fetchone()
            plan.append({"language": language, "setId": set_id, "setName": set_row[0] if set_row else set_id,
                         "missing": missing, "have": len(have_rows), "english": len(source_rows),
                         "candidateCount": len(candidates), "status": status,
                         "fillable": candidates if status == "fillable" else []})
    return plan


def apply_gap_fill(db: sqlite3.Connection, plan: list[dict[str, Any]]) -> dict[str, int]:
    """Copy the planned English cards into the target language as list-only rows (detailLoaded = 0).

    Prices are left empty on purpose (an English price is not the price of the local print). The card IDs follow
    the normal scheme, so a real card that appears in a later snapshot replaces the substitute and keeps the
    link to the user's collection.
    """
    columns = [row[1] for row in db.execute("PRAGMA table_info(cards)")]
    filled: dict[str, int] = {}
    for entry in plan:
        if entry["status"] != "fillable":
            continue
        language = entry["language"]
        code = _language_code(language)
        set_name_row = db.execute("SELECT setName FROM cards WHERE language=? AND setId=? LIMIT 1",
                                  (code, entry["setId"])).fetchone()
        for source_id, _number, _name in entry["fillable"]:
            new_id = _card_row_id(language, _raw_card_id(source_id, FALLBACK_LANGUAGE))
            expressions: list[str] = []
            params: list[Any] = []
            for column in columns:
                if column == "id":
                    expressions.append("?")
                    params.append(new_id)
                elif column == "language":
                    expressions.append("?")
                    params.append(code)
                elif column in ("marketPrice", "lowPrice", "trendPrice"):
                    expressions.append("NULL")
                elif column == "detailLoaded":
                    expressions.append("0")
                elif column == "setName" and set_name_row and set_name_row[0]:
                    expressions.append("?")
                    params.append(set_name_row[0])
                else:
                    expressions.append(f'"{column}"')
            cursor = db.execute(
                f"INSERT OR IGNORE INTO cards ({','.join(columns)}) SELECT {','.join(expressions)} FROM cards WHERE id=?",
                params + [source_id])
            if cursor.rowcount:
                db.execute("INSERT OR IGNORE INTO card_pokemon(cardId,dexId) SELECT ?, dexId FROM card_pokemon "
                           "WHERE cardId=?", (new_id, source_id))
                filled[language] = filled.get(language, 0) + 1
    db.commit()
    return filled


def apply_language_fallback(db_path: str, checkpoint_path: str, schema_version: int,
                            summary: dict[str, Any]) -> list[dict[str, Any]]:
    """Fill API gaps from the English catalog, record the counts in the summary, return the plan for the report."""
    checkpoint = _load_checkpoint(checkpoint_path, schema_version)
    with closing(sqlite3.connect(db_path)) as db:
        plan = plan_gap_fill(db, checkpoint)
        filled = apply_gap_fill(db, plan)
    languages = summary.get("languages", {})
    for language, count in filled.items():
        if isinstance(languages.get(language), dict):
            languages[language]["filledFromEnglish"] = count
    return plan


def write_gap_report(db_path: str, output_dir: str, plan: list[dict[str, Any]], summary: dict[str, Any]) -> None:
    """Write gap_report.md (overview) and gaps.csv (every filled card and every open set)."""
    languages = summary.get("languages", {}) if isinstance(summary.get("languages"), dict) else {}
    status_text = {
        "fillable": "mit englischen Karten ergänzt",
        "no_english_equivalent": "offen: kein englisches Gegenstück (nur in dieser Sprache erschienen)",
        "english_has_no_extra_cards": "offen: Englisch hat keine zusätzlichen Karten",
        "ambiguous": "offen: Zuordnung nicht eindeutig (Englisch hat mehr Karten als fehlen)",
    }
    lines = ["# Lücken-Bericht", "",
             "Karten, die TCGdex in einer Sprache nicht liefert, und was die Pipeline dagegen getan hat.",
             "Ersatzkarten sind englische Karten (Name und Bild englisch, ohne Preis).", "",
             "## Übersicht je Sprache", "",
             "| Sprache | Karten fehlen bei der API | Per Englisch ergänzt | Noch offen |", "|---|---:|---:|---:|"]
    for language in LANGUAGES:
        stats = languages.get(language, {})
        if not isinstance(stats, dict):
            continue
        missing = _integer(stats.get("missingCards"), 0)
        filled = _integer(stats.get("filledFromEnglish"), 0)
        lines.append(f"| {language} | {missing} | {filled} | {max(0, missing - filled)} |")
    open_sets = sorted((entry for entry in plan if entry["status"] != "fillable"),
                       key=lambda entry: (-entry["missing"], entry["language"], entry["setId"]))
    lines += ["", "## Offene Lücken (größte zuerst, max. 40)", "",
              "| Sprache | Set | Name | fehlen | Grund |", "|---|---|---|---:|---|"]
    for entry in open_sets[:40]:
        lines.append(f"| {entry['language']} | {entry['setId']} | {entry['setName']} | {entry['missing']} | "
                     f"{status_text.get(entry['status'], entry['status'])} |")
    if len(open_sets) > 40:
        lines.append(f"\n… und {len(open_sets) - 40} weitere Sets, siehe gaps.csv.")
    with open(os.path.join(output_dir, "gap_report.md"), "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines) + "\n")
    import csv
    with open(os.path.join(output_dir, "gaps.csv"), "w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.writer(handle, delimiter=";")
        writer.writerow(["language", "setId", "setName", "number", "cardName", "status", "missingInSet"])
        for entry in plan:
            if entry["status"] == "fillable":
                for _source_id, number, name in entry["fillable"]:
                    writer.writerow([entry["language"], entry["setId"], entry["setName"], number, name,
                                     "filled_from_en", entry["missing"]])
            else:
                writer.writerow([entry["language"], entry["setId"], entry["setName"], "", "",
                                 entry["status"], entry["missing"]])


def _normalize_pokemon_name_languages(db_path: str) -> int:
    """Map known PokéAPI language variants to catalog languages and delete all other name languages."""
    with closing(sqlite3.connect(db_path)) as db:
        changed = 0
        for old_code, new_code in POKEAPI_LANGUAGE_MAP.items():
            changed += db.execute("UPDATE OR IGNORE pokemon_names SET language=? WHERE language=?",
                                  (new_code, old_code)).rowcount
        placeholders = ",".join("?" * len(LANGUAGES))
        changed += db.execute(f"DELETE FROM pokemon_names WHERE language NOT IN ({placeholders})", LANGUAGES).rowcount
        db.commit()
    return changed


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--schema", default=SCHEMA_JSON_PATH)
    parser.add_argument("--output-dir", default=OUTPUT_DIR)
    parser.add_argument("--skip-bulbapedia", action="store_true", help="Skip optional serial set-list cross-check")
    parser.add_argument("--resume", action="store_true", help="Resume from catalog.sqlite and catalog-progress.json")
    args = parser.parse_args()
    output_dir = os.path.abspath(args.output_dir)
    os.makedirs(output_dir, exist_ok=True)
    _recover_interrupted_publish(output_dir)
    schema = load_schema(args.schema)
    staging_dir = tempfile.mkdtemp(prefix=".catalog-build-", dir=output_dir)
    stage_db = os.path.join(staging_dir, "catalog.sqlite")
    stage_checkpoint = os.path.join(staging_dir, "catalog-progress.json")
    output_db = os.path.join(output_dir, "catalog.sqlite")
    output_checkpoint = os.path.join(output_dir, "catalog-progress.json")
    output_partial = os.path.join(output_dir, PARTIAL_DB_NAME)
    try:
        if args.resume:
            # An unfinished build (publication gate failed) is kept next to its checkpoint; prefer it.
            resume_db = output_partial if os.path.exists(output_partial) else output_db
            if not os.path.exists(resume_db):
                parser.error("--resume requires an existing catalog-partial.sqlite or catalog.sqlite; run a normal build first")
            shutil.copy2(resume_db, stage_db)
            if os.path.exists(output_checkpoint):
                shutil.copy2(output_checkpoint, stage_checkpoint)
            conn = sqlite3.connect(stage_db)
            try:
                _validate_existing_catalog(conn, schema)
                _load_checkpoint(stage_checkpoint, schema["database"]["version"])
            except Exception:
                conn.close()
                raise
        else:
            conn = create_database(stage_db, schema)
        try:
            summary = populate_database(conn, include_bulbapedia=not args.skip_bulbapedia,
                                        checkpoint_path=stage_checkpoint, schema_version=schema["database"]["version"])
        finally:
            conn.close()

        failures = find_incomplete_catalog(summary)
        if failures:
            # Keep the failed run resumable, but never replace the last known-good published snapshot.
            # Keep the unfinished database together with its checkpoint, otherwise hours of downloads are lost.
            shutil.copy2(stage_db, output_partial)
            if os.path.exists(stage_checkpoint):
                shutil.copy2(stage_checkpoint, output_checkpoint)
            print("ERROR: catalog is incomplete; previous published artifacts were preserved.")
            print(f"Progress was saved. Run again with --resume to continue ({PARTIAL_DB_NAME}).")
            for source, reason in sorted(failures.items()):
                print(f"- {source}: {reason}")
            return 2

        _normalize_pokemon_name_languages(stage_db)
        pre_fill_db = os.path.join(staging_dir, "pre-fill.sqlite")
        shutil.copy2(stage_db, pre_fill_db)
        try:
            gap_plan = apply_language_fallback(stage_db, stage_checkpoint, schema["database"]["version"], summary)
            coverage = build_coverage(stage_db, summary)
            manifest_path = os.path.join(staging_dir, "manifest.json")
            report_path = os.path.join(staging_dir, "coverage_report.md")
            generate_manifest(stage_db, manifest_path, report_path, schema["database"]["version"], coverage, summary)
            from verify_catalog_artifacts import verify_catalog_artifacts
            verify_catalog_artifacts(stage_db, manifest_path, args.schema)
            _publish_staged_artifacts(staging_dir, output_dir)
        except Exception:
            # The download is the expensive part: keep it (without substitute cards) so --resume does not start over.
            shutil.copy2(pre_fill_db, output_partial)
            if os.path.exists(stage_checkpoint):
                shutil.copy2(stage_checkpoint, output_checkpoint)
            print("ERROR: the catalog was downloaded but building or verifying it failed.")
            print(f"Progress was saved. After fixing the problem run again with --resume ({PARTIAL_DB_NAME}).")
            raise
        if os.path.exists(stage_checkpoint):
            shutil.copy2(stage_checkpoint, output_checkpoint)
        if os.path.exists(output_partial):
            os.remove(output_partial)
        write_gap_report(output_db, output_dir, gap_plan, summary)
        print(f"Catalog built: {output_db}")
        print(f"Gap report: {os.path.join(output_dir, 'gap_report.md')} (details: gaps.csv)")
        print(f"Manifest: {os.path.join(output_dir, 'manifest.json')}")
        print(f"Coverage report: {os.path.join(output_dir, 'coverage_report.md')}")
        return 0
    finally:
        shutil.rmtree(staging_dir, ignore_errors=True)

if __name__ == "__main__":
    raise SystemExit(main())
