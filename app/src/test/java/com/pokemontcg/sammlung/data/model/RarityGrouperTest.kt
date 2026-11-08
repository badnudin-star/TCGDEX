package com.pokemontcg.sammlung.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RarityGrouperTest {

    @Test
    fun everyKnownCatalogRarityLandsInACanonicalGroup() {
        val rawValues = listOf(
            "ACE SPEC Rare", "Amazing Rare", "Black White Rare", "Character Rare",
            "Character Super Rare", "Classic Collection", "Common", "Crown", "Double rare",
            "Four Diamond", "Full Art Trainer", "Futuristic Rare", "Holo Rare", "Holo Rare V",
            "Holo Rare VMAX", "Holo Rare VSTAR", "Hyper rare", "Illustration rare", "LEGEND",
            "Mega Attack Rare", "Mega Hyper Rare", "None", "One Diamond", "One Shiny", "One Star",
            "Pikachu Rare", "Promo", "RGB Rare", "Radiant Rare", "Rare", "Rare Holo",
            "Rare Holo LV.X", "Rare PRIME", "Secret Rare", "Shiny Ultra Rare", "Shiny rare",
            "Shiny rare V", "Shiny rare VMAX", "Special illustration rare", "Three Diamond",
            "Three Star", "Triple Rare", "Two Diamond", "Two Shiny", "Two Star", "Ultra Rare",
            "Uncommon"
        )

        rawValues.forEach { raw ->
            val group = RarityGrouper.canonical(raw)
            assertTrue(
                "unexpected group '$group' for '$raw'",
                group in RarityGrouper.CANONICAL_ORDER
            )
        }
    }

    @Test
    fun spellingDriftCollapsesToTheSameGroup() {
        assertEquals(RarityGrouper.canonical("Holo Rare"), RarityGrouper.canonical("Rare Holo"))
        assertEquals(RarityGrouper.canonical("Holo Rare"), RarityGrouper.canonical("Double rare"))
        assertEquals(
            RarityGrouper.canonical("Illustration rare"),
            RarityGrouper.canonical("Special illustration rare")
        )
        assertEquals(RarityGrouper.canonical("Ultra Rare"), RarityGrouper.canonical("Hyper rare"))
    }

    @Test
    fun japaneseSymbolRaritiesMapToSensibleGroups() {
        assertEquals("Uncommon", RarityGrouper.canonical("One Diamond"))
        assertEquals("Holo Rare", RarityGrouper.canonical("Three Star"))
        assertEquals("Holo Rare", RarityGrouper.canonical("Two Shiny"))
        assertEquals("Ultra Rare", RarityGrouper.canonical("Crown"))
    }

    @Test
    fun blankRaritiesBecomeNone() {
        assertEquals("None", RarityGrouper.canonical(null))
        assertEquals("None", RarityGrouper.canonical(""))
        assertEquals("None", RarityGrouper.canonical("   "))
    }

    @Test
    fun unknownFutureRaritiesFallBackToSubstringRules() {
        assertEquals("Illustration Rare", RarityGrouper.canonical("Ultra Illustration rare"))
        assertEquals("Holo Rare", RarityGrouper.canonical("Amazing Holo Rare"))
        assertEquals("Promo", RarityGrouper.canonical("Promo S"))
        // "promo" wins over "uncommon" because it is tested first.
        assertEquals("Promo", RarityGrouper.canonical("Uncommon Promo"))
        // Unknown values that still contain a known keyword fall into that keyword's group.
        assertEquals("Rare", RarityGrouper.canonical("Coin Rare"))
        // Only values without any known keyword become their own group.
        assertEquals("Zenith", RarityGrouper.canonical("Zenith"))
    }

    @Test
    fun rawValuesForExpandsSelectedGroupsToCatalogValues() {
        val available = listOf("Common", "Uncommon", "Rare", "Holo Rare", "Double rare", "Hyper rare")

        val expanded = RarityGrouper.rawValuesFor(
            selectedGroups = setOf("Holo Rare", "Ultra Rare"),
            availableRaw = available
        )

        assertEquals(setOf("Holo Rare", "Double rare", "Hyper rare"), expanded)
        // An empty selection means "no rarity filter", not "match nothing".
        assertTrue(RarityGrouper.rawValuesFor(emptySet(), available).isEmpty())
    }

    @Test
    fun chipGroupsShowKnownGroupsFirstAndUnknownOnesAfter() {
        val available = listOf("Hyper rare", "Common", "Illustration rare", "Zenith", "None")

        val chips = RarityGrouper.chipGroups(available)

        assertEquals(listOf("None", "Common", "Ultra Rare", "Illustration Rare", "Zenith"), chips)
    }
}
