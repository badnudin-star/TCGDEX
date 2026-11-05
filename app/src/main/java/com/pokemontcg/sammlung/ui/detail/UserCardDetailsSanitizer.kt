package com.pokemontcg.sammlung.ui.detail

import com.pokemontcg.sammlung.data.local.user.CardCategory
import com.pokemontcg.sammlung.data.local.user.CardCondition
import com.pokemontcg.sammlung.data.local.user.UserCardEntityV2

internal const val MIN_CARD_QUANTITY = 1
internal const val MAX_CARD_QUANTITY = 999
internal const val MAX_CARD_NOTES_LENGTH = 500

internal val SUPPORTED_CARD_LANGUAGES = setOf("de", "en", "ja", "fr", "es", "it", "ko")

private val validConditions = CardCondition.entries.map { it.name }.toSet()
private val validCategories = CardCategory.entries.map { it.name }.toSet()

/**
 * Returns a normalized copy of [userCard] without touching persistence.
 * Invalid optional values fall back to null or to the value already stored on [userCard].
 */
internal fun sanitizeUserCardDetails(
    userCard: UserCardEntityV2,
    quantity: Int,
    condition: String?,
    language: String,
    notes: String?,
    category: String
): UserCardEntityV2 {
    val sanitizedCondition = condition?.takeIf { it in validConditions }
    val sanitizedLanguage = language.takeIf { it in SUPPORTED_CARD_LANGUAGES } ?: userCard.language
    val sanitizedCategory = category.takeIf { it in validCategories } ?: userCard.category
    val sanitizedNotes = notes
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.take(MAX_CARD_NOTES_LENGTH)

    return userCard.copy(
        quantity = quantity.coerceIn(MIN_CARD_QUANTITY, MAX_CARD_QUANTITY),
        condition = sanitizedCondition,
        language = sanitizedLanguage,
        notes = sanitizedNotes,
        category = sanitizedCategory
    )
}
