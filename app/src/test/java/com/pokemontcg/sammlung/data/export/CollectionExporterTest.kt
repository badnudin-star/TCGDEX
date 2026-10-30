package com.pokemontcg.sammlung.data.export

import com.pokemontcg.sammlung.data.local.user.UserCardEntityV2
import com.pokemontcg.sammlung.data.local.user.WishlistEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionExporterTest {

    @Test
    fun testJsonExportAndImportRoundtrip() {
        val cards = listOf(
            UserCardEntityV2(
                cardId = "base1-1",
                pokemonId = "p1",
                pokemonDexId = 1,
                language = "de",
                quantity = 2,
                condition = "NEAR_MINT",
                notes = "Erstausgabe",
                category = "FULLART",
                isPrimary = true,
                addedAt = 1000L
            )
        )
        val wishlist = listOf(
            WishlistEntity(
                cardId = "base1-4",
                targetPrice = 150.0,
                notes = "Glurak gesucht",
                addedAt = 2000L
            )
        )

        val jsonString = CollectionExporter.exportToJsonString(cards, wishlist)
        assertNotNull(jsonString)
        assertTrue(jsonString.contains("base1-1"))
        assertTrue(jsonString.contains("base1-4"))

        val parsed = CollectionExporter.parseExportJson(jsonString)
        assertEquals(2, parsed.schemaVersion)
        assertEquals(1, parsed.items.size)
        assertEquals(1, parsed.wishlist.size)

        val item = parsed.items.first()
        assertEquals("base1-1", item.cardId)
        assertEquals("de", item.language)
        assertEquals(2, item.quantity)
        assertEquals("NEAR_MINT", item.condition)
        assertEquals("Erstausgabe", item.notes)
        assertEquals("FULLART", item.category)
        assertTrue(item.isPrimary)
        assertEquals(1, item.pokemonDexId)

        val wish = parsed.wishlist.first()
        assertEquals("base1-4", wish.cardId)
        assertEquals(150.0, wish.targetPrice!!, 0.001)
        assertEquals("Glurak gesucht", wish.notes)
    }

    @Test
    fun testCsvExportAndEscaping() {
        val cards = listOf(
            UserCardEntityV2(
                cardId = "base1-2",
                pokemonId = "p2",
                pokemonDexId = 2,
                language = "de",
                quantity = 1,
                condition = "EXCELLENT",
                notes = "Notiz mit \"Anführungszeichen\" und ; Semikolon",
                category = "COMMON",
                isPrimary = false,
                addedAt = 3000L
            )
        )

        val csvString = CollectionExporter.exportToCsvString(cards)
        assertTrue(csvString.contains("cardId;language;quantity;condition;category;isPrimary;pokemonDexId;notes;addedAt"))
        assertTrue(csvString.contains("\"Notiz mit \"\"Anführungszeichen\"\" und ; Semikolon\""))

        val parsed = CollectionExporter.parseExportJson(csvString)
        assertEquals(2, parsed.schemaVersion)
        assertEquals(1, parsed.items.size)

        val item = parsed.items.first()
        assertEquals("base1-2", item.cardId)
        assertEquals("de", item.language)
        assertEquals(1, item.quantity)
        assertEquals("EXCELLENT", item.condition)
        assertEquals("Notiz mit \"Anführungszeichen\" und ; Semikolon", item.notes)
        assertEquals("COMMON", item.category)
        assertFalse(item.isPrimary)
        assertEquals(2, item.pokemonDexId)
    }

    @Test
    fun testSchemaVersion1Parsing() {
        val jsonV1 = """
            [
                {
                    "cardId": "xy1-1",
                    "language": "en",
                    "quantity": 3,
                    "condition": "GOOD",
                    "category": "NONE",
                    "isPrimary": false,
                    "pokemonDexId": 3,
                    "notes": "v1 card",
                    "addedAt": 5000
                }
            ]
        """.trimIndent()

        val parsed = CollectionExporter.parseExportJson(jsonV1)
        assertEquals(1, parsed.schemaVersion)
        assertEquals(1, parsed.items.size)
        assertEquals("xy1-1", parsed.items.first().cardId)
        assertEquals("en", parsed.items.first().language)
        assertEquals(3, parsed.items.first().quantity)
    }

    @Test
    fun testInvalidFileSchemaVersionTooNew() {
        val jsonFuture = """
            {
                "schemaVersion": 3,
                "exportedAt": "2030-01-01T00:00:00Z",
                "appVersion": "3.0.0",
                "items": []
            }
        """.trimIndent()

        try {
            CollectionExporter.parseExportJson(jsonFuture)
            org.junit.Assert.fail("Sollte Exception werfen bei schemaVersion > 2")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("neueren App-Version") == true)
        }
    }

    @Test
    fun testInvalidFileFormat() {
        val badContent = "Das ist weder ein gültiges JSON noch ein CSV mit den passenden Spalten."
        try {
            CollectionExporter.parseExportJson(badContent)
            org.junit.Assert.fail("Sollte Exception werfen bei ungültigem Format")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Ungültiges Dateiformat") == true)
        }
    }

    @Test
    fun testInvalidFileWithMissingRequiredFields() {
        val missingItems = """{"schemaVersion":2,"exportedAt":"2026-01-01T00:00:00Z"}"""

        try {
            CollectionExporter.parseExportJson(missingItems)
            org.junit.Assert.fail("Sollte Exception bei fehlenden Pflichtfeldern werfen")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Ungültiges Dateiformat") == true)
        }
    }

    @Test
    fun testCsvRoundtripWithNewlineAndBom() {
        val cards = listOf(
            UserCardEntityV2(
                cardId = "sv1-1",
                pokemonId = "1",
                pokemonDexId = 1,
                notes = "Zeile eins\nZeile zwei",
                addedAt = 42L
            )
        )

        val parsed = CollectionExporter.parseExportJson("\uFEFF" + CollectionExporter.exportToCsvString(cards))

        assertEquals("Zeile eins\nZeile zwei", parsed.items.single().notes)
        assertEquals(42L, parsed.items.single().addedAt)
    }

    @Test
    fun testGeneratePreviewIdentity() {
        val exportData = CollectionExportData(
            schemaVersion = 2,
            items = listOf(
                ExportedUserCardItem(cardId = "card1", language = "de", quantity = 1),
                ExportedUserCardItem(cardId = "card1", language = "en", quantity = 1), // new identity (card1, en)
                ExportedUserCardItem(cardId = "card2", language = "de", quantity = 1)  // unknown catalog card
            )
        )

        val existingCards = listOf(
            UserCardEntityV2(cardId = "card1", pokemonId = "p1", pokemonDexId = 1, language = "de", quantity = 1)
        )

        val validCatalogIds = setOf("card1")

        val preview = CollectionExporter.generatePreview(exportData, existingCards, validCatalogIds)
        assertEquals(3, preview.totalRead)
        assertEquals(2, preview.newCardsCount) // (card1, en) & (card2, de)
        assertEquals(1, preview.existingCardsCount) // (card1, de)
        assertEquals(1, preview.unknownCardIds.size)
        assertEquals("card2", preview.unknownCardIds.first())
    }

    @Test
    fun testMergeStrategiesAndPrimaryConflict() {
        val existing = UserCardEntityV2(
            cardId = "card1", pokemonId = "1", pokemonDexId = 1, language = "de",
            quantity = 2, category = "COMMON", notes = "alt", isPrimary = true
        )
        val item = ExportedUserCardItem(
            cardId = "card1", language = "de", quantity = 3, category = "FULLART", notes = "neu", pokemonDexId = 1
        )

        assertEquals(null, CollectionExporter.mergeExistingCard(existing, item, ExportMergeStrategy.SKIP_EXISTING, false))

        val overwritten = CollectionExporter.mergeExistingCard(existing, item, ExportMergeStrategy.OVERWRITE_EXISTING, false)!!
        assertEquals(3, overwritten.quantity)
        assertEquals("FULLART", overwritten.category)
        assertFalse(overwritten.isPrimary)

        val merged = CollectionExporter.mergeExistingCard(existing, item, ExportMergeStrategy.MERGE_ADD, false)!!
        assertEquals(5, merged.quantity)
        assertEquals("COMMON", merged.category)
        assertTrue(merged.isPrimary)

        val conflictingItem = item.copy(cardId = "card2", isPrimary = true)
        assertFalse(CollectionExporter.shouldKeepImportedPrimary(conflictingItem, existing))
        assertTrue(CollectionExporter.shouldKeepImportedPrimary(item.copy(isPrimary = true), existing))
    }

    @Test
    fun testNewNonPrimaryImportNeverBecomesFavorite() {
        val item = ExportedUserCardItem(
            cardId = "new-1",
            language = "de",
            isPrimary = false,
            pokemonDexId = 25
        )

        assertFalse(CollectionExporter.shouldKeepImportedPrimary(item, null))
    }

    @Test
    fun testPrimaryWithoutDexIdIsNotFavorite() {
        val item = ExportedUserCardItem(
            cardId = "unknown-1",
            language = "de",
            isPrimary = true,
            pokemonDexId = 0
        )

        assertFalse(CollectionExporter.shouldKeepImportedPrimary(item, null))
    }

    @Test
    fun testFirstImportedPrimaryBecomesFavorite() {
        val item = ExportedUserCardItem(
            cardId = "new-2",
            language = "de",
            isPrimary = true,
            pokemonDexId = 25
        )

        assertTrue(CollectionExporter.shouldKeepImportedPrimary(item, null))
    }

    @Test
    fun testEmptyCatalogMarksEveryImportedCardAsUnknown() {
        val exportData = CollectionExportData(
            schemaVersion = 2,
            items = listOf(
                ExportedUserCardItem(cardId = "card1", language = "de", quantity = 1),
                ExportedUserCardItem(cardId = "card2", language = "de", quantity = 1)
            )
        )

        val preview = CollectionExporter.generatePreview(
            exportData = exportData,
            existingCards = emptyList(),
            validCatalogCardIds = emptySet()
        )

        assertEquals(2, preview.unknownCardIds.size)
        assertEquals(setOf("card1", "card2"), preview.unknownCardIds.toSet())
    }

    @Test
    fun testIdentityDoesNotCollideWhenCardIdContainsSeparator() {
        val exportData = CollectionExportData(
            schemaVersion = 2,
            items = listOf(
                ExportedUserCardItem(cardId = "a", language = "de_en", quantity = 1)
            )
        )
        val existingCards = listOf(
            UserCardEntityV2(cardId = "a_de", pokemonId = "1", pokemonDexId = 1, language = "en")
        )

        val preview = CollectionExporter.generatePreview(
            exportData = exportData,
            existingCards = existingCards,
            validCatalogCardIds = setOf("a")
        )

        assertEquals(1, preview.newCardsCount)
        assertEquals(0, preview.existingCardsCount)
    }

    @Test
    fun testExportNormalizesUnknownConditionAndCategory() {
        val card = UserCardEntityV2(
            cardId = "legacy-1",
            pokemonId = "1",
            pokemonDexId = 1,
            condition = "Near Mint",
            category = "Holo"
        )

        val parsed = CollectionExporter.parseExportJson(CollectionExporter.exportToJsonString(listOf(card)))
        val item = parsed.items.single()

        assertEquals(null, item.condition)
        assertEquals("NONE", item.category)
    }

    @Test
    fun testJsonWithoutWishlistDefaultsToEmptyList() {
        val json = """{"schemaVersion":2,"exportedAt":"2026-01-01T00:00:00Z","appVersion":"2.0.0","items":[]}"""

        val parsed = CollectionExporter.parseExportJson(json)

        assertTrue(parsed.wishlist.isEmpty())
    }
}
