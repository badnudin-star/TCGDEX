package com.pokemontcg.sammlung

import com.pokemontcg.sammlung.ui.cards.CardMatchCandidate
import com.pokemontcg.sammlung.ui.cards.rankCardMatches
import org.junit.Assert.assertEquals
import org.junit.Test

class CardMatchRankerTest {
    private val numberOnly = CardMatchCandidate("b", "Pikachu", "Set Beta", "025/198")
    private val nameAndSet = CardMatchCandidate("a", "Pikachu VMAX", "Vivid Voltage", "025/198")

    @Test
    fun ranksNameAndSetEvidenceAheadOfAlphabeticalFallback() {
        val ranked = rankCardMatches(
            listOf(numberOnly, nameAndSet),
            "025/198",
            "PIKACHU VMAX VIVID VOLTAGE 025/198"
        )

        assertEquals(listOf("a", "b"), ranked.map { it.id })
    }

    @Test
    fun exactScannedNumberHasPriorityOverNameClues() {
        val wrongNumber = numberOnly.copy(number = "025/199", name = "Pikachu VMAX", setName = "Vivid Voltage")
        val ranked = rankCardMatches(
            listOf(wrongNumber, nameAndSet),
            "025/198",
            "PIKACHU VMAX VIVID VOLTAGE 025/198"
        )

        assertEquals("a", ranked.first().id)
    }

    @Test
    fun nameOnlyLookupDoesNotPretendThereIsAnExactNumberMatch() {
        val ranked = rankCardMatches(
            listOf(numberOnly, nameAndSet),
            null,
            "PIKACHU VMAX VIVID VOLTAGE"
        )
        assertEquals(listOf("a", "b"), ranked.map { it.id })
    }

    @Test
    fun leavesSingleCandidateUnchanged() {
        assertEquals(listOf(nameAndSet), rankCardMatches(listOf(nameAndSet), "025/198", "Pikachu VMAX"))
    }

    @Test
    fun deterministicTieBreakUsesCandidateId() {
        val second = numberOnly.copy(id = "z")
        val first = numberOnly.copy(id = "a")
        assertEquals(listOf("a", "z"), rankCardMatches(listOf(second, first), "025/198", "unknown text").map { it.id })
    }

    @Test
    fun cjkNameEmbeddedInLongerOcrLineRanksFirst() {
        // The Chinese recognizer returns one long undivided run; the candidate whose
        // name overlaps the OCR text must rank above an unrelated candidate.
        val chinese = CardMatchCandidate("zh", "皮卡丘", "朱＆紫", "025/198")
        val other = CardMatchCandidate("ja", "パラス", "バイオレット", "011/078")
        val ocrLine = "宝可梦卡牌皮卡丘基本宝可梦HP60"
        assertEquals("zh", rankCardMatches(listOf(other, chinese), null, ocrLine).first().id)
    }

    @Test
    fun shortCjkCandidateNamesRankToo() {
        val mewtwo = CardMatchCandidate("a", "超梦", "朱＆紫", "078/198")
        val unrelated = CardMatchCandidate("b", "喷火龙", "朱＆紫", "201/198")
        assertEquals("a", rankCardMatches(listOf(unrelated, mewtwo), null, "超梦 HP 130").first().id)
    }
}
