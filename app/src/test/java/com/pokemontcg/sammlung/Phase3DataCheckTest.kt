package com.pokemontcg.sammlung

import com.pokemontcg.sammlung.data.repository.SystemDataMetrics
import com.pokemontcg.sammlung.data.repository.DataCheckCalculations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase3DataCheckTest {

    @Test
    fun testSystemDataMetricsCalculation() {
        val metrics = SystemDataMetrics(
            setsCount = 12,
            totalCatalogCards = 1500,
            totalCards = 450,
            pokemonCount = 151,
            cardsWithoutImagesCount = 5,
            cardsWithoutPriceCount = 10,
            userCardsCount = 25,
            uniqueUserCardsCount = 20,
            wishlistCount = 3,
            priceHistoryCount = 50,
            unassignedUserCardsCount = 1
        )

        assertEquals(12, metrics.setsCount)
        assertEquals(151, metrics.pokemonCount)
        assertEquals(25, metrics.userCardsCount)
        assertEquals(1, metrics.unassignedUserCardsCount)
        assertTrue(metrics.appDbIntegrityOk)
        assertTrue(metrics.userDbIntegrityOk)
    }

    @Test
    fun testUnassignedCardDetection() {
        val validCatalogCardIds = setOf("xy1-1", "xy1-2", "xy1-3")
        val userCardIds = listOf("xy1-1", "xy1-2", "unknown-99")

        assertEquals(1, DataCheckCalculations.countUnassigned(userCardIds, validCatalogCardIds))
    }

    @Test
    fun testCardsCountedByLanguageCaseInsensitively() {
        val counts = DataCheckCalculations.countCardsByLanguage(listOf("de", "DE", "en", "", "en"))
        assertEquals(2, counts["de"])
        assertEquals(2, counts["en"])
        assertEquals(1, counts["unbekannt"])
    }
}
