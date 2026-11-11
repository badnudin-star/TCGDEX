package com.pokemontcg.sammlung.data.export

import com.pokemontcg.sammlung.R
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
        assertTrue(csvString.contains("cardId;language;quantity;condition;category;isPrimary;pokemonDexId;notes;gradingCompany;grade;certNumber;addedAt"))
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
        } catch (e: CollectionExportException) {
            assertEquals(R.string.import_error_newer_version, e.messageRes)
        }
    }

    @Test
    fun testInvalidFileFormat() {
        val badContent = "Das ist weder ein gültiges JSON noch ein CSV mit den passenden Spalten."
        try {
            CollectionExporter.parseExportJson(badContent)
            org.junit.Assert.fail("Sollte Exception werfen bei ungültigem Format")
        } catch (e: CollectionExportException) {
            assertEquals(R.string.import_error_invalid_format, e.messageRes)
        }
    }

    @Test
    fun testInvalidFileWithMissingRequiredFields() {
        val missingItems = """{"schemaVersion":2,"exportedAt":"2026-01-01T00:00:00Z"}"""

        try {
            CollectionExporter.parseExportJson(missingItems)
            org.junit.Assert.fail("Sollte Exception bei fehlenden Pflichtfeldern werfen")
        } catch (e: CollectionExportException) {
            assertEquals(R.string.import_error_invalid_format, e.messageRes)
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
    fun testGradingSurvivesJsonRoundtrip() {
        val graded = UserCardEntityV2(
            cardId = "sv3-180",
            pokemonId = "25",
            pokemonDexId = 25,
            language = "de",
            quantity = 1,
            gradingCompany = "PSA",
            grade = "10",
            certNumber = "84390123"
        )

        val parsed = CollectionExporter.parseExportJson(CollectionExporter.exportToJsonString(listOf(graded)))
        val item = parsed.items.single()

        assertEquals("PSA", item.gradingCompany)
        assertEquals("10", item.grade)
        assertEquals("84390123", item.certNumber)
    }

    @Test
    fun testUngradedCardOmitsGradingFieldsInJson() {
        val raw = UserCardEntityV2(
            cardId = "sv3-180",
            pokemonId = "25",
            pokemonDexId = 25,
            language = "de",
            quantity = 1
        )

        val json = CollectionExporter.exportToJsonString(listOf(raw))

        assertFalse(json.contains("gradingCompany"))
        assertFalse(json.contains("certNumber"))
    }

    @Test
    fun testGradingSurvivesCsvRoundtrip() {
        val graded = UserCardEntityV2(
            cardId = "sv3-180",
            pokemonId = "25",
            pokemonDexId = 25,
            language = "de",
            quantity = 1,
            gradingCompany = "CGC",
            grade = "9.5",
            certNumber = "22-1234567"
        )

        val parsed = CollectionExporter.parseExportJson(CollectionExporter.exportToCsvString(listOf(graded)))

        val item = parsed.items.single()
        assertEquals("CGC", item.gradingCompany)
        assertEquals("9.5", item.grade)
        assertEquals("22-1234567", item.certNumber)
    }

    @Test
    fun testLegacyCsvWithoutGradingColumnsStillImports() {
        val csv = "cardId;language;quantity;condition;category;isPrimary;pokemonDexId;notes;addedAt\n" +
            "\"sv3-180\";\"de\";\"1\";\"\";\"COMMON\";\"0\";\"25\";\"\";\"1000\"\n"

        val parsed = CollectionExporter.parseExportJson(csv)

        val item = parsed.items.single()
        assertEquals("sv3-180", item.cardId)
        assertEquals(null, item.gradingCompany)
        assertEquals(null, item.grade)
        assertEquals(null, item.certNumber)
    }

    @Test
    fun testMergeStrategiesKeepExistingGradingWhenImportHasNone() {
        val existing = UserCardEntityV2(
            cardId = "card1", pokemonId = "1", pokemonDexId = 1, language = "de",
            quantity = 2, category = "COMMON", gradingCompany = "PSA", grade = "10", certNumber = "84390123"
        )
        val item = ExportedUserCardItem(
            cardId = "card1", language = "de", quantity = 3, category = "COMMON", pokemonDexId = 1
        )

        val overwritten = CollectionExporter.mergeExistingCard(existing, item, ExportMergeStrategy.OVERWRITE_EXISTING, false)!!
        assertEquals("PSA", overwritten.gradingCompany)
        assertEquals("10", overwritten.grade)
        assertEquals("84390123", overwritten.certNumber)

        val itemGraded = item.copy(gradingCompany = "BGS", grade = "9")
        val regraded = CollectionExporter.mergeExistingCard(existing, itemGraded, ExportMergeStrategy.MERGE_ADD, false)!!
        assertEquals("BGS", regraded.gradingCompany)
        assertEquals("9", regraded.grade)
        assertEquals("84390123", regraded.certNumber)
    }

    @Test
    fun testMergeNormalizesInvalidImportedGrading() {
        val existing = UserCardEntityV2(
            cardId = "card1", pokemonId = "1", pokemonDexId = 1, language = "de",
            quantity = 2, category = "COMMON",
            gradingCompany = "PSA", grade = "10", certNumber = "84390123"
        )
        // Unknown grader and a free-text grade: both must not reach the database.
        val junkItem = ExportedUserCardItem(
            cardId = "card1", language = "de", quantity = 3, category = "COMMON", pokemonDexId = 1,
            gradingCompany = "ACE", grade = "sehr gut", certNumber = "9999"
        )

        val overwritten = CollectionExporter.mergeExistingCard(existing, junkItem, ExportMergeStrategy.OVERWRITE_EXISTING, false)!!
        assertEquals("PSA", overwritten.gradingCompany)
        assertEquals("10", overwritten.grade)
        assertEquals("84390123", overwritten.certNumber)

        val merged = CollectionExporter.mergeExistingCard(existing, junkItem, ExportMergeStrategy.MERGE_ADD, false)!!
        assertEquals("PSA", merged.gradingCompany)
        assertEquals("10", merged.grade)
    }

    @Test
    fun testMergeNormalizesImportedGradeNotation() {
        val existing = UserCardEntityV2(
            cardId = "card1", pokemonId = "1", pokemonDexId = 1, language = "de",
            quantity = 2, category = "COMMON"
        )
        val commaItem = ExportedUserCardItem(
            cardId = "card1", language = "de", quantity = 1, category = "COMMON", pokemonDexId = 1,
            gradingCompany = "CGC", grade = "9,5", certNumber = "22-1234567"
        )

        val overwritten = CollectionExporter.mergeExistingCard(existing, commaItem, ExportMergeStrategy.OVERWRITE_EXISTING, false)!!

        assertEquals("CGC", overwritten.gradingCompany)
        assertEquals("9.5", overwritten.grade)
        assertEquals("22-1234567", overwritten.certNumber)
    }

    @Test
    fun testJsonWithoutWishlistDefaultsToEmptyList() {
        val json = """{"schemaVersion":2,"exportedAt":"2026-01-01T00:00:00Z","appVersion":"2.0.0","items":[]}"""

        val parsed = CollectionExporter.parseExportJson(json)

        assertTrue(parsed.wishlist.isEmpty())
    }
}
