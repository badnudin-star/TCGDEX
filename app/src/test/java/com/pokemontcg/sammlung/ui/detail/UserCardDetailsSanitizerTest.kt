package com.pokemontcg.sammlung.ui.detail

import com.pokemontcg.sammlung.data.local.user.CardCategory
import com.pokemontcg.sammlung.data.local.user.CardCondition
import com.pokemontcg.sammlung.data.local.user.UserCardEntityV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserCardDetailsSanitizerTest {

    private val original = UserCardEntityV2(
        cardId = "card-1",
        pokemonId = "1",
        pokemonDexId = 1,
        language = "de",
        quantity = 1,
        condition = CardCondition.MINT.name,
        notes = "original",
        category = CardCategory.COMMON.name
    )

    private fun sanitize(
        quantity: Int = 1,
        condition: String? = null,
        language: String = "de",
        notes: String? = null,
        category: String = CardCategory.NONE.name
    ): UserCardEntityV2 = sanitizeUserCardDetails(
        userCard = original,
        quantity = quantity,
        condition = condition,
        language = language,
        notes = notes,
        category = category
    )

    @Test
    fun quantityIsClampedToValidRange() {
        assertEquals(1, sanitize(quantity = 0).quantity)
        assertEquals(1, sanitize(quantity = 1).quantity)
        assertEquals(999, sanitize(quantity = 999).quantity)
        assertEquals(999, sanitize(quantity = 1000).quantity)
    }

    @Test
    fun notesAreTrimmedAndBlankNotesBecomeNull() {
        assertNull(sanitize(notes = "").notes)
        assertNull(sanitize(notes = "   ").notes)
        assertEquals("Test", sanitize(notes = "  Test  ").notes)
    }

    @Test
    fun notesAreLimitedToFiveHundredCharacters() {
        val notes = "a".repeat(501)

        val sanitized = sanitize(notes = notes)

        assertEquals(500, sanitized.notes?.length)
        assertEquals("a".repeat(500), sanitized.notes)
    }

    @Test
    fun invalidConditionBecomesNull() {
        assertNull(sanitize(condition = "NOT_A_CONDITION").condition)
        assertNull(sanitize(condition = null).condition)
    }

    @Test
    fun validConditionIsKept() {
        assertEquals(
            CardCondition.NEAR_MINT.name,
            sanitize(condition = CardCondition.NEAR_MINT.name).condition
        )
    }

    @Test
    fun invalidLanguageKeepsStoredValue() {
        assertEquals("de", sanitize(language = "xx").language)
        assertEquals("de", sanitize(language = "").language)
    }

    @Test
    fun validLanguageIsKept() {
        assertEquals("ja", sanitize(language = "ja").language)
    }

    @Test
    fun invalidCategoryKeepsStoredValue() {
        assertEquals(CardCategory.COMMON.name, sanitize(category = "UNKNOWN").category)
        assertEquals(CardCategory.COMMON.name, sanitize(category = "").category)
    }

    @Test
    fun validCategoryIsKept() {
        assertEquals(
            CardCategory.FULLART.name,
            sanitize(category = CardCategory.FULLART.name).category
        )
    }
}
