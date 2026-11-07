#!/usr/bin/env python3
"""Python port of Room 2.6.1's database identity hash algorithm.

`app/schemas/.../18.json` was created without a local Gradle build, so its
`identityHash` had to be computed offline. This module re-implements the exact
algorithm the Room 2.6.1 annotation processor uses
(`room-compiler/.../vo/{Database,SchemaIdentityKey,Entity,Field,Index,PrimaryKey,
ForeignKey,DatabaseView}.kt`, androidx repo @ ad24dd81c2):

    idKey = SchemaIdentityKey()
    idKey.appendSorted(entities)   # sorted by lowercase(entity.getIdKey())
    idKey.appendSorted(views)      # sorted by lowercase(view.getIdKey())
    hash = md5hex(idKey.toString())

Each `append` writes the value followed by the separator `?:?`. Entity keys hash
tableName, primaryKey, fields, indices and foreignKeys; field keys use
columnName-affinity-notNull(-defaultValue). The port was validated against the
WorkManager schema JSONs 6/10/14/17/21/24 from the androidx repository (covering
foreign keys and defaultValue) and this repo's 15.json/16.json.

Background (docs/BUG-REVIEW-2026-11-07.md, section 5.5): the hand-written hash in
the historical 17.json never matched its own content; Room does not notice because
`DatabaseBundle.isSchemaEqual` compares only entities and views, so the export
skips rewriting the file. This guard prevents the same drift for the newest
schema: it recomputes the hash from the JSON content and fails on a mismatch.

Known limitation: database views are not implemented (this app has none); the
check raises instead of computing a wrong hash.
"""
from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

SEPARATOR = "?:?"
SCHEMA_DIR = Path(__file__).resolve().parents[1] / "app/schemas/com.pokemontcg.sammlung.data.local.AppDatabase"


def _kotlin_str(value: object) -> str:
    """Render a value the way a Kotlin string template would."""
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, list):
        return "[" + ", ".join(value) + "]"
    return str(value)


class SchemaIdentityKey:
    """Mirror of androidx.room.vo.SchemaIdentityKey."""

    def __init__(self) -> None:
        self._parts: list[str] = []

    def append(self, identity: str) -> None:
        self._parts.append(identity)
        self._parts.append(SEPARATOR)

    def append_sorted(self, id_keys: list[str]) -> None:
        # ENGLISH_SORT compares lowercase(Locale.ENGLISH); identifiers here are ASCII.
        for key in sorted(id_keys, key=lambda s: s.lower()):
            self.append(key)

    def hash(self) -> str:
        return hashlib.md5("".join(self._parts).encode("utf-8")).hexdigest()


def field_id_key(field: dict) -> str:
    key = f"{field['columnName']}-{field.get('affinity') or 'TEXT'}-{_kotlin_str(field.get('notNull', False))}"
    default = field.get("defaultValue")
    if default is not None:
        key += f"-defaultValue={default}"
    return key


def index_id_key(index: dict) -> str:
    key = f"{_kotlin_str(index['unique'])}-{index['name']}-{','.join(index['columnNames'])}"
    orders = index.get("orders")
    if orders:
        key += f"-{','.join(orders)}"
    return key


def foreign_key_id_key(fk: dict) -> str:
    return (
        fk["table"]
        + f"-{','.join(fk.get('referencedColumns', fk.get('columns', [])))}"
        + f"-{','.join(fk.get('columns', fk.get('fields', [])))}"
        + f"-{fk['onDelete']}"
        + f"-{fk['onUpdate']}"
        + f"-{_kotlin_str(fk.get('deferred', False))}"
    )


def primary_key_id_key(pk: dict) -> str:
    return f"{_kotlin_str(pk['autoGenerate'])}-{_kotlin_str(pk['columnNames'])}"


def entity_id_key(entity: dict) -> str:
    key = SchemaIdentityKey()
    key.append(entity["tableName"])
    key.append(primary_key_id_key(entity["primaryKey"]))
    key.append_sorted([field_id_key(f) for f in entity["fields"]])
    key.append_sorted([index_id_key(i) for i in entity.get("indices", [])])
    key.append_sorted([foreign_key_id_key(fk) for fk in entity.get("foreignKeys", [])])
    return key.hash()


def database_identity_hash(database: dict) -> str:
    """Compute the Room identity hash for a schema JSON `database` object."""
    if database.get("views"):
        raise NotImplementedError("database views are not supported by this port")
    key = SchemaIdentityKey()
    key.append_sorted([entity_id_key(e) for e in database["entities"]])
    return key.hash()


def newest_schema(schema_dir: Path = SCHEMA_DIR) -> tuple[int, Path]:
    versions = sorted((int(p.stem), p) for p in schema_dir.glob("*.json") if p.stem.isdigit())
    if not versions:
        raise FileNotFoundError(f"No Room schema JSON found in {schema_dir}")
    return versions[-1]


def main() -> int:
    version, path = newest_schema()
    database = json.loads(path.read_text(encoding="utf-8"))["database"]
    stored = database["identityHash"]
    computed = database_identity_hash(database)
    if stored != computed:
        print(f"Room identity hash check: FAIL ({path.name})")
        print(f"- stored:   {stored}")
        print(f"- computed: {computed}")
        print("The newest schema JSON does not match the Room 2.6.1 identity hash algorithm.")
        return 1
    print(f"Room identity hash check: PASS (schema {version}, {computed})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
