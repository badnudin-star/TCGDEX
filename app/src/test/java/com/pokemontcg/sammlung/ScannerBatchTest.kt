package com.pokemontcg.sammlung

import com.pokemontcg.sammlung.data.local.entities.CardEntity
import com.pokemontcg.sammlung.ui.cards.BatchScanItem
import com.pokemontcg.sammlung.ui.cards.adjustBatchItemCount
import com.pokemontcg.sammlung.ui.cards.mergeBatchScanItem
import com.pokemontcg.sammlung.ui.cards.totalBatchCards
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerBatchTest {

    private fun card(id: String, name: String = "Pikachu") = CardEntity(
        id = id,
        name = name,
        supertype = "Pokemon",
        number = "25/198",
        rarity = null,
        setId = "sv01",
        setName = "Karmesin & Purpur",
        series = "Karmesin & Purpur",
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
    fun newCardIsAppendedWithCountOne() {
        val items = mergeBatchScanItem(emptyList(), card("sv01-25"))
        assertEquals(listOf(BatchScanItem(card("sv01-25"), 1)), items)
        assertEquals(1, totalBatchCards(items))
    }

    @Test
    fun rescanningTheSameCardIncrementsItsCount() {
        var items = mergeBatchScanItem(emptyList(), card("sv01-25"))
        items = mergeBatchScanItem(items, card("sv01-199", "Evoli"))
        items = mergeBatchScanItem(items, card("sv01-25"))
        items = mergeBatchScanItem(items, card("sv01-25"))
        assertEquals(2, items.size)
        assertEquals(3, items.first { it.card.id == "sv01-25" }.count)
        assertEquals(1, items.first { it.card.id == "sv01-199" }.count)
        assertEquals(4, totalBatchCards(items))
    }

    @Test
    fun mergeKeepsStableOrdering() {
        var items = mergeBatchScanItem(emptyList(), card("a"))
        items = mergeBatchScanItem(items, card("b"))
        items = mergeBatchScanItem(items, card("c"))
        items = mergeBatchScanItem(items, card("a"))
        assertEquals(listOf("a", "b", "c"), items.map { it.card.id })
    }

    @Test
    fun adjustingCountsRemovesItemsDroppingToZero() {
        var items = mergeBatchScanItem(emptyList(), card("a"))
        items = mergeBatchScanItem(items, card("b"))
        items = adjustBatchItemCount(items, "a", -1)
        assertEquals(listOf("b"), items.map { it.card.id })
        items = adjustBatchItemCount(items, "b", +2)
        assertEquals(3, items.single().count)
        // Unknown ids leave the list untouched.
        assertEquals(items, adjustBatchItemCount(items, "unknown", +5))
        assertTrue(adjustBatchItemCount(emptyList(), "a", +1).isEmpty())
    }
}
