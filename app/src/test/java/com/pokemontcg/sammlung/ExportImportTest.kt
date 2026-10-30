package com.pokemontcg.sammlung

import com.pokemontcg.sammlung.data.export.CollectionExporter
import com.pokemontcg.sammlung.data.local.user.UserCardEntityV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportImportTest {

    @Test
    fun testExportToJsonAndParseRoundtrip() {
        val cards = listOf(
            UserCardEntityV2(
                id = 1,
                cardId = "xy1-1",
                pokemonId = "25",
                pokemonDexId = 25,
                language = "de",
                quantity = 2,
                condition = "NEAR_MINT",
                notes = "Holo Special",
                category = "COMMON",
                isPrimary = true,
                addedAt = 1700000000000L
            ),
            UserCardEntityV2(
                id = 2,
                cardId = "xy1-2",
                pokemonId = "26",
                pokemonDexId = 26,
                language = "en",
                quantity = 1,
                condition = "MINT",
                notes = null,
                category = "FULLART",
                isPrimary = false,
                addedAt = 1700000005000L
            )
        )

        val jsonString = CollectionExporter.exportToJsonString(cards)
        assertNotNull(jsonString)
        assertTrue(jsonString.contains("xy1-1"))
        assertTrue(jsonString.contains("xy1-2"))

        val parsedData = CollectionExporter.parseExportJson(jsonString)
        assertEquals(2, parsedData.schemaVersion)
        assertEquals(2, parsedData.items.size)

        val item1 = parsedData.items.find { it.cardId == "xy1-1" }
        assertNotNull(item1)
        assertEquals(25, item1?.pokemonDexId)
        assertEquals(2, item1?.quantity)
        assertEquals("NEAR_MINT", item1?.condition)
        assertEquals("Holo Special", item1?.notes)
        assertEquals("COMMON", item1?.category)
        assertEquals(true, item1?.isPrimary)
    }

    @Test
    fun testLegacyV14JsonFallbackParsing() {
        val legacyJson = """
            [
              {
                "cardId": "base1-4",
                "pokemonDexId": 6,
                "isPrimary": true,
                "category": "FULLART",
                "addedAt": 1600000000000
              }
            ]
        """.trimIndent()

        val parsedData = CollectionExporter.parseExportJson(legacyJson)
        assertEquals(1, parsedData.items.size)
        val item = parsedData.items[0]
        assertEquals("base1-4", item.cardId)
        assertEquals(6, item.pokemonDexId)
        assertEquals(true, item.isPrimary)
        assertEquals("FULLART", item.category)
    }

    @Test
    fun testExportToCsvFormat() {
        val cards = listOf(
            UserCardEntityV2(
                id = 10,
                cardId = "sm1-10",
                pokemonId = "1",
                pokemonDexId = 1,
                language = "de",
                quantity = 3,
                condition = "EXCELLENT",
                notes = "Sample \"quote\" test",
                category = "COMMON",
                isPrimary = false,
                addedAt = 1700000000000L
            )
        )

        val csvString = CollectionExporter.exportToCsvString(cards)
        assertNotNull(csvString)
        assertTrue(csvString.startsWith("cardId;language;quantity;condition;category;isPrimary;pokemonDexId"))
        assertTrue(csvString.contains("\"sm1-10\""))
        assertTrue(csvString.contains("Sample \"\"quote\"\" test"))
    }

    @Test
    fun testGeneratePreviewAndUnknownCardDetection() {
        val cards = listOf(
            UserCardEntityV2(id = 1, cardId = "known-1", pokemonId = "25", pokemonDexId = 25),
            UserCardEntityV2(id = 2, cardId = "unknown-99", pokemonId = "999", pokemonDexId = 999)
        )

        val json = CollectionExporter.exportToJsonString(cards)
        val exportData = CollectionExporter.parseExportJson(json)

        val validCatalogCardIds = setOf("known-1", "known-2")
        val existingCards = listOf(
            UserCardEntityV2(id = 10, cardId = "known-1", pokemonId = "25", pokemonDexId = 25)
        )

        val preview = CollectionExporter.generatePreview(
            exportData = exportData,
            existingCards = existingCards,
            validCatalogCardIds = validCatalogCardIds
        )

        assertEquals(2, preview.totalRead)
        assertEquals(1, preview.existingCardsCount)
        assertEquals(1, preview.newCardsCount)
        assertEquals(1, preview.unknownCardIds.size)
        assertEquals("unknown-99", preview.unknownCardIds[0])
    }
}
