package com.pokemontcg.sammlung

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pokemontcg.sammlung.data.importer.CatalogImportResult
import com.pokemontcg.sammlung.data.importer.CatalogImporter
import com.pokemontcg.sammlung.data.local.AppDatabase
import com.pokemontcg.sammlung.data.local.user.UserCardEntityV2
import com.pokemontcg.sammlung.data.local.user.UserDatabase
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogImporterTest {

    private lateinit var appDb: AppDatabase
    private lateinit var userDb: UserDatabase

    @Before
    fun createDbs() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        appDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        userDb = Room.inMemoryDatabaseBuilder(context, UserDatabase::class.java).build()
    }

    @After
    fun closeDbs() {
        appDb.close()
        userDb.close()
    }

    @Test
    fun failedMidTransactionCatalogImportRollsBackEarlierTableWrites(): Unit = runBlocking {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val instrumentationContext = InstrumentationRegistry.getInstrumentation().context
        val importer = CatalogImporter(targetContext, appDb)

        val validFixture = File(targetContext.cacheDir, "catalog_rollback_valid.sqlite")
        instrumentationContext.assets.open("catalog_import_fixture.sqlite").use { input ->
            FileOutputStream(validFixture).use { output -> input.copyTo(output) }
        }
        val initial = importer.importCatalogFile(validFixture)
        assertTrue("Initial fixture import should succeed, got $initial", initial is CatalogImportResult.Success)

        val brokenFixture = File(targetContext.cacheDir, "catalog_rollback_broken.sqlite")
        instrumentationContext.assets.open("catalog_import_fixture.sqlite").use { input ->
            FileOutputStream(brokenFixture).use { output -> input.copyTo(output) }
        }
        val brokenDb = android.database.sqlite.SQLiteDatabase.openDatabase(
            brokenFixture.absolutePath,
            null,
            android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
        )
        brokenDb.use { db ->
            db.execSQL("UPDATE sets SET name = 'Replacement Set' WHERE id = 'fixture-set'")
            db.execSQL("UPDATE cards SET name = 'Replacement Pikachu' WHERE id = 'fixture-card-1'")
            db.execSQL("UPDATE pokemon_names SET name = 'Replacement Name' WHERE language = 'de'")
        }

        // Fail only at the final catalog table, after earlier INSERT statements ran.
        // This exercises transaction rollback without relying on a malformed source schema.
        val targetDb = appDb.openHelper.writableDatabase
        targetDb.execSQL("CREATE TRIGGER fail_catalog_names BEFORE INSERT ON pokemon_names BEGIN SELECT RAISE(ABORT, 'forced catalog import failure'); END")
        val result = try {
            importer.importCatalogFile(brokenFixture)
        } finally {
            targetDb.execSQL("DROP TRIGGER IF EXISTS fail_catalog_names")
        }
        assertTrue("Broken fixture should fail during catalog transaction, got $result", result is CatalogImportResult.Error)

        val db = appDb.openHelper.writableDatabase
        val setName = db.query("SELECT name FROM sets WHERE id = 'fixture-set'").use { cursor ->
            assertTrue("Expected the original set row to remain", cursor.moveToFirst())
            cursor.getString(0)
        }
        val cardName = db.query("SELECT name FROM cards WHERE id = 'fixture-card-1'").use { cursor ->
            assertTrue("Expected the original card row to remain", cursor.moveToFirst())
            cursor.getString(0)
        }
        assertEquals("Fixture Set", setName)
        assertEquals("Fixture Pikachu", cardName)

        validFixture.delete()
        brokenFixture.delete()
    }

    @Test
    fun importingCatalogSnapshotRemovesRowsMissingFromNewCatalog(): Unit = runBlocking {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val instrumentationContext = InstrumentationRegistry.getInstrumentation().context
        val importer = CatalogImporter(targetContext, appDb)
        val fixture = File(targetContext.cacheDir, "catalog_snapshot_replace.sqlite")
        instrumentationContext.assets.open("catalog_import_fixture.sqlite").use { input ->
            FileOutputStream(fixture).use { output -> input.copyTo(output) }
        }

        val db = appDb.openHelper.writableDatabase
        db.execSQL("INSERT INTO sets (id, name, series, printedTotal, total, releaseDate) VALUES ('stale-set', 'Old Set', 'Old Series', 1, 1, '2000/01/01')")
        db.execSQL("INSERT INTO cards (id, name, supertype, number, setId, setName, series, language) VALUES ('stale-card', 'Old Card', 'Pokémon', '999', 'stale-set', 'Old Set', 'Old Series', 'en')")

        val result = importer.importCatalogFile(fixture)
        assertTrue("Expected snapshot import to succeed, got $result", result is CatalogImportResult.Success)
        assertEquals("Fixture set should replace stale set rows", 0L, db.query("SELECT COUNT(*) FROM sets WHERE id = 'stale-set'").use { cursor -> cursor.moveToFirst(); cursor.getLong(0) })
        assertEquals("Fixture card should replace stale card rows", 0L, db.query("SELECT COUNT(*) FROM cards WHERE id = 'stale-card'").use { cursor -> cursor.moveToFirst(); cursor.getLong(0) })
        assertEquals("Only the fixture set should remain", 1L, db.query("SELECT COUNT(*) FROM sets").use { cursor -> cursor.moveToFirst(); cursor.getLong(0) })
        assertEquals("Only the fixture card should remain", 1L, db.query("SELECT COUNT(*) FROM cards").use { cursor -> cursor.moveToFirst(); cursor.getLong(0) })
        fixture.delete()
    }

    @Test
    fun briefCatalogUpdatePreservesLoadedDetailsAndUserState(): Unit = runBlocking {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val instrumentationContext = InstrumentationRegistry.getInstrumentation().context
        val importer = CatalogImporter(targetContext, appDb)

        val fullFixture = File(targetContext.cacheDir, "catalog_merge_full.sqlite")
        instrumentationContext.assets.open("catalog_import_fixture.sqlite").use { input ->
            FileOutputStream(fullFixture).use { output -> input.copyTo(output) }
        }
        val initial = importer.importCatalogFile(fullFixture)
        assertTrue("Initial fixture import should succeed, got $initial", initial is CatalogImportResult.Success)

        val db = appDb.openHelper.writableDatabase
        db.execSQL(
            "UPDATE cards SET detailLoaded = 1, name = 'Full Pikachu', imageUrl = 'local-image', " +
                "rarity = 'Rare', largeImageUrl = 'local-large', marketPrice = 12.5, " +
                "nationalPokedexNumbers = '25', subtypes = 'Basic' WHERE id = 'fixture-card-1'"
        )
        db.execSQL(
            "UPDATE pokemon SET cardType = 'COMMON', primaryCardImageUrl = 'local-primary', " +
                "cardsUpdatedAt = 123 WHERE id = '25'"
        )
        db.execSQL(
            "INSERT INTO cards (id, name, supertype, number, setId, setName, series, language, detailLoaded) " +
                "VALUES ('protected-card', 'Protected', 'Pokémon', '99', 'fixture-set', 'Fixture Set', 'Fixture Series', 'de', 1)"
        )
        db.execSQL(
            "INSERT INTO user_cards (cardId, pokemonId, isPrimary, isCommon, isFullart, addedAt) " +
                "VALUES ('protected-card', '25', 1, 1, 0, 1700000000000)"
        )

        val briefFixture = File(targetContext.cacheDir, "catalog_merge_brief.sqlite")
        instrumentationContext.assets.open("catalog_import_fixture.sqlite").use { input ->
            FileOutputStream(briefFixture).use { output -> input.copyTo(output) }
        }
        val briefDb = android.database.sqlite.SQLiteDatabase.openDatabase(
            briefFixture.absolutePath,
            null,
            android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
        )
        briefDb.use {
            it.execSQL(
                "UPDATE cards SET detailLoaded = 0, name = 'Brief Pikachu', imageUrl = 'brief-image', " +
                    "rarity = NULL, largeImageUrl = NULL, marketPrice = NULL, lowPrice = NULL, " +
                    "trendPrice = NULL, nationalPokedexNumbers = NULL, subtypes = NULL, sortNumber = 1 " +
                    "WHERE id = 'fixture-card-1'"
            )
        }

        val result = importer.importCatalogFile(
            briefFixture,
            protectedCardIds = setOf("protected-card"),
            protectedPokemonIds = setOf("25")
        )
        assertTrue("Brief fixture import should succeed, got $result", result is CatalogImportResult.Success)

        db.query(
            "SELECT name, imageUrl, detailLoaded, rarity, largeImageUrl, marketPrice, " +
                "nationalPokedexNumbers, subtypes, setName FROM cards WHERE id = 'fixture-card-1'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Brief Pikachu", cursor.getString(0))
            assertEquals("brief-image", cursor.getString(1))
            assertEquals(1, cursor.getInt(2))
            assertEquals("Rare", cursor.getString(3))
            assertEquals("local-large", cursor.getString(4))
            assertEquals(12.5, cursor.getDouble(5), 0.0)
            assertEquals("25", cursor.getString(6))
            assertEquals("Basic", cursor.getString(7))
            assertEquals("Fixture Set", cursor.getString(8))
        }

        db.query("SELECT cardType, primaryCardImageUrl, cardsUpdatedAt, name FROM pokemon WHERE id = '25'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("COMMON", cursor.getString(0))
            assertEquals("local-primary", cursor.getString(1))
            assertEquals(123L, cursor.getLong(2))
            assertEquals("Pikachu", cursor.getString(3))
        }

        assertEquals(
            "Protected collection card must survive a snapshot that no longer contains it",
            1L,
            db.query("SELECT COUNT(*) FROM cards WHERE id = 'protected-card'").use { cursor ->
                cursor.moveToFirst(); cursor.getLong(0)
            }
        )
        assertEquals(
            "Legacy collection rows must remain untouched",
            1L,
            db.query("SELECT COUNT(*) FROM user_cards WHERE cardId = 'protected-card'").use { cursor ->
                cursor.moveToFirst(); cursor.getLong(0)
            }
        )
        assertEquals(
            "Brief source cards must keep their local card-to-Pokémon links",
            1L,
            db.query("SELECT COUNT(*) FROM card_pokemon WHERE cardId = 'fixture-card-1' AND dexId = 25").use { cursor ->
                cursor.moveToFirst(); cursor.getLong(0)
            }
        )

        fullFixture.delete()
        briefFixture.delete()
    }

    @Test
    fun emptyCatalogSnapshotIsRejectedWithoutClearingCurrentCatalog(): Unit = runBlocking {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val instrumentationContext = InstrumentationRegistry.getInstrumentation().context
        val importer = CatalogImporter(targetContext, appDb)
        val validFixture = File(targetContext.cacheDir, "catalog_nonempty_before_empty.sqlite")
        instrumentationContext.assets.open("catalog_import_fixture.sqlite").use { input ->
            FileOutputStream(validFixture).use { output -> input.copyTo(output) }
        }
        val initial = importer.importCatalogFile(validFixture)
        assertTrue("Initial fixture import should succeed, got $initial", initial is CatalogImportResult.Success)

        val emptyFixture = File(targetContext.cacheDir, "catalog_empty_snapshot.sqlite")
        instrumentationContext.assets.open("catalog_import_fixture.sqlite").use { input ->
            FileOutputStream(emptyFixture).use { output -> input.copyTo(output) }
        }
        val sourceDb = android.database.sqlite.SQLiteDatabase.openDatabase(
            emptyFixture.absolutePath,
            null,
            android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
        )
        sourceDb.use { db ->
            db.execSQL("DELETE FROM card_pokemon")
            db.execSQL("DELETE FROM cards")
            db.execSQL("DELETE FROM pokemon_names")
            db.execSQL("DELETE FROM pokemon")
            db.execSQL("DELETE FROM sets")
        }

        val rejected = importer.importCatalogFile(emptyFixture)
        assertTrue("Empty snapshot must be rejected, got $rejected", rejected is CatalogImportResult.Error)
        assertTrue(
            "Error should identify an empty/incomplete snapshot",
            (rejected as CatalogImportResult.Error).message.contains("empty or incomplete", ignoreCase = true)
        )
        assertEquals("Existing set must remain after rejecting empty snapshot", 1, appDb.setDao().getAllSets().first().size)
        assertEquals("Existing card must remain after rejecting empty snapshot", 1, appDb.cardDao().getAllCardsOnce().size)

        validFixture.delete()
        emptyFixture.delete()
    }

    @Test
    fun catalogWithChangedTableColumnsIsRejectedBeforeImport(): Unit = runBlocking {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val instrumentationContext = InstrumentationRegistry.getInstrumentation().context
        val importer = CatalogImporter(targetContext, appDb)
        val fixture = File(targetContext.cacheDir, "catalog_schema_mismatch.sqlite")
        instrumentationContext.assets.open("catalog_import_fixture.sqlite").use { input ->
            FileOutputStream(fixture).use { output -> input.copyTo(output) }
        }

        val malformedDb = android.database.sqlite.SQLiteDatabase.openDatabase(
            fixture.absolutePath,
            null,
            android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
        )
        malformedDb.use { db ->
            // Deliberately retain room_master_table.identity_hash while changing a table layout.
            db.execSQL("DROP TABLE pokemon_names")
            db.execSQL("CREATE TABLE pokemon_names (language TEXT NOT NULL, name TEXT NOT NULL, dexId INTEGER NOT NULL, extra TEXT)")
        }

        val result = importer.importCatalogFile(fixture)
        assertTrue("Changed table layout must be rejected, got $result", result is CatalogImportResult.Error)
        assertTrue("Schema mismatch should be identified", (result as CatalogImportResult.Error).message.contains("schema mismatch", ignoreCase = true))
        assertEquals(0, appDb.setDao().getAllSets().first().size)
        assertEquals(0, appDb.cardDao().getAllCardsOnce().size)
        fixture.delete()
    }

    @Test
    fun catalogWithOrphanedCardSetReferenceIsRejectedBeforeReplacingCurrentCatalog(): Unit = runBlocking {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val instrumentationContext = InstrumentationRegistry.getInstrumentation().context
        val importer = CatalogImporter(targetContext, appDb)
        val validFixture = File(targetContext.cacheDir, "catalog_relationship_valid.sqlite")
        instrumentationContext.assets.open("catalog_import_fixture.sqlite").use { input ->
            FileOutputStream(validFixture).use { output -> input.copyTo(output) }
        }
        val initial = importer.importCatalogFile(validFixture)
        assertTrue("Initial fixture import should succeed, got $initial", initial is CatalogImportResult.Success)

        val invalidFixture = File(targetContext.cacheDir, "catalog_relationship_invalid.sqlite")
        instrumentationContext.assets.open("catalog_import_fixture.sqlite").use { input ->
            FileOutputStream(invalidFixture).use { output -> input.copyTo(output) }
        }
        val malformedDb = android.database.sqlite.SQLiteDatabase.openDatabase(
            invalidFixture.absolutePath,
            null,
            android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
        )
        malformedDb.use { db ->
            db.execSQL("UPDATE cards SET setId = 'missing-set' WHERE id = 'fixture-card-1'")
        }

        val result = importer.importCatalogFile(invalidFixture)
        assertTrue("Orphaned relationship must be rejected, got $result", result is CatalogImportResult.Error)
        assertTrue(
            "Error should identify relationship validation",
            (result as CatalogImportResult.Error).message.contains("relationship validation", ignoreCase = true)
        )
        assertEquals("Existing set must remain after rejection", 1, appDb.setDao().getAllSets().first().size)
        assertEquals("Existing card must remain after rejection", 1, appDb.cardDao().getAllCardsOnce().size)

        validFixture.delete()
        invalidFixture.delete()
    }

    @Test
    fun catalogFixtureImportsOnlyCatalogTablesAndPreservesBothCollections(): Unit = runBlocking {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val instrumentationContext = InstrumentationRegistry.getInstrumentation().context

        // Populate both the retained legacy collection table and the current UserDatabase.
        // The legacy table has no DAO any more, so the fixture row is written directly.
        appDb.openHelper.writableDatabase.execSQL(
            "INSERT INTO user_cards (cardId, pokemonId, isPrimary, isCommon, isFullart, addedAt) " +
                "VALUES ('legacy-card-1', '25', 1, 1, 0, 100)"
        )
        userDb.userCardDao().insertUserCard(
            UserCardEntityV2(
                cardId = "user-card-1",
                pokemonId = "25",
                pokemonDexId = 25,
                language = "de",
                quantity = 2,
                category = "COMMON",
                isPrimary = true,
                addedAt = 200L
            )
        )

        val legacyCountBefore = legacyUserCardCount()
        val userDbCountBefore = userDb.userCardDao().getAllUserCardsOnce().size
        val fixture = File(targetContext.cacheDir, "catalog_import_fixture.sqlite")
        instrumentationContext.assets.open("catalog_import_fixture.sqlite").use { input ->
            FileOutputStream(fixture).use { output -> input.copyTo(output) }
        }

        val result = CatalogImporter(targetContext, appDb).importCatalogFile(fixture)

        assertTrue("Expected fixture catalog import to succeed, got $result", result is CatalogImportResult.Success)
        assertEquals("Fixture set should be imported", 1, appDb.setDao().getAllSets().first().size)
        assertEquals("Fixture card should be imported", 1, appDb.cardDao().getAllCardsOnce().size)
        assertEquals("Fixture card-to-Pokémon link should be imported", listOf(25), appDb.cardDao().getCachedPokedexNumbers())
        assertEquals("Legacy collection rows must remain unchanged", legacyCountBefore, legacyUserCardCount())
        assertEquals("Current UserDatabase rows must remain unchanged", userDbCountBefore, userDb.userCardDao().getAllUserCardsOnce().size)

        // A catalog with a mismatched Room identity must be rejected without changing live rows.
        val invalidFixture = File(targetContext.cacheDir, "catalog_import_invalid_fixture.sqlite")
        instrumentationContext.assets.open("catalog_import_fixture.sqlite").use { input ->
            FileOutputStream(invalidFixture).use { output -> input.copyTo(output) }
        }
        val invalidDb = android.database.sqlite.SQLiteDatabase.openDatabase(
            invalidFixture.absolutePath,
            null,
            android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
        )
        invalidDb.use {
            it.execSQL("UPDATE room_master_table SET identity_hash = ? WHERE id = 42", arrayOf("invalid-schema-hash"))
        }

        val rejected = CatalogImporter(targetContext, appDb).importCatalogFile(invalidFixture)
        assertTrue("Invalid schema must be rejected, got $rejected", rejected is CatalogImportResult.Error)
        assertEquals("Rejected catalog must not change existing sets", 1, appDb.setDao().getAllSets().first().size)
        assertEquals("Rejected catalog must not change existing cards", 1, appDb.cardDao().getAllCardsOnce().size)
        assertEquals("Legacy collection rows must remain unchanged after rejection", legacyCountBefore, legacyUserCardCount())
        assertEquals("Current UserDatabase rows must remain unchanged after rejection", userDbCountBefore, userDb.userCardDao().getAllUserCardsOnce().size)

        fixture.delete()
        invalidFixture.delete()
    }

    /**
     * Row count of the retained legacy collection table inside the catalog database.
     *
     * `SupportSQLiteDatabase` exposes `query`, not the framework `rawQuery`, and the table has no
     * DAO any more - see the comment on `UserCardEntity` in `AppDatabase`.
     */
    private fun legacyUserCardCount(): Int =
        appDb.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM user_cards")
            .use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
}
