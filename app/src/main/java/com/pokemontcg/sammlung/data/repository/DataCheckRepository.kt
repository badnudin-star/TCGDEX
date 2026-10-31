package com.pokemontcg.sammlung.data.repository

import com.pokemontcg.sammlung.data.local.AppDatabase
import com.pokemontcg.sammlung.data.local.entities.SourceAuditEntity
import com.pokemontcg.sammlung.data.local.user.UserDatabase
import com.pokemontcg.sammlung.data.preferences.AppPreferencesRepository
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class SystemDataMetrics(
    val setsCount: Int,
    val totalCatalogCards: Int,
    val totalCards: Int,
    val pokemonCount: Int,
    val cardsWithoutImagesCount: Int,
    val cardsWithoutPriceCount: Int,
    val userCardsCount: Int,
    val uniqueUserCardsCount: Int,
    val wishlistCount: Int,
    val priceHistoryCount: Int,
    val appDbVersion: Int = 17,
    val userDbVersion: Int = 2,
    val unassignedUserCardsCount: Int = 0,
    val appDbIntegrityOk: Boolean = true,
    val userDbIntegrityOk: Boolean = true,
    val checkedAt: Long = System.currentTimeMillis(),
    val cardsByLanguage: Map<String, Int> = emptyMap(),
    val lastUpdateAt: Long = 0L,
    val lastSourceCheckAt: Long = 0L,
    val migrationStatus: String = "Unbekannt",
    val catalogRequiresAppUpdate: Boolean = false,
    val catalogManifestSchemaVersion: Int = -1,
    val catalogInstalledVersion: String? = null
)

class DataCheckRepository(
    private val appDb: AppDatabase,
    private val userDb: UserDatabase,
    private val collectionRepository: CollectionRepository,
    private val preferencesRepository: AppPreferencesRepository,
    private val context: Context
) {

    suspend fun getSourceAudits(): List<SourceAuditEntity> = withContext(Dispatchers.IO) {
        appDb.sourceAuditDao().getSourceAuditsOnce()
    }

    suspend fun getLastDataCheckResult(): String? = preferencesRepository.lastDataCheckResultFlow.first()

    suspend fun calculateMetrics(): SystemDataMetrics = withContext(Dispatchers.IO) {
        val sets = appDb.setDao().getAllSets().first().size
        val pokemon = appDb.pokemonDao().getAllPokemon().first().size
        val allCards = appDb.cardDao().getAllCardsOnce()
        val totalCards = allCards.size
        val catalogCards = totalCards
        val cardsWithoutImages = allCards.count { it.imageUrl.isNullOrBlank() }
        val cardsWithoutPrice = allCards.count { it.marketPrice == null || it.marketPrice <= 0.0 }
        val cardsByLanguage = DataCheckCalculations.countCardsByLanguage(allCards.map { it.language })
        val lastUpdateAt = appDb.lastUpdateDao().getLastUpdate()?.lastUpdateTimestamp ?: 0L
        val lastSourceCheckAt = appDb.sourceAuditDao().getSourceAuditsOnce().maxOfOrNull { it.checkedAt } ?: 0L
        val migrationDone = context.getSharedPreferences("user_migration_prefs", Context.MODE_PRIVATE)
            .getBoolean("user_cards_v1_migrated", false)
        val catalogRequiresAppUpdate = preferencesRepository.catalogRequiresAppUpdateFlow.first()
        val catalogManifestSchemaVersion = preferencesRepository.catalogManifestSchemaVersionFlow.first()
        val catalogInstalledVersion = preferencesRepository.catalogInstalledVersionFlow.first()

        val userCards = collectionRepository.getAllUserCardsOnce()
        val userCardsCount = userCards.sumOf { it.quantity }
        val uniqueUserCardsCount = userCards.size
        val wishlistCount = userDb.wishlistDao().getWishlistOnce().size
        val priceHistoryCount = userDb.priceHistoryDao().getAllHistoryOnce().size

        val validCardIds = allCards.map { it.id }.toSet()
        val unassignedCount = DataCheckCalculations.countUnassigned(userCards.map { it.cardId }, validCardIds)

        SystemDataMetrics(
            setsCount = sets,
            totalCatalogCards = catalogCards,
            totalCards = totalCards,
            pokemonCount = pokemon,
            cardsWithoutImagesCount = cardsWithoutImages,
            cardsWithoutPriceCount = cardsWithoutPrice,
            userCardsCount = userCardsCount,
            uniqueUserCardsCount = uniqueUserCardsCount,
            wishlistCount = wishlistCount,
            priceHistoryCount = priceHistoryCount,
            unassignedUserCardsCount = unassignedCount,
            cardsByLanguage = cardsByLanguage,
            lastUpdateAt = lastUpdateAt,
            lastSourceCheckAt = lastSourceCheckAt,
            migrationStatus = if (migrationDone) "Abgeschlossen" else "Nicht bestätigt",
            catalogRequiresAppUpdate = catalogRequiresAppUpdate,
            catalogManifestSchemaVersion = catalogManifestSchemaVersion,
            catalogInstalledVersion = catalogInstalledVersion
        )
    }

    suspend fun runIntegrityCheck(): Pair<Boolean, Boolean> = withContext(Dispatchers.IO) {
        var appDbOk = false
        var userDbOk = false

        try {
            val appCursor = appDb.openHelper.writableDatabase.query("PRAGMA integrity_check")
            if (appCursor.moveToFirst()) {
                val result = appCursor.getString(0)
                appDbOk = result.equals("ok", ignoreCase = true)
            }
            appCursor.close()
        } catch (_: Exception) {}

        try {
            val userCursor = userDb.openHelper.writableDatabase.query("PRAGMA integrity_check")
            if (userCursor.moveToFirst()) {
                val result = userCursor.getString(0)
                userDbOk = result.equals("ok", ignoreCase = true)
            }
            userCursor.close()
        } catch (_: Exception) {}

        val timestamp = System.currentTimeMillis()
        val currentMetrics = calculateMetrics()
        val persisted = listOf(
            "checkedAt=$timestamp",
            "appDbIntegrityOk=$appDbOk",
            "userDbIntegrityOk=$userDbOk",
            "unassignedUserCardsCount=${currentMetrics.unassignedUserCardsCount}"
        ).joinToString(";")
        preferencesRepository.setLastDataCheckResult(persisted)
        Pair(appDbOk, userDbOk)
    }
}
