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
        userDb.userCardDao().clearPrimary(dexId, pokemonId)
        userDb.userCardDao().insertUserCard(
            UserCardEntityV2(
                cardId = card.id,
                pokemonId = pokemonId,
                pokemonDexId = dexId,
                language = card.language,
                quantity = 1,
                isPrimary = true,
                addedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun removePrimaryCard(pokemonId: String) {
        val dexId = pokemonId.toIntOrNull() ?: 0
        userDb.userCardDao().clearPrimary(dexId, pokemonId)
    }

    suspend fun addSecondaryCard(cardId: String, pokemonId: String, category: CardCategory) {
        val dexId = pokemonId.toIntOrNull() ?: 0
        userDb.userCardDao().insertUserCard(
            UserCardEntityV2(
                cardId = cardId,
                pokemonId = pokemonId,
                pokemonDexId = dexId,
                language = "de",
                quantity = 1,
                isPrimary = false,
                category = category.name,
                addedAt = System.currentTimeMillis()
            )
        )
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
