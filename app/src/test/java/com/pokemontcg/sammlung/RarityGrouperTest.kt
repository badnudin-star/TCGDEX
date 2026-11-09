package com.pokemontcg.sammlung

import com.pokemontcg.sammlung.data.model.RarityGrouper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Localized rarity labels (delivered by the TCGdex API for de/fr/es/it/pt cards) must
 * collapse into the canonical English groups instead of showing up as separate chips.
 */
class RarityGrouperTest {

    @Test
    fun localizedRaritiesJoinTheEnglishGroups() {
        assertEquals("Common", RarityGrouper.canonical("Häufig"))
        assertEquals("Common", RarityGrouper.canonical("Commune"))
        assertEquals("Common", RarityGrouper.canonical("Común"))
        assertEquals("Common", RarityGrouper.canonical("Comune"))
        assertEquals("Common", RarityGrouper.canonical("Comum"))
        assertEquals("Uncommon", RarityGrouper.canonical("Ungewöhnlich"))
        assertEquals("Uncommon", RarityGrouper.canonical("Peu Commune"))
        assertEquals("Holo Rare", RarityGrouper.canonical("Holografisch Selten"))
        assertEquals("Ultra Rare", RarityGrouper.canonical("Couronne"))
    }

    @Test
    fun chipGroupsNeverContainLanguageVariants() {
        val raw = listOf("Common", "Häufig", "Commune", "Común", "Uncommon", "Ungewöhnlich",
                         "Holo Rare", "Holografisch Selten", "Rare", "Unbekannte Seltenheit")
        val chips = RarityGrouper.chipGroups(raw)
        assertEquals(listOf("Common", "Uncommon", "Rare", "Holo Rare", "Unbekannte Seltenheit"), chips)
        // The English groups appear exactly once even with five languages feeding them.
        assertEquals(1, chips.count { it == "Common" })
    }

    @Test
    fun rawValuesForSelectionMatchesAllLanguages() {
        val raw = listOf("Common", "Häufig", "Commune", "Común", "Uncommon")
        val selected = RarityGrouper.rawValuesFor(setOf("Common"), raw)
        assertEquals(setOf("Common", "Häufig", "Commune", "Común"), selected)
    }

    @Test
    fun englishKeysStillGroupUnchanged() {
        assertEquals("Holo Rare", RarityGrouper.canonical("Holo Rare VMAX"))
        assertEquals("Ultra Rare", RarityGrouper.canonical("Hyper rare"))
        assertEquals("None", RarityGrouper.canonical(null))
        assertEquals("None", RarityGrouper.canonical("  "))
        assertTrue(RarityGrouper.canonical("Double rare").isNotEmpty())
    }
}
