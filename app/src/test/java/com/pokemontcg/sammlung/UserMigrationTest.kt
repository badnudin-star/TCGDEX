package com.pokemontcg.sammlung

import com.pokemontcg.sammlung.data.local.user.CardCategory
import com.pokemontcg.sammlung.data.local.user.UserMigrationHelper
import org.junit.Assert.assertEquals
import org.junit.Test

class UserMigrationTest {

    @Test
    fun testCategoryDerivationFromCommonFlag() {
        val category = UserMigrationHelper.deriveCategory(
            isCommon = true,
            isFullart = false,
            pokemonCardType = "Nichts"
        )
        assertEquals(CardCategory.COMMON, category)
    }

    @Test
    fun testCategoryDerivationFromFullartFlag() {
        val category = UserMigrationHelper.deriveCategory(
            isCommon = false,
            isFullart = true,
            pokemonCardType = "Nichts"
        )
        assertEquals(CardCategory.FULLART, category)
    }

    @Test
    fun testCategoryDerivationFromPokemonType() {
        val categoryCommon = UserMigrationHelper.deriveCategory(
            isCommon = false,
            isFullart = false,
            pokemonCardType = "Common"
        )
        assertEquals(CardCategory.COMMON, categoryCommon)

        val categoryFullart = UserMigrationHelper.deriveCategory(
            isCommon = false,
            isFullart = false,
            pokemonCardType = "Fullart"
        )
        assertEquals(CardCategory.FULLART, categoryFullart)
    }

    @Test
    fun testCategoryDerivationFallbackNone() {
        val category = UserMigrationHelper.deriveCategory(
            isCommon = false,
            isFullart = false,
            pokemonCardType = "Nichts"
        )
        assertEquals(CardCategory.NONE, category)
    }
}
