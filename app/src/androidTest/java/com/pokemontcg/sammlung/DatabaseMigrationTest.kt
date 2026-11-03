package com.pokemontcg.sammlung

import android.database.sqlite.SQLiteDatabase
import android.content.ContextWrapper
import java.io.File
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pokemontcg.sammlung.data.local.AppDatabase
import com.pokemontcg.sammlung.data.local.user.UserDatabase
import com.pokemontcg.sammlung.data.local.user.UserMigrationHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    private val TEST_DB = "migration-test-db"

    /**
     * Schema 14 is reconstructed from the exported schema 15 by reversing MIGRATION_14_15:
     * sortNumber, card_pokemon, pokemon_names and their indices are removed. No real v14
     * database exists in the repository, and a generated identity hash for the old entity set
     * cannot be produced without compiling that historical schema. MigrationTestHelper only
     * consumes the create statements here; the migration to 15 writes the real v15 hash.
     */
    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun testAppDatabaseMigration14To15() {
        val db14 = helper.createDatabase(TEST_DB, 14).apply {
            execSQL(
                "INSERT INTO cards (id, name, supertype, number, setId, setName, series, language, nationalPokedexNumber, nationalPokedexNumbers) " +
                "VALUES ('xy1-1', 'Pikachu', 'Pokémon', '25/108', 'xy1', 'XY', 'XY', 'de', 25, '25,26')"
            )
            close()
        }

        val db15 = helper.runMigrationsAndValidate(TEST_DB, 15, true, *AppDatabase.ALL_MIGRATIONS)
        db15.query("SELECT cardId, dexId FROM card_pokemon WHERE cardId = 'xy1-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("xy1-1", cursor.getString(0))
            assertEquals(25, cursor.getInt(1))
            assertTrue("Only one dex link is known before 15→16", !cursor.moveToNext())
        }
    }

    @Test
    fun testAppDatabaseMigration15To16BackfillsSortAndAllPokemonLinks() {
        val db15 = helper.createDatabase(TEST_DB, 15).apply {
            execSQL(
                "INSERT INTO cards (id, name, supertype, number, setId, setName, series, language, nationalPokedexNumber, nationalPokedexNumbers, sortNumber) " +
                    "VALUES " +
                    "('card-10', 'Ten', 'Pokémon', '10', 'set', 'Set', 'Series', 'de', 10, '10', 0), " +
                    "('card-2', 'Two', 'Pokémon', '2', 'set', 'Set', 'Series', 'de', 2, '2,3', 0), " +
                    "('card-svp', 'Promo', 'Pokémon', 'SVP 001', 'set', 'Set', 'Series', 'de', 25, '25,26', 0), " +
                    "('card-gg', 'Gallery', 'Pokémon', 'GG01', 'set', 'Set', 'Series', 'de', 1, '1', 0)"
            )
            execSQL("INSERT INTO last_update (id, lastUpdateTimestamp) VALUES (1, 1700000000000)")
            close()
        }

        val db16 = helper.runMigrationsAndValidate(TEST_DB, 16, true, *AppDatabase.ALL_MIGRATIONS)
        db16.query("SELECT id FROM cards ORDER BY sortNumber").use { cursor ->
            val ids = mutableListOf<String>()
            while (cursor.moveToNext()) ids += cursor.getString(0)
            assertEquals(listOf("card-2", "card-10", "card-gg", "card-svp"), ids)
        }
        db16.query("SELECT COUNT(*) FROM card_pokemon WHERE cardId = 'card-2'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Multi-Pokémon card must have both links", 2, cursor.getInt(0))
        }
        db16.query("SELECT COUNT(*) FROM card_pokemon WHERE cardId = 'card-svp'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Legacy primary dex ID and all listed IDs are retained", 2, cursor.getInt(0))
        }
        db16.query("SELECT lastUpdateTimestamp FROM last_update WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0L, cursor.getLong(0))
        }
    }

    @Test
    fun testAppDatabaseMigration16To17() {
        val db16 = helper.createDatabase(TEST_DB, 16).apply {
            execSQL(
                "INSERT INTO pokemon (id, dexId, number, name, searchName, cardType) " +
                "VALUES ('25', 25, '025', 'Pikachu', 'pikachu', 'Common'), " +
                "('1', 1, '001', 'Bisasam', 'bisasam', 'Nichts'), " +
                "('6', 6, '006', 'Glurak', 'glurak', 'Fullart'), " +
                "('999', 999, '999', 'Unbekannt', 'unbekannt', 'legacy-invalid')"
            )
            execSQL(
                "INSERT INTO sets (id, name, series, printedTotal, total, releaseDate, logoUrl, symbolUrl) " +
                    "VALUES ('xy1', 'XY', 'XY', 108, 108, '2014-02-05', 'https://assets.tcgdex.net/logo.png', 'https://assets.tcgdex.net/symbol.png')"
            )
            execSQL(
                "INSERT INTO cards (id, name, supertype, number, setId, setName, series, language, sortNumber) " +
                "VALUES ('xy1-detail-1', 'Detailkarte', 'Pokémon', '2', 'xy1', 'XY', 'XY', 'de', 2)"
            )
            execSQL(
                "INSERT INTO catalog_cards (id, localId, name, setId, imageUrl, language, updatedAt) " +
                "VALUES ('xy1-cat-1', '1', 'Pikachu', 'xy1', 'https://assets.tcgdex.net/xy1/1.png', 'de', 1234)"
            )
            // A catalog row with an existing detail ID must not overwrite rich card data.
            execSQL(
                "INSERT INTO catalog_cards (id, localId, name, setId, imageUrl, language, updatedAt) " +
                    "VALUES ('xy1-detail-1', '2', 'Catalog overwrite', 'xy1', 'https://assets.tcgdex.net/xy1/catalog.png', 'de', 5678)"
            )
            execSQL(
                "INSERT INTO user_cards (cardId, pokemonId, isPrimary, isCommon, isFullart, addedAt) " +
                    "VALUES ('xy1-detail-1', '25', 1, 1, 0, 1700000000000)"
            )
            close()
        }

        val db17 = helper.runMigrationsAndValidate(TEST_DB, 17, true, AppDatabase.MIGRATION_16_17)

        val pokemonCursor = db17.query("SELECT cardType FROM pokemon WHERE id = '25'")
        assertTrue(pokemonCursor.moveToFirst())
        assertEquals("COMMON", pokemonCursor.getString(0))
        pokemonCursor.close()

        val categoriesCursor = db17.query("SELECT cardType FROM pokemon ORDER BY dexId")
        assertTrue(categoriesCursor.moveToFirst())
        assertEquals("NONE", categoriesCursor.getString(0))
        assertTrue(categoriesCursor.moveToNext())
        assertEquals("FULLART", categoriesCursor.getString(0))
        assertTrue(categoriesCursor.moveToNext())
        assertEquals("COMMON", categoriesCursor.getString(0))
        assertTrue(categoriesCursor.moveToNext())
        assertEquals("NONE", categoriesCursor.getString(0))
        categoriesCursor.close()

        val cardCursor = db17.query("SELECT id, detailLoaded FROM cards WHERE id = 'xy1-cat-1'")
        assertTrue(cardCursor.moveToFirst())
        assertEquals("xy1-cat-1", cardCursor.getString(0))
        assertEquals(0, cardCursor.getInt(1))
        cardCursor.close()

        db17.query(
            "SELECT setName, series, setReleaseDate, setLogoUrl, setSymbolUrl, " +
                "setCardCountOfficial, setCardCountTotal, sortNumber FROM cards WHERE id = 'xy1-cat-1'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("XY", cursor.getString(0))
            assertEquals("XY", cursor.getString(1))
            assertEquals("2014-02-05", cursor.getString(2))
            assertEquals("https://assets.tcgdex.net/logo.png", cursor.getString(3))
            assertEquals("https://assets.tcgdex.net/symbol.png", cursor.getString(4))
            assertEquals(108, cursor.getInt(5))
            assertEquals(108, cursor.getInt(6))
            assertEquals(1, cursor.getInt(7))
        }

        db17.query("SELECT id FROM cards ORDER BY sortNumber").use { cursor ->
            val ids = mutableListOf<String>()
            while (cursor.moveToNext()) ids += cursor.getString(0)
            assertEquals(listOf("xy1-cat-1", "xy1-detail-1"), ids)
        }

        val detailCursor = db17.query("SELECT name, imageUrl, detailLoaded FROM cards WHERE id = 'xy1-detail-1'")
        assertTrue(detailCursor.moveToFirst())
        assertEquals("Detailkarte", detailCursor.getString(0))
        assertEquals(null, detailCursor.getString(1))
        assertEquals(1, detailCursor.getInt(2))
        detailCursor.close()

        // The migration must not alter the user's saved collection.
        db17.query(
            "SELECT cardId, pokemonId, isPrimary, isCommon, isFullart, addedAt FROM user_cards WHERE cardId = 'xy1-detail-1' AND pokemonId = '25'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("xy1-detail-1", cursor.getString(0))
            assertEquals("25", cursor.getString(1))
            assertEquals(1, cursor.getInt(2))
            assertEquals(1, cursor.getInt(3))
            assertEquals(0, cursor.getInt(4))
            assertEquals(1700000000000L, cursor.getLong(5))
        }

        // Room's declared default for newly inserted brief catalog rows is zero.
        db17.execSQL(
            "INSERT INTO cards (id, name, supertype, number, setId, setName, series, language) " +
                "VALUES ('new-brief-card', 'Brief card', '', '3', 'xy1', 'XY', 'XY', 'de')"
        )
        db17.query("SELECT detailLoaded FROM cards WHERE id = 'new-brief-card'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }

        val cardCountCursor = db17.query("SELECT COUNT(*) FROM cards")
        assertTrue(cardCountCursor.moveToFirst())
        assertEquals(3, cardCountCursor.getInt(0))
        cardCountCursor.close()

        val imageCoverageCursor = db17.query(
            "SELECT SUM(CASE WHEN imageUrl IS NOT NULL AND imageUrl != '' THEN 1 ELSE 0 END) FROM cards"
        )
        assertTrue(imageCoverageCursor.moveToFirst())
        assertEquals(1, imageCoverageCursor.getInt(0))
        imageCoverageCursor.close()

        db17.query("PRAGMA table_info(sync_checkpoint)").use { cursor ->
            val columns = mutableListOf<String>()
            while (cursor.moveToNext()) columns += cursor.getString(cursor.getColumnIndexOrThrow("name"))
            assertEquals(
                listOf("unit", "runId", "state", "attempts", "updatedAt", "error"),
                columns
            )
        }

        db17.execSQL(
            "INSERT INTO sync_checkpoint (unit, runId, state, attempts, updatedAt, error) " +
                "VALUES ('cards:de', 7, 'RUNNING', 1, 1700000000000, NULL)"
        )
        db17.query("SELECT runId, state, attempts FROM sync_checkpoint WHERE unit = 'cards:de'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(7, cursor.getInt(0))
            assertEquals("RUNNING", cursor.getString(1))
            assertEquals(1, cursor.getInt(2))
        }
        var duplicateCheckpointRejected = false
        try {
            db17.execSQL(
                "INSERT INTO sync_checkpoint (unit, runId, state, attempts, updatedAt, error) " +
                    "VALUES ('cards:de', 8, 'DONE', 2, 1700000000001, NULL)"
            )
        } catch (_: android.database.sqlite.SQLiteConstraintException) {
            duplicateCheckpointRejected = true
        }
        assertTrue("sync_checkpoint.unit must enforce primary-key uniqueness", duplicateCheckpointRejected)

        db17.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'catalog_cards'"
        ).use { cursor ->
            assertEquals("Legacy catalog_cards table must be removed", false, cursor.moveToFirst())
        }
    }

    @Test
    fun testRoomBuilderMigratesRealV14FixtureThroughLatestSchema() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "migration-v14-full-chain"
        context.deleteDatabase(databaseName)

        // Reconstructed v14 fixture: schema 15 with the MIGRATION_14_15 additions reversed
        // (sortNumber and the card_pokemon/pokemon_names tables and their indexes are absent).
        val db14 = helper.createDatabase(databaseName, 14)
        try {
            db14.execSQL(
                "INSERT INTO cards (id, name, supertype, number, setId, setName, series, language, nationalPokedexNumber, nationalPokedexNumbers) " +
                    "VALUES ('v14-card-2', 'Pikachu', 'Pokémon', '2', 'xy1', 'XY', 'XY', 'de', 25, '25,26')"
            )
            db14.execSQL(
                "INSERT INTO cards (id, name, supertype, number, setId, setName, series, language, nationalPokedexNumber, nationalPokedexNumbers) " +
                    "VALUES ('v14-card-10', 'Raichu', 'Pokémon', '10', 'xy1', 'XY', 'XY', 'de', 26, '26')"
            )
        } finally {
            db14.close()
        }

        val migratedDb = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .build()
        try {
            val targetDb = migratedDb.openHelper.writableDatabase
            targetDb.query("SELECT id, sortNumber, detailLoaded FROM cards ORDER BY sortNumber").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("v14-card-2", cursor.getString(0))
                assertEquals(1, cursor.getInt(1))
                assertEquals(1, cursor.getInt(2))
                assertTrue(cursor.moveToNext())
                assertEquals("v14-card-10", cursor.getString(0))
                assertEquals(2, cursor.getInt(1))
                assertTrue(!cursor.moveToNext())
            }
            targetDb.query("SELECT COUNT(*) FROM card_pokemon WHERE cardId = 'v14-card-2'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Multi-Pokémon card must retain both dex links", 2, cursor.getInt(0))
            }
        } finally {
            migratedDb.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun testV14OpenFailsWhenRequiredMigrationIsRemoved() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "migration-v14-missing-step"
        context.deleteDatabase(databaseName)
        helper.createDatabase(databaseName, 14).close()

        val migrationsWithout14To15 = AppDatabase.ALL_MIGRATIONS.filterNot { it.startVersion == 14 }.toTypedArray()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(*migrationsWithout14To15)
            .build()
        var failedAsExpected = false
        try {
            db.openHelper.writableDatabase
        } catch (expected: IllegalStateException) {
            failedAsExpected = expected.message.orEmpty().contains("migration", ignoreCase = true) ||
                expected.message.orEmpty().contains("14", ignoreCase = true)
        } finally {
            db.close()
            context.deleteDatabase(databaseName)
        }
        assertTrue("Removing migration 14→15 must make opening the v14 database fail", failedAsExpected)
    }

    @Test
    fun testUserDatabaseMigration1To2() {
        val userHelper = MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            UserDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory()
        )

        val db1 = userHelper.createDatabase("user-test-db", 1).apply {
            execSQL(
                "INSERT INTO user_cards (cardId, pokemonId, pokemonDexId, language, quantity, category, isPrimary, addedAt) " +
                "VALUES ('xy1-1', '25', 25, 'de', 1, 'COMMON', 1, 1700000000000)"
            )
            close()
        }

        val db2 = userHelper.runMigrationsAndValidate("user-test-db", 2, true, *UserDatabase.ALL_MIGRATIONS)
        db2.query("SELECT * FROM wishlist").use { cursor ->
            assertNotNull(cursor)
        }
        db2.query("SELECT cardId, pokemonId, pokemonDexId, language, quantity, category, isPrimary, addedAt FROM user_cards").use { cursor ->
            assertTrue("Existing user card must survive 1→2", cursor.moveToFirst())
            assertEquals("xy1-1", cursor.getString(0))
            assertEquals("25", cursor.getString(1))
            assertEquals(25, cursor.getInt(2))
            assertEquals("de", cursor.getString(3))
            assertEquals(1, cursor.getInt(4))
            assertEquals("COMMON", cursor.getString(5))
            assertEquals(1, cursor.getInt(6))
            assertEquals(1700000000000L, cursor.getLong(7))
            assertTrue("Fixture must contain exactly one user card", !cursor.moveToNext())
        }
    }

    @Test
    fun testLegacyToV2EndToEndMigrationFixture(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Isolate the fixture from the app's real database and migration preference flag.
        val fixtureContext = object : ContextWrapper(context) {
            override fun getDatabasePath(name: String): File = File(context.cacheDir, "migration-fixture-$name")
            override fun getSharedPreferences(name: String, mode: Int) =
                context.getSharedPreferences("instrumentation-$name", mode)
        }
        val oldDbFile = fixtureContext.getDatabasePath("tcg_sammlung_database")
        oldDbFile.parentFile?.mkdirs()
        if (oldDbFile.exists()) oldDbFile.delete()

        val sqliteDb = SQLiteDatabase.openOrCreateDatabase(oldDbFile, null)
        sqliteDb.execSQL(
            "CREATE TABLE pokemon (id TEXT PRIMARY KEY, dexId INTEGER, number TEXT, name TEXT, searchName TEXT, imageUrl TEXT, primaryCardImageUrl TEXT, cardType TEXT DEFAULT 'Nichts', cardsUpdatedAt INTEGER)"
        )
        sqliteDb.execSQL(
            "CREATE TABLE cards (id TEXT PRIMARY KEY, name TEXT, supertype TEXT, number TEXT, rarity TEXT, setId TEXT, setName TEXT, series TEXT, imageUrl TEXT, largeImageUrl TEXT, marketPrice REAL, lowPrice REAL, trendPrice REAL, nationalPokedexNumber INTEGER, subtypes TEXT, language TEXT, nationalPokedexNumbers TEXT, setLogoUrl TEXT, setSymbolUrl TEXT, setCardCountOfficial INTEGER, setCardCountTotal INTEGER, setReleaseDate TEXT)"
        )
        sqliteDb.execSQL(
            "CREATE TABLE user_cards (cardId TEXT, pokemonId TEXT, isPrimary INTEGER, isCommon INTEGER, isFullart INTEGER, addedAt INTEGER, PRIMARY KEY(cardId, pokemonId))"
        )

        sqliteDb.execSQL("INSERT INTO pokemon (id, dexId, cardType) VALUES ('25', 25, 'Common'), ('6', 6, 'Fullart'), ('1', 1, 'Nichts'), ('150', 150, 'Nichts'), ('384', 384, 'Nichts')")
        sqliteDb.execSQL("INSERT INTO cards (id, language) VALUES ('c1', 'de'), ('c2', 'de'), ('c3', 'en'), ('c4', 'ja'), ('c5', 'fr')")

        // c1 is the favourite (primary) card; c2 is a full-art card; c3-c5 are ordinary cards.
        sqliteDb.execSQL("INSERT INTO user_cards VALUES ('c1', '25', 1, 1, 0, 1000)")
        sqliteDb.execSQL("INSERT INTO user_cards VALUES ('c2', '6', 0, 0, 1, 2000)")
        sqliteDb.execSQL("INSERT INTO user_cards VALUES ('c3', '1', 0, 0, 0, 3000)")
        sqliteDb.execSQL("INSERT INTO user_cards VALUES ('c4', '150', 0, 0, 0, 4000)")
        sqliteDb.execSQL("INSERT INTO user_cards VALUES ('c5', '384', 0, 0, 0, 5000)")
        sqliteDb.close()

        val expectedLegacyRows = listOf(
            listOf("c1", "25", "1", "1", "0", "1000"),
            listOf("c2", "6", "0", "0", "1", "2000"),
            listOf("c3", "1", "0", "0", "0", "3000"),
            listOf("c4", "150", "0", "0", "0", "4000"),
            listOf("c5", "384", "0", "0", "0", "5000")
        )

        context.deleteDatabase("test_user_db_e2e")
        val userDb = Room.databaseBuilder(context, UserDatabase::class.java, "test_user_db_e2e")
            .build()

        userDb.userCardDao().deleteAll()

        fixtureContext.getSharedPreferences("user_migration_prefs", 0).edit().clear().commit()

        val result = UserMigrationHelper.migrateLegacyUserCardsIfNeeded(fixtureContext, userDb)
        assertEquals(UserMigrationHelper.MigrationResult.Success(5, 5), result)

        val migrated = userDb.userCardDao().getAllUserCardsOnce()
        assertEquals(5, migrated.size)
        assertEquals(5, userDb.userCardDao().getUserCardCount())

        val c1 = migrated.find { it.cardId == "c1" }
        assertEquals("COMMON", c1?.category)
        assertEquals("de", c1?.language)
        assertEquals(25, c1?.pokemonDexId)
        assertEquals(true, c1?.isPrimary)

        val c2 = migrated.find { it.cardId == "c2" }
        assertEquals("FULLART", c2?.category)
        assertEquals("de", c2?.language)
        assertEquals(6, c2?.pokemonDexId)
        assertEquals(false, c2?.isPrimary)

        val c3 = migrated.find { it.cardId == "c3" }
        assertEquals("NONE", c3?.category)
        assertEquals("en", c3?.language)
        assertEquals(1, c3?.pokemonDexId)

        val c4 = migrated.find { it.cardId == "c4" }
        assertEquals("NONE", c4?.category)
        assertEquals("ja", c4?.language)
        assertEquals(150, c4?.pokemonDexId)

        val c5 = migrated.find { it.cardId == "c5" }
        assertEquals("NONE", c5?.category)
        assertEquals("fr", c5?.language)
        assertEquals(384, c5?.pokemonDexId)

        val secondResult = UserMigrationHelper.migrateLegacyUserCardsIfNeeded(fixtureContext, userDb)
        assertEquals(UserMigrationHelper.MigrationResult.AlreadyCompleted, secondResult)
        assertEquals("Repeated migration must not insert or change rows", migrated, userDb.userCardDao().getAllUserCardsOnce())

        SQLiteDatabase.openDatabase(oldDbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { legacyAfter ->
            legacyAfter.rawQuery(
                "SELECT cardId, pokemonId, isPrimary, isCommon, isFullart, addedAt FROM user_cards ORDER BY cardId",
                null
            ).use { cursor ->
                val rows = mutableListOf<List<String>>()
                while (cursor.moveToNext()) {
                    rows += listOf(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getInt(2).toString(),
                        cursor.getInt(3).toString(),
                        cursor.getInt(4).toString(),
                        cursor.getLong(5).toString()
                    )
                }
                assertEquals("Legacy source must remain byte-for-byte unchanged", expectedLegacyRows, rows)
            }
        }
        userDb.close()
        context.deleteDatabase("test_user_db_e2e")
        oldDbFile.delete()
        fixtureContext.getSharedPreferences("user_migration_prefs", 0).edit().clear().commit()
    }
}
