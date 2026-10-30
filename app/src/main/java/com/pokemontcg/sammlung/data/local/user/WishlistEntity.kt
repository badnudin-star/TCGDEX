package com.pokemontcg.sammlung.data.local.user

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "wishlist",
    indices = [
        Index(value = ["cardId"])
    ]
)
data class WishlistEntity(
    @PrimaryKey val cardId: String,
    val targetPrice: Double? = null,
    val notes: String? = null,
    val addedAt: Long = System.currentTimeMillis()
)
