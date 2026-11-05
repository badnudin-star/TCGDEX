package com.pokemontcg.sammlung.ui.main

import com.pokemontcg.sammlung.data.local.user.CardCategory
import com.pokemontcg.sammlung.data.local.user.UserCardEntityV2

/**
 * Derives the displayed category per Pokédex id solely from the user's cards.
 * A primary card always wins; otherwise the first non-NONE category is used.
 */
fun deriveCategoryByDex(userCards: List<UserCardEntityV2>): Map<Int, CardCategory> =
    userCards.groupBy { it.pokemonDexId }.mapValues { (_, cards) ->
        val primary = cards.firstOrNull { it.isPrimary }
        if (primary != null) {
            categoryFromName(primary.category)
        } else {
            cards.asSequence()
                .map { categoryFromName(it.category) }
                .firstOrNull { it != CardCategory.NONE }
                ?: CardCategory.NONE
        }
    }

private fun categoryFromName(value: String): CardCategory =
    CardCategory.entries.firstOrNull { it.name == value } ?: CardCategory.NONE
