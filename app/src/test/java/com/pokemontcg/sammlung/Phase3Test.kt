package com.pokemontcg.sammlung

import com.pokemontcg.sammlung.data.export.TradeListExporter
import com.pokemontcg.sammlung.data.export.TradeListLabels
import com.pokemontcg.sammlung.data.local.entities.CardEntity
import com.pokemontcg.sammlung.data.local.user.UserCardEntityV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase3Test {

    @Test
    fun testTradeListGeneration() {
        val duplicates = listOf(
            UserCardEntityV2(
                id = 1,
                cardId = "xy1-1",
                pokemonId = "25",
                pokemonDexId = 25,
                quantity = 3,
                condition = "NEAR_MINT"
            )
        )

        val cardDetails = mapOf(
            "xy1-1" to CardEntity(
                id = "xy1-1",
                name = "Pikachu",
                supertype = "Pokémon",
                number = "25/108",
                rarity = "Common",
                setId = "xy1",
                setName = "XY Base",
                series = "XY",
                imageUrl = "http://example.com/pika.png",
                largeImageUrl = "http://example.com/pika_large.png",
                marketPrice = 2.50,
                lowPrice = 1.00,
                trendPrice = 2.00,
                nationalPokedexNumber = 25,
                subtypes = null,
                language = "de"
            )
        )

        val labels = TradeListLabels(
            title = "🎴 Meine Doppelte- & Tauschliste (TCG Sammlung)",
            empty = "Derzeit keine doppelten Karten in der Sammlung.",
            unknownSet = "Unbekanntes Set",
            setLabel = "Set",
            availableLabel = "Verfügbar zum Tauschen",
            conditionLabel = "Zustand",
            defaultCondition = "Near Mint",
            priceLabel = "Richtpreis",
            footer = "Erstellt mit TCG Sammlung App.",
            noPriceText = "Kein Preis verfügbar"
        )

        val tradeText = TradeListExporter.generateTradeListText(duplicates, cardDetails, labels)
        assertNotNull(tradeText)
        assertTrue(tradeText.contains("Pikachu"))
        assertTrue(tradeText.contains("XY Base"))
        assertTrue(tradeText.contains("Verfügbar zum Tauschen: 2 x"))
        assertTrue(tradeText.contains("2,50 €"))
    }

    @Test
    fun testExtractCardNumberRegexPattern() {
        val numberRegex = Regex("([0-9]{1,3}\\s*/\\s*[0-9]{1,3})")
        val sampleVisionText = "Pikachu HP 60 \n 25 / 108 \n Illust. Ken Sugimori"

        val match = numberRegex.find(sampleVisionText)
        assertNotNull(match)
        val cleanNumber = match?.value?.replace(" ", "")
        assertEquals("25/108", cleanNumber)
    }
}
