package com.pokemontcg.sammlung.ui.wishlist

import com.pokemontcg.sammlung.data.local.entities.CardEntity
import com.pokemontcg.sammlung.data.local.user.WishlistEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WishlistTest {

    @Test
    fun testIsTargetReached() {
        // Price <= target -> reached
        assertTrue(isTargetReached(10.0, 15.0))
        assertTrue(isTargetReached(15.0, 15.0))

        // Price > target -> not reached
        assertFalse(isTargetReached(20.0, 15.0))

        // Null handling
        assertFalse(isTargetReached(null, 15.0))
        assertFalse(isTargetReached(10.0, null))
        assertFalse(isTargetReached(null, null))
    }

    @Test
    fun testSortWishlist() {
        val w1 = WishlistEntity("card1", targetPrice = 20.0, addedAt = 1000L)
        val w2 = WishlistEntity("card2", targetPrice = 10.0, addedAt = 2000L)

        val card1 = CardEntity("card1", "Card 1", "", "1", null, "s1", "S1", "Series", null, null, 15.0, null, null, 1, null, "de")
        val card2 = CardEntity("card2", "Card 2", "", "2", null, "s1", "S1", "Series", null, null, 5.0, null, null, 2, null, "de")

        val cardMap = mapOf("card1" to card1, "card2" to card2)
        val list = listOf(w1, w2)

        // Added desc -> w2 first (2000 > 1000)
        val sortedAdded = sortWishlist(list, cardMap, WishlistSortOption.ADDED_DESC)
        assertEquals("card2", sortedAdded[0].cardId)

        // Price asc -> card2 marketPrice 5.0 < card1 15.0 -> w2 first
        val sortedPrice = sortWishlist(list, cardMap, WishlistSortOption.PRICE_ASC)
        assertEquals("card2", sortedPrice[0].cardId)

        // Target reached first -> card2 (marketPrice 5.0 <= target 10.0) is reached, card1 (15.0 <= 20.0) is also reached!
        // Suppose card1 target was 12.0 (so 15.0 > 12.0 = not reached)
        val w1NotReached = w1.copy(targetPrice = 12.0)
        val sortedReached = sortWishlist(listOf(w1NotReached, w2), cardMap, WishlistSortOption.TARGET_REACHED_FIRST)
        assertEquals("card2", sortedReached[0].cardId)
    }
}
