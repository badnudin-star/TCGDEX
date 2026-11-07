package com.pokemontcg.sammlung.data.repository

import com.pokemontcg.sammlung.data.export.CollectionExportData
import com.pokemontcg.sammlung.data.export.CollectionExporter
import com.pokemontcg.sammlung.data.export.ExportMergeStrategy
import com.pokemontcg.sammlung.data.export.ImportResult
import com.pokemontcg.sammlung.data.local.entities.CardEntity
import com.pokemontcg.sammlung.data.local.user.UserDatabase
import com.pokemontcg.sammlung.data.local.user.UserCardEntityV2
import com.pokemontcg.sammlung.data.local.user.CardCategory
import com.pokemontcg.sammlung.data.local.user.WishlistEntity
import kotlinx.coroutines.flow.Flow

class CollectionRepository(private val userDb: UserDatabase) {

    fun getAllUserCards(): Flow<List<UserCardEntityV2>> =
        userDb.userCardDao().getAllUserCards()

    suspend fun getAllUserCardsOnce(): List<UserCardEntityV2> =
        userDb.userCardDao().getAllUserCardsOnce()

    fun getUserCardsForPokemon(dexId: Int, pokemonIdStr: String): Flow<List<UserCardEntityV2>> =
        userDb.userCardDao().getUserCardsForPokemon(dexId, pokemonIdStr)

    fun getPrimaryCard(dexId: Int, pokemonIdStr: String): Flow<UserCardEntityV2?> =
        userDb.userCardDao().getPrimaryCard(dexId, pokemonIdStr)

    fun getWishlist(): Flow<List<WishlistEntity>> =
        userDb.wishlistDao().getWishlist()

    suspend fun getWishlistOnce(): List<WishlistEntity> =
        userDb.wishlistDao().getWishlistOnce()

    suspend fun addUserCard(card: UserCardEntityV2): Long =
        userDb.userCardDao().insertUserCard(card)

    suspend fun updateUserCard(card: UserCardEntityV2) =
        userDb.userCardDao().updateUserCard(card)

    suspend fun removeUserCard(cardId: String, dexId: Int, pokemonIdStr: String) =
        userDb.userCardDao().deleteUserCard(cardId, dexId, pokemonIdStr)

    suspend fun clearPrimary(dexId: Int, pokemonIdStr: String) =
        userDb.userCardDao().clearPrimary(dexId, pokemonIdStr)

    suspend fun setAsPrimaryCard(card: CardEntity, pokemonId: String) {
        val dexId = pokemonId.toIntOrNull() ?: 0
        val dao = userDb.userCardDao()
        val existing = dao.findUserCard(card.id, card.language)
        val resolved = planPrimaryCardAssignment(
            cardId = card.id,
            language = card.language,
            pokemonDexId = dexId,
            pokemonId = pokemonId,
            existingCard = existing,
            now = System.currentTimeMillis()
        )
        when (val persistence = primaryCardPersistence(existing, resolved)) {
            is PrimaryCardPersistence.Insert -> {
                dao.clearPrimary(dexId, pokemonId)
                dao.insertUserCard(persistence.card)
            }
            is PrimaryCardPersistence.Update -> {
                dao.updateUserCard(persistence.card)
                // Only this row stays the favourite; the previous row must lose the flag.
                dao.clearOtherPrimaryRows(dexId, pokemonId, persistence.card.id)
            }
        }
    }

    suspend fun removePrimaryCard(pokemonId: String) {
        val dexId = pokemonId.toIntOrNull() ?: 0
        userDb.userCardDao().clearPrimary(dexId, pokemonId)
    }

    /**
     * Adds a non-favourite card for the requested language. An already owned variant keeps its
     * row and only gains one copy, so repeated use cannot duplicate the collection entry.
     */
    suspend fun addSecondaryCard(
        cardId: String,
        pokemonId: String,
        category: CardCategory,
        language: String = "de"
    ) {
        val dexId = pokemonId.toIntOrNull() ?: 0
        val dao = userDb.userCardDao()
        val resolvedLanguage = language.ifBlank { "de" }
        val existing = dao.findUserCard(cardId, resolvedLanguage)
        if (existing != null) {
            dao.updateUserCard(
                existing.copy(
                    quantity = existing.quantity + 1,
                    category = if (existing.category == CardCategory.NONE.name) category.name else existing.category
                )
            )
        } else {
            dao.insertUserCard(
                UserCardEntityV2(
                    cardId = cardId,
                    pokemonId = pokemonId,
                    pokemonDexId = dexId,
                    language = resolvedLanguage,
                    quantity = 1,
                    isPrimary = false,
                    category = category.name,
                    addedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun addToWishlist(wish: WishlistEntity) =
        userDb.wishlistDao().insert(wish)

    suspend fun removeFromWishlist(cardId: String) =
        userDb.wishlistDao().delete(cardId)

    suspend fun executeCollectionImport(
        exportData: CollectionExportData,
        validCatalogCardIds: Set<String>,
        strategy: ExportMergeStrategy
    ): ImportResult {
        return CollectionExporter.executeImport(exportData, userDb, validCatalogCardIds, strategy)
    }
}
