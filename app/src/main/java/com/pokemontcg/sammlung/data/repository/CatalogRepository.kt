package com.pokemontcg.sammlung.data.repository

import com.pokemontcg.sammlung.data.local.AppDatabase
import com.pokemontcg.sammlung.data.local.dao.SetIdName
import com.pokemontcg.sammlung.data.local.entities.CardEntity
import com.pokemontcg.sammlung.data.local.entities.PokemonEntity
import com.pokemontcg.sammlung.data.local.entities.PokemonNameEntity
import com.pokemontcg.sammlung.data.local.entities.SetEntity
import com.pokemontcg.sammlung.data.local.entities.SourceAuditEntity
import com.pokemontcg.sammlung.data.model.batchCardIds
import com.pokemontcg.sammlung.ui.cards.extractOcrSearchTokens
import kotlinx.coroutines.flow.Flow

class CatalogRepository(private val db: AppDatabase) {

    fun getAllPokemon(): Flow<List<PokemonEntity>> = db.pokemonDao().getAllPokemon()

    fun observePokemonCount(): Flow<Int> = db.pokemonDao().observePokemonCount()

    fun observeCardCount(): Flow<Int> = db.cardDao().observeCardCount()

    fun observeSetCount(): Flow<Int> = db.setDao().observeSetCount()

    fun getAllPokemonNames(): Flow<List<PokemonNameEntity>> = db.pokemonNameDao().getAllPokemonNames()

    fun getAvailablePokemonNameLanguages(): Flow<List<String>> =
        db.pokemonNameDao().getAvailableLanguages()

    fun getAllSets(): Flow<List<SetEntity>> = db.setDao().getAllSets()

    fun getCardsByPokemon(pokedexNumber: Int): Flow<List<CardEntity>> =
        db.cardDao().getCardsByPokemon(pokedexNumber)

    fun getCardsByPokemonAndSet(pokedexNumber: Int, setId: String): Flow<List<CardEntity>> =
        db.cardDao().getCardsByPokemonAndSet(pokedexNumber, setId)

    fun getSetsForPokemon(pokedexNumber: Int): Flow<List<SetIdName>> =
        db.cardDao().getSetsForPokemon(pokedexNumber)

    suspend fun getCardById(cardId: String): CardEntity? =
        db.cardDao().getCardById(cardId)

    suspend fun getCardsByNumber(number: String): List<CardEntity> {
        val normalized = number.filterNot { it.isWhitespace() }.uppercase()
        val parts = normalized.split('/', limit = 2)
        val localNumber = if (parts.size == 2) parts[0].toIntOrNull() else null
        val denominator = if (parts.size == 2) parts[1].toIntOrNull() else null
        return db.cardDao().getCardsByNumber(normalized, localNumber, denominator)
    }

    /** Searches a small, bounded set of OCR name/set clues when no card number was readable. */
    suspend fun getCardsByOcrText(rawText: String): List<CardEntity> {
        val tokens = extractOcrSearchTokens(rawText)
        if (tokens.isEmpty()) return emptyList()
        val dao = db.cardDao()
        val byId = LinkedHashMap<String, CardEntity>()
        for (token in tokens) {
            for (card in dao.getCardsByNameFragment(token)) {
                byId.putIfAbsent(card.id, card)
                if (byId.size >= 60) break
            }
            if (byId.size >= 60) break
        }
        return byId.values.take(60)
    }

    suspend fun getPokemonByDexId(dexId: Int): PokemonEntity? =
        db.pokemonDao().getPokemonByDexId(dexId)

    suspend fun getCardsByIds(cardIds: List<String>): List<CardEntity> =
        if (cardIds.isEmpty()) emptyList() else db.cardDao().getCardsByIds(cardIds)

    suspend fun getCardsByIdsBatched(cardIds: List<String>, batchSize: Int = 500): List<CardEntity> =
        batchCardIds(cardIds, batchSize).flatMap { batch -> db.cardDao().getCardsByIds(batch) }

    suspend fun getCardCountsBySet(): Map<String, Int> =
        db.cardDao().getCardCountsBySet().associate { it.setId to it.count }

    suspend fun getAllCardIds(): Set<String> =
        db.cardDao().getAllCardsOnce().map { it.id }.toSet()

    fun getSourceAudits(): Flow<List<SourceAuditEntity>> =
        db.sourceAuditDao().getAllSourceAudits()
}
