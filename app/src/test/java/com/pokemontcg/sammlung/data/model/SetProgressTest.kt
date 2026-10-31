package com.pokemontcg.sammlung.data.model

import com.pokemontcg.sammlung.data.local.entities.CardEntity
import com.pokemontcg.sammlung.data.local.entities.SetEntity
import com.pokemontcg.sammlung.data.local.user.UserCardEntityV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetProgressTest {

    private fun card(id: String, setId: String): CardEntity = CardEntity(
        id = id,
        name = "Pikachu",
        supertype = "Pokémon",
        number = "1",
        rarity = null,
        setId = setId,
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

    private fun userCard(cardId: String, language: String): UserCardEntityV2 = UserCardEntityV2(
        cardId = cardId,
        pokemonId = "25",
        pokemonDexId = 25,
        language = language
    )

    @Test
    fun testComputeSetProgressDistinctCardsAndIncompleteCatalog() {
        val sets = listOf(
            SetEntity("base1", "Base Set", "Base", 102, 102, "1999-01-09"),
            SetEntity("xy1", "XY Base Set", "XY", 146, 146, "2014-02-05")
        )

        // Multiple copies of same cardId in base1
        val ownedCardIdsBySet = mapOf(
            "base1" to setOf("base1-1", "base1-2", "base1-3"), // 3 distinct cards
            "xy1" to setOf("xy1-1")                           // 1 card
        )

        val catalogCardCountBySet = mapOf(
            "base1" to 102,
            "xy1" to 100 // smaller than printedTotal (146) -> incomplete!
        )

        val progress = computeSetProgress(ownedCardIdsBySet, catalogCardCountBySet, sets)
        assertEquals(2, progress.size)

        val base1Progress = progress.find { it.set.id == "base1" }!!
        assertEquals(3, base1Progress.collectedCount)
        assertEquals(102, base1Progress.totalCount)
        assertFalse(base1Progress.isCatalogIncomplete)

        val xy1Progress = progress.find { it.set.id == "xy1" }!!
        assertEquals(1, xy1Progress.collectedCount)
        assertEquals(146, xy1Progress.totalCount)
        assertEquals(100, xy1Progress.catalogCount)
        assertTrue(xy1Progress.isCatalogIncomplete)
    }

    @Test
    fun testSortSetProgress() {
        val set1 = SetEntity("base1", "Base Set", "Base", 100, 100, "1999-01-09")
        val set2 = SetEntity("xy1", "XY", "XY", 100, 100, "2014-02-05")

        val p1 = SetProgress(set1, 50, 100, 100, 0.50f, false)
        val p2 = SetProgress(set2, 80, 100, 100, 0.80f, false)

        val list = listOf(p1, p2)

        // Progress desc -> p2 (80%) first
        val sortedProgress = sortSetProgress(list, SetSortOption.PROGRESS_DESC)
        assertEquals("xy1", sortedProgress[0].set.id)

        // Release date desc -> xy1 (2014) first
        val sortedRelease = sortSetProgress(list, SetSortOption.RELEASE_DATE_DESC)
        assertEquals("xy1", sortedRelease[0].set.id)

        // Name asc -> Base Set first
        val sortedName = sortSetProgress(list, SetSortOption.NAME_ASC)
        assertEquals("base1", sortedName[0].set.id)
    }

    @Test
    fun testMissingCatalogCountIsIncomplete() {
        val set = SetEntity("base1", "Base Set", "Base", 102, 102, "1999-01-09")

        val progress = computeSetProgress(emptyMap(), emptyMap(), listOf(set)).single()

        assertEquals(0, progress.collectedCount)
        assertEquals(102, progress.totalCount)
        assertEquals(0, progress.catalogCount)
        assertTrue(progress.isCatalogIncomplete)
        assertEquals(0f, progress.progressPercent, 0.0001f)
    }

    @Test
    fun testOwnedCardIdsBySetFiltersLanguageAndDeduplicates() {
        val cardsMap = mapOf(
            "c1" to card("c1", "set-a"),
            "c2" to card("c2", "set-a"),
            "c3" to card("c3", "set-b")
        )
        val userCards = listOf(
            userCard("c1", "de"),
            userCard("c1", "de"),
            userCard("c2", "en"),
            userCard("c3", "de"),
            userCard("missing", "de")
        )

        val german = ownedCardIdsBySet(userCards, cardsMap, "de")
        assertEquals(setOf("c1"), german["set-a"])
        assertEquals(setOf("c3"), german["set-b"])

        val all = ownedCardIdsBySet(userCards, cardsMap, null)
        assertEquals(setOf("c1", "c2"), all["set-a"])
        assertEquals(setOf("c3"), all["set-b"])
    }

    @Test
    fun testCardIdBatchesStayBelowRoomLimit() {
        val ids = (1..1201).map { "card-$it" } + "card-1"

        val batches = batchCardIds(ids)

        assertEquals(3, batches.size)
        assertTrue(batches.all { it.size <= 500 })
        assertEquals(1201, batches.sumOf { it.size })
    }

    @Test
    fun testCardIdBatchesRejectUnsafeBatchSize() {
        try {
            batchCardIds(listOf("card-1"), 501)
            throw AssertionError("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertEquals("batchSize must be between 1 and 500", expected.message)
        }
    }
}
