#!/usr/bin/env python3
"""Offline SQLite regression check for the 17 -> 18 migration.

This is a schema/SQL simulation based on the checked-in Room schema JSON. It does
not execute Android Room's Migration class and does not replace instrumentation tests.
Run from any directory: python3 tools/verify_migration_17_18.py
"""
import json, sqlite3, pathlib
root=pathlib.Path(__file__).resolve().parents[1] / 'app/schemas/com.pokemontcg.sammlung.data.local.AppDatabase'
s17=json.load(open(root/'17.json'))['database']; s18=json.load(open(root/'18.json'))['database']
assert s18['version'] == 18, '18.json must declare version 18'
db=sqlite3.connect(':memory:')
for e in s17['entities']:
    db.execute(e['createSql'].replace('${TABLE_NAME}',e['tableName']))
    for ix in e.get('indices',[]): db.execute(ix['createSql'].replace('${TABLE_NAME}',e['tableName']))
for sql in [
"INSERT INTO pokemon (id,dexId,number,name,searchName,cardType) VALUES ('25',25,'025','Pikachu','pikachu','COMMON')",
"INSERT INTO cards (id,name,supertype,number,setId,setName,series,language,sortNumber,detailLoaded) VALUES ('detail','Detail','Pokémon','2','xy1','XY','XY','de',2,1)",
"INSERT INTO user_cards (cardId,pokemonId,isPrimary,isCommon,isFullart,addedAt) VALUES ('detail','25',1,1,0,1700000000000)",
"INSERT INTO sync_checkpoint (unit,runId,state,attempts,updatedAt,error) VALUES ('cards:de',7,'RUNNING',1,1700000000000,NULL)"
]: db.execute(sql)
# The production MIGRATION_17_18 body (AppDatabase.kt). Dropping user_cards is a
# deliberate destructive step: the legacy table has been unreadable since the
# collection moved to UserDatabase, and schema 18 finally removes it.
db.execute("DROP TABLE IF EXISTS user_cards")
# Do not create schema-18 indexes here: the verifier must detect missing indexes
# produced by the migration rather than silently repairing them.
errs=[]
for e in s18['entities']:
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
assert not errs, 'Migrated SQLite schema differs from schema 18'
assert not db.execute("select 1 from sqlite_master where type='table' and name='user_cards'").fetchone(), \
    'MIGRATION_17_18 must drop the legacy user_cards table'
assert db.execute("select id,name,sortNumber,detailLoaded from cards").fetchall() == [('detail','Detail',2,1)], \
    'catalog rows must survive the legacy table drop'
assert db.execute("select id,cardType from pokemon").fetchall() == [('25','COMMON')], \
    'pokemon rows must survive the legacy table drop'
assert db.execute("select runId,state,attempts,error from sync_checkpoint where unit='cards:de'").fetchone() == (7,'RUNNING',1,None), \
    'sync checkpoints must survive the legacy table drop'
assert [e['tableName'] for e in s18['entities']] == ['pokemon','sets','cards','last_update','source_audit','card_pokemon','pokemon_names','sync_checkpoint'], \
    'schema 18 must not contain user_cards'
print('PASS: schema, index signatures, foreign keys, SQLite integrity, legacy user_cards removal, and catalog/checkpoint preservation')
