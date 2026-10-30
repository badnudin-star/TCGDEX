package com.pokemontcg.sammlung.data.local.user

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "price_history",
    indices = [
        Index(value = ["cardId"]),
        Index(value = ["timestamp"])
    ]
)
data class PriceHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: String,
    val marketPrice: Double,
    val timestamp: Long = System.currentTimeMillis()
)
