package com.pokemontcg.sammlung

import com.pokemontcg.sammlung.ui.cards.extractCardNumber
import com.pokemontcg.sammlung.ui.cards.extractOcrSearchTokens
import com.pokemontcg.sammlung.ui.cards.foldOcrAccents
import com.pokemontcg.sammlung.ui.cards.splitPrefixedNumber
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
    fun extractsJapaneseReversedPromoNumbers() {
        // Japanese promo prints put the set prefix behind the number.
        assertEquals("SV-P001", extractCardNumber("ピカチュウ 001/SV-P"))
        assertEquals("SM-P203", extractCardNumber("203/SM-P"))
        assertEquals("S-P001", extractCardNumber("001/S-P"))
        // Reversed runs before the fraction branch: "001/S-P" must not become "001/5".
        assertEquals("S-P016", extractCardNumber("016/S-P"))
    }

    @Test
    fun plainFractionsStillWinWhenNoSetPrefixIsPresent() {
        assertEquals("016/102", extractCardNumber("ピカチュウ 016/102"))
    }

    @Test
    fun splitsPrefixedNumbersIntoSetIdAndPlainNumber() {
        assertEquals("SV-P" to "001", splitPrefixedNumber("SV-P001"))
        assertEquals("SWSH" to "001", splitPrefixedNumber("SWSH001"))
        assertEquals("SM-P" to "203", splitPrefixedNumber("sm-p203"))
        assertNull(splitPrefixedNumber("025/198"))
        assertNull(splitPrefixedNumber("GLURAK"))
    }

    @Test
    fun foldsLatinDiacriticsForAccentInsensitiveSearch() {
        assertEquals("EVOLI", foldOcrAccents("Évoli"))
        assertEquals("ENERGIE", foldOcrAccents("ÉNERGIE"))
        assertEquals("PIKACHU", foldOcrAccents("Pikachu"))
        assertEquals("皮卡丘", foldOcrAccents("皮卡丘"))
    }

    @Test
    fun ignoresMultilingualLayoutBoilerplate() {
        // French/Spanish/Italian layout words must not burn the token budget...
        assertEquals(
            listOf("ÉVOLI"),
            extractOcrSearchTokens("ÉVOLI TALENT ATTAQUE FAIBLESSE RETRAITE ÉNERGIE")
        )
        assertEquals(emptyList<String>(), extractOcrSearchTokens("ENERGÍA RETIRADA DEBILIDAD"))
        // ...while Asian layout words step aside for real card names.
        assertEquals(listOf("ピカチュウ"), extractOcrSearchTokens("ポケモン ピカチュウ トレーナー エネルギー"))
        assertEquals(listOf("皮卡丘"), extractOcrSearchTokens("宝可梦 皮卡丘 训练家 能量"))
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
        // embedded card name searchable for the catalog LIKE search. A trigram that
        // exactly equals a layout word ("宝可梦") is filtered just like the standalone
        // token would be - it is never part of a card name.
        val tokens = extractOcrSearchTokens("宝可梦卡牌皮卡丘基本宝可梦", limit = 4)
        assertEquals(listOf("可梦卡", "梦卡牌", "卡牌皮", "牌皮卡"), tokens)
    }

    @Test
    fun mixedScriptsKeepLatinAndCjkTokens() {
        val tokens = extractOcrSearchTokens("Pikachu 皮卡丘 025/198", limit = 4)
        // Numbers are searched through extractCardNumber, not as name tokens.
        assertEquals(listOf("PIKACHU", "皮卡丘"), tokens)
    }
}
