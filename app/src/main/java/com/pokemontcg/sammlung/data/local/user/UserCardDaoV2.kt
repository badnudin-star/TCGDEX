package com.pokemontcg.sammlung.data.local.user

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserCardDaoV2 {

    @Query("SELECT * FROM user_cards ORDER BY addedAt DESC")
    fun getAllUserCards(): Flow<List<UserCardEntityV2>>

    @Query("SELECT * FROM user_cards ORDER BY addedAt DESC")
    suspend fun getAllUserCardsOnce(): List<UserCardEntityV2>

    @Query("SELECT * FROM user_cards WHERE pokemonDexId = :dexId OR pokemonId = :pokemonIdStr")
    fun getUserCardsForPokemon(dexId: Int, pokemonIdStr: String): Flow<List<UserCardEntityV2>>

    @Query("SELECT * FROM user_cards WHERE (pokemonDexId = :dexId OR pokemonId = :pokemonIdStr) AND isPrimary = 1 LIMIT 1")
    fun getPrimaryCard(dexId: Int, pokemonIdStr: String): Flow<UserCardEntityV2?>

    /** Existing row for one card; used to update instead of duplicating owned cards. */
    @Query(
        "SELECT * FROM user_cards WHERE cardId = :cardId " +
            "ORDER BY CASE WHEN language = :language THEN 0 ELSE 1 END, id ASC LIMIT 1"
    )
    suspend fun findUserCard(cardId: String, language: String): UserCardEntityV2?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserCard(userCard: UserCardEntityV2): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(userCards: List<UserCardEntityV2>): List<Long>

    @Update
    suspend fun updateUserCard(userCard: UserCardEntityV2)

    @Query("DELETE FROM user_cards WHERE id = :rowId")
    suspend fun deleteById(rowId: Long)

    @Query("UPDATE user_cards SET isPrimary = 0 WHERE pokemonDexId = :dexId OR pokemonId = :pokemonIdStr")
    suspend fun clearPrimary(dexId: Int, pokemonIdStr: String)

    @Query("UPDATE user_cards SET isPrimary = 0 WHERE isPrimary = 1 AND id <> :keepRowId AND (pokemonDexId = :dexId OR pokemonId = :pokemonIdStr)")
    suspend fun clearOtherPrimaryRows(dexId: Int, pokemonIdStr: String, keepRowId: Long)

    @Query("SELECT COUNT(*) FROM user_cards")
    suspend fun getUserCardCount(): Int

    @Query("SELECT DISTINCT cardId FROM user_cards")
    suspend fun getCollectedCardIds(): List<String>

    @Query("DELETE FROM user_cards")
    suspend fun deleteAll()
}
