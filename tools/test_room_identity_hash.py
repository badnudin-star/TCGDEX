"""Regression tests for the offline Room identity hash port."""
import importlib.util
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("room_identity_hash.py")
spec = importlib.util.spec_from_file_location("room_identity_hash", MODULE_PATH)
module = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(module)


def entity(table: str, columns: list[tuple[str, str, bool, str | None]],
           pk: list[str], indices: list[dict] | None = None,
           auto_generate: bool = False) -> dict:
    return {
        "tableName": table,
        "createSql": "",
        "fields": [
            {"fieldPath": name, "columnName": name, "affinity": affinity,
             **({"notNull": not_null}), **({"defaultValue": default} if default is not None else {})}
            for name, affinity, not_null, default in columns
        ],
        "primaryKey": {"autoGenerate": auto_generate, "columnNames": pk},
        "indices": indices or [],
        "foreignKeys": [],
    }


class RoomIdentityHashTest(unittest.TestCase):
    def test_committed_newest_schema_matches_algorithm(self):
        version, path = module.newest_schema()
        database = __import__("json").loads(path.read_text(encoding="utf-8"))["database"]
        self.assertEqual(database["identityHash"], module.database_identity_hash(database))
        self.assertEqual(19, version)

    def test_key_components_render_like_kotlin(self):
        key = module.SchemaIdentityKey()
        key.append("a")
        key.append_sorted(["B", "a", "C"])
        self.assertEqual("a?:?a?:?B?:?C?:?", "".join(key._parts))
        self.assertEqual(
            "3b85c65c3e00e1918466f0a8733997e2", key.hash()
        )  # guards accidental format drift; value recomputed when the format changes on purpose

    def test_field_key_includes_default_value_only_when_set(self):
        without = {"columnName": "c", "affinity": "TEXT", "notNull": True}
        with_default = {"columnName": "c", "affinity": "TEXT", "notNull": True, "defaultValue": "'NONE'"}
        self.assertEqual("c-TEXT-true", module.field_id_key(without))
        self.assertEqual("c-TEXT-true-defaultValue='NONE'", module.field_id_key(with_default))

    def test_multi_column_primary_key_renders_like_list_to_string(self):
        pk = {"autoGenerate": False, "columnNames": ["cardId", "pokemonId"]}
        self.assertEqual("false-[cardId, pokemonId]", module.primary_key_id_key(pk))

    def test_entity_hash_is_order_insensitive_for_fields(self):
        base = entity("t", [("a", "TEXT", True, None), ("b", "INTEGER", False, "0")], ["a"])
        swapped = entity("t", [("b", "INTEGER", False, "0"), ("a", "TEXT", True, None)], ["a"])
        self.assertEqual(module.entity_id_key(base), module.entity_id_key(swapped))

    def test_views_are_rejected_explicitly(self):
        with self.assertRaises(NotImplementedError):
            module.database_identity_hash({"entities": [], "views": [{"viewName": "v"}]})


if __name__ == "__main__":
    unittest.main(verbosity=2)
