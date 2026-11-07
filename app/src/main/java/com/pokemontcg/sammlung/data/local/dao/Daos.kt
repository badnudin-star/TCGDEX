package com.pokemontcg.sammlung.data.local.dao

import androidx.room.*
import com.pokemontcg.sammlung.data.local.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PokemonDao {
    @Query("SELECT * FROM pokemon ORDER BY dexId ASC")
    fun getAllPokemon(): Flow<List<PokemonEntity>>

    @Query("SELECT * FROM pokemon WHERE dexId = :dexId LIMIT 1")
    suspend fun getPokemonByDexId(dexId: Int): PokemonEntity?

    @Query("SELECT COUNT(*) FROM pokemon")
    suspend fun getPokemonCount(): Int

    @Query("SELECT COUNT(*) FROM pokemon")
    fun observePokemonCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIfAbsent(pokemon: List<PokemonEntity>): List<Long>

    @Query("UPDATE pokemon SET cardsUpdatedAt = :timestamp WHERE id = :pokemonId")
    suspend fun updateCardsFetchedAt(pokemonId: String, timestamp: Long)

    @Query("DELETE FROM pokemon")
    suspend fun deleteAll()
}

@Dao
interface SetDao {
    @Query("SELECT * FROM sets ORDER BY name ASC")
    fun getAllSets(): Flow<List<SetEntity>>
    @Query("SELECT COUNT(*) FROM sets")
    fun observeSetCount(): Flow<Int>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sets: List<SetEntity>)
    @Query("SELECT * FROM sets WHERE id = :setId LIMIT 1")
    suspend fun getSetById(setId: String): SetEntity?
    @Query("DELETE FROM sets")
    suspend fun deleteAll()
}

@Dao
interface CardDao {
    @Query("SELECT DISTINCT c.* FROM cards c INNER JOIN card_pokemon cp ON c.id = cp.cardId WHERE cp.dexId = :pokedexNumber ORDER BY COALESCE(c.setReleaseDate, '') DESC, c.setName COLLATE NOCASE ASC, c.sortNumber ASC, c.number COLLATE NOCASE ASC")
    fun getCardsByPokemon(pokedexNumber: Int): Flow<List<CardEntity>>

    @Query("SELECT DISTINCT c.* FROM cards c INNER JOIN card_pokemon cp ON c.id = cp.cardId WHERE cp.dexId = :pokedexNumber AND c.setId = :setId ORDER BY COALESCE(c.setReleaseDate, '') DESC, c.setName COLLATE NOCASE ASC, c.sortNumber ASC, c.number COLLATE NOCASE ASC")
    fun getCardsByPokemonAndSet(pokedexNumber: Int, setId: String): Flow<List<CardEntity>>

    @Query("SELECT DISTINCT c.setId AS setId, c.setName AS setName, COALESCE(c.setReleaseDate, '') AS releaseDate FROM cards c INNER JOIN card_pokemon cp ON c.id = cp.cardId WHERE cp.dexId = :pokedexNumber ORDER BY COALESCE(c.setReleaseDate, '') DESC, c.setName COLLATE NOCASE ASC")
    fun getSetsForPokemon(pokedexNumber: Int): Flow<List<SetIdName>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cards: List<CardEntity>)
    @Query("UPDATE cards SET marketPrice = :marketPrice, lowPrice = :lowPrice, trendPrice = :trendPrice WHERE id = :cardId")
    suspend fun updateCardPricing(cardId: String, marketPrice: Double?, lowPrice: Double?, trendPrice: Double?)

    @Query("UPDATE cards SET imageUrl = :imageUrl, largeImageUrl = :largeImageUrl WHERE id = :cardId")
    suspend fun updateCardImages(cardId: String, imageUrl: String?, largeImageUrl: String?)

    @Query("UPDATE cards SET setLogoUrl = COALESCE(setLogoUrl, :logoUrl), setSymbolUrl = COALESCE(setSymbolUrl, :symbolUrl) WHERE setId = :setId")
    suspend fun fillMissingSetImages(setId: String, logoUrl: String?, symbolUrl: String?)
    @Query("SELECT DISTINCT dexId FROM card_pokemon")
    suspend fun getCachedPokedexNumbers(): List<Int>
    @Query("DELETE FROM cards")
    suspend fun deleteAll()
    @Query("SELECT * FROM cards WHERE id = :cardId")
    suspend fun getCardById(cardId: String): CardEntity?

    @Query("SELECT * FROM cards WHERE UPPER(REPLACE(number, ' ', '')) = :normalizedNumber OR (:localNumber IS NOT NULL AND TRIM(number) <> '' AND TRIM(number) NOT GLOB '*[^0-9]*' AND CAST(number AS INTEGER) = :localNumber) ORDER BY CASE WHEN :denominator IS NOT NULL AND (setCardCountOfficial = :denominator OR setCardCountTotal = :denominator) THEN 0 ELSE 1 END, COALESCE(setReleaseDate, '') DESC, setName COLLATE NOCASE ASC LIMIT 30")
    suspend fun getCardsByNumber(normalizedNumber: String, localNumber: Int?, denominator: Int?): List<CardEntity>

    @Query("SELECT * FROM cards WHERE LOWER(name) LIKE '%' || LOWER(:fragment) || '%' OR LOWER(setName) LIKE '%' || LOWER(:fragment) || '%' ORDER BY COALESCE(setReleaseDate, '') DESC, setName COLLATE NOCASE ASC, sortNumber ASC, number COLLATE NOCASE ASC LIMIT 30")
    suspend fun getCardsByNameFragment(fragment: String): List<CardEntity>

    @Query("SELECT * FROM cards")
    suspend fun getAllCardsOnce(): List<CardEntity>

    @Query("SELECT COUNT(*) FROM cards")
    fun observeCardCount(): Flow<Int>

    @Query("UPDATE cards SET nationalPokedexNumber = :firstDexId, nationalPokedexNumbers = :allDexIds WHERE id = :cardId")
    suspend fun updateCardPokemonLinks(cardId: String, firstDexId: Int?, allDexIds: String?)

    @Query("UPDATE cards SET setReleaseDate = :releaseDate WHERE setId = :setId AND (setReleaseDate IS NULL OR setReleaseDate = '')")
    suspend fun fillMissingSetReleaseDate(setId: String, releaseDate: String)
    @Query("SELECT * FROM cards WHERE id IN (:cardIds)")
    suspend fun getCardsByIds(cardIds: List<String>): List<CardEntity>
    @Query("SELECT setId, COUNT(*) AS count FROM cards GROUP BY setId")
    suspend fun getCardCountsBySet(): List<SetCardCount>
    @Query("SELECT * FROM cards WHERE setId = :setId ORDER BY COALESCE(setReleaseDate, '') DESC, setName COLLATE NOCASE ASC, sortNumber ASC, number COLLATE NOCASE ASC")
    suspend fun getCardsForSetOnce(setId: String): List<CardEntity>
    @Query("SELECT * FROM cards WHERE imageUrl IS NULL OR imageUrl = ''")
    suspend fun getCardsWithMissingImagesOnce(): List<CardEntity>

    /** Every rarity present in the catalog, for the wishlist search filter chips. */
    @Query("SELECT DISTINCT rarity FROM cards WHERE rarity IS NOT NULL AND TRIM(rarity) <> '' ORDER BY rarity COLLATE NOCASE ASC")
    fun getAllRarities(): Flow<List<String>>

    /**
     * Catalog search for the wishlist: rarity multi-select plus a price ceiling, already ordered
     * by National Pokédex id so the result can be grouped per Pokémon like the main screen.
     * Cards without a Pokédex link (trainer/energy cards) sort last.
     *
     * `hasRarityFilter` keeps the `IN` clause valid: Room expands an empty list to `IN ()`,
     * which SQLite rejects, so callers pass a placeholder list together with `false`.
     */
    @Query(
        "SELECT c.id AS cardId, MIN(cp.dexId) AS dexId FROM cards c " +
            "LEFT JOIN card_pokemon cp ON c.id = cp.cardId " +
            "WHERE (:hasRarityFilter = 0 OR c.rarity IN (:rarities)) " +
            "AND (:maxPrice IS NULL OR (c.marketPrice IS NOT NULL AND c.marketPrice <= :maxPrice)) " +
            "AND (:query = '' OR LOWER(c.name) LIKE '%' || LOWER(:query) || '%' " +
            "OR LOWER(c.setName) LIKE '%' || LOWER(:query) || '%' " +
            "OR LOWER(c.number) LIKE '%' || LOWER(:query) || '%') " +
            "GROUP BY c.id " +
            "ORDER BY CASE WHEN MIN(cp.dexId) IS NULL THEN 1 ELSE 0 END, MIN(cp.dexId) ASC, " +
            "COALESCE(c.setReleaseDate, '') DESC, c.setName COLLATE NOCASE ASC, " +
            "c.sortNumber ASC, c.number COLLATE NOCASE ASC " +
            "LIMIT :limit"
    )
    fun searchCatalogCardIds(
        query: String,
        rarities: List<String>,
        hasRarityFilter: Boolean,
        maxPrice: Double?,
        limit: Int
    ): Flow<List<CardSearchRow>>
}

@Dao
interface CardPokemonDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(links: List<CardPokemonEntity>)

    @Query("DELETE FROM card_pokemon WHERE cardId = :cardId")
    suspend fun deleteForCard(cardId: String)

    @Query("DELETE FROM card_pokemon")
    suspend fun deleteAll()
}

@Dao
interface PokemonNameDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIfAbsent(names: List<PokemonNameEntity>): List<Long>

    @Query("SELECT dexId FROM pokemon_names WHERE language = :language AND name = :name LIMIT 1")
    suspend fun findDexIdByName(language: String, name: String): Int?

    @Query("SELECT * FROM pokemon_names")
    fun getAllPokemonNames(): Flow<List<PokemonNameEntity>>

    @Query("SELECT DISTINCT language FROM pokemon_names ORDER BY language")
    fun getAvailableLanguages(): Flow<List<String>>

    @Query("DELETE FROM pokemon_names")
    suspend fun deleteAll()
}

data class SetIdName(val setId: String, val setName: String, val releaseDate: String = "")

data class SetCardCount(val setId: String, val count: Int)

/** One catalog search hit: the card plus the Pokédex id it is grouped under. */
data class CardSearchRow(val cardId: String, val dexId: Int?)

@Dao
interface LastUpdateDao {
    @Query("SELECT * FROM last_update WHERE id = 1")
    suspend fun getLastUpdate(): LastUpdateEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUpdate(update: LastUpdateEntity)
}

@Dao
interface SourceAuditDao {
    @Query("SELECT * FROM source_audit WHERE id = :id LIMIT 1")
    suspend fun getSourceAuditById(id: String): SourceAuditEntity?

    @Query("SELECT * FROM source_audit ORDER BY checkedAt DESC, sourceName ASC")
    fun getAllSourceAudits(): Flow<List<SourceAuditEntity>>

    @Query("SELECT * FROM source_audit ORDER BY checkedAt DESC, sourceName ASC")
    suspend fun getSourceAuditsOnce(): List<SourceAuditEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<SourceAuditEntity>)

    @Query("DELETE FROM source_audit WHERE id LIKE 'set-coverage:%'")
    suspend fun clearSetCoverageRecords()
}
