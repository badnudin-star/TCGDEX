package com.pokemontcg.sammlung.ui.detail

import com.pokemontcg.sammlung.data.local.user.WishlistEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WishlistToggleTest {

    private class FakeWishlistDao {
        val entries = mutableListOf<WishlistEntity>()

        suspend fun load(): List<WishlistEntity> = entries.toList()

        suspend fun add(wish: WishlistEntity) {
            entries.removeAll { it.cardId == wish.cardId }
            entries.add(wish)
        }

        suspend fun remove(cardId: String) {
            entries.removeAll { it.cardId == cardId }
        }
    }

    @Test
    fun cardOutsideWishlistIsAdded() {
        assertEquals(
            WishlistToggleAction.ADD,
            wishlistToggleAction("card-1", emptySet())
        )
    }

    @Test
    fun cardInsideWishlistIsRemoved() {
        assertEquals(
            WishlistToggleAction.REMOVE,
            wishlistToggleAction("card-1", setOf("card-1", "card-2"))
        )
    }

    @Test
    fun unrelatedCardIsStillAdded() {
        assertEquals(
            WishlistToggleAction.ADD,
            wishlistToggleAction("card-3", setOf("card-1", "card-2"))
        )
    }

    @Test
    fun exactCardIdMatchIsRequired() {
        assertEquals(
            WishlistToggleAction.ADD,
            wishlistToggleAction("card-1", setOf("card-10"))
        )
    }

    @Test
    fun addingUsesSpecifiedDefaults() = runBlocking {
        val dao = FakeWishlistDao()

        applyWishlistToggle(
            cardId = "card-1",
            loadWishlist = dao::load,
            add = dao::add,
            remove = dao::remove,
            now = 123L
        )

        assertEquals(1, dao.entries.size)
        val added = dao.entries.single()
        assertEquals("card-1", added.cardId)
        assertNull(added.targetPrice)
        assertEquals("", added.notes)
        assertEquals(123L, added.addedAt)
    }

    @Test
    fun removingExistingEntryDoesNotAddItAgain() = runBlocking {
        val dao = FakeWishlistDao()
        dao.entries.add(WishlistEntity(cardId = "card-1", notes = "alt", addedAt = 1L))

        applyWishlistToggle(
            cardId = "card-1",
            loadWishlist = dao::load,
            add = dao::add,
            remove = dao::remove,
            now = 123L
        )

        assertTrue(dao.entries.isEmpty())
    }
}
