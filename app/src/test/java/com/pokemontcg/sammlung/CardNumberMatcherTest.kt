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

    @Test
    fun normalizesOcrLookalikeLettersInsideFractions() {
        assertEquals("12/102", extractCardNumber("Pikachu 12/1O2 Illustration"))
        assertEquals("001/024", extractCardNumber("001/O24"))
        assertEquals("025/198", extractCardNumber("O25/198"))
    }

    @Test
    fun normalizesOcrLookalikeLettersInsidePrefixedNumbers() {
        assertEquals("SV-P001", extractCardNumber("Pikachu SV-P OO1"))
        assertEquals("SWSH001", extractCardNumber("SWSH OOl"))
    }

    @Test
    fun rejectsLetterOnlyMatchesThatMimicNumbers() {
        // Set names and words must not turn into numbers just because they contain
        // digit lookalikes.
        assertNull(extractCardNumber("Scarlet & Violet SVI Base Set"))
        assertNull(extractCardNumber("Input I/O Fehler"))
        assertNull(extractCardNumber("S/B"))
    }

    @Test
    fun rejectsImplausibleFractions() {
        // Card numbers are n/m with 0 < n <= m; "5/2" is OCR noise, not a number.
        assertNull(extractCardNumber("Attack 5/2 Energie"))
    }
}
