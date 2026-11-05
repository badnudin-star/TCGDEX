package com.pokemontcg.sammlung.ui.main

import com.pokemontcg.sammlung.data.local.entities.CardEntity
import com.pokemontcg.sammlung.data.local.user.UserCardEntityV2

/**
 * Maps each Pokédex id with a primary card to that card's image.
 * The first primary entry per dex id wins; cards without an image are omitted.
 */
fun derivePrimaryImageByDexId(
    userCards: List<UserCardEntityV2>,
    cardsById: Map<String, CardEntity>
): Map<Int, String> {
    val result = LinkedHashMap<Int, String>()
    for (userCard in userCards) {
        if (!userCard.isPrimary || result.containsKey(userCard.pokemonDexId)) continue
        val card = cardsById[userCard.cardId] ?: continue
        val imageUrl = card.largeImageUrl?.takeIf { it.isNotBlank() }
            ?: card.imageUrl?.takeIf { it.isNotBlank() }
            ?: continue
        result[userCard.pokemonDexId] = imageUrl
    }
    return result
}
