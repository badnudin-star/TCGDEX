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

    @Test
    fun shortCjkRunsAreSearchTokensWhileShortLatinIsNot() {
        // Chinese card names are two or three characters; Latin noise needs four.
        assertEquals(listOf("皮卡丘"), extractOcrSearchTokens("皮卡丘 HP 60", limit = 4))
        assertEquals(listOf("超梦"), extractOcrSearchTokens("超梦", limit = 4))
        assertEquals(listOf("ABCD"), extractOcrSearchTokens("ABCD HP 60 DEF", limit = 4))
        // Short Latin fragments stay filtered out (minimum length four).
        assertEquals(emptyList<String>(), extractOcrSearchTokens("ABC HP 60 no", limit = 4))
    }

    @Test
    fun longCjkRunsBecomeSlidingTrigrams() {
        // The Chinese recognizer returns whole undivided lines; trigrams keep the
        // embedded card name searchable for the catalog LIKE search.
        val tokens = extractOcrSearchTokens("宝可梦卡牌皮卡丘基本宝可梦", limit = 4)
        assertEquals(listOf("宝可梦", "可梦卡", "梦卡牌", "卡牌皮"), tokens)
    }

    @Test
    fun mixedScriptsKeepLatinAndCjkTokens() {
        val tokens = extractOcrSearchTokens("Pikachu 皮卡丘 025/198", limit = 4)
        // Numbers are searched through extractCardNumber, not as name tokens.
        assertEquals(listOf("PIKACHU", "皮卡丘"), tokens)
    }
}
