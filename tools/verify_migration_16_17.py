#!/usr/bin/env python3
"""Offline SQLite regression check for the 16 -> 17 migration.

This is a schema/SQL simulation based on the checked-in Room schema JSON. It does
not execute Android Room's Migration class and does not replace instrumentation tests.
Run from any directory: python3 tools/verify_migration_16_17.py
"""
import json, sqlite3, pathlib
root=pathlib.Path(__file__).resolve().parents[1] / 'app/schemas/com.pokemontcg.sammlung.data.local.AppDatabase'
s16=json.load(open(root/'16.json'))['database']; s17=json.load(open(root/'17.json'))['database']
db=sqlite3.connect(':memory:')
for e in s16['entities']:
    db.execute(e['createSql'].replace('${TABLE_NAME}',e['tableName']))
    for ix in e.get('indices',[]): db.execute(ix['createSql'].replace('${TABLE_NAME}',e['tableName']))
for sql in [
"INSERT INTO pokemon (id,dexId,number,name,searchName,cardType) VALUES ('25',25,'025','Pikachu','pikachu','Common'),('1',1,'001','Bisasam','bisasam','Nichts'),('6',6,'006','Glurak','glurak','Fullart'),('999',999,'999','Unknown','unknown','legacy-invalid')",
"INSERT INTO cards (id,name,supertype,number,setId,setName,series,language,sortNumber) VALUES ('detail','Detail','Pokémon','2','xy1','XY','XY','de',2)",
"INSERT INTO catalog_cards (id,localId,name,setId,imageUrl,language,updatedAt) VALUES ('catalog','1','Pikachu','xy1','https://assets.tcgdex.net/xy1/1.png','de',1234)",
"INSERT INTO catalog_cards (id,localId,name,setId,imageUrl,language,updatedAt) VALUES ('detail','2','Catalog overwrite','xy1','https://assets.tcgdex.net/xy1/catalog.png','de',5678)",
"INSERT INTO user_cards (cardId,pokemonId,isPrimary,isCommon,isFullart,addedAt) VALUES ('detail','25',1,1,0,1700000000000)"
]: db.execute(sql)
for q in [
"CREATE TABLE IF NOT EXISTS pokemon_new (id TEXT NOT NULL PRIMARY KEY, dexId INTEGER NOT NULL, number TEXT NOT NULL, name TEXT NOT NULL, searchName TEXT NOT NULL, imageUrl TEXT, primaryCardImageUrl TEXT, cardType TEXT NOT NULL DEFAULT 'NONE', cardsUpdatedAt INTEGER)",
"INSERT INTO pokemon_new SELECT id, dexId, number, name, searchName, imageUrl, primaryCardImageUrl, CASE cardType WHEN 'Nichts' THEN 'NONE' WHEN 'Common' THEN 'COMMON' WHEN 'Fullart' THEN 'FULLART' ELSE 'NONE' END, cardsUpdatedAt FROM pokemon",
"DROP TABLE pokemon", "ALTER TABLE pokemon_new RENAME TO pokemon",
"ALTER TABLE cards ADD COLUMN detailLoaded INTEGER NOT NULL DEFAULT 0", "UPDATE cards SET detailLoaded = 1",
"INSERT OR IGNORE INTO cards (id,name,supertype,number,rarity,setId,setName,series,imageUrl,largeImageUrl,marketPrice,lowPrice,trendPrice,nationalPokedexNumber,subtypes,language,nationalPokedexNumbers,setLogoUrl,setSymbolUrl,setCardCountOfficial,setCardCountTotal,setReleaseDate,sortNumber,detailLoaded) SELECT id,name,'',localId,NULL,setId,'','',imageUrl,NULL,NULL,NULL,NULL,NULL,NULL,language,NULL,NULL,NULL,NULL,NULL,NULL,0,0 FROM catalog_cards",
"DROP TABLE IF EXISTS catalog_cards",
"CREATE TABLE IF NOT EXISTS sync_checkpoint (unit TEXT NOT NULL PRIMARY KEY, runId INTEGER NOT NULL, state TEXT NOT NULL, attempts INTEGER NOT NULL, updatedAt INTEGER NOT NULL, error TEXT)"
]: db.execute(q)
# Do not create schema-17 indexes here: the verifier must detect missing indexes
# produced by the migration rather than silently repairing them.
errs=[]
for e in s17['entities']:
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
        (fk['table'], tuple(fk['columnNames']), tuple(fk['referencedColumnNames']),
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
assert not errs, 'Migrated SQLite schema differs from schema 17'
assert db.execute('select id,detailLoaded from cards order by id').fetchall() == [('catalog', 0), ('detail', 1)]
assert db.execute("select name,imageUrl from cards where id='detail'").fetchone() == ('Detail', None)
assert db.execute('select id,cardType from pokemon order by dexId').fetchall() == [('1', 'NONE'), ('6', 'FULLART'), ('25', 'COMMON'), ('999', 'NONE')]
assert db.execute('select cardId,pokemonId,isPrimary,isCommon,isFullart,addedAt from user_cards').fetchall() == [('detail', '25', 1, 1, 0, 1700000000000)]
assert not db.execute("select 1 from sqlite_master where type='table' and name='catalog_cards'").fetchone()
checkpoint_columns = [row[1] for row in db.execute('PRAGMA table_info("sync_checkpoint")')]
assert checkpoint_columns == ['unit', 'runId', 'state', 'attempts', 'updatedAt', 'error']
db.execute("INSERT INTO sync_checkpoint (unit,runId,state,attempts,updatedAt,error) VALUES ('cards:de',7,'RUNNING',1,1700000000000,NULL)")
assert db.execute("SELECT runId,state,attempts,error FROM sync_checkpoint WHERE unit='cards:de'").fetchone() == (7, 'RUNNING', 1, None)
try:
    db.execute("INSERT INTO sync_checkpoint (unit,runId,state,attempts,updatedAt,error) VALUES ('cards:de',8,'DONE',2,1700000000001,NULL)")
except sqlite3.IntegrityError:
    pass
else:
    raise AssertionError('sync_checkpoint.unit must enforce primary-key uniqueness')
assert db.execute("SELECT COUNT(*) FROM cards WHERE imageUrl IS NOT NULL AND imageUrl != ''").fetchone()[0] == 1
assert db.execute("SELECT COUNT(*) FROM cards").fetchone()[0] == 2
print('PASS: schema, index signatures, foreign keys, SQLite integrity, categories, detail flags, image coverage, checkpoint schema and key uniqueness, catalog removal, and user collection preservation')
