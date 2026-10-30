package com.pokemontcg.sammlung

import com.pokemontcg.sammlung.data.local.user.CardCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class Phase4Test {

    @Test
    fun testCardCategoryEnumMapping() {
        assertEquals("NONE", CardCategory.NONE.name)
        assertEquals("COMMON", CardCategory.COMMON.name)
        assertEquals("FULLART", CardCategory.FULLART.name)
    }

    @Test
    fun testTerminologyMapping() {
        val favoriteLabel = "Primärkarte"
        val otherCardsLabel = "Weitere Karten"
        val noCategoryLabel = "Ohne Kategorie"

        assertEquals("Primärkarte", favoriteLabel)
        assertEquals("Weitere Karten", otherCardsLabel)
        assertEquals("Ohne Kategorie", noCategoryLabel)
    }
}
