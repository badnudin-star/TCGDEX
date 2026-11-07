package com.pokemontcg.sammlung.ui.wishlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WishlistSearchParamsTest {

    @Test
    fun germanAndEnglishDecimalSeparatorsBothParse() {
        assertEquals(12.5, WishlistSearchParams(maxPriceText = "12,50").maxPrice!!, 0.0001)
        assertEquals(12.5, WishlistSearchParams(maxPriceText = "12.50").maxPrice!!, 0.0001)
        assertEquals(20.0, WishlistSearchParams(maxPriceText = "  20  ").maxPrice!!, 0.0001)
        assertEquals(12.5, parseMaxPrice("12,5")!!, 0.0001)
    }

    @Test
    fun unusablePriceTextDisablesTheCeiling() {
        assertNull(WishlistSearchParams(maxPriceText = "").maxPrice)
        assertNull(WishlistSearchParams(maxPriceText = "   ").maxPrice)
        assertNull(WishlistSearchParams(maxPriceText = "abc").maxPrice)
        assertNull("Zero would hide every card", WishlistSearchParams(maxPriceText = "0").maxPrice)
        assertNull(WishlistSearchParams(maxPriceText = "-5").maxPrice)
    }

    @Test
    fun filterStateIsOnlyActiveWhenSomethingIsFilledIn() {
        assertFalse(WishlistSearchParams().hasActiveFilter)
        assertFalse(WishlistSearchParams(query = "   ").hasActiveFilter)
        assertTrue(WishlistSearchParams(query = "pikachu").hasActiveFilter)
        assertTrue(WishlistSearchParams(rarities = setOf("Common")).hasActiveFilter)
        assertTrue(WishlistSearchParams(maxPriceText = "10").hasActiveFilter)
        // Unparsable price text still counts: the user typed something and expects a reset option.
        assertTrue(WishlistSearchParams(maxPriceText = "abc").hasActiveFilter)
    }

    @Test
    fun thousandSeparatorsAreNormalized() {
        assertEquals(1234.56, WishlistSearchParams(maxPriceText = "1.234,56").maxPrice!!, 0.0001)
        assertEquals(1234.56, WishlistSearchParams(maxPriceText = "1,234.56").maxPrice!!, 0.0001)
        assertEquals(1234.0, parseMaxPrice("1.234")!!, 0.0001)
    }

    @Test
    fun inputIsLimitedToDigitsAndSeparators() {
        val typed = "1.234,56 €"
        val accepted = typed.filter { it.isDigit() || it == ',' || it == '.' }

        assertEquals("1.234,56", accepted)
        assertEquals(1234.56, WishlistSearchParams(maxPriceText = accepted).maxPrice!!, 0.0001)
    }
}
