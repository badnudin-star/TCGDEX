package com.pokemontcg.sammlung

import com.pokemontcg.sammlung.ui.sets.setBadgeColorIndex
import com.pokemontcg.sammlung.ui.sets.setBadgeLabel
import com.pokemontcg.sammlung.ui.sets.setLabelWithAbbreviation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SetBadgeLogicTest {

    @Test
    fun officialAbbreviationWins() {
        assertEquals("SVI", setBadgeLabel("SVI", "Scarlet & Violet Base"))
        assertEquals("BS", setBadgeLabel("BS", "Base Set"))
    }

    @Test
    fun longAbbreviationsAreTruncated() {
        assertEquals("SV01", setBadgeLabel("SV01-A", "Karmesin & Purpur"))
    }

    @Test
    fun missingAbbreviationFallsBackToTheFirstNameWord() {
        assertEquals("KAR", setBadgeLabel(null, "Karmesin & Purpur"))
        assertEquals("SCA", setBadgeLabel(null, "Scarlet & Violet"))
        assertEquals("JUN", setBadgeLabel(null, "Jungle"))
        assertEquals("OBS", setBadgeLabel("", "Obsidian Flames"))
    }

    @Test
    fun separatorsAreTreatedAsWordBoundaries() {
        assertEquals("SV", setBadgeLabel(null, "SV-Promo"))
        assertEquals("CRO", setBadgeLabel(null, "Crown Zenith - Galar"))
    }

    @Test
    fun completelyMissingDataShowsAPlaceholder() {
        assertEquals("?", setBadgeLabel(null, null))
        assertEquals("?", setBadgeLabel("   ", "   "))
    }

    @Test
    fun colorIndexIsDeterministicAndWithinPalette() {
        assertEquals(
            setBadgeColorIndex("sv01", 6),
            setBadgeColorIndex("sv01", 6)
        )
        assertTrue(setBadgeColorIndex("sv01", 6) in 0 until 6)
        assertTrue(setBadgeColorIndex(null, 6) in 0 until 6)
        assertTrue(setBadgeColorIndex("base1", 1) == 0)
        // Non-empty ids produce an index, empty/none falls back to a stable slot too.
        assertTrue(setBadgeColorIndex("", 3) in 0 until 3)
        assertEquals(0, setBadgeColorIndex("sv01", 0))
    }

    @Test
    fun setLabelAppendsTheAbbreviation() {
        assertEquals("Karmesin & Purpur (SVI)", setLabelWithAbbreviation("Karmesin & Purpur", "SVI"))
        assertEquals("Jungle (JU)", setLabelWithAbbreviation("Jungle", "JU"))
    }

    @Test
    fun setLabelHandlesMissingParts() {
        assertEquals("Karmesin & Purpur", setLabelWithAbbreviation("Karmesin & Purpur", null))
        assertEquals("Karmesin & Purpur", setLabelWithAbbreviation("Karmesin & Purpur", "   "))
        assertEquals("SVI", setLabelWithAbbreviation(null, "SVI"))
        assertEquals("", setLabelWithAbbreviation(null, null))
    }

    @Test
    fun differentSetsUsuallyGetDifferentColors() {
        val indices = (1..40).map { setBadgeColorIndex("set-$it", 6) }.toSet()
        // With six palette slots and 40 sets, at least four distinct colors must be used.
        assertTrue(indices.size >= 4)
    }
}
