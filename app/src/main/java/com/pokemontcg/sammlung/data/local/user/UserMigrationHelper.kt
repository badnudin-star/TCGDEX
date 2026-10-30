package com.pokemontcg.sammlung.data.local.user

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.pokemontcg.sammlung.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.room.withTransaction

object UserMigrationHelper {

    private const val PREFS_NAME = "user_migration_prefs"
    private const val KEY_MIGRATION_DONE = "user_cards_v1_migrated"

    suspend fun migrateLegacyUserCardsIfNeeded(
        context: Context,
        userDb: UserDatabase,
        oldAppDatabase: AppDatabase? = null
    ): MigrationResult = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_MIGRATION_DONE, false)) {
            return@withContext MigrationResult.AlreadyCompleted
        }

        com.pokemontcg.sammlung.util.AppLogger.log("UserMigrationHelper", "Migration der Legacy-Sammlung gestartet")

        val oldDbFile = context.getDatabasePath("tcg_sammlung_database")
        if (!oldDbFile.exists()) {
            com.pokemontcg.sammlung.util.AppLogger.log("UserMigrationHelper", "Keine Legacy-Datenbank gefunden.")
            prefs.edit().putBoolean(KEY_MIGRATION_DONE, true).apply()
            return@withContext MigrationResult.NoLegacyDatabaseFound
        }

        var legacyCardsCount = 0
        var migratedCount = 0

        try {
            val legacyUserCards = mutableListOf<LegacyUserCardRecord>()
            val pokemonTypes = mutableMapOf<String, String>()
            val pokemonDexIds = mutableMapOf<String, Int>()
            val cardLanguages = mutableMapOf<String, String>()

            val db = SQLiteDatabase.openDatabase(
                oldDbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )

            db.use { sqliteDb ->
                // Read pokemon dexId & cardType
                sqliteDb.rawQuery("SELECT id, dexId, cardType FROM pokemon", null).use { cursor ->
                    val idIdx = cursor.getColumnIndex("id")
                    val dexIdx = cursor.getColumnIndex("dexId")
                    val typeIdx = cursor.getColumnIndex("cardType")
                    while (cursor.moveToNext()) {
                        if (idIdx != -1) {
                            val pId = cursor.getString(idIdx)
                            if (pId != null) {
                                if (dexIdx != -1) pokemonDexIds[pId] = cursor.getInt(dexIdx)
                                if (typeIdx != -1) cursor.getString(typeIdx)?.let { pokemonTypes[pId] = it }
                            }
                        }
                    }
                }

                // Read card languages
                sqliteDb.rawQuery("SELECT id, language FROM cards", null).use { cursor ->
                    val idIdx = cursor.getColumnIndex("id")
                    val langIdx = cursor.getColumnIndex("language")
                    while (cursor.moveToNext()) {
                        if (idIdx != -1 && langIdx != -1) {
                            val cId = cursor.getString(idIdx)
                            val lang = cursor.getString(langIdx)
                            if (cId != null && lang != null) {
                                cardLanguages[cId] = lang
                            }
                        }
                    }
                }

                // Read user_cards
                sqliteDb.rawQuery(
                    "SELECT cardId, pokemonId, isPrimary, isCommon, isFullart, addedAt FROM user_cards",
                    null
                ).use { cursor ->
                    val cardIdIdx = cursor.getColumnIndex("cardId")
                    val pokemonIdIdx = cursor.getColumnIndex("pokemonId")
                    val isPrimaryIdx = cursor.getColumnIndex("isPrimary")
                    val isCommonIdx = cursor.getColumnIndex("isCommon")
                    val isFullartIdx = cursor.getColumnIndex("isFullart")
                    val addedAtIdx = cursor.getColumnIndex("addedAt")

                    while (cursor.moveToNext()) {
                        if (cardIdIdx != -1 && pokemonIdIdx != -1) {
                            val cardId = cursor.getString(cardIdIdx)
                            val pokemonId = cursor.getString(pokemonIdIdx)
                            val isPrimary = if (isPrimaryIdx != -1) cursor.getInt(isPrimaryIdx) == 1 else false
                            val isCommon = if (isCommonIdx != -1) cursor.getInt(isCommonIdx) == 1 else false
                            val isFullart = if (isFullartIdx != -1) cursor.getInt(isFullartIdx) == 1 else false
                            val addedAt = if (addedAtIdx != -1) cursor.getLong(addedAtIdx) else System.currentTimeMillis()

                            if (cardId != null && pokemonId != null) {
                                legacyUserCards.add(
                                    LegacyUserCardRecord(
                                        cardId = cardId,
                                        pokemonId = pokemonId,
                                        isPrimary = isPrimary,
                                        isCommon = isCommon,
                                        isFullart = isFullart,
                                        addedAt = addedAt
                                    )
                                )
                            }
                        }
                    }
                }
            }

            legacyCardsCount = legacyUserCards.size
            if (legacyCardsCount == 0) {
                prefs.edit().putBoolean(KEY_MIGRATION_DONE, true).apply()
                return@withContext MigrationResult.Success(0, 0)
            }

            val newEntities = legacyUserCards.map { old ->
                val category = deriveCategory(
                    isCommon = old.isCommon,
                    isFullart = old.isFullart,
                    pokemonCardType = pokemonTypes[old.pokemonId]
                )
                val dexId = pokemonDexIds[old.pokemonId] ?: old.pokemonId.toIntOrNull() ?: 0
                val lang = cardLanguages[old.cardId] ?: "de"

                UserCardEntityV2(
                    cardId = old.cardId,
                    pokemonId = old.pokemonId,
                    pokemonDexId = dexId,
                    language = lang,
                    quantity = 1,
                    category = category.name,
                    isPrimary = old.isPrimary,
                    addedAt = old.addedAt
                )
            }

            // A legacy row must map to one distinct (cardId, pokemonId) pair.
            // Detect duplicates before writing anything: otherwise a failed verification
            // could leave duplicate V2 rows behind while the legacy database remains intact.
            val expectedPairs = legacyUserCards.map { it.cardId to it.pokemonId }
            if (expectedPairs.toSet().size != legacyCardsCount) {
                return@withContext MigrationResult.Error(
                    "Legacy-Daten enthalten doppelte (cardId, pokemonId)-Paare. Es wurde nichts migriert; bitte Legacy-Export sichern."
                )
            }
            val expectedPairSet = expectedPairs.toSet()

            // Merge and verify in a single transaction. If either count or pair-set
            // verification fails, throwing rolls back every write from this attempt.
            val migrated = userDb.withTransaction {
                val before = userDb.userCardDao().getAllUserCardsOnce()
                val beforePairs = before.map { it.cardId to it.pokemonId }.toSet()
                val missing = newEntities.filter { (it.cardId to it.pokemonId) !in beforePairs }
                val inserted = if (missing.isEmpty()) 0 else userDb.userCardDao().insertAll(missing).size
                val after = userDb.userCardDao().getAllUserCardsOnce()
                val rowsForLegacyPairs = after.filter { (it.cardId to it.pokemonId) in expectedPairSet }
                val afterPairs = rowsForLegacyPairs.map { it.cardId to it.pokemonId }.toSet()
                val samePairSet = afterPairs == expectedPairSet
                val sameRowCount = rowsForLegacyPairs.size == legacyCardsCount
                if (!samePairSet || !sameRowCount) {
                    throw IllegalStateException(
                        "Verifikation fehlgeschlagen: Legacy-Zeilen=$legacyCardsCount, " +
                            "Zielzeilen=${rowsForLegacyPairs.size}, Paare=${afterPairs.size}/${expectedPairSet.size}"
                    )
                }
                inserted
            }
            migratedCount = migrated
            com.pokemontcg.sammlung.util.AppLogger.log(
                "UserMigrationHelper",
                "Migration verifiziert: $legacyCardsCount Legacy-Zeilen und identische Paarmenge; $migratedCount neu eingefügt"
            )
            // This flag is only written after transactional row-count and pair-set verification.
            prefs.edit().putBoolean(KEY_MIGRATION_DONE, true).apply()
            MigrationResult.Success(legacyCardsCount, migratedCount)
        } catch (e: Exception) {
            com.pokemontcg.sammlung.util.AppLogger.log("UserMigrationHelper", "Migration Ausnahme-Fehler: ${e.message}")
            MigrationResult.Error(e.message ?: "Unknown migration error")
        }
    }

    /** Exports the untouched legacy user_cards table to CSV when migration needs recovery. */
    suspend fun exportLegacyCsv(context: Context): String = withContext(Dispatchers.IO) {
        val oldDbFile = context.getDatabasePath("tcg_sammlung_database")
        require(oldDbFile.exists()) { "Keine Legacy-Datenbank gefunden" }
        val rows = mutableListOf<List<String>>()
        SQLiteDatabase.openDatabase(oldDbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery(
                "SELECT cardId, pokemonId, isPrimary, isCommon, isFullart, addedAt FROM user_cards",
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    rows += listOf(
                        cursor.getString(0).orEmpty(),
                        cursor.getString(1).orEmpty(),
                        cursor.getInt(2).toString(),
                        cursor.getInt(3).toString(),
                        cursor.getInt(4).toString(),
                        cursor.getLong(5).toString()
                    )
                }
            }
        }
        val header = listOf("cardId", "pokemonId", "isPrimary", "isCommon", "isFullart", "addedAt")
        (listOf(header) + rows).joinToString("\n") { row ->
            row.joinToString(",") { value -> "\"${value.replace("\"", "\"\"")}\"" }
        }
    }

    fun deriveCategory(
        isCommon: Boolean,
        isFullart: Boolean,
        pokemonCardType: String?
    ): CardCategory {
        return when {
            isCommon -> CardCategory.COMMON
            isFullart -> CardCategory.FULLART
            pokemonCardType.equals("Common", ignoreCase = true) -> CardCategory.COMMON
            pokemonCardType.equals("Fullart", ignoreCase = true) -> CardCategory.FULLART
            else -> CardCategory.NONE
        }
    }

    private data class LegacyUserCardRecord(
        val cardId: String,
        val pokemonId: String,
        val isPrimary: Boolean,
        val isCommon: Boolean,
        val isFullart: Boolean,
        val addedAt: Long
    )

    sealed class MigrationResult {
        object AlreadyCompleted : MigrationResult()
        object NoLegacyDatabaseFound : MigrationResult()
        data class Success(val legacyCount: Int, val migratedCount: Int) : MigrationResult()
        data class Error(val message: String) : MigrationResult()
    }
}
