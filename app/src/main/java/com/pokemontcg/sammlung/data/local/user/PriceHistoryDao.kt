package com.pokemontcg.sammlung.data.local.user

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PriceHistoryDao {

    @Query("SELECT * FROM price_history WHERE cardId = :cId ORDER BY timestamp ASC")
    fun getHistoryForCard(cId: String): Flow<List<PriceHistoryEntity>>

    @Query("SELECT * FROM price_history WHERE cardId = :cId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestHistoryForCard(cId: String): PriceHistoryEntity?

    @Query("SELECT * FROM price_history ORDER BY timestamp ASC")
    fun getAllHistory(): Flow<List<PriceHistoryEntity>>

    @Query("SELECT * FROM price_history ORDER BY timestamp ASC")
    suspend fun getAllHistoryOnce(): List<PriceHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: PriceHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<PriceHistoryEntity>)

    @Query("DELETE FROM price_history WHERE timestamp < :oldTimestamp")
    suspend fun deleteOlderThan(oldTimestamp: Long)
}
