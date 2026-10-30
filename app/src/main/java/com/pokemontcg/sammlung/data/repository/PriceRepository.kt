package com.pokemontcg.sammlung.data.repository

import com.pokemontcg.sammlung.data.local.AppDatabase
import com.pokemontcg.sammlung.data.local.user.PriceHistoryEntity
import com.pokemontcg.sammlung.data.local.user.UserDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.pokemontcg.sammlung.util.shouldRecordPricePoint

class PriceRepository(
    private val userDb: UserDatabase,
    private val collectionRepository: CollectionRepository,
    private val appDb: AppDatabase
) {

    fun getHistoryForCard(cardId: String): Flow<List<PriceHistoryEntity>> =
        userDb.priceHistoryDao().getHistoryForCard(cardId)

    fun getAllHistory(): Flow<List<PriceHistoryEntity>> =
        userDb.priceHistoryDao().getAllHistory()

    suspend fun getAllHistoryOnce(): List<PriceHistoryEntity> =
        userDb.priceHistoryDao().getAllHistoryOnce()

    suspend fun recordPricePoints(now: Long = System.currentTimeMillis()) {
        val userCards = collectionRepository.getAllUserCardsOnce()
        val wishlist = collectionRepository.getWishlistOnce()

        val cardIds = (userCards.map { it.cardId } + wishlist.map { it.cardId }).distinct()
        val twoYearsAgo = now - (2L * 365L * 24L * 60L * 60L * 1000L)

        for (cardId in cardIds) {
            val card = appDb.cardDao().getCardById(cardId) ?: continue
            val price = card.marketPrice ?: continue
            if (price <= 0.0) continue

            val latest = userDb.priceHistoryDao().getLatestHistoryForCard(cardId)
            val shouldRecord = shouldRecordPricePoint(
                latestPrice = latest?.marketPrice,
                latestTimestamp = latest?.timestamp,
                newPrice = price,
                now = now
            )

            if (shouldRecord) {
                userDb.priceHistoryDao().insert(
                    PriceHistoryEntity(
                        cardId = cardId,
                        marketPrice = price,
                        timestamp = now
                    )
                )
            }
        }

        userDb.priceHistoryDao().deleteOlderThan(twoYearsAgo)
    }

    suspend fun recordPricePoint(cardId: String, price: Double) {
        if (price > 0.0) {
            userDb.priceHistoryDao().insert(
                PriceHistoryEntity(cardId = cardId, marketPrice = price)
            )
        }
    }

    fun getTotalCollectionPrice(): Flow<Double?> =
        collectionRepository.getAllUserCards().map { userCards ->
            if (userCards.isEmpty()) return@map null
            val cardsById = loadCardsByIdsInBatches(userCards.map { it.cardId }.distinct())
            val sum = userCards.sumOf { userCard ->
                (cardsById[userCard.cardId]?.marketPrice ?: 0.0) * userCard.quantity
            }
            sum.takeIf { it > 0.0 }
        }

    fun getTotalPriceForPokemon(pokemonId: String): Flow<Double?> {
        val dexId = pokemonId.toIntOrNull() ?: 0
        return collectionRepository.getUserCardsForPokemon(dexId, pokemonId).map { userCards ->
            if (userCards.isEmpty()) return@map null
            val cardsById = loadCardsByIdsInBatches(userCards.map { it.cardId }.distinct())
            val sum = userCards.sumOf { userCard ->
                (cardsById[userCard.cardId]?.marketPrice ?: 0.0) * userCard.quantity
            }
            sum.takeIf { it > 0.0 }
        }
    }

    fun getCardsWithoutPriceCount(): Flow<Int> =
        collectionRepository.getAllUserCards().map { userCards ->
            if (userCards.isEmpty()) return@map 0
            val cardsById = loadCardsByIdsInBatches(userCards.map { it.cardId }.distinct())
            userCards.count { userCard ->
                val price = cardsById[userCard.cardId]?.marketPrice
                price == null || price <= 0.0
            }
        }

    /** SQLite's bound-parameter limit is kept safely below 999. */
    private suspend fun loadCardsByIdsInBatches(cardIds: List<String>) = buildMap {
        val dao = appDb.cardDao()
        cardIds.distinct().chunked(CARD_ID_BATCH_SIZE).forEach { batch ->
            dao.getCardsByIds(batch).forEach { card -> put(card.id, card) }
        }
    }

    private companion object {
        const val CARD_ID_BATCH_SIZE = 500
    }
}
