package com.pokemontcg.sammlung.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pokemontcg.sammlung.data.local.converters.CardCategoryConverter
import com.pokemontcg.sammlung.data.local.dao.*
import com.pokemontcg.sammlung.data.local.entities.*

@Database(
    entities = [
        PokemonEntity::class,
        SetEntity::class,
        CardEntity::class,
        UserCardEntity::class,
        LastUpdateEntity::class,
        SourceAuditEntity::class,
        CardPokemonEntity::class,
        PokemonNameEntity::class,
        SyncCheckpointEntity::class,
    ],
    version = 17,
    exportSchema = true
)
@TypeConverters(CardCategoryConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pokemonDao(): PokemonDao
    abstract fun setDao(): SetDao
    abstract fun cardDao(): CardDao
    abstract fun userCardDao(): UserCardDao
    abstract fun lastUpdateDao(): LastUpdateDao
    abstract fun sourceAuditDao(): SourceAuditDao
    abstract fun cardPokemonDao(): CardPokemonDao
    abstract fun pokemonNameDao(): PokemonNameDao
    abstract fun syncCheckpointDao(): SyncCheckpointDao

    companion object {
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pokemon ADD COLUMN cardType TEXT NOT NULL DEFAULT 'Nichts'")
                db.execSQL("ALTER TABLE cards ADD COLUMN nationalPokedexNumbers TEXT")
                db.execSQL("ALTER TABLE cards ADD COLUMN setLogoUrl TEXT")
                db.execSQL("ALTER TABLE cards ADD COLUMN setSymbolUrl TEXT")
                db.execSQL("ALTER TABLE cards ADD COLUMN setCardCountOfficial INTEGER")
                db.execSQL("ALTER TABLE cards ADD COLUMN setCardCountTotal INTEGER")
                db.execSQL("ALTER TABLE cards ADD COLUMN setReleaseDate TEXT")
                db.execSQL("UPDATE pokemon SET cardType = 'Common' WHERE id IN (SELECT pokemonId FROM user_cards WHERE isCommon = 1)")
                db.execSQL("UPDATE pokemon SET cardType = 'Fullart' WHERE cardType = 'Nichts' AND id IN (SELECT pokemonId FROM user_cards WHERE isFullart = 1)")
                db.execSQL("UPDATE cards SET imageUrl = imageUrl || '/high.png' WHERE imageUrl LIKE 'https://assets.tcgdex.net/%' AND imageUrl NOT LIKE '%.png' AND imageUrl NOT LIKE '%.webp' AND imageUrl NOT LIKE '%.jpg'")
                db.execSQL("UPDATE cards SET largeImageUrl = largeImageUrl || '/high.png' WHERE largeImageUrl LIKE 'https://assets.tcgdex.net/%' AND largeImageUrl NOT LIKE '%.png' AND largeImageUrl NOT LIKE '%.webp' AND largeImageUrl NOT LIKE '%.jpg'")
                db.execSQL("UPDATE pokemon SET primaryCardImageUrl = primaryCardImageUrl || '/high.png' WHERE primaryCardImageUrl LIKE 'https://assets.tcgdex.net/%' AND primaryCardImageUrl NOT LIKE '%.png' AND primaryCardImageUrl NOT LIKE '%.webp' AND primaryCardImageUrl NOT LIKE '%.jpg'")
                db.execSQL("UPDATE last_update SET lastUpdateTimestamp = 0 WHERE id = 1")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS catalog_cards (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "localId TEXT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "setId TEXT NOT NULL, " +
                        "imageUrl TEXT, " +
                        "language TEXT NOT NULL, " +
                        "updatedAt INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_catalog_cards_setId ON catalog_cards(setId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_catalog_cards_name ON catalog_cards(name)")
                db.execSQL("ALTER TABLE pokemon ADD COLUMN cardsUpdatedAt INTEGER")
                db.execSQL("UPDATE last_update SET lastUpdateTimestamp = 0 WHERE id = 1")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE last_update SET lastUpdateTimestamp = 0 WHERE id = 1")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sets ADD COLUMN logoUrl TEXT")
                db.execSQL("ALTER TABLE sets ADD COLUMN symbolUrl TEXT")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE last_update SET lastUpdateTimestamp = 0 WHERE id = 1")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE last_update SET lastUpdateTimestamp = 0 WHERE id = 1")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS source_audit (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "sourceName TEXT NOT NULL, " +
                        "language TEXT NOT NULL, " +
                        "sourceUrl TEXT NOT NULL, " +
                        "checkedAt INTEGER NOT NULL, " +
                        "success INTEGER NOT NULL, " +
                        "discoveredSets INTEGER NOT NULL, " +
                        "matchedSets INTEGER NOT NULL, " +
                        "missingSets INTEGER NOT NULL, " +
                        "discoveredCards INTEGER NOT NULL, " +
                        "cardsWithImages INTEGER NOT NULL, " +
                        "note TEXT NOT NULL DEFAULT ''" +
                    ")"
                )
                db.execSQL("UPDATE last_update SET lastUpdateTimestamp = 0 WHERE id = 1")
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE source_audit ADD COLUMN expectedCards INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE source_audit ADD COLUMN indexedCards INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE source_audit ADD COLUMN missingCards INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE source_audit ADD COLUMN cardsMissingImages INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE last_update SET lastUpdateTimestamp = 0 WHERE id = 1")
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS card_pokemon (" +
                        "cardId TEXT NOT NULL, " +
                        "dexId INTEGER NOT NULL, " +
                        "PRIMARY KEY(cardId, dexId))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_card_pokemon_dexId ON card_pokemon(dexId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_card_pokemon_cardId ON card_pokemon(cardId)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS pokemon_names (" +
                        "language TEXT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "dexId INTEGER NOT NULL, " +
                        "PRIMARY KEY(language, name))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pokemon_names_language_name ON pokemon_names(language, name)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pokemon_names_dexId ON pokemon_names(dexId)")

                db.execSQL("CREATE INDEX IF NOT EXISTS index_cards_setId ON cards(setId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cards_language_setId ON cards(language, setId)")

                db.execSQL("ALTER TABLE cards ADD COLUMN sortNumber INTEGER NOT NULL DEFAULT 0")

                db.execSQL("INSERT OR REPLACE INTO card_pokemon (cardId, dexId) SELECT id, nationalPokedexNumber FROM cards WHERE nationalPokedexNumber IS NOT NULL AND nationalPokedexNumber > 0")
            }
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE last_update SET lastUpdateTimestamp = 0 WHERE id = 1")

                data class ExistingCard(
                    val id: String,
                    val number: String,
                    val dexIds: List<Int>,
                    val prefix: String,
                    val numericPart: Int,
                    val suffix: String,
                )

                val cards = mutableListOf<ExistingCard>()
                val cursor = db.query("SELECT id, number, nationalPokedexNumber, nationalPokedexNumbers FROM cards")
                try {
                    val numberPattern = Regex("^\\s*([A-Za-z]+)?\\s*(\\d+)")
                    while (cursor.moveToNext()) {
                        val cardId = cursor.getString(0)
                        val number = cursor.getString(1).orEmpty().trim()
                        val firstDex = if (cursor.isNull(2)) null else cursor.getInt(2)
                        val multiDex = if (cursor.isNull(3)) null else cursor.getString(3)
                        val match = numberPattern.find(number)
                        val prefix = match?.groups?.get(1)?.value.orEmpty().uppercase()
                        val numericPart = match?.groups?.get(2)?.value?.toIntOrNull() ?: Int.MAX_VALUE
                        val suffix = if (match != null) number.substring(match.range.last + 1).trim().uppercase() else number.uppercase()
                        val dexIds = buildList {
                            if (firstDex != null && firstDex > 0) add(firstDex)
                            multiDex.orEmpty().split(',').mapNotNullTo(this) { it.trim().toIntOrNull()?.takeIf { dex -> dex > 0 } }
                        }.distinct()
                        cards += ExistingCard(cardId, number, dexIds, prefix, numericPart, suffix)
                    }
                } finally {
                    cursor.close()
                }

                // Assign deterministic ordinal sort keys: ordinary numeric cards first,
                // then alphanumeric/promotional prefixes alphabetically, then card number.
                val sortedCards = cards.sortedWith(
                    compareBy<ExistingCard> { if (it.prefix.isEmpty()) 0 else 1 }
                        .thenBy { if (it.prefix.isEmpty()) "" else it.prefix }
                        .thenBy { it.numericPart }
                        .thenBy { it.suffix }
                        .thenBy { it.id }
                )
                sortedCards.forEachIndexed { index, card ->
                    db.execSQL("UPDATE cards SET sortNumber = ? WHERE id = ?", arrayOf(index + 1, card.id))
                    card.dexIds.forEach { dexId ->
                        db.execSQL(
                            "INSERT OR IGNORE INTO card_pokemon (cardId, dexId) VALUES (?, ?)",
                            arrayOf(card.id, dexId)
                        )
                    }
                }
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS pokemon_new (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "dexId INTEGER NOT NULL, " +
                        "number TEXT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "searchName TEXT NOT NULL, " +
                        "imageUrl TEXT, " +
                        "primaryCardImageUrl TEXT, " +
                        "cardType TEXT NOT NULL DEFAULT 'NONE', " +
                        "cardsUpdatedAt INTEGER)"
                )
                db.execSQL(
                    "INSERT INTO pokemon_new SELECT id, dexId, number, name, searchName, imageUrl, primaryCardImageUrl, " +
                        "CASE cardType WHEN 'Nichts' THEN 'NONE' WHEN 'Common' THEN 'COMMON' WHEN 'Fullart' THEN 'FULLART' ELSE 'NONE' END, " +
                        "cardsUpdatedAt FROM pokemon"
                )
                db.execSQL("DROP TABLE pokemon")
                db.execSQL("ALTER TABLE pokemon_new RENAME TO pokemon")

                db.execSQL("ALTER TABLE cards ADD COLUMN detailLoaded INTEGER NOT NULL DEFAULT 0")
                // Rows already present in cards contain full detail data; only imported catalog rows are brief.
                db.execSQL("UPDATE cards SET detailLoaded = 1")

                db.execSQL(
                    "INSERT OR IGNORE INTO cards (" +
                        "id, name, supertype, number, rarity, setId, setName, series, " +
                        "imageUrl, largeImageUrl, marketPrice, lowPrice, trendPrice, " +
                        "nationalPokedexNumber, subtypes, language, nationalPokedexNumbers, " +
                        "setLogoUrl, setSymbolUrl, setCardCountOfficial, setCardCountTotal, " +
                        "setReleaseDate, sortNumber, detailLoaded) " +
                        "SELECT cc.id, cc.name, '', cc.localId, NULL, cc.setId, " +
                        "COALESCE(s.name, ''), COALESCE(s.series, ''), " +
                        "cc.imageUrl, NULL, NULL, NULL, NULL, " +
                        "NULL, NULL, cc.language, NULL, " +
                        "s.logoUrl, s.symbolUrl, s.printedTotal, s.total, " +
                        "s.releaseDate, 0, 0 " +
                        "FROM catalog_cards cc LEFT JOIN sets s ON s.id = cc.setId"
                )
                db.execSQL("DROP TABLE IF EXISTS catalog_cards")

                // Recompute one deterministic ordinal for every card so brief catalog
                // rows and fully loaded rows share the same stable sort order.
                val sortableCards = mutableListOf<CardSortOrder.Entry>()
                db.query("SELECT id, number FROM cards").use { cursor ->
                    while (cursor.moveToNext()) {
                        sortableCards += CardSortOrder.Entry(
                            id = cursor.getString(0),
                            number = cursor.getString(1).orEmpty()
                        )
                    }
                }
                CardSortOrder.sortedIds(sortableCards).forEachIndexed { index, id ->
                    db.execSQL(
                        "UPDATE cards SET sortNumber = ? WHERE id = ?",
                        arrayOf(index + 1, id)
                    )
                }

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS sync_checkpoint (" +
                        "unit TEXT NOT NULL PRIMARY KEY, " +
                        "runId INTEGER NOT NULL, " +
                        "state TEXT NOT NULL, " +
                        "attempts INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL, " +
                        "error TEXT)"
                )
            }
        }

        /** The exact migration chain used by production and instrumentation tests. */
        val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_10_11,
            MIGRATION_11_12,
            MIGRATION_12_13,
            MIGRATION_13_14,
            MIGRATION_14_15,
            MIGRATION_15_16,
            MIGRATION_16_17
        )

        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val builder = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tcg_sammlung_database"
                )
                    .addMigrations(*ALL_MIGRATIONS)

                val instance = builder.build()
                INSTANCE = instance
                instance
            }
        }
    }
}
