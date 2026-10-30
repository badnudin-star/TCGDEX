package com.pokemontcg.sammlung.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class CardSortOrderTest {

    @Test
    fun sortsPlainNumbersNumerically() {
        val ids = CardSortOrder.sortedIds(
            listOf(
                CardSortOrder.Entry("ten", "10"),
                CardSortOrder.Entry("two", "2"),
                CardSortOrder.Entry("one", "1"),
            )
        )
        assertEquals(listOf("one", "two", "ten"), ids)
    }

    @Test
    fun sortsPrefixedNumbersAfterPlainNumbers() {
        val ids = CardSortOrder.sortedIds(
            listOf(
                CardSortOrder.Entry("svp", "SVP 001"),
                CardSortOrder.Entry("plain", "2"),
                CardSortOrder.Entry("gallery", "GG01"),
            )
        )
        assertEquals(listOf("plain", "gallery", "svp"), ids)
    }

    @Test
    fun sortsSuffixesAfterThePlainNumber() {
        val ids = CardSortOrder.sortedIds(
            listOf(
                CardSortOrder.Entry("b", "1b"),
                CardSortOrder.Entry("plain", "1"),
                CardSortOrder.Entry("a", "1a"),
            )
        )
        assertEquals(listOf("plain", "a", "b"), ids)
    }

    @Test
    fun usesIdAsStableTieBreaker() {
        val ids = CardSortOrder.sortedIds(
            listOf(
                CardSortOrder.Entry("b", "1"),
                CardSortOrder.Entry("a", "1"),
            )
        )
        assertEquals(listOf("a", "b"), ids)
    }
}
