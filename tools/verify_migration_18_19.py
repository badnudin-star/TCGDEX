#!/usr/bin/env python3
"""Offline SQLite regression check for the 18 -> 19 migration.

This is a schema/SQL simulation based on the checked-in Room schema JSON. It does
not execute Android Room's Migration class and does not replace instrumentation tests.
Run from any directory: python3 tools/verify_migration_18_19.py
"""
import json, sqlite3, pathlib
root=pathlib.Path(__file__).resolve().parents[1] / 'app/schemas/com.pokemontcg.sammlung.data.local.AppDatabase'
s18=json.load(open(root/'18.json'))['database']; s19=json.load(open(root/'19.json'))['database']
assert s19['version'] == 19, '19.json must declare version 19'
db=sqlite3.connect(':memory:')
for e in s18['entities']:
    db.execute(e['createSql'].replace('${TABLE_NAME}',e['tableName']))
    for ix in e.get('indices',[]): db.execute(ix['createSql'].replace('${TABLE_NAME}',e['tableName']))
for sql in [
"INSERT INTO sets (id,name,series,printedTotal,total,releaseDate,logoUrl,symbolUrl) VALUES ('xy1','XY','XY','1','2','2014-01-01','https://example/xy1/logo.png',NULL)",
"INSERT INTO cards (id,name,supertype,number,setId,setName,series,language,sortNumber,detailLoaded) VALUES ('xy1-1','Pikachu','Pokémon','1','xy1','XY','XY','de',1,1)"
]: db.execute(sql)
# The production MIGRATION_18_19 body (AppDatabase.kt): adds the official set
# abbreviation to sets and cards. Existing rows keep NULL and the next catalog
# import fills the column.
db.execute("ALTER TABLE sets ADD COLUMN setAbbreviation TEXT")
db.execute("ALTER TABLE cards ADD COLUMN setAbbreviation TEXT")
# Do not create schema-19 indexes here: the verifier must detect missing indexes
# produced by the migration rather than silently repairing them.
errs=[]
for e in s19['entities']:
    name=e['tableName']; actual=db.execute(f'PRAGMA table_info("{name}")').fetchall()
    if not actual: errs.append(f'missing table {name}'); continue
    expected={f['columnName']:f for f in e['fields']}; amap={r[1]:r for r in actual}
    if set(expected)!=set(amap): errs.append(f'{name} columns mismatch missing={set(expected)-set(amap)} extra={set(amap)-set(expected)}')
    pks=e.get('primaryKey',{}).get('columnNames',[])
    for col,f in expected.items():
        if col not in amap: continue
        row=amap[col]
        for k,x,y in [('affinity',f['affinity'],row[2].upper()),('notNull',int(f['notNull']),row[3]),('primaryKey',int(col in pks),int(row[5]>0))]:
            if x!=y: errs.append(f'{name}.{col} {k}: expected {x}, got {y}')
        exp=f.get('defaultValue'); act=row[4]
        normalize=lambda value: None if value is None else str(value).strip("'")
        if normalize(exp) != normalize(act): errs.append(f'{name}.{col} default expected {exp}, got {act}')
    expected_indices = {ix['name']: ix for ix in e.get('indices', [])}
    actual_index_rows = [r for r in db.execute(f'PRAGMA index_list("{name}")') if not r[1].startswith('sqlite_autoindex')]
    actual_indices = {r[1]: r for r in actual_index_rows}
    if set(expected_indices) != set(actual_indices):
        errs.append(f'{name} indexes expected {set(expected_indices)}, got {set(actual_indices)}')
    for index_name, expected_index in expected_indices.items():
        if index_name not in actual_indices:
            continue
        actual_index = actual_indices[index_name]
        if bool(actual_index[2]) != bool(expected_index.get('unique', False)):
            errs.append(f'{name}.{index_name} unique expected {expected_index.get("unique", False)}, got {bool(actual_index[2])}')
        actual_columns = [row[2] for row in db.execute(f'PRAGMA index_info("{index_name}")')]
        if actual_columns != expected_index.get('columnNames', []):
            errs.append(f'{name}.{index_name} columns expected {expected_index.get("columnNames", [])}, got {actual_columns}')

    expected_foreign_keys = sorted(
        (fk['table'], tuple(fk.get('columns', [])), tuple(fk.get('referencedColumns', [])),
         fk.get('onDelete', 'NO ACTION').upper(), fk.get('onUpdate', 'NO ACTION').upper())
        for fk in e.get('foreignKeys', [])
    )
    actual_foreign_keys = sorted(
        (row[2], tuple([row[3]]), tuple([row[4]]), row[6].upper(), row[5].upper())
        for row in db.execute(f'PRAGMA foreign_key_list("{name}")')
    )
    if expected_foreign_keys != actual_foreign_keys:
        errs.append(f'{name} foreign keys expected {expected_foreign_keys}, got {actual_foreign_keys}')
integrity_result = db.execute('PRAGMA integrity_check').fetchone()[0]
if integrity_result != 'ok':
    errs.append(f'SQLite integrity_check failed: {integrity_result}')
foreign_key_errors = db.execute('PRAGMA foreign_key_check').fetchall()
if foreign_key_errors:
    errs.append(f'SQLite foreign_key_check failed: {foreign_key_errors}')
print('Schema comparison errors:',len(errs))
for e in errs: print('-',e)
assert not errs, 'Migrated SQLite schema differs from schema 19'
assert db.execute("select id,name,series,printedTotal,total,releaseDate,logoUrl,symbolUrl,setAbbreviation from sets").fetchall() == \
    [('xy1','XY','XY',1,2,'2014-01-01','https://example/xy1/logo.png',None,None)], 'migration must keep set data intact'
assert db.execute("select setAbbreviation from cards").fetchall() == [(None,)], 'new column must start NULL'
db.execute("UPDATE cards SET setAbbreviation='XY' WHERE id='xy1-1'")
assert db.execute("select setAbbreviation from cards").fetchall() == [('XY',)], 'new column must be writable'
print('PASS: 18 -> 19 migration keeps all data and adds setAbbreviation to sets and cards')
