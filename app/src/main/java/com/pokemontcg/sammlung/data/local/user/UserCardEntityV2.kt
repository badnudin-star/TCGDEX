package com.pokemontcg.sammlung.data.local.user

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class CardCondition {
    MINT,
    NEAR_MINT,
    EXCELLENT,
    GOOD,
    LIGHT_PLAYED,
    PLAYED,
    POOR
}

enum class CardCategory {
    NONE,
    COMMON,
    FULLART
}

@Entity(
    tableName = "user_cards",
    indices = [
        Index(value = ["cardId"]),
        Index(value = ["pokemonDexId"])
    ]
)
data class UserCardEntityV2(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: String,
    val pokemonId: String,
    val pokemonDexId: Int,
    val language: String = "de",
    val quantity: Int = 1,
    val condition: String? = null,
    val notes: String? = null,
    val category: String = CardCategory.NONE.name,
    val isPrimary: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
)
