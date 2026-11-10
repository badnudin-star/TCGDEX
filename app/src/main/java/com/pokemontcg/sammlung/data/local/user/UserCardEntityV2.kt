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

/**
 * Grading information for a professionally graded copy (PSA/CGC/BGS slab).
 * All fields are null for raw, ungraded copies - the default case.
 */
data class CardGrading(
    val gradingCompany: String? = null,
    val grade: String? = null,
    val certNumber: String? = null
)

/** Grading companies with dedicated UI labels. */
enum class GradingCompany {
    PSA,
    CGC,
    BGS
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
    val addedAt: Long = System.currentTimeMillis(),
    val gradingCompany: String? = null,
    val grade: String? = null,
    val certNumber: String? = null
)
