package com.pokemontcg.sammlung.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardmarketLinksTest {

    @Test
    fun buildsAGermanSearchLinkFromCardAndSetName() {
        val url = CardmarketLinks.forCard("Pikachu V", "Scarlet & Violet")

        assertEquals(
            "https://www.cardmarket.com/de/Pokemon/Products/Search?searchString=Pikachu+V+Scarlet+%26+Violet",
            url
        )
    }

    @Test
    fun urlEncodesSpacesAndSpecialCharacters() {
        val url = CardmarketLinks.forCard("Nidoran♀", "Base Set")

        assertTrue(url.endsWith("searchString=Nidoran%E2%99%80+Base+Set"))
    }

    @Test
    fun emptyNamesStillProduceAValidSearchLink() {
        assertEquals(
            "https://www.cardmarket.com/de/Pokemon/Products/Search?searchString=Pikachu",
            CardmarketLinks.forCard(" Pikachu ", "")
        )
        assertEquals(
            "https://www.cardmarket.com/de/Pokemon/Products/Search?searchString=",
            CardmarketLinks.forCard(" ", "  ")
        )
    }
}
