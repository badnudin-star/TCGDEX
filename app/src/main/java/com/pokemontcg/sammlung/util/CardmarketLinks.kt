package com.pokemontcg.sammlung.util

import java.net.URLEncoder

/**
 * Builds Cardmarket links for catalog cards.
 *
 * The catalog schema does not store Cardmarket product ids (they only exist per TCGdex variant
 * and would require another Room + catalog schema migration), so links target the Cardmarket
 * search with the card and set name prefilled. That URL is stable, never 404s for a valid card
 * and lands the user one tap away from the product page - including when opened from a shared
 * text file on another device.
 */
object CardmarketLinks {

    private const val SEARCH_URL = "https://www.cardmarket.com/de/Pokemon/Products/Search?searchString="

    /** Search link for a card: the card name plus its set name disambiguates the result list. */
    fun forCard(cardName: String, setName: String): String {
        val query = listOf(cardName.trim(), setName.trim())
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        if (query.isEmpty()) return SEARCH_URL
        // URLEncoder encodes spaces as '+', which is the correct form inside a query value.
        return SEARCH_URL + URLEncoder.encode(query, "UTF-8")
    }
}
