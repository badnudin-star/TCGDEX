package com.pokemontcg.sammlung.ui.main

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogEmptyStateTest {

    @Test
    fun catalogIsEmptyOnlyWhenPokemonAndCardsAreEmpty() {
        assertTrue(isCatalogEmpty(pokemonCount = 0, cardCount = 0))
        assertFalse(isCatalogEmpty(pokemonCount = 1, cardCount = 0))
        assertFalse(isCatalogEmpty(pokemonCount = 0, cardCount = 1))
        assertFalse(isCatalogEmpty(pokemonCount = 1, cardCount = 1))
    }
}
