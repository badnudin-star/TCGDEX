package com.pokemontcg.sammlung.ui.main

import com.pokemontcg.sammlung.data.local.user.CardCategory
import com.pokemontcg.sammlung.data.local.user.UserCardEntityV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryDerivationTest {

    private fun card(
        cardId: String,
        dexId: Int,
        category: CardCategory,
        isPrimary: Boolean = false
    ) = UserCardEntityV2(
        cardId = cardId,
        pokemonId = dexId.toString(),
        pokemonDexId = dexId,
        category = category.name,
        isPrimary = isPrimary
    )

    @Test
    fun emptyCollectionProducesEmptyMap() {
        assertTrue(deriveCategoryByDex(emptyList()).isEmpty())
    }

    @Test
    fun primaryCategoryWinsOverOtherCards() {
        val cards = listOf(
            card("secondary-1", 1, CardCategory.COMMON),
            card("primary", 1, CardCategory.FULLART, isPrimary = true),
            card("secondary-2", 1, CardCategory.NONE)
        )

        assertEquals(
            mapOf(1 to CardCategory.FULLART),
            deriveCategoryByDex(cards)
        )
    }

    @Test
    fun primaryNoneStillWinsOverSecondaryCategories() {
        val cards = listOf(
            card("secondary", 1, CardCategory.FULLART),
            card("primary", 1, CardCategory.NONE, isPrimary = true)
        )

        assertEquals(
            mapOf(1 to CardCategory.NONE),
            deriveCategoryByDex(cards)
        )
    }

    @Test
    fun firstNonNoneCategoryWinsWithoutPrimary() {
        val cards = listOf(
            card("none", 1, CardCategory.NONE),
            card("common", 1, CardCategory.COMMON),
            card("fullart", 1, CardCategory.FULLART)
        )

        assertEquals(
            mapOf(1 to CardCategory.COMMON),
            deriveCategoryByDex(cards)
        )
    }

    @Test
    fun onlyNoneCardsRemainNone() {
        val cards = listOf(
            card("none-1", 1, CardCategory.NONE),
            card("none-2", 1, CardCategory.NONE)
        )

        assertEquals(
            mapOf(1 to CardCategory.NONE),
            deriveCategoryByDex(cards)
        )
    }

    @Test
    fun categoriesAreDerivedIndependentlyPerDexId() {
        val cards = listOf(
            card("one-primary", 1, CardCategory.FULLART, isPrimary = true),
            card("two-secondary", 2, CardCategory.COMMON),
            card("three-none", 3, CardCategory.NONE)
        )

        assertEquals(
            mapOf(
                1 to CardCategory.FULLART,
                2 to CardCategory.COMMON,
                3 to CardCategory.NONE
            ),
            deriveCategoryByDex(cards)
        )
    }
}
