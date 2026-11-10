package com.pokemontcg.sammlung

import com.pokemontcg.sammlung.ui.cards.detectOcrLanguageClue
import com.pokemontcg.sammlung.ui.cards.needsScriptFallback
import com.pokemontcg.sammlung.ui.cards.pickScriptFallbackText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerOcrChainTest {

    // --- needsScriptFallback ---

    @Test
    fun fallbackRunsWhenTheLatinPassFindsNoNumber() {
        assertTrue(needsScriptFallback("Pikachu Illustration some unreadable text"))
    }

    @Test
    fun noFallbackWhenTheLatinPassIdentifiesTheCard() {
        // Number plus readable name tokens: the Latin model did its job.
        assertFalse(needsScriptFallback("Pikachu 025/198 Vivid Voltage"))
    }

    @Test
    fun fallbackRunsForNumberOnlyLatinText() {
        // A number without any name clue is the signature of an Asian print whose
        // name the Latin model cannot see - the script models may recover the name.
        assertTrue(needsScriptFallback("025/198"))
    }

    @Test
    fun noFallbackWhenTheLatinTextAlreadyCarriesCjk() {
        assertFalse(needsScriptFallback("ピカチュウ 025/198"))
    }

    // --- pickScriptFallbackText ---

    @Test
    fun picksTheCjkRichestScriptText() {
        val korean = "피카츄"
        val japanese = "ピカチュウ ポケモンカードゲーム 2024"
        assertEquals(japanese, pickScriptFallbackText(listOf(korean, japanese)))
    }

    @Test
    fun returnsNullWhenNoScriptTextContainsCjk() {
        assertNull(pickScriptFallbackText(listOf("", "025/198", "Pikachu")))
        assertNull(pickScriptFallbackText(emptyList()))
    }

    @Test
    fun breaksLengthTiesDeterministically() {
        val a = "ピカチュウ"
        val b = "ライチュウ"
        // Equal CJK count and length: the earlier pass wins (maxWith keeps the first).
        assertEquals(a, pickScriptFallbackText(listOf(a, b)))
        assertEquals(b, pickScriptFallbackText(listOf(b, a)))
    }

    // --- detectOcrLanguageClue ---

    @Test
    fun kanaImpliesJapaneseEvenWithKanjiPresent() {
        assertEquals("ja", detectOcrLanguageClue("ピカチュウ でんき"))
        assertEquals("ja", detectOcrLanguageClue("電気 ピカチュウ"))
    }

    @Test
    fun hangulImpliesKorean() {
        assertEquals("ko", detectOcrLanguageClue("피카츄 포켓몬"))
    }

    @Test
    fun hanWithoutKanaImpliesChinese() {
        assertEquals("zh", detectOcrLanguageClue("皮卡丘 基本宝可梦"))
    }

    @Test
    fun cyrillicImpliesRussian() {
        assertEquals("ru", detectOcrLanguageClue("Пикачу покемон"))
    }

    @Test
    fun latinTextYieldsNoLanguageClue() {
        assertNull(detectOcrLanguageClue("Pikachu 025/198 Vivid Voltage"))
        assertNull(detectOcrLanguageClue(""))
    }
}
