package com.pokemontcg.sammlung

import com.pokemontcg.sammlung.data.local.entities.CardEntity
import com.pokemontcg.sammlung.ui.cards.resolveScannerMatches
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerLookupTest {

    private fun card(id: String, number: String): CardEntity = CardEntity(
        id = id,
        name = "Pikachu",
        supertype = "Pokémon",
        number = number,
        rarity = null,
        setId = "set-1",
        setName = "Test Set",
        series = "Test Series",
        imageUrl = null,
        largeImageUrl = null,
        marketPrice = null,
        lowPrice = null,
        trendPrice = null,
        nationalPokedexNumber = 25,
        subtypes = null,
        language = "de"
    )

    @Test
    fun readableNumberSkipsOcrFallbackWhenItFindsCards() = runBlocking {
        var ocrCalls = 0
        val result = resolveScannerMatches(
            detectedNumber = "025/198",
            rawOcrText = "Pikachu",
            lookupByNumber = { listOf(card("number-hit", "025/198")) },
            lookupByOcrText = {
                ocrCalls++
                emptyList()
            }
        )

        assertEquals(listOf("number-hit"), result.map { it.id })
        assertEquals(0, ocrCalls)
    }

    @Test
    fun misreadNumberFallsBackToOcrText() = runBlocking {
        val result = resolveScannerMatches(
            detectedNumber = "999/999",
            rawOcrText = "Pikachu Vivid Voltage",
            lookupByNumber = { emptyList() },
            lookupByOcrText = { listOf(card("ocr-hit", "025/198")) }
        )

        assertEquals(listOf("ocr-hit"), result.map { it.id })
    }

    @Test
    fun missingNumberUsesOcrDirectly() = runBlocking {
        var numberCalls = 0
        val result = resolveScannerMatches(
            detectedNumber = null,
            rawOcrText = "Pikachu Vivid Voltage",
            lookupByNumber = {
                numberCalls++
                emptyList()
            },
            lookupByOcrText = { listOf(card("ocr-hit", "025/198")) }
        )

        assertEquals(listOf("ocr-hit"), result.map { it.id })
        assertEquals(0, numberCalls)
    }

    @Test
    fun bothLookupsEmptyReturnNoCandidates() = runBlocking {
        val result = resolveScannerMatches(
            detectedNumber = "999/999",
            rawOcrText = "unreadable",
            lookupByNumber = { emptyList() },
            lookupByOcrText = { emptyList() }
        )

        assertTrue(result.isEmpty())
    }
}
