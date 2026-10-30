package com.pokemontcg.sammlung.data.local.user

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WishlistDao {

    @Query("SELECT * FROM wishlist ORDER BY addedAt DESC")
    fun getWishlist(): Flow<List<WishlistEntity>>

    @Query("SELECT * FROM wishlist ORDER BY addedAt DESC")
    suspend fun getWishlistOnce(): List<WishlistEntity>

    @Query("SELECT * FROM wishlist WHERE cardId = :cId LIMIT 1")
    suspend fun getWishItem(cId: String): WishlistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(wish: WishlistEntity)

    @Query("DELETE FROM wishlist WHERE cardId = :cId")
    suspend fun delete(cId: String)
}
