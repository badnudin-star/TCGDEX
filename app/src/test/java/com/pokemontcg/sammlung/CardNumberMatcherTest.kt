package com.pokemontcg.sammlung

import com.pokemontcg.sammlung.ui.cards.extractCardNumber
import com.pokemontcg.sammlung.ui.cards.extractOcrSearchTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardNumberMatcherTest {
    @Test
    fun extractsFractionAndRemovesWhitespace() {
        assertEquals("025/198", extractCardNumber("Pikachu 025 / 198 Illustration"))
    }

    @Test
    fun extractsInternationalSetPrefixedNumber() {
        assertEquals("SWSH001", extractCardNumber("SWSH 001"))
        assertEquals("SV012", extractCardNumber("SV012"))
    }

    @Test
    fun extractsAsianPromoPrefixesWithoutLosingSetIdentity() {
        assertEquals("SV-P001", extractCardNumber("Pikachu SV-P 001"))
        assertEquals("S-P001", extractCardNumber("S-P 001 promo"))
        assertEquals("SM-P001", extractCardNumber("SM-P 001"))
        assertEquals("SVP001", extractCardNumber("SVP 001"))
        assertEquals("PROMO001", extractCardNumber("PROMO 001"))
    }

    @Test
    fun extractsUsefulNameTokensForNumberlessOcr() {
        assertEquals(listOf("PIKACHU", "VIVID", "VOLTAGE"), extractOcrSearchTokens("Pokémon PIKACHU Vivid Voltage HP 120"))
    }

    @Test
    fun ignoresGenericCardLabelsAndHonorsTokenLimit() {
        assertEquals(listOf("CHARIZARD", "OBSIDIAN"), extractOcrSearchTokens("BASIC Charizard Obsidian Flames HP 330", limit = 2))
        assertEquals(emptyList<String>(), extractOcrSearchTokens("Pokémon BASIC HP"))
        assertEquals(emptyList<String>(), extractOcrSearchTokens("Pokémon Trading Card Game"))
    }

    @Test
    fun ignoresTextWithoutSupportedCardNumber() {
        assertNull(extractCardNumber("Glurak seltene Karte"))
        assertNull(extractCardNumber("2025 edition"))
    }
}
