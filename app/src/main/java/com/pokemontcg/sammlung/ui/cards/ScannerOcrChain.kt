package com.pokemontcg.sammlung.ui.cards

/**
 * Pure helpers for the multi-script OCR fallback chain of the card scanner.
 *
 * ML Kit ships one on-device model per script: the Latin model (de/en/fr/es/it/pt/
 * nl/pl/id ...) is completely blind to Han, Kana and Hangul, and the Asian models
 * only cover their own script. The scanner therefore chains the Latin pass with
 * the Japanese, Korean and Chinese models whenever the Latin result alone cannot
 * identify the card, then merges the most informative texts.
 *
 * All functions here are pure so the chain decisions are unit-testable without
 * a camera or ML Kit.
 */

/**
 * True when the Latin pass alone cannot identify the card and the Asian script
 * models should run on the same frame:
 *
 * 1. No card number was readable at all - the standard fallback case - or
 * 2. a number was found but the text carries neither searchable name tokens nor
 *    CJK characters. A Latin print always contributes name text, so this shape
 *    means "Asian print whose name the Latin model cannot see"; the script models
 *    can recover the name for ranking while the Latin number stays usable.
 */
internal fun needsScriptFallback(latinText: String): Boolean {
    if (extractCardNumber(latinText) == null) return true
    return extractOcrSearchTokens(latinText).isEmpty() && !containsCjk(latinText)
}

/**
 * Picks the most informative text from the Asian-model results: the one with the
 * most CJK characters, ties broken by total length. Returns null when no script
 * pass delivered CJK content, in which case the Latin text stays authoritative.
 */
internal fun pickScriptFallbackText(scriptTexts: List<String>): String? =
    scriptTexts
        .filter { it.isNotBlank() && containsCjk(it) }
        .maxWithOrNull(compareBy({ countCjkChars(it) }, { it.length }))

private fun countCjkChars(value: String): Int = value.count(::isCjkChar)

/**
 * Script clue implied by an OCR text, used to rank catalog candidates by language.
 * Kana only occur on Japanese prints; kanji without kana point to a Chinese print;
 * Hangul to Korean; Cyrillic to Russian. Latin script is deliberately ambiguous
 * (covers a dozen catalog languages) and yields no clue.
 */
internal fun detectOcrLanguageClue(text: String): String? {
    var hasKana = false
    var hasHangul = false
    var hasHan = false
    var hasCyrillic = false
    for (c in text) {
        val code = c.code
        when {
            code in 0x3040..0x30FF -> hasKana = true                      // Hiragana + Katakana
            code in 0xAC00..0xD7AF -> hasHangul = true                    // Hangul syllables
            code in 0x3400..0x9FFF || code in 0xF900..0xFAFF -> hasHan = true
            code in 0x0400..0x04FF -> hasCyrillic = true                  // Cyrillic
        }
    }
    return when {
        hasKana -> "ja"
        hasHangul -> "ko"
        hasHan -> "zh"
        hasCyrillic -> "ru"
        else -> null
    }
}
